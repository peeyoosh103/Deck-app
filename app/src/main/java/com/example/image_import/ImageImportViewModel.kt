package com.example.image_import

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.card_validation.CardValidationEngine
import com.example.card_validation.OfflineCardGenerator
import com.example.models.CardQualityStatus
import com.example.models.GeneratedCardCandidate
import com.example.models.SourceType
import com.example.ocr.ImageBitmapUtils
import com.example.ocr.NormalizedCropRect
import com.example.ocr.OfflineOcrEngine
import com.example.repositories.FlashcardInsertInput
import com.example.repositories.MemoDeckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImageImportStep {
    SELECT_SOURCE,
    PREVIEW_IMAGE,
    EXTRACTED_TEXT,
    CARD_PREVIEW
}

data class ImageImportUiState(
    val deckId: Long = 0L,
    val step: ImageImportStep = ImageImportStep.SELECT_SOURCE,
    val sourceName: String = "Captured Image",
    val previewBitmap: Bitmap? = null,
    val rotationDegrees: Int = 0,
    val cropRect: NormalizedCropRect = NormalizedCropRect(),
    val isProcessing: Boolean = false,
    val extractedText: String = "",
    val ocrConfidence: Float = 0f,
    val candidates: List<GeneratedCardCandidate> = emptyList(),
    val errorMessage: String? = null,
    val savedCount: Int? = null
) {
    val totalGenerated: Int get() = candidates.size
    val verifiedCount: Int get() = candidates.count { it.qualityStatus == CardQualityStatus.VERIFIED_AGAINST_SOURCE }
    val needsReviewCount: Int get() = candidates.count { it.qualityStatus == CardQualityStatus.NEEDS_REVIEW }
    val rejectedCount: Int get() = candidates.count { it.qualityStatus == CardQualityStatus.REJECTED }
    val selectedValidCount: Int get() = candidates.count { it.selected && it.qualityStatus != CardQualityStatus.REJECTED }
}

class ImageImportViewModel(
    private val deckId: Long,
    private val repository: MemoDeckRepository,
    private val ocrEngine: OfflineOcrEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageImportUiState(deckId = deckId))
    val uiState: StateFlow<ImageImportUiState> = _uiState.asStateFlow()

    private var rawDecodedBitmap: Bitmap? = null
    private var existingDeckCardKeys: Set<String> = emptySet()
    private var existingDeckFronts: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            refreshExistingDeckIndex()
        }
    }

    private suspend fun refreshExistingDeckIndex() {
        val existing = repository.getCardsByDeckOnce(deckId)
        existingDeckCardKeys = existing.map { CardValidationEngine.buildDuplicateKey(it.front, it.back) }.toSet()
        existingDeckFronts = existing.map { CardValidationEngine.normalizeForComparison(it.front) }.toSet()
    }

    fun loadImageFromUri(context: Context, uri: Uri, defaultName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val name = withContext(Dispatchers.IO) {
                ImageBitmapUtils.resolveDisplayName(context, uri, defaultName)
            }
            val bitmap = ImageBitmapUtils.decodeSampledBitmap(context, uri, maxDimension = 2048)
            if (bitmap == null) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Could not load the selected image. Please try another photo."
                    )
                }
                return@launch
            }
            rawDecodedBitmap = bitmap
            _uiState.update {
                it.copy(
                    step = ImageImportStep.PREVIEW_IMAGE,
                    sourceName = name,
                    previewBitmap = bitmap,
                    rotationDegrees = 0,
                    cropRect = NormalizedCropRect(),
                    isProcessing = false,
                    errorMessage = null
                )
            }
        }
    }

    fun rotateImage(deltaDegrees: Int) {
        val raw = rawDecodedBitmap ?: return
        val newDegrees = ((_uiState.value.rotationDegrees + deltaDegrees) % 360 + 360) % 360
        viewModelScope.launch(Dispatchers.Default) {
            val rotated = ImageBitmapUtils.rotateBitmap(raw, newDegrees)
            _uiState.update {
                it.copy(
                    rotationDegrees = newDegrees,
                    previewBitmap = rotated,
                    cropRect = NormalizedCropRect()
                )
            }
        }
    }

    fun updateCropRect(rect: NormalizedCropRect) {
        _uiState.update { it.copy(cropRect = rect) }
    }

    fun resetCrop() {
        _uiState.update { it.copy(cropRect = NormalizedCropRect()) }
    }

    fun processImageWithOcr() {
        val bitmap = _uiState.value.previewBitmap ?: return
        val crop = _uiState.value.cropRect
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val cropped = ImageBitmapUtils.cropBitmap(bitmap, crop)
                    val ocrResult = ocrEngine.recognizeText(cropped)
                    if (cropped !== bitmap) cropped.recycle()
                    ocrResult
                }
            }
            result.fold(
                onSuccess = { ocr ->
                    _uiState.update {
                        it.copy(
                            step = ImageImportStep.EXTRACTED_TEXT,
                            isProcessing = false,
                            extractedText = ocr.extractedText,
                            ocrConfidence = ocr.confidence,
                            errorMessage = if (ocr.extractedText.isBlank()) {
                                "No readable text was detected in this image. Try cropping closer, rotating, or entering text manually."
                            } else null
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Offline OCR failed: ${e.message ?: "Unable to read image."}"
                        )
                    }
                }
            )
        }
    }

    fun updateExtractedText(newText: String) {
        _uiState.update { it.copy(extractedText = newText, errorMessage = null) }
    }

    fun backToImagePreview() {
        _uiState.update { it.copy(step = ImageImportStep.PREVIEW_IMAGE, errorMessage = null) }
    }

    fun backToExtractedText() {
        _uiState.update { it.copy(step = ImageImportStep.EXTRACTED_TEXT, errorMessage = null) }
    }

    fun generateCardsFromExtractedText() {
        val text = _uiState.value.extractedText.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No text available to generate flashcards.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            refreshExistingDeckIndex()
            val generated = withContext(Dispatchers.Default) {
                OfflineCardGenerator.generateFromText(
                    rawText = text,
                    sourceType = SourceType.IMAGE_OCR,
                    sourceName = _uiState.value.sourceName,
                    sourcePageNumber = null,
                    ocrConfidence = _uiState.value.ocrConfidence.coerceAtLeast(0.75f),
                    existingDeckCardKeys = existingDeckCardKeys,
                    existingDeckFronts = existingDeckFronts
                )
            }
            _uiState.update {
                it.copy(
                    step = ImageImportStep.CARD_PREVIEW,
                    isProcessing = false,
                    candidates = generated,
                    errorMessage = if (generated.isEmpty()) {
                        "No reliable structured facts or Q&A pairs could be extracted automatically. Edit the text using \"Term: Definition\" or \"Q: ... / A: ...\" format and regenerate."
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

    fun selectAllValid() {
        _uiState.update { state ->
            state.copy(
                candidates = state.candidates.map { c ->
                    if (c.qualityStatus != CardQualityStatus.REJECTED) c.copy(selected = true) else c
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
            existingDeckFronts = existingDeckFronts,
            ocrConfidenceProvider = { _uiState.value.ocrConfidence.coerceAtLeast(0.85f) }
        )
        _uiState.update { it.copy(candidates = revalidated) }
    }

    fun deleteCandidate(id: String) {
        val remaining = _uiState.value.candidates.filterNot { it.id == id }
        val revalidated = CardValidationEngine.revalidateBatch(
            candidates = remaining,
            existingDeckCardKeys = existingDeckCardKeys,
            existingDeckFronts = existingDeckFronts,
            ocrConfidenceProvider = { _uiState.value.ocrConfidence.coerceAtLeast(0.75f) }
        )
        _uiState.update { it.copy(candidates = revalidated) }
    }

    fun saveSelectedCards(onSaved: (Int) -> Unit) {
        val toSave = _uiState.value.candidates.filter {
            it.selected && it.qualityStatus != CardQualityStatus.REJECTED &&
                it.front.isNotBlank() && it.back.isNotBlank()
        }
        if (toSave.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val inputs = toSave.map { c ->
                FlashcardInsertInput(
                    front = c.front,
                    back = c.back,
                    sourceType = SourceType.IMAGE_OCR,
                    sourceName = c.sourceName,
                    sourcePageNumber = null,
                    sourceExcerpt = c.sourceExcerpt
                )
            }
            repository.addFlashcardsBatch(deckId, inputs)
            _uiState.update { it.copy(isProcessing = false, savedCount = inputs.size) }
            onSaved(inputs.size)
        }
    }

    class Factory(
        private val deckId: Long,
        private val repository: MemoDeckRepository,
        private val ocrEngine: OfflineOcrEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ImageImportViewModel(deckId, repository, ocrEngine) as T
    }
}
