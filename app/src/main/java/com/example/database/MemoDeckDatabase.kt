package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.models.LearningStatus
import com.example.models.SourceType
import com.example.models.WeakReason

class MemoDeckTypeConverters {
    @TypeConverter
    fun fromLearningStatus(status: LearningStatus): String = status.name

    @TypeConverter
    fun toLearningStatus(value: String?): LearningStatus = LearningStatus.fromNameOrDefault(value)

    @TypeConverter
    fun fromWeakReason(reason: WeakReason): String = reason.name

    @TypeConverter
    fun toWeakReason(value: String?): WeakReason = WeakReason.fromNameOrDefault(value)

    @TypeConverter
    fun fromSourceType(type: SourceType?): String? = type?.name

    @TypeConverter
    fun toSourceType(value: String?): SourceType? = SourceType.fromNameOrNull(value)
}

@Database(
    entities = [
        DeckEntity::class,
        FlashcardEntity::class,
        WeakCardEntity::class,
        QuizHistoryEntity::class,
        StudyActivityEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(MemoDeckTypeConverters::class)
abstract class MemoDeckDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun weakCardDao(): WeakCardDao
    abstract fun quizHistoryDao(): QuizHistoryDao
    abstract fun studyActivityDao(): StudyActivityDao

    companion object {
        @Volatile
        private var INSTANCE: MemoDeckDatabase? = null

        fun getInstance(context: Context): MemoDeckDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoDeckDatabase::class.java,
                    "memodeck_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
