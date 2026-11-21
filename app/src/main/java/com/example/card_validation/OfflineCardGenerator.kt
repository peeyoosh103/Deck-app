package com.example.card_validation

import com.example.models.CardQualityStatus
import com.example.models.GeneratedCardCandidate
import com.example.models.SourceType
import java.util.UUID

/**
 * Conservative, 100% offline rule-based flashcard generator for English, Hindi (Devanagari),
 * and mixed-language study notes (Images and PDFs).
 *
 * Strictly adheres to:
 * - Uses ONLY facts explicitly present in source text
 * - Never invents knowledge or alters numbers, dates, or names
 * - Preserves source language (Hindi -> Hindi cards, English -> English cards)
 * - Tracks source excerpt and page number without polluting Front or Back text
 * - Marks uncertain extractions as Needs Review rather than Verified
 */
object OfflineCardGenerator {

    private val devanagariRegex = Regex("[\\u0900-\\u097F]")

    // Explicit Q/A headers
    private val qaQuestionPrefix = Regex("^(?:Q\\.?\\s*\\d*\\s*[:.)\\-]|Question\\s*\\d*\\s*[:.\\-]|प्रश्न\\s*\\d*\\s*[:.\\-])\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val qaAnswerPrefix = Regex("^(?:A\\.?\\s*\\d*\\s*[:.)\\-]|Ans\\.?\\s*\\d*\\s*[:.)\\-]|Answer\\s*\\d*\\s*[:.\\-]|उत्तर\\s*\\d*\\s*[:.\\-])\\s*(.+)$", RegexOption.IGNORE_CASE)

    // Constitutional Articles (English & Hindi)
    private val enArticleRegex = Regex("^(Article\\s+\\d+[A-Za-z0-9()\\-]*)\\s*(?:[-:–—]|deals with|provides(?: for)?|relates to|states that|guarantees|prohibits|establishes|defines)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val hiArticleRegex = Regex("^(अनुच्छेद\\s+\\d+[क-हA-Za-z0-9()\\-]*)\\s*(?:[-:–—]|में|के अंतर्गत|से संबंधित|का संबंध)\\s*(.+)$")

    // Books & Authors (English & Hindi)
    private val enBookAuthorRegex = Regex("^(.{3,80}?)\\s+(?:was written by|is written by|was authored by|is authored by)\\s+(.{2,80})$", RegexOption.IGNORE_CASE)
    private val enAuthorWroteRegex = Regex("^([A-Z][A-Za-z.\\s]{2,50}?)\\s+(?:wrote|authored)\\s+(.{3,90})$")
    private val hiBookAuthorRegex = Regex("^(.{3,80}?)\\s+(?:के लेखक|की रचना|द्वारा लिखी गई|लिखी)\\s+(.{2,80})$")

    // Dates / Events (English & Hindi)
    private val enDatePrefixRegex = Regex("^(?:In|On)\\s+((?:\\d{1,2}\\s+[A-Za-z]+\\s+)?\\d{3,4}(?:\\s*(?:BC|BCE|AD|CE))?)\\s*,?\\s+(.{10,200})$", RegexOption.IGNORE_CASE)
    private val enEventYearRegex = Regex("^(.{5,110}?)\\s+(?:took place in|occurred in|happened in|was founded in|was established in|was signed in|was passed in|began in|started in|ended in)\\s+((?:\\d{1,2}\\s+[A-Za-z]+\\s+)?\\d{3,4}(?:\\s*(?:BC|BCE|AD|CE))?.*)$", RegexOption.IGNORE_CASE)
    private val hiDateRegex = Regex("^(.{5,120}?)\\s+(\\d{3,4}\\s*(?:ई\\.?|ईसा पूर्व)?\\s*में)\\s+(.{5,120})$")

    // Definitions & Explanations (English & Hindi)
    private val enDefinitionRegex = Regex("^([A-Za-z0-9][A-Za-z0-9\\s(),/'\\-]{2,70}?)\\s+(is defined as|refers to|means|is known as|is called|is the process of|is the study of|is the capital of|is located in|is situated in|causes|leads to|results in|is measured in|SI unit is)\\s+(.{4,240})$", RegexOption.IGNORE_CASE)
    private val hiDefinitionRegex = Regex("^(.{2,70}?)\\s+(का अर्थ|को कहते हैं|कहलाता है|कहलाती है|की राजधानी|का मात्रक|स्थित है|का कारण)\\s+(.{3,220})$")

    // Key : Value or Term - Definition lines (supports bullets/numbering)
    private val bulletPrefixRegex = Regex("^(?:[•●▪\\-*]|\\d+[.)]|\\([a-zA-Z0-9]+\\))\\s*")
    private val colonOrDashSplitRegex = Regex("^([^:–—\\-]{2,80}?)\\s*(?::|–|—|\\s-\\s)\\s*(.{4,280})$")

    // Difference / Comparison
    private val differenceRegex = Regex("^(?:Difference between|Comparison between|Distinction between)\\s+(.{3,90}?)\\s*[:\\-–—]\\s*(.{8,300})$", RegexOption.IGNORE_CASE)

    fun generateFromText(
        rawText: String,
        sourceType: SourceType,
        sourceName: String?,
        sourcePageNumber: Int? = null,
        ocrConfidence: Float = 1.0f,
        existingDeckCardKeys: Set<String> = emptySet(),
        existingDeckFronts: Set<String> = emptySet()
    ): List<GeneratedCardCandidate> {
        val cleanedText = rawText.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (cleanedText.isBlank()) return emptyList()

        val rawPairs = mutableListOf<RawExtraction>()

        // Pass 1: Extract explicit multi-line Q & A pairs first
        val lines = cleanedText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val consumedLineIndices = mutableSetOf<Int>()
        var i = 0
        while (i < lines.size) {
            val qMatch = qaQuestionPrefix.matchEntire(lines[i])
            if (qMatch != null && i + 1 < lines.size) {
                val aMatch = qaAnswerPrefix.matchEntire(lines[i + 1])
                if (aMatch != null) {
                    val qText = qMatch.groupValues[1].trim()
                    val aText = aMatch.groupValues[1].trim()
                    if (qText.isNotBlank() && aText.isNotBlank()) {
                        rawPairs.add(
                            RawExtraction(
                                front = qText,
                                back = aText,
                                excerpt = "${lines[i]}\n${lines[i + 1]}",
                                isUncertain = false
                            )
                        )
                        consumedLineIndices.add(i)
                        consumedLineIndices.add(i + 1)
                        i += 2
                        continue
                    }
                }
            }
            i++
        }

        // Pass 2: Extract structured lines and factual sentences from remaining text
        val remainingLines = lines.filterIndexed { idx, _ -> idx !in consumedLineIndices }
        for (line in remainingLines) {
            val stripped = bulletPrefixRegex.replaceFirst(line, "").trim()
            if (stripped.length < 10) continue

            // Try direct line-level structured extractors
            val lineExtraction = extractFromUnit(stripped)
            if (lineExtraction != null) {
                rawPairs.add(lineExtraction)
                continue
            }

            // Otherwise split paragraph into sentences (English '.'/'?' and Hindi purna viram '।')
            val sentences = splitIntoSentences(stripped)
            for (sentence in sentences) {
                if (sentence.length < 12) continue
                val sentenceExtraction = extractFromUnit(sentence)
                if (sentenceExtraction != null) {
                    rawPairs.add(sentenceExtraction)
                }
            }
        }

        // Validate every generated card against its source excerpt
        val seenKeys = mutableSetOf<String>()
        val seenFronts = mutableSetOf<String>()
        val results = mutableListOf<GeneratedCardCandidate>()

        for (raw in rawPairs) {
            val effectiveConfidence = if (raw.isUncertain) minOf(ocrConfidence, 0.72f) else ocrConfidence
            val validation = CardValidationEngine.validateCandidate(
                front = raw.front,
                back = raw.back,
                sourceText = raw.excerpt,
                ocrConfidence = effectiveConfidence,
                existingDeckCardKeys = existingDeckCardKeys,
                existingDeckFronts = existingDeckFronts,
                seenBatchCardKeys = seenKeys,
                seenBatchFronts = seenFronts
            )
            if (validation.status != CardQualityStatus.REJECTED) {
                seenKeys.add(CardValidationEngine.buildDuplicateKey(raw.front, raw.back))
                seenFronts.add(CardValidationEngine.normalizeForComparison(raw.front))
            }

            val notes = buildList {
                if (raw.isUncertain) {
                    add("Heuristic extraction from general text; please verify wording.")
                }
                addAll(validation.notes)
            }.distinct()

            results.add(
                GeneratedCardCandidate(
                    id = UUID.randomUUID().toString(),
                    front = raw.front.trim(),
                    back = raw.back.trim(),
                    qualityStatus = validation.status,
                    validationNotes = notes,
                    selected = validation.status == CardQualityStatus.VERIFIED_AGAINST_SOURCE,
                    sourceType = sourceType,
                    sourceName = sourceName,
                    sourcePageNumber = sourcePageNumber,
                    sourceExcerpt = raw.excerpt.trim()
                )
            )
        }

        return results
    }

    private fun extractFromUnit(unit: String): RawExtraction? {
        val text = unit.trim().trimEnd('.', '।')
        val isHindi = isPredominantlyHindi(text)

        // 1. Difference / Comparison
        differenceRegex.matchEntire(text)?.let { m ->
            val subjects = m.groupValues[1].trim()
            val detail = m.groupValues[2].trim()
            return RawExtraction(
                front = "What is the difference between $subjects?",
                back = detail,
                excerpt = unit,
                isUncertain = false
            )
        }

        // 2. Constitutional Articles (English & Hindi)
        enArticleRegex.matchEntire(text)?.let { m ->
            val article = m.groupValues[1].trim()
            val provision = m.groupValues[2].trim()
            return RawExtraction(
                front = "What does $article provide?",
                back = provision,
                excerpt = unit,
                isUncertain = false
            )
        }
        hiArticleRegex.matchEntire(text)?.let { m ->
            val article = m.groupValues[1].trim()
            val provision = m.groupValues[2].trim()
            return RawExtraction(
                front = "$article किससे संबंधित है?",
                back = provision,
                excerpt = unit,
                isUncertain = false
            )
        }

        // 3. Books & Authors
        enBookAuthorRegex.matchEntire(text)?.let { m ->
            val book = m.groupValues[1].trim()
            val author = m.groupValues[2].trim()
            return RawExtraction(
                front = "Who wrote $book?",
                back = author,
                excerpt = unit,
                isUncertain = false
            )
        }
        enAuthorWroteRegex.matchEntire(text)?.let { m ->
            val author = m.groupValues[1].trim()
            val book = m.groupValues[2].trim()
            return RawExtraction(
                front = "Who authored $book?",
                back = author,
                excerpt = unit,
                isUncertain = false
            )
        }
        hiBookAuthorRegex.matchEntire(text)?.let { m ->
            val book = m.groupValues[1].trim()
            val author = m.groupValues[2].trim()
            return RawExtraction(
                front = "$book के लेखक कौन हैं?",
                back = author,
                excerpt = unit,
                isUncertain = false
            )
        }

        // 4. Dates & Historical Events
        enDatePrefixRegex.matchEntire(text)?.let { m ->
            val date = m.groupValues[1].trim()
            val event = m.groupValues[2].trim()
            return RawExtraction(
                front = "What happened in $date?",
                back = event,
                excerpt = unit,
                isUncertain = false
            )
        }
        enEventYearRegex.matchEntire(text)?.let { m ->
            val event = m.groupValues[1].trim()
            val date = m.groupValues[2].trim()
            return RawExtraction(
                front = "When did $event take place?",
                back = date,
                excerpt = unit,
                isUncertain = false
            )
        }
        hiDateRegex.matchEntire(text)?.let { m ->
            val subject = m.groupValues[1].trim()
            val year = m.groupValues[2].trim()
            val rest = m.groupValues[3].trim()
            return RawExtraction(
                front = "$subject ($rest) कब हुआ था?",
                back = year.removeSuffix("में").trim(),
                excerpt = unit,
                isUncertain = true
            )
        }

        // 5. Definitions, Places, Causes & Scientific Concepts (English)
        enDefinitionRegex.matchEntire(text)?.let { m ->
            val term = m.groupValues[1].trim()
            val connector = m.groupValues[2].trim().lowercase()
            val value = m.groupValues[3].trim()
            if (term.split(" ").size <= 9 && value.length >= 3) {
                val question = when {
                    connector.contains("capital") -> "What is the capital of $term?"
                    connector.contains("located") || connector.contains("situated") -> "Where is $term located?"
                    connector.contains("causes") || connector.contains("leads to") || connector.contains("results in") -> "What does $term cause or lead to?"
                    connector.contains("measured in") || connector.contains("si unit") -> "What is the unit of $term?"
                    else -> "What is $term?"
                }
                return RawExtraction(
                    front = question,
                    back = value,
                    excerpt = unit,
                    isUncertain = false
                )
            }
        }

        // 6. Definitions & Facts (Hindi)
        hiDefinitionRegex.matchEntire(text)?.let { m ->
            val term = m.groupValues[1].trim()
            val connector = m.groupValues[2].trim()
            val value = m.groupValues[3].trim()
            val question = when {
                connector.contains("राजधानी") -> "$term की राजधानी क्या है?"
                connector.contains("मात्रक") -> "$term का मात्रक क्या है?"
                connector.contains("स्थित") -> "$term कहाँ स्थित है?"
                connector.contains("कारण") -> "$term का कारण क्या है?"
                else -> "$term क्या है?"
            }
            return RawExtraction(
                front = question,
                back = value,
                excerpt = unit,
                isUncertain = false
            )
        }

        // 7. Key : Value or Term — Definition notes (works cleanly for both English & Hindi!)
        colonOrDashSplitRegex.matchEntire(text)?.let { m ->
            val term = m.groupValues[1].trim()
            val explanation = m.groupValues[2].trim()
            val wordCount = term.split(Regex("\\s+")).size
            if (wordCount in 1..10 && explanation.length >= 3 && !term.startsWith("http", ignoreCase = true)) {
                val front = if (term.endsWith("?")) {
                    term
                } else if (isHindi) {
                    "$term क्या है?"
                } else {
                    term
                }
                return RawExtraction(
                    front = front,
                    back = explanation,
                    excerpt = unit,
                    isUncertain = wordCount > 7
                )
            }
        }

        return null
    }

    private fun splitIntoSentences(text: String): List<String> {
        return text.split(Regex("(?<=[.!?।])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun isPredominantlyHindi(text: String): Boolean {
        val devCount = devanagariRegex.findAll(text).count()
        val latinCount = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        return devCount > 0 && devCount >= latinCount
    }

    private data class RawExtraction(
        val front: String,
        val back: String,
        val excerpt: String,
        val isUncertain: Boolean
    )
}
