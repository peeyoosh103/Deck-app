package com.example.file_import

import com.example.card_validation.CardValidationEngine
import com.example.models.ImportCardCandidate
import com.example.models.SourceType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed class FileParseResult {
    data class Success(
        val sourceType: SourceType,
        val candidates: List<ImportCardCandidate>
    ) : FileParseResult()

    data class Error(val message: String) : FileParseResult()
}

/**
 * Independent offline JSON and RFC-4180 CSV parser with duplicate detection.
 * Supports:
 * - JSON: { "cards": [ { "front": "...", "back": "..." } ] } (ignores unknown fields)
 * - CSV: front,back headers (case-insensitive, any column order, quoted commas, multiline fields, UTF-8/Unicode/Hindi)
 */
object FileImportParser {

    fun parseFileContent(
        fileName: String,
        content: String,
        existingDeckCardKeys: Set<String>
    ): FileParseResult {
        val lower = fileName.lowercase()
        val cleaned = content.removePrefix("\uFEFF") // Strip UTF-8 BOM if present

        return when {
            lower.endsWith(".json") -> parseJson(cleaned, existingDeckCardKeys)
            lower.endsWith(".csv") -> parseCsv(cleaned, existingDeckCardKeys)
            else -> {
                // Fallback sniff when document provider omits file extension
                val trimmed = cleaned.trimStart()
                if (trimmed.startsWith("{")) {
                    parseJson(cleaned, existingDeckCardKeys)
                } else if (trimmed.isNotEmpty()) {
                    parseCsv(cleaned, existingDeckCardKeys)
                } else {
                    FileParseResult.Error("Unsupported or empty file. Please choose a valid .json or .csv file.")
                }
            }
        }
    }

    fun parseJson(
        content: String,
        existingDeckCardKeys: Set<String>
    ): FileParseResult {
        if (content.isBlank()) {
            return FileParseResult.Error("The JSON file is empty.")
        }
        val root = try {
            JSONObject(content)
        } catch (e: Exception) {
            return FileParseResult.Error("Invalid JSON format: ${e.message ?: "Unable to parse JSON object."}")
        }

        if (!root.has("cards")) {
            return FileParseResult.Error("Invalid JSON structure: missing required \"cards\" array.")
        }

        val cardsArray: JSONArray = root.optJSONArray("cards")
            ?: return FileParseResult.Error("Invalid JSON structure: \"cards\" must be a JSON array.")

        if (cardsArray.length() == 0) {
            return FileParseResult.Error("The \"cards\" array contains no flashcards.")
        }

        val rawPairs = mutableListOf<Pair<String, String>>()
        val structuralErrors = mutableMapOf<Int, String>()

        for (index in 0 until cardsArray.length()) {
            val item = cardsArray.optJSONObject(index)
            if (item == null) {
                rawPairs.add("" to "")
                structuralErrors[index] = "Entry #${index + 1} is not a valid JSON object."
                continue
            }
            val frontVal = if (item.isNull("front")) "" else item.optString("front", "")
            val backVal = if (item.isNull("back")) "" else item.optString("back", "")
            rawPairs.add(frontVal to backVal)
        }

        val candidates = evaluateCandidates(rawPairs, structuralErrors, existingDeckCardKeys)
        return FileParseResult.Success(SourceType.JSON_IMPORT, candidates)
    }

    fun parseCsv(
        content: String,
        existingDeckCardKeys: Set<String>
    ): FileParseResult {
        if (content.isBlank()) {
            return FileParseResult.Error("The CSV file is empty.")
        }

        val rows = try {
            parseRfc4180Csv(content)
        } catch (e: Exception) {
            return FileParseResult.Error("Malformed CSV file: ${e.message ?: "Unclosed quote or invalid syntax."}")
        }

        val nonEmptyRows = rows.filter { row -> row.any { it.isNotBlank() } }
        if (nonEmptyRows.isEmpty()) {
            return FileParseResult.Error("The CSV file has no rows.")
        }

        val headerRow = nonEmptyRows.first().map { it.trim().lowercase() }
        val frontIndex = headerRow.indexOfFirst { it == "front" }
        val backIndex = headerRow.indexOfFirst { it == "back" }

        if (frontIndex == -1 || backIndex == -1) {
            return FileParseResult.Error("Missing required CSV columns. Expected header columns named \"front\" and \"back\".")
        }

        if (nonEmptyRows.size < 2) {
            return FileParseResult.Error("The CSV file contains headers but no flashcard rows.")
        }

        val rawPairs = mutableListOf<Pair<String, String>>()
        for (rowIdx in 1 until nonEmptyRows.size) {
            val row = nonEmptyRows[rowIdx]
            val front = row.getOrNull(frontIndex) ?: ""
            val back = row.getOrNull(backIndex) ?: ""
            rawPairs.add(front to back)
        }

        val candidates = evaluateCandidates(rawPairs, emptyMap(), existingDeckCardKeys)
        return FileParseResult.Success(SourceType.CSV_IMPORT, candidates)
    }

    /**
     * Re-evaluates all candidates (for example after user edits or deletes a card in Import Preview).
     */
    fun revalidateCandidates(
        candidates: List<ImportCardCandidate>,
        existingDeckCardKeys: Set<String>,
        skipDuplicates: Boolean
    ): List<ImportCardCandidate> {
        val seenFileKeys = mutableSetOf<String>()
        return candidates.map { item ->
            val cleanFront = item.front.trim()
            val cleanBack = item.back.trim()
            val validationError = when {
                cleanFront.isEmpty() && cleanBack.isEmpty() -> "Front and Back are empty."
                cleanFront.isEmpty() -> "Front cannot be empty."
                cleanBack.isEmpty() -> "Back cannot be empty."
                else -> null
            }
            val isValid = validationError == null
            val key = if (isValid) CardValidationEngine.buildDuplicateKey(cleanFront, cleanBack) else ""
            val dupInDeck = isValid && key in existingDeckCardKeys
            val dupInFile = isValid && !dupInDeck && !seenFileKeys.add(key)
            val isDuplicate = dupInDeck || dupInFile
            val dupReason = when {
                dupInDeck -> "Already exists in this deck"
                dupInFile -> "Duplicate within imported file"
                else -> null
            }
            val selected = when {
                !isValid -> false
                isDuplicate && skipDuplicates -> false
                else -> item.selected
            }
            item.copy(
                isValid = isValid,
                validationError = validationError,
                isDuplicate = isDuplicate,
                duplicateReason = dupReason,
                selected = selected
            )
        }
    }

    private fun evaluateCandidates(
        rawPairs: List<Pair<String, String>>,
        structuralErrors: Map<Int, String>,
        existingDeckCardKeys: Set<String>
    ): List<ImportCardCandidate> {
        val seenFileKeys = mutableSetOf<String>()
        return rawPairs.mapIndexed { index, (front, back) ->
            val structuralErr = structuralErrors[index]
            val cleanFront = front.trim()
            val cleanBack = back.trim()
            val validationError = structuralErr ?: when {
                cleanFront.isEmpty() && cleanBack.isEmpty() -> "Front and Back are empty."
                cleanFront.isEmpty() -> "Front cannot be empty."
                cleanBack.isEmpty() -> "Back cannot be empty."
                else -> null
            }
            val isValid = validationError == null
            val key = if (isValid) CardValidationEngine.buildDuplicateKey(cleanFront, cleanBack) else ""
            val dupInDeck = isValid && key in existingDeckCardKeys
            val dupInFile = isValid && !dupInDeck && !seenFileKeys.add(key)
            val isDuplicate = dupInDeck || dupInFile
            val dupReason = when {
                dupInDeck -> "Already exists in this deck"
                dupInFile -> "Duplicate within imported file"
                else -> null
            }
            ImportCardCandidate(
                id = UUID.randomUUID().toString(),
                front = front,
                back = back,
                isValid = isValid,
                validationError = validationError,
                isDuplicate = isDuplicate,
                duplicateReason = dupReason,
                selected = isValid && !isDuplicate
            )
        }
    }

    /**
     * Full RFC-4180 compliant CSV parser handling:
     * - Commas inside quoted fields
     * - Escaped double quotes ("")
     * - Multiline quoted fields (\n and \r\n)
     * - Unicode / Hindi text
     */
    internal fun parseRfc4180Csv(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = input.length

        while (i < len) {
            val c = input[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && input[i + 1] == '"') {
                        currentField.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '"' -> {
                        if (currentField.isEmpty()) {
                            inQuotes = true
                        } else {
                            currentField.append(c)
                        }
                    }
                    ',' -> {
                        currentRow.add(currentField.toString())
                        currentField.clear()
                    }
                    '\r' -> {
                        currentRow.add(currentField.toString())
                        currentField.clear()
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                        if (i + 1 < len && input[i + 1] == '\n') {
                            i++
                        }
                    }
                    '\n' -> {
                        currentRow.add(currentField.toString())
                        currentField.clear()
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    else -> currentField.append(c)
                }
            }
            i++
        }

        if (inQuotes) {
            throw IllegalArgumentException("Unclosed quotation mark in CSV file.")
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            rows.add(currentRow)
        }

        return rows
    }
}
