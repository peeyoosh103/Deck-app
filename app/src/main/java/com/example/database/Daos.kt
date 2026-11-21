package com.example.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.models.LearningStatus
import com.example.models.WeakReason
import kotlinx.coroutines.flow.Flow

data class DeckSummaryRow(
    val deckId: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val totalCards: Int,
    val rememberedCards: Int,
    val forgottenCards: Int,
    val unstudiedCards: Int
)

data class WeakCardJoinedRow(
    val cardId: Long,
    val deckId: Long,
    val deckName: String,
    val front: String,
    val back: String,
    val learningStatus: LearningStatus,
    val reason: WeakReason,
    val updatedAt: Long
)

data class SearchCardJoinedRow(
    val cardId: Long,
    val deckId: Long,
    val deckName: String,
    val front: String,
    val back: String,
    val learningStatus: LearningStatus,
    val isWeak: Int
)

data class QuizHistoryJoinedRow(
    val quizId: Long,
    val deckId: Long,
    val deckName: String,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val scorePercentage: Float,
    val completionDate: Long
)

data class DailyStudyAggregateRow(
    val dateKey: String,
    val totalCardsStudied: Int
)

@Dao
interface DeckDao {
    @Query(
        """
        SELECT d.id AS deckId,
               d.name AS name,
               d.createdAt AS createdAt,
               d.updatedAt AS updatedAt,
               COUNT(f.id) AS totalCards,
               COALESCE(SUM(CASE WHEN f.learningStatus = 'REMEMBERED' THEN 1 ELSE 0 END), 0) AS rememberedCards,
               COALESCE(SUM(CASE WHEN f.learningStatus = 'FORGOTTEN' THEN 1 ELSE 0 END), 0) AS forgottenCards,
               COALESCE(SUM(CASE WHEN f.learningStatus = 'UNSTUDIED' THEN 1 ELSE 0 END), 0) AS unstudiedCards
        FROM decks d
        LEFT JOIN flashcards f ON d.id = f.deckId
        GROUP BY d.id
        ORDER BY d.updatedAt DESC
        """
    )
    fun observeDeckSummaries(): Flow<List<DeckSummaryRow>>

    @Query(
        """
        SELECT d.id AS deckId,
               d.name AS name,
               d.createdAt AS createdAt,
               d.updatedAt AS updatedAt,
               COUNT(f.id) AS totalCards,
               COALESCE(SUM(CASE WHEN f.learningStatus = 'REMEMBERED' THEN 1 ELSE 0 END), 0) AS rememberedCards,
               COALESCE(SUM(CASE WHEN f.learningStatus = 'FORGOTTEN' THEN 1 ELSE 0 END), 0) AS forgottenCards,
               COALESCE(SUM(CASE WHEN f.learningStatus = 'UNSTUDIED' THEN 1 ELSE 0 END), 0) AS unstudiedCards
        FROM decks d
        LEFT JOIN flashcards f ON d.id = f.deckId
        WHERE d.id = :deckId
        GROUP BY d.id
        """
    )
    fun observeDeckSummary(deckId: Long): Flow<DeckSummaryRow?>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    suspend fun getDeckById(deckId: Long): DeckEntity?

    @Query("SELECT * FROM decks ORDER BY updatedAt DESC")
    suspend fun getAllDecksOnce(): List<DeckEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: DeckEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecks(decks: List<DeckEntity>)

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Query("UPDATE decks SET name = :name, updatedAt = :updatedAt WHERE id = :deckId")
    suspend fun renameDeck(deckId: Long, name: String, updatedAt: Long)

    @Query("UPDATE decks SET updatedAt = :updatedAt WHERE id = :deckId")
    suspend fun touchDeck(deckId: Long, updatedAt: Long)

    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun deleteDeckById(deckId: Long)

    @Query("DELETE FROM decks")
    suspend fun deleteAllDecks()
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY originalOrder ASC, id ASC")
    fun observeCardsByDeck(deckId: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY originalOrder ASC, id ASC")
    suspend fun getCardsByDeckOnce(deckId: Long): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE id = :cardId")
    fun observeCardById(cardId: Long): Flow<FlashcardEntity?>

    @Query("SELECT * FROM flashcards WHERE id = :cardId")
    suspend fun getCardByIdOnce(cardId: Long): FlashcardEntity?

    @Query("SELECT * FROM flashcards ORDER BY deckId ASC, originalOrder ASC, id ASC")
    suspend fun getAllCardsOnce(): List<FlashcardEntity>

    @Query("SELECT COALESCE(MAX(originalOrder), -1) FROM flashcards WHERE deckId = :deckId")
    suspend fun getMaxOrderInDeck(deckId: Long): Int

    @Query(
        """
        SELECT f.id AS cardId,
               f.deckId AS deckId,
               d.name AS deckName,
               f.front AS front,
               f.back AS back,
               f.learningStatus AS learningStatus,
               CASE WHEN w.cardId IS NOT NULL AND w.isWeak = 1 THEN 1 ELSE 0 END AS isWeak
        FROM flashcards f
        INNER JOIN decks d ON f.deckId = d.id
        LEFT JOIN weak_cards w ON f.id = w.cardId
        ORDER BY f.updatedAt DESC
        """
    )
    fun observeAllSearchableCards(): Flow<List<SearchCardJoinedRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: FlashcardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<FlashcardEntity>): List<Long>

    @Update
    suspend fun updateCard(card: FlashcardEntity)

    @Update
    suspend fun updateCards(cards: List<FlashcardEntity>)

    @Query("UPDATE flashcards SET learningStatus = :status, updatedAt = :updatedAt WHERE id = :cardId")
    suspend fun updateLearningStatus(cardId: Long, status: LearningStatus, updatedAt: Long)

    @Delete
    suspend fun deleteCard(card: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE id = :cardId")
    suspend fun deleteCardById(cardId: Long)

    @Query("DELETE FROM flashcards")
    suspend fun deleteAllCards()
}

@Dao
interface WeakCardDao {
    @Query(
        """
        SELECT w.cardId AS cardId,
               f.deckId AS deckId,
               d.name AS deckName,
               f.front AS front,
               f.back AS back,
               f.learningStatus AS learningStatus,
               w.reason AS reason,
               w.updatedAt AS updatedAt
        FROM weak_cards w
        INNER JOIN flashcards f ON w.cardId = f.id
        INNER JOIN decks d ON f.deckId = d.id
        WHERE w.isWeak = 1
        ORDER BY w.updatedAt DESC
        """
    )
    fun observeWeakCards(): Flow<List<WeakCardJoinedRow>>

    @Query("SELECT * FROM weak_cards WHERE cardId = :cardId")
    fun observeWeakStatus(cardId: Long): Flow<WeakCardEntity?>

    @Query("SELECT * FROM weak_cards WHERE isWeak = 1")
    suspend fun getAllWeakCardsOnce(): List<WeakCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeakCard(weakCard: WeakCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeakCards(weakCards: List<WeakCardEntity>)

    @Query("DELETE FROM weak_cards WHERE cardId = :cardId")
    suspend fun removeWeakStatus(cardId: Long)

    @Query("DELETE FROM weak_cards")
    suspend fun deleteAllWeakCards()
}

@Dao
interface QuizHistoryDao {
    @Query(
        """
        SELECT q.id AS quizId,
               q.deckId AS deckId,
               d.name AS deckName,
               q.totalQuestions AS totalQuestions,
               q.correctAnswers AS correctAnswers,
               q.wrongAnswers AS wrongAnswers,
               q.scorePercentage AS scorePercentage,
               q.completionDate AS completionDate
        FROM quiz_history q
        INNER JOIN decks d ON q.deckId = d.id
        ORDER BY q.completionDate ASC
        """
    )
    fun observeQuizHistory(): Flow<List<QuizHistoryJoinedRow>>

    @Query("SELECT * FROM quiz_history ORDER BY completionDate ASC")
    suspend fun getAllQuizHistoryOnce(): List<QuizHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizHistory(entry: QuizHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizHistories(entries: List<QuizHistoryEntity>)

    @Query("DELETE FROM quiz_history")
    suspend fun deleteAllQuizHistory()
}

@Dao
interface StudyActivityDao {
    @Query(
        """
        SELECT dateKey, SUM(cardsStudied) AS totalCardsStudied
        FROM study_activity
        GROUP BY dateKey
        ORDER BY dateKey ASC
        """
    )
    fun observeDailyStudyTotals(): Flow<List<DailyStudyAggregateRow>>

    @Query("SELECT * FROM study_activity WHERE dateKey = :dateKey AND ((:deckId IS NULL AND deckId IS NULL) OR deckId = :deckId) LIMIT 1")
    suspend fun findByDateAndDeck(dateKey: String, deckId: Long?): StudyActivityEntity?

    @Query("SELECT * FROM study_activity ORDER BY dateKey ASC")
    suspend fun getAllStudyActivityOnce(): List<StudyActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: StudyActivityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<StudyActivityEntity>)

    @Update
    suspend fun updateActivity(activity: StudyActivityEntity)

    @Query("DELETE FROM study_activity")
    suspend fun deleteAllStudyActivity()
}
