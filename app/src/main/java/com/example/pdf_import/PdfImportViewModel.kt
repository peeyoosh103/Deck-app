package com.example.pdf_import

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.card_validation.CardValidationEngine
import com.example.card_validation.OfflineCardGenerator
import com.example.models.CardQualityStatus
import com.example.models.ExtractedPageResult
import com.example.models.GeneratedCardCandidate
import com.example.models.PageExtractionStatus
import com.example.models.SourceType
import com.example.repositories.FlashcardInsertInput
import com.example.repositories.MemoDeckRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PdfImportStep {
    PICK_PDF,
    PDF_PREVIEW,
    PROCESSING,
    EXTRACTED_TEXT_PREVIEW,
    CARD_PREVIEW
}

enum class PageSelectionMode(val label: String) {
    ALL("All Pages"),
    RANGE("Page Range"),
    SPECIFIC("Specific Pages")
}

data class PdfImportUiState(
    val deckId: Long = 0L,
    val step: PdfImportStep = PdfImportStep.PICK_PDF,
    val isLoading: Boolean = false,
    val pdfInfo: PdfDocumentInfo? = null,
    val currentPreviewPage: Int = 1, // 1-based
    val previewBitmap: Bitmap? = null,
    val selectionMode: PageSelectionMode = PageSelectionMode.ALL,
    val rangeStartInput: String = "1",
    val rangeEndInput: String = "1",
    val specificPagesInput: String = "1",
    val processedPagesCount: Int = 0,
    val totalSelectedPages: Int = 0,
    val currentProcessingPage: Int = 0,
    val extractedPages: List<ExtractedPageResult> = emptyList(),
    val candidates: List<GeneratedCardCandidate> = emptyList(),
    val errorMessage: String? = null
) {
    val totalGenerated: Int get() = candidates.size
    val verifiedCount: Int get() = candidates.count { it.qualityStatus == CardQualityStatus.VERIFIED_AGAINST_SOURCE }
    val needsReviewCount: Int get() = candidates.count { it.qualityStatus == CardQualityStatus.NEEDS_REVIEW }
    val rejectedCount: Int get() = candidates.count { it.qualityStatus == CardQualityStatus.REJECTED }
    val selectedValidCount: Int get() = candidates.count { it.selected && it.qualityStatus != CardQualityStatus.REJECTED }
}

class PdfImportViewModel(
    private val deckId: Long,
    private val repository: MemoDeckRepository,
    private val pdfProcessor: OfflinePdfProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfImportUiState(deckId = deckId))
    val uiState: StateFlow<PdfImportUiState> = _uiState.asStateFlow()

    private var processingJob: Job? = null
    private var existingDeckCardKeys: Set<String> = emptySet()
    private var existingDeckFronts: Set<String> = emptySet()

    fun openPdfUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = pdfProcessor.openPdf(uri)) {
                is PdfOpenResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
                is PdfOpenResult.Success -> {
                    val info = result.info
                    val firstBitmap = pdfProcessor.renderPagePreview(info.localCacheFile, 0)
                    _uiState.update {
                        it.copy(
                            step = PdfImportStep.PDF_PREVIEW,
                            isLoading = false,
                            pdfInfo = info,
                            currentPreviewPage = 1,
                            previewBitmap = firstBitmap,
                            selectionMode = PageSelectionMode.ALL,
                            rangeStartInput = "1",
                            rangeEndInput = info.pageCount.toString(),
                            specificPagesInput = "1",
                            errorMessage = null
                        )
                    }
                }
            }
        }
    }

    fun navigatePreviewPage(pageOneBased: Int) {
        val info = _uiState.value.pdfInfo ?: return
        val clamped = pageOneBased.coerceIn(1, info.pageCount)
        if (clamped == _uiState.value.currentPreviewPage) return
        viewModelScope.launch {
            val bmp = pdfProcessor.renderPagePreview(info.localCacheFile, clamped - 1)
            _uiState.update { it.copy(currentPreviewPage = clamped, previewBitmap = bmp) }
        }
    }

    fun selectMode(mode: PageSelectionMode) {
        _uiState.update { it.copy(selectionMode = mode, errorMessage = null) }
    }

    fun updateRangeStart(value: String) {
        _uiState.update { it.copy(rangeStartInput = value.filter { c -> c.isDigit() }, errorMessage = null) }
    }

    fun updateRangeEnd(value: String) {
        _uiState.update { it.copy(rangeEndInput = value.filter { c -> c.isDigit() }, errorMessage = null) }
    }

    fun updateSpecificPages(value: String) {
        _uiState.update { it.copy(specificPagesInput = value, errorMessage = null) }
    }

    fun toggleCurrentPageInSpecificSelection() {
        val page = _uiState.value.currentPreviewPage
        val total = _uiState.value.pdfInfo?.pageCount ?: return
        val currentSet = OfflinePdfProcessor.parsePageSelectionSpec(_uiState.value.specificPagesInput, total)
            .getOrDefault(emptyList())
            .toMutableSet()
        if (page in currentSet) currentSet.remove(page) else currentSet.add(page)
        _uiState.update {
            it.copy(
                selectionMode = PageSelectionMode.SPECIFIC,
                specificPagesInput = currentSet.sorted().joinToString(", "),
                errorMessage = null
            )
        }
    }

    fun processSelectedPages() {
        val state = _uiState.value
        val info = state.pdfInfo ?: return

        val selectedPagesResult: Result<List<Int>> = when (state.selectionMode) {
            PageSelectionMode.ALL -> Result.success((1..info.pageCount).toList())
            PageSelectionMode.RANGE -> {
                val spec = "${state.rangeStartInput.trim()}-${state.rangeEndInput.trim()}"
                OfflinePdfProcessor.parsePageSelectionSpec(spec, info.pageCount)
            }
            PageSelectionMode.SPECIFIC -> {
                OfflinePdfProcessor.parsePageSelectionSpec(state.specificPagesInput, info.pageCount)
            }
        }

        val selectedPages = selectedPagesResult.getOrElse { err ->
            _uiState.update { it.copy(errorMessage = err.message ?: "Invalid page selection.") }
            return
        }

        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    step = PdfImportStep.PROCESSING,
                    processedPagesCount = 0,
                    totalSelectedPages = selectedPages.size,
                    currentProcessingPage = selectedPages.firstOrNull() ?: 1,
                    errorMessage = null
                )
            }

            try {
                val extracted = pdfProcessor.extractPages(
                    pdfFile = info.localCacheFile,
                    selectedPagesOneBased = selectedPages,
                    onProgress = { done, total, current ->
                        _uiState.update {
                            it.copy(
                                processedPagesCount = done,
                                totalSelectedPages = total,
                                currentProcessingPage = current
                            )
                        }
                    }
                )

                val anyReadable = extracted.any { it.text.isNotBlank() }
                _uiState.update {
                    it.copy(
                        step = PdfImportStep.EXTRACTED_TEXT_PREVIEW,
                        extractedPages = extracted,
                        errorMessage = if (!anyReadable) {
                            "No readable text could be extracted from the selected pages. You can edit page text manually or reprocess individual pages."
                        } else null
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        step = PdfImportStep.PDF_PREVIEW,
                        errorMessage = "PDF processing was cancelled."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        step = PdfImportStep.PDF_PREVIEW,
                        errorMessage = "Failed to process PDF: ${e.message ?: "Unexpected error."}"
                    )
                }
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
    }

    fun updateExtractedPageText(pageNumber: Int, newText: String) {
        _uiState.update { state ->
            state.copy(
                extractedPages = state.extractedPages.map { p ->
                    if (p.pageNumber == pageNumber) p.copy(text = newText) else p
                }
            )
        }
    }

    fun togglePageIncluded(pageNumber: Int) {
        _uiState.update { state ->
            state.copy(
                extractedPages = state.extractedPages.map { p ->
                    if (p.pageNumber == pageNumber) p.copy(included = !p.included) else p
                }
            )
        }
    }

    fun reprocessPageWithOcr(pageNumber: Int) {
        val info = _uiState.value.pdfInfo ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val reprocessed = pdfProcessor.reprocessSinglePage(
                pdfFile = info.localCacheFile,
                pageNumberOneBased = pageNumber,
                forceOcr = true
            )
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    extractedPages = state.extractedPages.map { p ->
                        if (p.pageNumber == pageNumber) reprocessed else p
                    }
                )
            }
        }
    }

    fun backToPdfPreview() {
        _uiState.update { it.copy(step = PdfImportStep.PDF_PREVIEW, errorMessage = null) }
    }

    fun backToExtractedText() {
        _uiState.update { it.copy(step = PdfImportStep.EXTRACTED_TEXT_PREVIEW, errorMessage = null) }
    }

    fun generateCardsFromPages() {
        val state = _uiState.value
        val info = state.pdfInfo ?: return
        val activePages = state.extractedPages.filter { it.included && it.text.isNotBlank() }
        if (activePages.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Include at least one page with readable text to generate flashcards.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val existing = repository.getCardsByDeckOnce(deckId)
            existingDeckCardKeys = existing.map { CardValidationEngine.buildDuplicateKey(it.front, it.back) }.toSet()
            existingDeckFronts = existing.map { CardValidationEngine.normalizeForComparison(it.front) }.toSet()

            val generated = withContext(Dispatchers.Default) {
                val allCards = mutableListOf<GeneratedCardCandidate>()
                val runningKeys = existingDeckCardKeys.toMutableSet()
                val runningFronts = existingDeckFronts.toMutableSet()

                for (page in activePages) {
                    val srcType = if (page.status == PageExtractionStatus.SCANNED_OCR) {
                        SourceType.PDF_OCR
                    } else {
                        SourceType.PDF_TEXT
                    }
                    val pageCards = OfflineCardGenerator.generateFromText(
                        rawText = page.text,
                        sourceType = srcType,
                        sourceName = info.fileName,
                        sourcePageNumber = page.pageNumber,
                        ocrConfidence = page.ocrConfidence.coerceAtLeast(0.75f),
                        existingDeckCardKeys = runningKeys,
                        existingDeckFronts = runningFronts
                    )
                    pageCards.forEach { card ->
                        if (card.qualityStatus != CardQualityStatus.REJECTED) {
                            runningKeys.add(CardValidationEngine.buildDuplicateKey(card.front, card.back))
                            runningFronts.add(CardValidationEngine.normalizeForComparison(card.front))
                        }
                    }
                    allCards.addAll(pageCards)
                }
                allCards
            }

            _uiState.update {
                it.copy(
                    step = PdfImportStep.CARD_PREVIEW,
                    isLoading = false,
                    candidates = generated,
                    errorMessage = if (generated.isEmpty()) {
                        "No reliable flashcards could be generated from the selected pages. Edit the page text to clarify definitions or Q&A pairs and try again."
                    } else null
                )
            }
        }
    }

    fun toggleCandidateSelection(id: String) {
        _uiState.update { state ->
            state.copy(
                candidates = state.candidates.map { c ->
                    if (c.id == id && c.qualityStatus != CardQualityStatus.REJECTED) {
                        c.copy(selected = !c.selected)
                    } else c
                }
            )
        }
    }

    fun selectAllVerified() {
        _uiState.update { state ->
            state.copy(
                candidates = state.candidates.map { c ->
                    c.copy(selected = c.qualityStatus == CardQualityStatus.VERIFIED_AGAINST_SOURCE)
                }
            )
        }
    }

    fun selectAllValid() {
        _uiState.update { state ->
            state.copy(
                candidates = state.candidates.map { c ->
                    c.copy(selected = c.qualityStatus != CardQualityStatus.REJECTED)
                }
            )
        }
    }

    fun deselectAll() {
        _uiState.update { state ->
            state.copy(candidates = state.candidates.map { it.copy(selected = false) })
        }
    }

    fun editCandidate(id: String, newFront: String, newBack: String) {
        val updatedRaw = _uiState.value.candidates.map { c ->
            if (c.id == id) c.copy(front = newFront, back = newBack, selected = true) else c
        }
        val revalidated = CardValidationEngine.revalidateBatch(
            candidates = updatedRaw,
            existingDeckCardKeys = existingDeckCardKeys,
            existingDeckFronts = existingDeckFronts
        )
        _uiState.update { it.copy(candidates = revalidated) }
    }

    fun deleteCandidate(id: String) {
        val remaining = _uiState.value.candidates.filterNot { it.id == id }
        val revalidated = CardValidationEngine.revalidateBatch(
            candidates = remaining,
            existingDeckCardKeys = existingDeckCardKeys,
            existingDeckFronts = existingDeckFronts
        )
        _uiState.update { it.copy(candidates = revalidated) }
    }

    fun importSelectedCards(onComplete: () -> Unit) {
        val toSave = _uiState.value.candidates.filter {
            it.selected && it.qualityStatus != CardQualityStatus.REJECTED &&
                it.front.isNotBlank() && it.back.isNotBlank()
        }
        if (toSave.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                val latestKeys = repository.getCardsByDeckOnce(deckId)
                    .map { CardValidationEngine.buildDuplicateKey(it.front, it.back) }
                    .toMutableSet()

                val inputs = mutableListOf<FlashcardInsertInput>()
                for (c in toSave) {
                    val key = CardValidationEngine.buildDuplicateKey(c.front, c.back)
                    if (latestKeys.add(key)) {
                        inputs.add(
                            FlashcardInsertInput(
                                front = c.front.trim(),
                                back = c.back.trim(),
                                sourceType = c.sourceType,
                                sourceName = c.sourceName,
                                sourcePageNumber = c.sourcePageNumber,
                                sourceExcerpt = c.sourceExcerpt
                            )
                        )
                    }
                }
                if (inputs.isNotEmpty()) {
                    repository.addFlashcardsBatch(deckId, inputs)
                }
            }
            _uiState.update { it.copy(isLoading = false) }
            onComplete()
        }
    }

    override fun onCleared() {
        super.onCleared()
        processingJob?.cancel()
        pdfProcessor.cleanupTempFiles()
    }

    class Factory(
        private val deckId: Long,
        private val repository: MemoDeckRepository,
        private val pdfProcessor: OfflinePdfProcessor
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PdfImportViewModel(deckId, repository, pdfProcessor) as T
    }
}
