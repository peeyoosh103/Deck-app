package com.example.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(
    val extractedText: String,
    val confidence: Float, // 0f..1f estimated readability confidence
    val detectedDevanagari: Boolean,
    val blockCount: Int
)

/**
 * Independent 100% offline OCR engine powered by the bundled ML Kit Devanagari + Latin model.
 * Recognizes Hindi (Devanagari), English (Latin), and mixed bilingual printed text with
 * zero internet access and zero runtime model downloads.
 */
class OfflineOcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    private val devanagariRegex = Regex("[\\u0900-\\u097F]")
    private val readableCharRegex = Regex("[\\p{L}\\p{N}\\p{M}]")

    suspend fun recognizeText(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(inputImage).awaitResult()
        val formatted = formatStructuredText(visionText)
        val confidence = estimateReadabilityConfidence(formatted, visionText)

        OcrResult(
            extractedText = formatted,
            confidence = confidence,
            detectedDevanagari = devanagariRegex.containsMatchIn(formatted),
            blockCount = visionText.textBlocks.size
        )
    }

    private fun formatStructuredText(visionText: Text): String {
        if (visionText.textBlocks.isEmpty()) return visionText.text.trim()
        val sortedBlocks = visionText.textBlocks.sortedWith(
            compareBy<Text.TextBlock>({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 })
        )
        return sortedBlocks.joinToString("\n") { block ->
            block.lines.joinToString("\n") { line -> line.text.trim() }
        }.trim()
    }

    private fun estimateReadabilityConfidence(text: String, visionText: Text?): Float {
        if (text.isBlank()) return 0f

        val elementConfidences = visionText?.textBlocks
            ?.flatMap { it.lines }
            ?.flatMap { it.elements }
            ?.mapNotNull { it.confidence }
            ?.filter { !it.isNaN() && it > 0f }
            .orEmpty()

        val modelConf = if (elementConfidences.isNotEmpty()) {
            elementConfidences.average().toFloat().coerceIn(0f, 1f)
        } else {
            null
        }

        val nonWhitespace = text.filterNot { it.isWhitespace() }
        if (nonWhitespace.isEmpty()) return 0f
        val readableCount = readableCharRegex.findAll(nonWhitespace).count()
        val legibilityRatio = (readableCount.toFloat() / nonWhitespace.length.toFloat()).coerceIn(0f, 1f)

        return if (modelConf != null) {
            (modelConf * 0.65f + legibilityRatio * 0.35f).coerceIn(0f, 1f)
        } else {
            (legibilityRatio * 0.92f).coerceIn(0f, 1f)
        }
    }

    fun close() {
        runCatching { recognizer.close() }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { e ->
        if (cont.isActive) cont.resumeWithException(e)
    }
    addOnCanceledListener {
        cont.cancel()
    }
}
