package com.example.repositories

import androidx.room.withTransaction
import com.example.database.DailyStudyAggregateRow
import com.example.database.DeckEntity
import com.example.database.FlashcardEntity
import com.example.database.MemoDeckDatabase
import com.example.database.QuizHistoryEntity
import com.example.database.QuizHistoryJoinedRow
import com.example.database.StudyActivityEntity
import com.example.database.WeakCardEntity
import com.example.models.DeckSummary
import com.example.models.LearningStatus
import com.example.models.SearchCardResult
import com.example.models.SourceType
import com.example.models.WeakCardItem
import com.example.models.WeakReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoDeckRepository(
    private val database: MemoDeckDatabase
) {
    private val deckDao = database.deckDao()
    private val flashcardDao = database.flashcardDao()
    private val weakCardDao = database.weakCardDao()
    private val quizHistoryDao = database.quizHistoryDao()
    private val studyActivityDao = database.studyActivityDao()

    fun observeDeckSummaries(): Flow<List<DeckSummary>> =
        deckDao.observeDeckSummaries().map { rows ->
            rows.map { row ->
                DeckSummary(
                    deckId = row.deckId,
                    name = row.name,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    totalCards = row.totalCards,
                    rememberedCards = row.rememberedCards,
                    forgottenCards = row.forgottenCards,
                    unstudiedCards = row.unstudiedCards
                )
            }
        }

    fun observeDeckSummary(deckId: Long): Flow<DeckSummary?> =
        deckDao.observeDeckSummary(deckId).map { row ->
            row?.let {
                DeckSummary(
                    deckId = it.deckId,
                    name = it.name,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    totalCards = it.totalCards,
                    rememberedCards = it.rememberedCards,
                    forgottenCards = it.forgottenCards,
                    unstudiedCards = it.unstudiedCards
                )
            }
        }

    suspend fun getDeckById(deckId: Long): DeckEntity? = deckDao.getDeckById(deckId)

    suspend fun createDeck(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Deck name cannot be empty." }
        val now = System.currentTimeMillis()
        return deckDao.insertDeck(DeckEntity(name = trimmed, createdAt = now, updatedAt = now))
    }

    suspend fun renameDeck(deckId: Long, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "Deck name cannot be empty." }
        deckDao.renameDeck(deckId, trimmed, System.currentTimeMillis())
    }

    suspend fun deleteDeck(deckId: Long) {
        deckDao.deleteDeckById(deckId)
    }

    fun observeCardsByDeck(deckId: Long): Flow<List<FlashcardEntity>> =
        flashcardDao.observeCardsByDeck(deckId)

    suspend fun getCardsByDeckOnce(deckId: Long): List<FlashcardEntity> =
        flashcardDao.getCardsByDeckOnce(deckId)

    fun observeCardById(cardId: Long): Flow<FlashcardEntity?> =
        flashcardDao.observeCardById(cardId)

    suspend fun getCardByIdOnce(cardId: Long): FlashcardEntity? =
        flashcardDao.getCardByIdOnce(cardId)

    suspend fun addFlashcard(
        deckId: Long,
        front: String,
        back: String,
        sourceType: SourceType = SourceType.MANUAL,
        sourceName: String? = null,
        sourcePageNumber: Int? = null,
        sourceExcerpt: String? = null
    ): Long {
        val cleanFront = front.trim()
        val cleanBack = back.trim()
        require(cleanFront.isNotEmpty() && cleanBack.isNotEmpty()) {
            "Front and back cannot be empty."
        }
        val now = System.currentTimeMillis()
        return database.withTransaction {
            val nextOrder = flashcardDao.getMaxOrderInDeck(deckId) + 1
            val id = flashcardDao.insertCard(
                FlashcardEntity(
                    deckId = deckId,
                    front = cleanFront,
                    back = cleanBack,
                    originalOrder = nextOrder,
                    learningStatus = LearningStatus.UNSTUDIED,
                    createdAt = now,
                    updatedAt = now,
                    sourceType = sourceType,
                    sourceName = sourceName,
                    sourcePageNumber = sourcePageNumber,
                    sourceExcerpt = sourceExcerpt
                )
            )
            deckDao.touchDeck(deckId, now)
            id
        }
    }

    suspend fun addFlashcardsBatch(
        deckId: Long,
        cards: List<FlashcardInsertInput>
    ): List<Long> {
        if (cards.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return database.withTransaction {
            var nextOrder = flashcardDao.getMaxOrderInDeck(deckId) + 1
            val entities = cards.map { item ->
                FlashcardEntity(
                    deckId = deckId,
                    front = item.front.trim(),
                    back = item.back.trim(),
                    originalOrder = nextOrder++,
                    learningStatus = LearningStatus.UNSTUDIED,
                    createdAt = now,
                    updatedAt = now,
                    sourceType = item.sourceType,
                    sourceName = item.sourceName,
                    sourcePageNumber = item.sourcePageNumber,
                    sourceExcerpt = item.sourceExcerpt
                )
            }
            val ids = flashcardDao.insertCards(entities)
            deckDao.touchDeck(deckId, now)
            ids
        }
    }

    suspend fun updateFlashcardContent(cardId: Long, front: String, back: String) {
        val cleanFront = front.trim()
        val cleanBack = back.trim()
        require(cleanFront.isNotEmpty() && cleanBack.isNotEmpty()) {
            "Front and back cannot be empty."
        }
        val existing = flashcardDao.getCardByIdOnce(cardId) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            flashcardDao.updateCard(
                existing.copy(
                    front = cleanFront,
                    back = cleanBack,
                    updatedAt = now
                )
            )
            deckDao.touchDeck(existing.deckId, now)
        }
    }

    suspend fun deleteFlashcard(cardId: Long) {
        val existing = flashcardDao.getCardByIdOnce(cardId) ?: return
        database.withTransaction {
            flashcardDao.deleteCardById(cardId)
            deckDao.touchDeck(existing.deckId, System.currentTimeMillis())
        }
    }

    suspend fun reorderFlashcards(deckId: Long, orderedCardIds: List<Long>) {
        database.withTransaction {
            val existing = flashcardDao.getCardsByDeckOnce(deckId).associateBy { it.id }
            val updated = orderedCardIds.mapIndexedNotNull { index, id ->
                existing[id]?.copy(originalOrder = index)
            }
            if (updated.isNotEmpty()) {
                flashcardDao.updateCards(updated)
                deckDao.touchDeck(deckId, System.currentTimeMillis())
            }
        }
    }

    suspend fun markCardStudyStatus(
        cardId: Long,
        deckId: Long,
        status: LearningStatus
    ) {
        val now = System.currentTimeMillis()
        val todayKey = formatDateKey(now)
        database.withTransaction {
            flashcardDao.updateLearningStatus(cardId, status, now)
            if (status == LearningStatus.FORGOTTEN) {
                weakCardDao.upsertWeakCard(
                    WeakCardEntity(
                        cardId = cardId,
                        isWeak = true,
                        reason = WeakReason.FORGOTTEN_IN_STUDY,
                        updatedAt = now
                    )
                )
            }
            val existingActivity = studyActivityDao.findByDateAndDeck(todayKey, deckId)
            if (existingActivity == null) {
                studyActivityDao.insertActivity(
                    StudyActivityEntity(
                        dateKey = todayKey,
                        deckId = deckId,
                        cardsStudied = 1
                    )
                )
            } else {
                studyActivityDao.updateActivity(
                    existingActivity.copy(cardsStudied = existingActivity.cardsStudied + 1)
                )
            }
            deckDao.touchDeck(deckId, now)
        }
    }

    fun observeWeakCards(): Flow<List<WeakCardItem>> =
        weakCardDao.observeWeakCards().map { rows ->
            rows.map { row ->
                WeakCardItem(
                    cardId = row.cardId,
                    deckId = row.deckId,
                    deckName = row.deckName,
                    front = row.front,
                    back = row.back,
                    learningStatus = row.learningStatus,
                    reason = row.reason,
                    updatedAt = row.updatedAt
                )
            }
        }

    fun observeWeakStatus(cardId: Long): Flow<WeakCardEntity?> =
        weakCardDao.observeWeakStatus(cardId)

    suspend fun removeWeakStatus(cardId: Long) {
        weakCardDao.removeWeakStatus(cardId)
    }

    fun observeAllSearchableCards(): Flow<List<SearchCardResult>> =
        flashcardDao.observeAllSearchableCards().map { rows ->
            rows.map { row ->
                SearchCardResult(
                    cardId = row.cardId,
                    deckId = row.deckId,
                    deckName = row.deckName,
                    front = row.front,
                    back = row.back,
                    learningStatus = row.learningStatus,
                    isWeak = row.isWeak == 1
                )
            }
        }

    suspend fun recordCompletedQuiz(
        deckId: Long,
        totalQuestions: Int,
        correctAnswers: Int,
        wrongAnswers: Int,
        scorePercentage: Float,
        wrongCardIds: List<Long>
    ): Long {
        val now = System.currentTimeMillis()
        return database.withTransaction {
            val quizId = quizHistoryDao.insertQuizHistory(
                QuizHistoryEntity(
                    deckId = deckId,
                    totalQuestions = totalQuestions,
                    correctAnswers = correctAnswers,
                    wrongAnswers = wrongAnswers,
                    scorePercentage = scorePercentage,
                    completionDate = now
                )
            )
            wrongCardIds.distinct().forEach { cardId ->
                weakCardDao.upsertWeakCard(
                    WeakCardEntity(
                        cardId = cardId,
                        isWeak = true,
                        reason = WeakReason.INCORRECT_IN_QUIZ,
                        updatedAt = now
                    )
                )
            }
            deckDao.touchDeck(deckId, now)
            quizId
        }
    }

    fun observeQuizHistory(): Flow<List<QuizHistoryJoinedRow>> =
        quizHistoryDao.observeQuizHistory()

    fun observeDailyStudyTotals(): Flow<List<DailyStudyAggregateRow>> =
        studyActivityDao.observeDailyStudyTotals()

    suspend fun getAllDecksOnce(): List<DeckEntity> = deckDao.getAllDecksOnce()
    suspend fun getAllCardsOnce(): List<FlashcardEntity> = flashcardDao.getAllCardsOnce()
    suspend fun getAllWeakCardsOnce(): List<WeakCardEntity> = weakCardDao.getAllWeakCardsOnce()
    suspend fun getAllQuizHistoryOnce(): List<QuizHistoryEntity> = quizHistoryDao.getAllQuizHistoryOnce()
    suspend fun getAllStudyActivityOnce(): List<StudyActivityEntity> = studyActivityDao.getAllStudyActivityOnce()

    suspend fun replaceAllDatabaseData(
        decks: List<DeckEntity>,
        cards: List<FlashcardEntity>,
        weakCards: List<WeakCardEntity>,
        quizHistory: List<QuizHistoryEntity>,
        studyActivity: List<StudyActivityEntity>
    ) {
        database.withTransaction {
            weakCardDao.deleteAllWeakCards()
            quizHistoryDao.deleteAllQuizHistory()
            studyActivityDao.deleteAllStudyActivity()
            flashcardDao.deleteAllCards()
            deckDao.deleteAllDecks()

            if (decks.isNotEmpty()) deckDao.insertDecks(decks)
            if (cards.isNotEmpty()) flashcardDao.insertCards(cards)
            if (weakCards.isNotEmpty()) weakCardDao.insertWeakCards(weakCards)
            if (quizHistory.isNotEmpty()) quizHistoryDao.insertQuizHistories(quizHistory)
            if (studyActivity.isNotEmpty()) studyActivityDao.insertActivities(studyActivity)
        }
    }

    suspend fun clearAllDatabaseData() {
        database.withTransaction {
            weakCardDao.deleteAllWeakCards()
            quizHistoryDao.deleteAllQuizHistory()
            studyActivityDao.deleteAllStudyActivity()
            flashcardDao.deleteAllCards()
            deckDao.deleteAllDecks()
        }
    }

    companion object {
        fun formatDateKey(timestampMillis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date(timestampMillis))
        }
    }
}

data class FlashcardInsertInput(
    val front: String,
    val back: String,
    val sourceType: SourceType = SourceType.MANUAL,
    val sourceName: String? = null,
    val sourcePageNumber: Int? = null,
    val sourceExcerpt: String? = null
)
