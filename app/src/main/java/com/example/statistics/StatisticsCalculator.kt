package com.example.statistics

import com.example.database.DailyStudyAggregateRow
import com.example.database.QuizHistoryJoinedRow
import com.example.models.DailyStudyPoint
import com.example.models.DeckSummary
import com.example.models.HomeStats
import com.example.models.QuizHistoryPoint
import com.example.models.QuizStatsSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pure reusable statistics calculator strictly derived from actual local database records.
 */
object StatisticsCalculator {

    fun calculateHomeStats(decks: List<DeckSummary>): HomeStats {
        return HomeStats(
            totalDecks = decks.size,
            totalFlashcards = decks.sumOf { it.totalCards },
            rememberedCards = decks.sumOf { it.rememberedCards },
            forgottenCards = decks.sumOf { it.forgottenCards }
        )
    }

    fun calculateDeckProgressPercentage(rememberedCards: Int, totalCards: Int): Float {
        if (totalCards <= 0) return 0f
        return (rememberedCards.toFloat() / totalCards.toFloat()) * 100f
    }

    fun calculateQuizStats(history: List<QuizHistoryJoinedRow>): QuizStatsSummary {
        if (history.isEmpty()) return QuizStatsSummary()
        val sortedByDate = history.sortedBy { it.completionDate }
        val highest = sortedByDate.maxOf { it.scorePercentage }
        val average = sortedByDate.map { it.scorePercentage.toDouble() }.average().toFloat()
        val last = sortedByDate.last().scorePercentage
        return QuizStatsSummary(
            totalCompletedQuizzes = sortedByDate.size,
            highestScore = highest,
            averageScore = average,
            lastQuizScore = last
        )
    }

    /**
     * Builds the recent daily study activity timeline (last [days] days ending today),
     * filling days with 0 when no cards were studied so the chart reflects actual activity cleanly.
     */
    fun buildRecentDailyStudyPoints(
        rows: List<DailyStudyAggregateRow>,
        days: Int = 7,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DailyStudyPoint> {
        val lookup = rows.associate { it.dateKey to it.totalCardsStudied }
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayFormat = SimpleDateFormat("MMM d", Locale.US)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }

        val points = mutableListOf<DailyStudyPoint>()
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
        repeat(days) {
            val date = cal.time
            val key = keyFormat.format(date)
            points.add(
                DailyStudyPoint(
                    dateKey = key,
                    displayLabel = displayFormat.format(date),
                    cardsStudied = lookup[key] ?: 0
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return points
    }

    fun buildQuizHistoryPoints(
        rows: List<QuizHistoryJoinedRow>,
        maxPoints: Int = 15
    ): List<QuizHistoryPoint> {
        val labelFormat = SimpleDateFormat("MMM d", Locale.US)
        return rows.sortedBy { it.completionDate }
            .takeLast(maxPoints)
            .map { row ->
                QuizHistoryPoint(
                    quizId = row.quizId,
                    deckName = row.deckName,
                    scorePercentage = row.scorePercentage,
                    completionDate = row.completionDate,
                    label = labelFormat.format(Date(row.completionDate))
                )
            }
    }
}
