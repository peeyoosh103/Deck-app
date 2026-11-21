package com.example.backup

import com.example.database.DeckEntity
import com.example.database.FlashcardEntity
import com.example.database.QuizHistoryEntity
import com.example.database.StudyActivityEntity
import com.example.database.WeakCardEntity
import com.example.models.AppSettings
import com.example.models.LearningStatus
import com.example.models.QuizQuestionCountOption
import com.example.models.SourceType
import com.example.models.ThemeMode
import com.example.models.WeakReason
import com.example.repositories.MemoDeckRepository
import com.example.repositories.SettingsRepository
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val version: Int,
    val exportedAt: Long,
    val decks: List<DeckEntity>,
    val flashcards: List<FlashcardEntity>,
    val weakCards: List<WeakCardEntity>,
    val quizHistory: List<QuizHistoryEntity>,
    val studyActivity: List<StudyActivityEntity>,
    val settings: AppSettings
)

sealed class BackupValidationResult {
    data class Valid(val payload: BackupPayload) : BackupValidationResult()
    data class Invalid(val errorMessage: String) : BackupValidationResult()
}

/**
 * Complete offline Backup & Restore manager.
 * Serializes and validates all app data including:
 * - Decks, Flashcards, Original Order, Learning Statuses, Source Metadata
 * - Weak Card Statuses, Quiz History, Study Activity, and Settings
 * Strictly validates before replacing any local data.
 */
class BackupManager(
    private val repository: MemoDeckRepository,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private const val FORMAT_IDENTIFIER = "MEMODECK_OFFLINE_BACKUP"
    }

    suspend fun exportToJson(): String {
        val decks = repository.getAllDecksOnce()
        val cards = repository.getAllCardsOnce()
        val weakCards = repository.getAllWeakCardsOnce()
        val quizHistory = repository.getAllQuizHistoryOnce()
        val studyActivity = repository.getAllStudyActivityOnce()
        val settings = settingsRepository.getSettingsOnce()

        val root = JSONObject().apply {
            put("format", FORMAT_IDENTIFIER)
            put("version", CURRENT_SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())

            put("settings", JSONObject().apply {
                put("themeMode", settings.themeMode.name)
                put("defaultShuffle", settings.defaultShuffle)
                put("defaultQuizQuestionCount", settings.defaultQuizQuestionCount.name)
            })

            put("decks", JSONArray().apply {
                decks.forEach { deck ->
                    put(JSONObject().apply {
                        put("id", deck.id)
                        put("name", deck.name)
                        put("createdAt", deck.createdAt)
                        put("updatedAt", deck.updatedAt)
                    })
                }
            })

            put("flashcards", JSONArray().apply {
                cards.forEach { card ->
                    put(JSONObject().apply {
                        put("id", card.id)
                        put("deckId", card.deckId)
                        put("front", card.front)
                        put("back", card.back)
                        put("originalOrder", card.originalOrder)
                        put("learningStatus", card.learningStatus.name)
                        put("createdAt", card.createdAt)
                        put("updatedAt", card.updatedAt)
                        put("sourceType", card.sourceType?.name ?: JSONObject.NULL)
                        put("sourceName", card.sourceName ?: JSONObject.NULL)
                        put("sourcePageNumber", card.sourcePageNumber ?: JSONObject.NULL)
                        put("sourceExcerpt", card.sourceExcerpt ?: JSONObject.NULL)
                    })
                }
            })

            put("weakCards", JSONArray().apply {
                weakCards.forEach { weak ->
                    put(JSONObject().apply {
                        put("cardId", weak.cardId)
                        put("isWeak", weak.isWeak)
                        put("reason", weak.reason.name)
                        put("updatedAt", weak.updatedAt)
                    })
                }
            })

            put("quizHistory", JSONArray().apply {
                quizHistory.forEach { q ->
                    put(JSONObject().apply {
                        put("id", q.id)
                        put("deckId", q.deckId)
                        put("totalQuestions", q.totalQuestions)
                        put("correctAnswers", q.correctAnswers)
                        put("wrongAnswers", q.wrongAnswers)
                        put("scorePercentage", q.scorePercentage.toDouble())
                        put("completionDate", q.completionDate)
                    })
                }
            })

            put("studyActivity", JSONArray().apply {
                studyActivity.forEach { act ->
                    put(JSONObject().apply {
                        put("id", act.id)
                        put("dateKey", act.dateKey)
                        put("deckId", act.deckId ?: JSONObject.NULL)
                        put("cardsStudied", act.cardsStudied)
                    })
                }
            })
        }

        return root.toString(2)
    }

    fun validateBackupJson(jsonContent: String): BackupValidationResult {
        if (jsonContent.isBlank()) {
            return BackupValidationResult.Invalid("Backup file is empty.")
        }

        val root = try {
            JSONObject(jsonContent.removePrefix("\uFEFF"))
        } catch (e: Exception) {
            return BackupValidationResult.Invalid("Invalid JSON syntax: ${e.message ?: "Corrupted file."}")
        }

        if (root.optString("format") != FORMAT_IDENTIFIER) {
            return BackupValidationResult.Invalid("This file is not a valid MemoDeck backup.")
        }

        val version = root.optInt("version", -1)
        if (version !in 1..CURRENT_SCHEMA_VERSION) {
            return BackupValidationResult.Invalid("Unsupported backup version: $version.")
        }

        val decksArray = root.optJSONArray("decks")
            ?: return BackupValidationResult.Invalid("Backup is missing \"decks\" data.")
        val cardsArray = root.optJSONArray("flashcards")
            ?: return BackupValidationResult.Invalid("Backup is missing \"flashcards\" data.")
        val weakArray = root.optJSONArray("weakCards")
            ?: return BackupValidationResult.Invalid("Backup is missing \"weakCards\" data.")
        val quizArray = root.optJSONArray("quizHistory")
            ?: return BackupValidationResult.Invalid("Backup is missing \"quizHistory\" data.")
        val activityArray = root.optJSONArray("studyActivity")
            ?: return BackupValidationResult.Invalid("Backup is missing \"studyActivity\" data.")
        val settingsObj = root.optJSONObject("settings")
            ?: return BackupValidationResult.Invalid("Backup is missing \"settings\" data.")

        return try {
            val decks = mutableListOf<DeckEntity>()
            val deckIds = mutableSetOf<Long>()
            for (i in 0 until decksArray.length()) {
                val obj = decksArray.getJSONObject(i)
                val id = obj.getLong("id")
                val name = obj.getString("name").trim()
                if (name.isEmpty()) {
                    return BackupValidationResult.Invalid("Backup contains a deck with an empty name.")
                }
                deckIds.add(id)
                decks.add(
                    DeckEntity(
                        id = id,
                        name = name,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val cards = mutableListOf<FlashcardEntity>()
            val cardIds = mutableSetOf<Long>()
            for (i in 0 until cardsArray.length()) {
                val obj = cardsArray.getJSONObject(i)
                val id = obj.getLong("id")
                val deckId = obj.getLong("deckId")
                if (deckId !in deckIds) {
                    return BackupValidationResult.Invalid("Backup contains a flashcard referencing a missing deck.")
                }
                val front = obj.getString("front").trim()
                val back = obj.getString("back").trim()
                if (front.isEmpty() || back.isEmpty()) {
                    return BackupValidationResult.Invalid("Backup contains a flashcard with empty front or back text.")
                }
                cardIds.add(id)
                cards.add(
                    FlashcardEntity(
                        id = id,
                        deckId = deckId,
                        front = front,
                        back = back,
                        originalOrder = obj.optInt("originalOrder", i),
                        learningStatus = LearningStatus.fromNameOrDefault(obj.optString("learningStatus")),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        sourceType = if (obj.isNull("sourceType")) null else SourceType.fromNameOrNull(obj.optString("sourceType")),
                        sourceName = if (obj.isNull("sourceName")) null else obj.optString("sourceName"),
                        sourcePageNumber = if (obj.isNull("sourcePageNumber")) null else obj.optInt("sourcePageNumber"),
                        sourceExcerpt = if (obj.isNull("sourceExcerpt")) null else obj.optString("sourceExcerpt")
                    )
                )
            }

            val weakCards = mutableListOf<WeakCardEntity>()
            for (i in 0 until weakArray.length()) {
                val obj = weakArray.getJSONObject(i)
                val cardId = obj.getLong("cardId")
                if (cardId !in cardIds) continue
                weakCards.add(
                    WeakCardEntity(
                        cardId = cardId,
                        isWeak = obj.optBoolean("isWeak", true),
                        reason = WeakReason.fromNameOrDefault(obj.optString("reason")),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val quizHistory = mutableListOf<QuizHistoryEntity>()
            for (i in 0 until quizArray.length()) {
                val obj = quizArray.getJSONObject(i)
                val deckId = obj.getLong("deckId")
                if (deckId !in deckIds) continue
                quizHistory.add(
                    QuizHistoryEntity(
                        id = obj.optLong("id", 0L),
                        deckId = deckId,
                        totalQuestions = obj.getInt("totalQuestions"),
                        correctAnswers = obj.getInt("correctAnswers"),
                        wrongAnswers = obj.getInt("wrongAnswers"),
                        scorePercentage = obj.getDouble("scorePercentage").toFloat(),
                        completionDate = obj.optLong("completionDate", System.currentTimeMillis())
                    )
                )
            }

            val studyActivity = mutableListOf<StudyActivityEntity>()
            for (i in 0 until activityArray.length()) {
                val obj = activityArray.getJSONObject(i)
                val dateKey = obj.getString("dateKey")
                val deckId = if (obj.isNull("deckId")) null else obj.optLong("deckId")
                studyActivity.add(
                    StudyActivityEntity(
                        id = obj.optLong("id", 0L),
                        dateKey = dateKey,
                        deckId = deckId,
                        cardsStudied = obj.getInt("cardsStudied")
                    )
                )
            }

            val settings = AppSettings(
                themeMode = ThemeMode.fromNameOrDefault(settingsObj.optString("themeMode")),
                defaultShuffle = settingsObj.optBoolean("defaultShuffle", false),
                defaultQuizQuestionCount = QuizQuestionCountOption.fromNameOrDefault(
                    settingsObj.optString("defaultQuizQuestionCount")
                )
            )

            BackupValidationResult.Valid(
                BackupPayload(
                    version = version,
                    exportedAt = root.optLong("exportedAt", System.currentTimeMillis()),
                    decks = decks,
                    flashcards = cards,
                    weakCards = weakCards,
                    quizHistory = quizHistory,
                    studyActivity = studyActivity,
                    settings = settings
                )
            )
        } catch (e: Exception) {
            BackupValidationResult.Invalid("Backup integrity check failed: ${e.message ?: "Invalid fields."}")
        }
    }

    suspend fun restoreFromPayload(payload: BackupPayload) {
        repository.replaceAllDatabaseData(
            decks = payload.decks,
            cards = payload.flashcards,
            weakCards = payload.weakCards,
            quizHistory = payload.quizHistory,
            studyActivity = payload.studyActivity
        )
        settingsRepository.replaceSettings(payload.settings)
    }
}
