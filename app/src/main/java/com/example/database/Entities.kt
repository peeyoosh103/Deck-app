package com.example.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.models.LearningStatus
import com.example.models.SourceType
import com.example.models.WeakReason

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["deckId"]), Index(value = ["deckId", "originalOrder"])]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deckId: Long,
    val front: String,
    val back: String,
    val originalOrder: Int = 0,
    val learningStatus: LearningStatus = LearningStatus.UNSTUDIED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceType: SourceType? = SourceType.MANUAL,
    val sourceName: String? = null,
    val sourcePageNumber: Int? = null,
    val sourceExcerpt: String? = null
)

@Entity(
    tableName = "weak_cards",
    foreignKeys = [
        ForeignKey(
            entity = FlashcardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["cardId"], unique = true)]
)
data class WeakCardEntity(
    @PrimaryKey val cardId: Long,
    val isWeak: Boolean = true,
    val reason: WeakReason,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "quiz_history",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["deckId"]), Index(value = ["completionDate"])]
)
data class QuizHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deckId: Long,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val scorePercentage: Float,
    val completionDate: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_activity",
    indices = [Index(value = ["dateKey", "deckId"], unique = true)]
)
data class StudyActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateKey: String, // YYYY-MM-DD local date
    val deckId: Long? = null,
    val cardsStudied: Int = 0
)
