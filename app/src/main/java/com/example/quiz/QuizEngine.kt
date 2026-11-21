package com.example.quiz

import com.example.database.FlashcardEntity
import com.example.models.QuizQuestion
import com.example.models.QuizQuestionCountOption
import kotlin.random.Random

/**
 * Pure, independent offline Multiple Choice Quiz generator.
 * Enforces all strict MCQ rules:
 * - Front = Question, Back = Correct Answer
 * - 3 distinct distractor answers from OTHER cards in the SAME deck
 * - Never duplicates options (compared via normalized answer text)
 * - Never repeats the same question card in one quiz
 * - Never invents fake answers; excludes any card that cannot form 4 unique options
 */
object QuizEngine {

    fun normalizeAnswer(text: String): String =
        text.trim().replace(Regex("\\s+"), " ").lowercase()

    fun countEligibleQuestions(cards: List<FlashcardEntity>): Int {
        val validCards = cards.filter { it.front.isNotBlank() && it.back.isNotBlank() }
        if (validCards.size < 4) return 0

        val normalizedBackCounts = validCards.groupingBy { normalizeAnswer(it.back) }.eachCount()
        val totalDistinctAnswers = normalizedBackCounts.size
        if (totalDistinctAnswers < 4) return 0

        // Every valid card has at least 3 distinct other answers when totalDistinctAnswers >= 4
        return validCards.size
    }

    fun generateQuiz(
        cards: List<FlashcardEntity>,
        countOption: QuizQuestionCountOption,
        random: Random = Random.Default
    ): List<QuizQuestion> {
        val validCards = cards.filter { it.front.isNotBlank() && it.back.isNotBlank() }
        if (validCards.size < 4) return emptyList()

        // Group unique answer representatives by normalized back text
        val shuffledPool = validCards.shuffled(random)
        val eligibleQuestions = mutableListOf<QuizQuestion>()
        val usedCardIds = mutableSetOf<Long>()

        for (questionCard in shuffledPool) {
            if (!usedCardIds.add(questionCard.id)) continue
            val correctNorm = normalizeAnswer(questionCard.back)

            // Collect 3 distinct distractors from OTHER cards in the same deck
            val distractorCandidates = validCards
                .asSequence()
                .filter { it.id != questionCard.id }
                .filter { normalizeAnswer(it.back) != correctNorm }
                .toList()
                .shuffled(random)

            val selectedDistractors = mutableListOf<String>()
            val seenOptionNorms = mutableSetOf(correctNorm)

            for (candidate in distractorCandidates) {
                val candNorm = normalizeAnswer(candidate.back)
                if (seenOptionNorms.add(candNorm)) {
                    selectedDistractors.add(candidate.back.trim())
                    if (selectedDistractors.size == 3) break
                }
            }

            // Exclude question if 4 unique options cannot be created (never invent fake answers)
            if (selectedDistractors.size < 3) continue

            val options = (selectedDistractors + questionCard.back.trim()).shuffled(random)
            eligibleQuestions.add(
                QuizQuestion(
                    cardId = questionCard.id,
                    question = questionCard.front.trim(),
                    correctAnswer = questionCard.back.trim(),
                    options = options
                )
            )
        }

        val targetLimit = countOption.count ?: eligibleQuestions.size
        return eligibleQuestions.take(targetLimit)
    }

    fun calculateAccuracyPercentage(correctAnswers: Int, attemptedQuestions: Int): Float {
        if (attemptedQuestions <= 0) return 0f
        return (correctAnswers.toFloat() / attemptedQuestions.toFloat()) * 100f
    }
}
