package com.example.models

enum class LearningStatus(val label: String) {
    UNSTUDIED("Unstudied"),
    REMEMBERED("Remembered"),
    FORGOTTEN("Forgotten");

    companion object {
        fun fromNameOrDefault(name: String?): LearningStatus =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: UNSTUDIED
    }
}

enum class WeakReason(val displayLabel: String) {
    FORGOTTEN_IN_STUDY("Forgotten in Study Mode"),
    INCORRECT_IN_QUIZ("Incorrect in Quiz");

    companion object {
        fun fromNameOrDefault(name: String?): WeakReason =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FORGOTTEN_IN_STUDY
    }
}

enum class ThemeMode(val displayLabel: String) {
    LIGHT("Light Mode"),
    DARK("Dark Mode"),
    SYSTEM_DEFAULT("System Default");

    companion object {
        fun fromNameOrDefault(name: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SYSTEM_DEFAULT
    }
}

enum class QuizQuestionCountOption(val count: Int?, val label: String) {
    FIVE(5, "5 Questions"),
    TEN(10, "10 Questions"),
    TWENTY(20, "20 Questions"),
    ALL(null, "All Questions");

    companion object {
        fun fromNameOrDefault(name: String?): QuizQuestionCountOption =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: TEN
    }
}

enum class SourceType(val displayLabel: String) {
    MANUAL("Manual"),
    IMAGE_OCR("Image OCR"),
    JSON_IMPORT("JSON Import"),
    CSV_IMPORT("CSV Import"),
    PDF_TEXT("PDF Notes"),
    PDF_OCR("Scanned PDF (OCR)");

    companion object {
        fun fromNameOrNull(name: String?): SourceType? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

enum class CardQualityStatus(val displayLabel: String) {
    VERIFIED_AGAINST_SOURCE("Verified Against Source"),
    NEEDS_REVIEW("Needs Review"),
    REJECTED("Rejected")
}

enum class SearchFilter(val label: String) {
    ALL("All Cards"),
    REMEMBERED("Remembered"),
    FORGOTTEN("Forgotten"),
    UNSTUDIED("Unstudied"),
    WEAK("Weak Cards")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM_DEFAULT,
    val defaultShuffle: Boolean = false,
    val defaultQuizQuestionCount: QuizQuestionCountOption = QuizQuestionCountOption.TEN
)

data class DeckSummary(
    val deckId: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val totalCards: Int,
    val rememberedCards: Int,
    val forgottenCards: Int,
    val unstudiedCards: Int
) {
    val progressPercentage: Float
        get() = if (totalCards <= 0) 0f else (rememberedCards.toFloat() / totalCards.toFloat()) * 100f
}

data class HomeStats(
    val totalDecks: Int = 0,
    val totalFlashcards: Int = 0,
    val rememberedCards: Int = 0,
    val forgottenCards: Int = 0
)

data class WeakCardItem(
    val cardId: Long,
    val deckId: Long,
    val deckName: String,
    val front: String,
    val back: String,
    val learningStatus: LearningStatus,
    val reason: WeakReason,
    val updatedAt: Long
)

data class SearchCardResult(
    val cardId: Long,
    val deckId: Long,
    val deckName: String,
    val front: String,
    val back: String,
    val learningStatus: LearningStatus,
    val isWeak: Boolean
)

data class QuizQuestion(
    val cardId: Long,
    val question: String,
    val correctAnswer: String,
    val options: List<String>
)

data class WrongAnswerRecord(
    val cardId: Long,
    val question: String,
    val selectedAnswer: String,
    val correctAnswer: String
)

data class QuizResultData(
    val deckId: Long,
    val deckName: String,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val accuracyPercentage: Float,
    val wrongRecords: List<WrongAnswerRecord>,
    val requestedCountOption: QuizQuestionCountOption
)

data class QuizStatsSummary(
    val totalCompletedQuizzes: Int = 0,
    val highestScore: Float = 0f,
    val averageScore: Float = 0f,
    val lastQuizScore: Float? = null
)

data class DailyStudyPoint(
    val dateKey: String, // YYYY-MM-DD
    val displayLabel: String, // e.g. Nov 20
    val cardsStudied: Int
)

data class QuizHistoryPoint(
    val quizId: Long,
    val deckName: String,
    val scorePercentage: Float,
    val completionDate: Long,
    val label: String
)

data class GeneratedCardCandidate(
    val id: String,
    val front: String,
    val back: String,
    val qualityStatus: CardQualityStatus,
    val validationNotes: List<String>,
    val selected: Boolean,
    val sourceType: SourceType,
    val sourceName: String?,
    val sourcePageNumber: Int? = null,
    val sourceExcerpt: String? = null
)

data class ImportCardCandidate(
    val id: String,
    val front: String,
    val back: String,
    val isValid: Boolean,
    val validationError: String?,
    val isDuplicate: Boolean,
    val duplicateReason: String?,
    val selected: Boolean
)

data class ImportSummaryResult(
    val importedCount: Int,
    val skippedDuplicatesCount: Int,
    val skippedInvalidCount: Int
)

enum class PageExtractionStatus(val label: String) {
    EMBEDDED_TEXT("Embedded Text"),
    SCANNED_OCR("Scanned (OCR)"),
    EMPTY("No Text Found"),
    FAILED("Extraction Failed")
}

data class ExtractedPageResult(
    val pageNumber: Int, // 1-based
    val text: String,
    val status: PageExtractionStatus,
    val ocrConfidence: Float, // 0f..1f (1f for embedded text)
    val included: Boolean = true,
    val errorMessage: String? = null
)
