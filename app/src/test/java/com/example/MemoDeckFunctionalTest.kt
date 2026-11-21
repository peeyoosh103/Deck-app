package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupManager
import com.example.backup.BackupValidationResult
import com.example.card_validation.CardValidationEngine
import com.example.card_validation.OfflineCardGenerator
import com.example.database.FlashcardEntity
import com.example.database.MemoDeckDatabase
import com.example.file_import.FileImportParser
import com.example.file_import.FileParseResult
import com.example.models.CardQualityStatus
import com.example.models.LearningStatus
import com.example.models.QuizQuestionCountOption
import com.example.models.SourceType
import com.example.models.ThemeMode
import com.example.models.WeakReason
import com.example.pdf_import.OfflinePdfProcessor
import com.example.quiz.QuizEngine
import com.example.repositories.MemoDeckRepository
import com.example.repositories.SettingsRepository
import com.example.statistics.StatisticsCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MemoDeckFunctionalTest {

    @Test
    fun `quiz engine enforces 4 unique options and excludes ineligible decks`() {
        val tooFew = listOf(
            FlashcardEntity(id = 1, deckId = 1, front = "Q1", back = "A1"),
            FlashcardEntity(id = 2, deckId = 1, front = "Q2", back = "A2"),
            FlashcardEntity(id = 3, deckId = 1, front = "Q3", back = "A3")
        )
        assertEquals(0, QuizEngine.countEligibleQuestions(tooFew))
        assertTrue(QuizEngine.generateQuiz(tooFew, QuizQuestionCountOption.FIVE).isEmpty())

        // Duplicate answers reduce distinct count below 4
        val duplicateAnswers = listOf(
            FlashcardEntity(id = 1, deckId = 1, front = "Q1", back = "India"),
            FlashcardEntity(id = 2, deckId = 1, front = "Q2", back = "india "),
            FlashcardEntity(id = 3, deckId = 1, front = "Q3", back = "Japan"),
            FlashcardEntity(id = 4, deckId = 1, front = "Q4", back = "France")
        )
        assertEquals(0, QuizEngine.countEligibleQuestions(duplicateAnswers))

        // Valid 6-card deck with unique answers
        val validDeck = (1..6).map { i ->
            FlashcardEntity(id = i.toLong(), deckId = 1, front = "Question $i", back = "Answer $i")
        }
        assertEquals(6, QuizEngine.countEligibleQuestions(validDeck))
        val questions = QuizEngine.generateQuiz(validDeck, QuizQuestionCountOption.FIVE, Random(42))
        assertEquals(5, questions.size)
        // No repeated question IDs
        assertEquals(5, questions.map { it.cardId }.distinct().size)
        questions.forEach { q ->
            assertEquals(4, q.options.size)
            assertEquals(4, q.options.map { QuizEngine.normalizeAnswer(it) }.distinct().size)
            assertTrue(q.options.contains(q.correctAnswer))
        }
        assertEquals(80f, QuizEngine.calculateAccuracyPercentage(8, 10), 0.01f)
    }

    @Test
    fun `json and csv import parsers handle unicode hindi multiline quotes invalid files and duplicates`() {
        val existingKey = CardValidationEngine.buildDuplicateKey("Article 21", "Protection of life and personal liberty")
        val json = """
            {
              "cards": [
                {"front": "Article 21", "back": "Protection of life and personal liberty", "extraField": 123},
                {"front": "भारत की राजधानी क्या है?", "back": "नई दिल्ली\nराष्ट्रीय राजधानी क्षेत्र"},
                {"front": "", "back": "Missing question"},
                {"front": "भारत की राजधानी क्या है?", "back": "नई दिल्ली\nराष्ट्रीय राजधानी क्षेत्र"}
              ]
            }
        """.trimIndent()

        val jsonRes = FileImportParser.parseFileContent("polity.json", json, setOf(existingKey))
        assertTrue(jsonRes is FileParseResult.Success)
        val jsonCards = (jsonRes as FileParseResult.Success).candidates
        assertEquals(4, jsonCards.size)
        assertTrue(jsonCards[0].isDuplicate) // Exists in deck
        assertTrue(jsonCards[1].isValid && !jsonCards[1].isDuplicate)
        assertFalse(jsonCards[2].isValid) // Empty front
        assertTrue(jsonCards[3].isDuplicate) // Duplicate in file

        val invalidJson = FileImportParser.parseFileContent("broken.json", "{ not json", emptySet())
        assertTrue(invalidJson is FileParseResult.Error)

        // CSV with reversed uppercase headers, quoted commas, multiline fields, Hindi
        val csv = """
            BACK,Front
            "Delhi, India","What is the capital
            of India?"
            "अनुच्छेद 32","संवैधानिक उपचारों का अधिकार"
        """.trimIndent()
        val csvRes = FileImportParser.parseFileContent("notes.csv", csv, emptySet())
        assertTrue(csvRes is FileParseResult.Success)
        val csvCards = (csvRes as FileParseResult.Success).candidates
        assertEquals(2, csvCards.size)
        assertEquals("Delhi, India", csvCards[0].back)
        assertTrue(csvCards[0].front.contains("\n"))
        assertEquals("संवैधानिक उपचारों का अधिकार", csvCards[1].front)

        val missingColsCsv = FileImportParser.parseFileContent("bad.csv", "term,meaning\nA,B", emptySet())
        assertTrue(missingColsCsv is FileParseResult.Error)
    }

    @Test
    fun `offline card generator and validation engine preserve source facts and reject altered numbers`() {
        val sampleNotes = """
            Article 17 deals with the abolition of untouchability.
            In 1950, the Constitution of India came into effect.
            Photosynthesis is defined as the process by which green plants make food using sunlight.
            अनुच्छेद 21 - प्राण और दैहिक स्वतंत्रता का संरक्षण।
        """.trimIndent()

        val generated = OfflineCardGenerator.generateFromText(
            rawText = sampleNotes,
            sourceType = SourceType.PDF_TEXT,
            sourceName = "Polity.pdf",
            sourcePageNumber = 4,
            ocrConfidence = 1.0f
        )
        assertTrue(generated.size >= 4)
        assertTrue(generated.any { it.qualityStatus == CardQualityStatus.VERIFIED_AGAINST_SOURCE })
        assertTrue(generated.all { it.sourcePageNumber == 4 })
        assertTrue(generated.any { it.front.contains("अनुच्छेद 21") })

        // Validation MUST reject altered year/number not in source
        val alteredValidation = CardValidationEngine.validateCandidate(
            front = "When did the Constitution come into effect?",
            back = "1947",
            sourceText = "In 1950, the Constitution of India came into effect.",
            ocrConfidence = 1.0f
        )
        assertEquals(CardQualityStatus.REJECTED, alteredValidation.status)
    }

    @Test
    fun `pdf page range spec parser validates ranges and bounds`() {
        val range = OfflinePdfProcessor.parsePageSelectionSpec("2-5", totalPages = 10)
        assertTrue(range.isSuccess)
        assertEquals(listOf(2, 3, 4, 5), range.getOrNull())

        val specific = OfflinePdfProcessor.parsePageSelectionSpec("1, 3, 7-9", totalPages = 10)
        assertTrue(specific.isSuccess)
        assertEquals(listOf(1, 3, 7, 8, 9), specific.getOrNull())

        val invalid = OfflinePdfProcessor.parsePageSelectionSpec("5-2", totalPages = 10)
        assertTrue(invalid.isFailure)

        val outOfBounds = OfflinePdfProcessor.parsePageSelectionSpec("12", totalPages = 10)
        assertTrue(outOfBounds.isFailure)
    }

    @Test
    fun `room repository weak cards quiz history and offline backup restore work end to end`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MemoDeckDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = MemoDeckRepository(db)
        val settingsRepo = SettingsRepository(context)
        val backupManager = BackupManager(repository, settingsRepo)

        try {
            val deckId = repository.createDeck("Indian Polity")
            repository.renameDeck(deckId, "Indian Polity & Constitution")
            val c1 = repository.addFlashcard(
                deckId = deckId,
                front = "Article 14",
                back = "Equality before law",
                sourceType = SourceType.PDF_TEXT,
                sourceName = "Laxmikanth.pdf",
                sourcePageNumber = 12,
                sourceExcerpt = "Article 14: Equality before law"
            )
            val c2 = repository.addFlashcard(deckId, "Article 21", "Protection of life and personal liberty")
            val c3 = repository.addFlashcard(deckId, "Article 32", "Right to Constitutional Remedies")
            val c4 = repository.addFlashcard(deckId, "Article 44", "Uniform Civil Code")

            // Reorder cards and verify originalOrder persists
            repository.reorderFlashcards(deckId, listOf(c4, c1, c2, c3))
            val ordered = repository.getCardsByDeckOnce(deckId)
            assertEquals(listOf(c4, c1, c2, c3), ordered.map { it.id })

            // Study marking -> Forgotten adds Weak Card; Remembered updates progress
            repository.markCardStudyStatus(c1, deckId, LearningStatus.REMEMBERED)
            repository.markCardStudyStatus(c2, deckId, LearningStatus.FORGOTTEN)
            val weakAfterStudy = repository.observeWeakCards().first()
            assertEquals(1, weakAfterStudy.size)
            assertEquals(c2, weakAfterStudy.first().cardId)
            assertEquals(WeakReason.FORGOTTEN_IN_STUDY, weakAfterStudy.first().reason)

            // Quiz completion -> marks wrong card weak (updates existing without duplicate)
            repository.recordCompletedQuiz(
                deckId = deckId,
                totalQuestions = 4,
                correctAnswers = 3,
                wrongAnswers = 1,
                scorePercentage = 75f,
                wrongCardIds = listOf(c2)
            )
            val weakAfterQuiz = repository.observeWeakCards().first()
            assertEquals(1, weakAfterQuiz.size)
            assertEquals(WeakReason.INCORRECT_IN_QUIZ, weakAfterQuiz.first().reason)

            // Stats calculation
            val summaries = repository.observeDeckSummaries().first()
            assertEquals(1, summaries.size)
            assertEquals(25f, summaries.first().progressPercentage, 0.01f)
            val homeStats = StatisticsCalculator.calculateHomeStats(summaries)
            assertEquals(4, homeStats.totalFlashcards)
            assertEquals(1, homeStats.rememberedCards)
            assertEquals(1, homeStats.forgottenCards)

            // Settings + Export Backup
            settingsRepo.setThemeMode(ThemeMode.DARK)
            settingsRepo.setDefaultShuffle(true)
            settingsRepo.setDefaultQuizQuestionCount(QuizQuestionCountOption.TWENTY)
            val backupJson = backupManager.exportToJson()

            // Validate invalid backup fails safely
            assertTrue(backupManager.validateBackupJson("{}") is BackupValidationResult.Invalid)

            // Clear all data then restore from valid backup
            repository.clearAllDatabaseData()
            settingsRepo.resetToDefaults()
            assertTrue(repository.getAllDecksOnce().isEmpty())

            val validation = backupManager.validateBackupJson(backupJson)
            assertTrue(validation is BackupValidationResult.Valid)
            backupManager.restoreFromPayload((validation as BackupValidationResult.Valid).payload)

            val restoredDecks = repository.getAllDecksOnce()
            val restoredCards = repository.getAllCardsOnce()
            val restoredQuiz = repository.getAllQuizHistoryOnce()
            val restoredSettings = settingsRepo.getSettingsOnce()
            assertEquals(1, restoredDecks.size)
            assertEquals("Indian Polity & Constitution", restoredDecks.first().name)
            assertEquals(4, restoredCards.size)
            assertEquals(12, restoredCards.first { it.id == c1 }.sourcePageNumber)
            assertEquals(1, restoredQuiz.size)
            assertEquals(ThemeMode.DARK, restoredSettings.themeMode)
            assertTrue(restoredSettings.defaultShuffle)
            assertEquals(QuizQuestionCountOption.TWENTY, restoredSettings.defaultQuizQuestionCount)

            // Deleting deck cascades cards, weak cards, and quiz history
            repository.deleteDeck(deckId)
            assertTrue(repository.getAllCardsOnce().isEmpty())
            assertTrue(repository.getAllWeakCardsOnce().isEmpty())
            assertTrue(repository.getAllQuizHistoryOnce().isEmpty())
            assertNotNull(backupJson)
        } finally {
            db.close()
        }
    }
}
