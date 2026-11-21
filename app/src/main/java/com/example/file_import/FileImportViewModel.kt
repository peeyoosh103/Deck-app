package com.example.file_import

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.card_validation.CardValidationEngine
import com.example.models.ImportCardCandidate
import com.example.models.ImportSummaryResult
import com.example.models.SourceType
import com.example.ocr.ImageBitmapUtils
import com.example.repositories.FlashcardInsertInput
import com.example.repositories.MemoDeckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FileImportStep {
    PICK_FILE,
    PREVIEW,
    COMPLETED
}

data class FileImportUiState(
    val deckId: Long = 0L,
    val step: FileImportStep = FileImportStep.PICK_FILE,
    val fileName: String = "",
    val sourceType: SourceType = SourceType.JSON_IMPORT,
    val isLoading: Boolean = false,
    val skipDuplicates: Boolean = true,
    val candidates: List<ImportCardCandidate> = emptyList(),
    val errorMessage: String? = null,
    val completionResult: ImportSummaryResult? = null
) {
    val totalFound: Int get() = candidates.size
    val validCount: Int get() = candidates.count { it.isValid }
    val invalidCount: Int get() = candidates.count { !it.isValid }
    val duplicateCount: Int get() = candidates.count { it.isValid && it.isDuplicate }
    val selectedImportableCount: Int
        get() = candidates.count { it.selected && it.isValid && (!skipDuplicates || !it.isDuplicate) }
}

class FileImportViewModel(
    private val deckId: Long,
    private val repository: MemoDeckRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileImportUiState(deckId = deckId))
    val uiState: StateFlow<FileImportUiState> = _uiState.asStateFlow()

    private var existingDeckCardKeys: Set<String> = emptySet()

    fun loadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = withContext(Dispatchers.IO) {
                val existing = repository.getCardsByDeckOnce(deckId)
                existingDeckCardKeys = existing.map {
                    CardValidationEngine.buildDuplicateKey(it.front, it.back)
                }.toSet()

                val name = ImageBitmapUtils.resolveDisplayName(context, uri, "import.json")
                val lower = name.lowercase()
                if (lower.contains(".") && !lower.endsWith(".json") && !lower.endsWith(".csv")) {
                    return@withContext Triple(
                        name,
                        FileParseResult.Error("Unsupported file format. Only JSON (.json) and CSV (.csv) files are supported."),
                        Unit
                    )
                }

                val text = try {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    }
                } catch (e: Exception) {
                    return@withContext Triple(
                        name,
                        FileParseResult.Error("Failed to read file: ${e.message ?: "Unable to open stream."}"),
                        Unit
                    )
                }

                if (text == null) {
                    return@withContext Triple(
                        name,
                        FileParseResult.Error("Unable to open the selected file."),
                        Unit
                    )
                }

                Triple(name, FileImportParser.parseFileContent(name, text, existingDeckCardKeys), Unit)
            }

            val fileName = result.first
            when (val parsed = result.second) {
                is FileParseResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            fileName = fileName,
                            errorMessage = parsed.message
                        )
                    }
                }
                is FileParseResult.Success -> {
                    _uiState.update {
                        it.copy(
                            step = FileImportStep.PREVIEW,
                            fileName = fileName,
                            sourceType = parsed.sourceType,
                            isLoading = false,
                            skipDuplicates = true,
                            candidates = parsed.candidates,
                            errorMessage = null
                        )
                    }
                }
            }
        }
    }

    fun toggleSkipDuplicates(skip: Boolean) {
        _uiState.update { state ->
            val updated = state.candidates.map { c ->
                if (!c.isValid) c.copy(selected = false)
                else if (c.isDuplicate) c.copy(selected = !skip)
                else c
            }
            state.copy(skipDuplicates = skip, candidates = updated)
        }
    }

    fun toggleSelect(id: String) {
        _uiState.update { state ->
            state.copy(
                candidates = state.candidates.map { c ->
                    val canToggle = c.isValid && (!state.skipDuplicates || !c.isDuplicate)
                    if (c.id == id && canToggle) c.copy(selected = !c.selected) else c
                }
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(
                candidates = state.candidates.map { c ->
                    val eligible = c.isValid && (!state.skipDuplicates || !c.isDuplicate)
                    c.copy(selected = eligible)
                }
            )
        }
    }

    fun deselectAll() {
        _uiState.update { state ->
            state.copy(candidates = state.candidates.map { it.copy(selected = false) })
        }
    }

    fun editCandidate(id: String, front: String, back: String) {
        val state = _uiState.value
        val edited = state.candidates.map { c ->
            if (c.id == id) c.copy(front = front, back = back, selected = true) else c
        }
        val revalidated = FileImportParser.revalidateCandidates(
            candidates = edited,
            existingDeckCardKeys = existingDeckCardKeys,
            skipDuplicates = state.skipDuplicates
        )
        _uiState.update { it.copy(candidates = revalidated) }
    }

    fun deleteCandidate(id: String) {
        val state = _uiState.value
        val remaining = state.candidates.filterNot { it.id == id }
        val revalidated = FileImportParser.revalidateCandidates(
            candidates = remaining,
            existingDeckCardKeys = existingDeckCardKeys,
            skipDuplicates = state.skipDuplicates
        )
        _uiState.update { it.copy(candidates = revalidated) }
    }

    fun importSelectedCards() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val summary = withContext(Dispatchers.IO) {
                // Re-read existing deck cards right before insert to prevent race/double-insertion duplicates
                val latestKeys = repository.getCardsByDeckOnce(deckId)
                    .map { CardValidationEngine.buildDuplicateKey(it.front, it.back) }
                    .toMutableSet()

                val toInsert = mutableListOf<FlashcardInsertInput>()
                var skippedDuplicates = 0
                var skippedInvalid = 0

                for (card in state.candidates) {
                    if (!card.isValid) {
                        skippedInvalid++
                        continue
                    }
                    val key = CardValidationEngine.buildDuplicateKey(card.front, card.back)
                    val isDupNow = key in latestKeys
                    if (isDupNow && state.skipDuplicates) {
                        skippedDuplicates++
                        continue
                    }
                    if (!card.selected) {
                        if (card.isDuplicate) skippedDuplicates++
                        continue
                    }
                    latestKeys.add(key)
                    toInsert.add(
                        FlashcardInsertInput(
                            front = card.front.trim(),
                            back = card.back.trim(),
                            sourceType = state.sourceType,
                            sourceName = state.fileName
                        )
                    )
                }

                if (toInsert.isNotEmpty()) {
                    repository.addFlashcardsBatch(deckId, toInsert)
                }

                ImportSummaryResult(
                    importedCount = toInsert.size,
                    skippedDuplicatesCount = skippedDuplicates,
                    skippedInvalidCount = skippedInvalid
                )
            }

            _uiState.update {
                it.copy(
                    step = FileImportStep.COMPLETED,
                    isLoading = false,
                    completionResult = summary
                )
            }
        }
    }

    class Factory(
        private val deckId: Long,
        private val repository: MemoDeckRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FileImportViewModel(deckId, repository) as T
    }
}
