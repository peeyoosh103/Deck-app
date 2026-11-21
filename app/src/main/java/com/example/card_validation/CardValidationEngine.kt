package com.example.card_validation

import com.example.models.CardQualityStatus
import com.example.models.GeneratedCardCandidate
import com.example.models.SourceType

/**
 * Reusable offline flashcard quality validation and duplicate-detection engine.
 * Enforces Section 13 & Section 14 global accuracy rules:
 * - Never marks uncertain content as Verified Against Source
 * - Detects empty fields, duplicates, repeated facts, altered numbers/dates,
 *   unsupported content, OCR noise, incomplete fragments, and Q-A mismatch.
 */
object CardValidationEngine {

    private val numberOrDateRegex = Regex("\\b\\d{1,4}(?:[.,:/-]\\d{1,4})*%?\\b")
    private val garbledSymbolRegex = Regex("[|~`^\\\\]{2,}|[^\\p{L}\\p{N}\\p{P}\\p{Z}\\p{M}]{2,}")
    private val wordTokenRegex = Regex("[\\p{L}\\p{M}\\p{N}]{2,}")

    fun normalizeForComparison(text: String): String {
        val collapsed = text.trim().replace(Regex("\\s+"), " ")
        return collapsed.lowercase()
    }

    fun buildDuplicateKey(front: String, back: String): String =
        "${normalizeForComparison(front)}||${normalizeForComparison(back)}"

    data class ValidationOutcome(
        val status: CardQualityStatus,
        val notes: List<String>
    )

    fun validateCandidate(
        front: String,
        back: String,
        sourceText: String?,
        ocrConfidence: Float = 1.0f,
        existingDeckCardKeys: Set<String> = emptySet(),
        existingDeckFronts: Set<String> = emptySet(),
        seenBatchCardKeys: Set<String> = emptySet(),
        seenBatchFronts: Set<String> = emptySet()
    ): ValidationOutcome {
        val cleanFront = front.trim()
        val cleanBack = back.trim()
        val rejectReasons = mutableListOf<String>()
        val reviewReasons = mutableListOf<String>()

        // 1. Empty checks
        if (cleanFront.isEmpty()) rejectReasons.add("Question (Front) is empty.")
        if (cleanBack.isEmpty()) rejectReasons.add("Answer (Back) is empty.")

        // 2. Duplicate checks (exact card & duplicate question)
        val normFront = normalizeForComparison(cleanFront)
        val cardKey = buildDuplicateKey(cleanFront, cleanBack)
        if (cleanFront.isNotEmpty() && cleanBack.isNotEmpty()) {
            if (cardKey in existingDeckCardKeys || cardKey in seenBatchCardKeys) {
                rejectReasons.add("Duplicate flashcard already exists.")
            } else if (normFront in existingDeckFronts || normFront in seenBatchFronts) {
                rejectReasons.add("Duplicate question detected.")
            }
        }

        // 3. Incomplete / meaningless / Q-A mismatch checks
        if (cleanFront.length in 1..2) {
            rejectReasons.add("Question is too short or incomplete.")
        }
        if (cleanBack.length == 1 && !cleanBack.first().isDigit()) {
            rejectReasons.add("Answer appears incomplete.")
        }
        if (cleanFront.isNotEmpty() && normFront == normalizeForComparison(cleanBack)) {
            rejectReasons.add("Question and answer are identical.")
        }

        // 4. OCR noise / readability check
        val hasGarbledFront = garbledSymbolRegex.containsMatchIn(cleanFront)
        val hasGarbledBack = garbledSymbolRegex.containsMatchIn(cleanBack)
        if (hasGarbledFront || hasGarbledBack) {
            if (ocrConfidence < 0.5f) {
                rejectReasons.add("Unreadable OCR characters detected.")
            } else {
                reviewReasons.add("Possible OCR artifacts detected; verify spelling.")
            }
        }

        if (ocrConfidence < 0.55f) {
            rejectReasons.add("OCR confidence is too low for reliable extraction.")
        } else if (ocrConfidence < 0.80f) {
            reviewReasons.add("Moderate OCR confidence; verify text against original.")
        }

        // 5. Source verification checks (when source text is available)
        if (sourceText != null) {
            val cleanSource = sourceText.trim()
            if (cleanSource.isEmpty()) {
                rejectReasons.add("No reliable source text available.")
            } else {
                val normSource = normalizeForComparison(cleanSource)

                // Verify numbers & dates in front/back exist in source (prevent altered numbers/dates)
                val sourceNumbers = numberOrDateRegex.findAll(cleanSource).map { it.value }.toSet()
                val cardNumbers = (numberOrDateRegex.findAll(cleanFront).map { it.value } +
                    numberOrDateRegex.findAll(cleanBack).map { it.value }).toSet()
                val alteredNumbers = cardNumbers.filter { num ->
                    num !in sourceNumbers && !normSource.contains(num.lowercase())
                }
                if (alteredNumbers.isNotEmpty()) {
                    rejectReasons.add("Contains numbers or dates not found in source: ${alteredNumbers.joinToString(", ")}.")
                }

                // Verify lexical grounding of answer against source
                val sourceTokens = extractContentTokens(normSource)
                val backTokens = extractContentTokens(normalizeForComparison(cleanBack))
                if (backTokens.isNotEmpty()) {
                    val supportedBackCount = backTokens.count { token ->
                        token in sourceTokens || normSource.contains(token)
                    }
                    val backSupportRatio = supportedBackCount.toFloat() / backTokens.size.toFloat()
                    if (backSupportRatio < 0.45f) {
                        rejectReasons.add("Answer is not sufficiently supported by source text.")
                    } else if (backSupportRatio < 0.80f) {
                        reviewReasons.add("Answer phrasing partially differs from source; please verify.")
                    }
                }

                // Verify question terms are grounded in source (excluding common question words)
                val frontTokens = extractContentTokens(normalizeForComparison(cleanFront))
                    .filterNot { it in QUESTION_STOP_WORDS }
                if (frontTokens.isNotEmpty()) {
                    val supportedFrontCount = frontTokens.count { token ->
                        token in sourceTokens || normSource.contains(token)
                    }
                    val frontSupportRatio = supportedFrontCount.toFloat() / frontTokens.size.toFloat()
                    if (frontSupportRatio < 0.35f) {
                        rejectReasons.add("Question references terms not present in source text.")
                    } else if (frontSupportRatio < 0.65f) {
                        reviewReasons.add("Question wording should be reviewed against source.")
                    }
                }

                // Incomplete sentence / trailing truncation warning
                if (cleanBack.endsWith("...") || cleanFront.endsWith("...") ||
                    cleanBack.endsWith(",") || cleanBack.endsWith("and") || cleanBack.endsWith("और")
                ) {
                    reviewReasons.add("Content may be incomplete; review before importing.")
                }
            }
        } else {
            reviewReasons.add("Source excerpt unavailable for automatic verification.")
        }

        return when {
            rejectReasons.isNotEmpty() -> ValidationOutcome(
                status = CardQualityStatus.REJECTED,
                notes = rejectReasons + reviewReasons
            )
            reviewReasons.isNotEmpty() -> ValidationOutcome(
                status = CardQualityStatus.NEEDS_REVIEW,
                notes = reviewReasons
            )
            else -> ValidationOutcome(
                status = CardQualityStatus.VERIFIED_AGAINST_SOURCE,
                notes = listOf("Directly supported by source excerpt. Please review before saving.")
            )
        }
    }

    /**
     * Re-validates an entire batch of candidates (e.g., after a user edits or deletes a card).
     */
    fun revalidateBatch(
        candidates: List<GeneratedCardCandidate>,
        existingDeckCardKeys: Set<String>,
        existingDeckFronts: Set<String>,
        ocrConfidenceProvider: (GeneratedCardCandidate) -> Float = { 1.0f }
    ): List<GeneratedCardCandidate> {
        val seenKeys = mutableSetOf<String>()
        val seenFronts = mutableSetOf<String>()

        return candidates.map { candidate ->
            val outcome = validateCandidate(
                front = candidate.front,
                back = candidate.back,
                sourceText = candidate.sourceExcerpt,
                ocrConfidence = ocrConfidenceProvider(candidate),
                existingDeckCardKeys = existingDeckCardKeys,
                existingDeckFronts = existingDeckFronts,
                seenBatchCardKeys = seenKeys,
                seenBatchFronts = seenFronts
            )
            if (outcome.status != CardQualityStatus.REJECTED) {
                seenKeys.add(buildDuplicateKey(candidate.front, candidate.back))
                seenFronts.add(normalizeForComparison(candidate.front))
            }
            val shouldSelect = if (outcome.status == CardQualityStatus.REJECTED) {
                false
            } else {
                candidate.selected
            }
            candidate.copy(
                qualityStatus = outcome.status,
                validationNotes = outcome.notes,
                selected = shouldSelect
            )
        }
    }

    private fun extractContentTokens(normalizedText: String): List<String> {
        return wordTokenRegex.findAll(normalizedText)
            .map { it.value }
            .filter { it.length >= 2 && it !in COMMON_STOP_WORDS }
            .toList()
    }

    private val COMMON_STOP_WORDS = setOf(
        "the", "is", "are", "was", "were", "am", "be", "been", "being",
        "in", "on", "at", "to", "for", "of", "by", "with", "from", "as",
        "an", "or", "and", "it", "its", "this", "that", "these", "those",
        "का", "के", "की", "को", "में", "से", "पर", "है", "हैं", "था", "थे", "थी", "और", "या", "एक"
    )

    private val QUESTION_STOP_WORDS = setOf(
        "what", "who", "when", "where", "why", "how", "which", "define", "explain",
        "describe", "state", "name", "difference", "between", "author", "wrote",
        "provision", "article", "event", "happened", "year", "located", "capital",
        "meaning", "significance", "cause", "consequence", "result", "value",
        "क्या", "कौन", "कब", "कहाँ", "क्यों", "कैसे", "किसने", "किस", "परिभाषा",
        "अर्थ", "लेखक", "अनुच्छेद", "घटना", "अंतर", "कारण", "परिणाम", "बताइए"
    )
}
