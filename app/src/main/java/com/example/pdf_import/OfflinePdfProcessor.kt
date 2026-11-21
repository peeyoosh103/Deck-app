package com.example.pdf_import

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.models.ExtractedPageResult
import com.example.models.PageExtractionStatus
import com.example.ocr.ImageBitmapUtils
import com.example.ocr.OfflineOcrEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.min

data class PdfDocumentInfo(
    val fileName: String,
    val localCacheFile: File,
    val pageCount: Int
)

sealed class PdfOpenResult {
    data class Success(val info: PdfDocumentInfo) : PdfOpenResult()
    data class Error(val message: String) : PdfOpenResult()
}

/**
 * Independent offline PDF processing engine.
 * - Copies selected document to a managed temp file (cleaned up automatically)
 * - Detects empty, corrupted, or password-protected PDFs safely without crashing
 * - Renders individual pages on demand using Android PdfRenderer (low memory footprint)
 * - Extracts embedded text via PDFBox-Android and falls back to bundled offline OCR for scanned pages
 */
class OfflinePdfProcessor(
    private val context: Context,
    private val ocrEngine: OfflineOcrEngine
) {
    private val tempDir: File
        get() = File(context.cacheDir, "temp_pdfs").apply { mkdirs() }

    suspend fun openPdf(uri: Uri): PdfOpenResult = withContext(Dispatchers.IO) {
        val fileName = ImageBitmapUtils.resolveDisplayName(context, uri, "document.pdf")
        val targetFile = File(tempDir, "active_${System.currentTimeMillis()}.pdf")

        try {
            // Copy PDF stream locally so both PDFBox and PdfRenderer can seek reliably offline
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext PdfOpenResult.Error("Unable to open the selected PDF file.")

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!targetFile.exists() || targetFile.length() == 0L) {
                targetFile.delete()
                return@withContext PdfOpenResult.Error("The selected PDF file is empty.")
            }

            // Validate structure & check for password protection using PDFBox
            val pageCount = try {
                PDDocument.load(targetFile).use { doc ->
                    if (doc.isEncrypted) {
                        targetFile.delete()
                        return@withContext PdfOpenResult.Error("This PDF is password-protected. Please unlock it before importing.")
                    }
                    doc.numberOfPages
                }
            } catch (_: InvalidPasswordException) {
                targetFile.delete()
                return@withContext PdfOpenResult.Error("This PDF is password-protected and cannot be read.")
            } catch (_: Exception) {
                // Fallback validation via native PdfRenderer
                try {
                    ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer -> renderer.pageCount }
                    }
                } catch (_: SecurityException) {
                    targetFile.delete()
                    return@withContext PdfOpenResult.Error("This PDF is password-protected and cannot be opened.")
                } catch (_: Exception) {
                    targetFile.delete()
                    return@withContext PdfOpenResult.Error("The selected file appears to be corrupted or is not a valid PDF.")
                }
            }

            if (pageCount <= 0) {
                targetFile.delete()
                return@withContext PdfOpenResult.Error("The PDF contains 0 pages.")
            }

            PdfOpenResult.Success(
                PdfDocumentInfo(
                    fileName = fileName,
                    localCacheFile = targetFile,
                    pageCount = pageCount
                )
            )
        } catch (e: OutOfMemoryError) {
            targetFile.delete()
            PdfOpenResult.Error("Not enough memory to open this PDF file.")
        } catch (e: Exception) {
            targetFile.delete()
            PdfOpenResult.Error("Could not read PDF: ${e.message ?: "Unexpected error."}")
        }
    }

    suspend fun renderPagePreview(
        pdfFile: File,
        pageIndexZeroBased: Int,
        maxWidthPx: Int = 900
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext null
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndexZeroBased !in 0 until renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndexZeroBased).use { page ->
                        val scale = min(maxWidthPx.toFloat() / page.width.coerceAtLeast(1), 2.0f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts text for selected 1-based page numbers in memory-safe batches.
     * Supports cooperative cancellation and progress callbacks.
     */
    suspend fun extractPages(
        pdfFile: File,
        selectedPagesOneBased: List<Int>,
        forceOcrPages: Set<Int> = emptySet(),
        onProgress: (processedCount: Int, totalCount: Int, currentPage: Int) -> Unit
    ): List<ExtractedPageResult> = withContext(Dispatchers.IO) {
        val sortedPages = selectedPagesOneBased.distinct().sorted()
        if (sortedPages.isEmpty() || !pdfFile.exists()) return@withContext emptyList()

        val results = mutableListOf<ExtractedPageResult>()
        val pdDoc = runCatching { PDDocument.load(pdfFile) }.getOrNull()
        val pfd = runCatching { ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
        val renderer = pfd?.let { runCatching { PdfRenderer(it) }.getOrNull() }

        try {
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }

            sortedPages.forEachIndexed { idx, pageNumber ->
                coroutineContext.ensureActive()
                onProgress(idx, sortedPages.size, pageNumber)

                val pageResult = extractSinglePageInternal(
                    pdDoc = pdDoc,
                    stripper = stripper,
                    renderer = renderer,
                    pageNumberOneBased = pageNumber,
                    forceOcr = pageNumber in forceOcrPages
                )
                results.add(pageResult)
                onProgress(idx + 1, sortedPages.size, pageNumber)
            }
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
            runCatching { pdDoc?.close() }
        }

        results
    }

    suspend fun reprocessSinglePage(
        pdfFile: File,
        pageNumberOneBased: Int,
        forceOcr: Boolean
    ): ExtractedPageResult = withContext(Dispatchers.IO) {
        val list = extractPages(
            pdfFile = pdfFile,
            selectedPagesOneBased = listOf(pageNumberOneBased),
            forceOcrPages = if (forceOcr) setOf(pageNumberOneBased) else emptySet(),
            onProgress = { _, _, _ -> }
        )
        list.firstOrNull() ?: ExtractedPageResult(
            pageNumber = pageNumberOneBased,
            text = "",
            status = PageExtractionStatus.FAILED,
            ocrConfidence = 0f,
            included = false,
            errorMessage = "Unable to reprocess page $pageNumberOneBased."
        )
    }

    private suspend fun extractSinglePageInternal(
        pdDoc: PDDocument?,
        stripper: PDFTextStripper,
        renderer: PdfRenderer?,
        pageNumberOneBased: Int,
        forceOcr: Boolean
    ): ExtractedPageResult {
        try {
            // 1. Try embedded text extraction first (unless forced OCR)
            if (!forceOcr && pdDoc != null && pageNumberOneBased in 1..pdDoc.numberOfPages) {
                val embeddedText = runCatching {
                    stripper.startPage = pageNumberOneBased
                    stripper.endPage = pageNumberOneBased
                    stripper.getText(pdDoc).trim()
                }.getOrDefault("")

                if (isUsableEmbeddedText(embeddedText)) {
                    return ExtractedPageResult(
                        pageNumber = pageNumberOneBased,
                        text = embeddedText,
                        status = PageExtractionStatus.EMBEDDED_TEXT,
                        ocrConfidence = 1.0f,
                        included = true
                    )
                }
            }

            // 2. Scanned PDF page or sparse/unusable embedded text -> Render locally & run offline OCR
            if (renderer == null) {
                return ExtractedPageResult(
                    pageNumber = pageNumberOneBased,
                    text = "",
                    status = PageExtractionStatus.FAILED,
                    ocrConfidence = 0f,
                    included = false,
                    errorMessage = "Unable to render page $pageNumberOneBased for offline OCR."
                )
            }

            val pageIdx = pageNumberOneBased - 1
            if (pageIdx !in 0 until renderer.pageCount) {
                return ExtractedPageResult(
                    pageNumber = pageNumberOneBased,
                    text = "",
                    status = PageExtractionStatus.FAILED,
                    ocrConfidence = 0f,
                    included = false,
                    errorMessage = "Page $pageNumberOneBased is out of range."
                )
            }

            var renderedBitmap: Bitmap? = null
            try {
                renderedBitmap = renderer.openPage(pageIdx).use { page ->
                    // Crisp OCR scale (~1600px wide max) while preventing OOM on large PDFs
                    val targetWidth = min(1600, maxOf(page.width * 2, 1000))
                    val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    bmp
                }

                val ocr = ocrEngine.recognizeText(renderedBitmap)
                if (ocr.extractedText.isBlank()) {
                    return ExtractedPageResult(
                        pageNumber = pageNumberOneBased,
                        text = "",
                        status = PageExtractionStatus.EMPTY,
                        ocrConfidence = 0f,
                        included = false,
                        errorMessage = "No readable text detected on page $pageNumberOneBased."
                    )
                }

                return ExtractedPageResult(
                    pageNumber = pageNumberOneBased,
                    text = ocr.extractedText,
                    status = PageExtractionStatus.SCANNED_OCR,
                    ocrConfidence = ocr.confidence,
                    included = true
                )
            } finally {
                renderedBitmap?.recycle()
            }
        } catch (e: OutOfMemoryError) {
            return ExtractedPageResult(
                pageNumber = pageNumberOneBased,
                text = "",
                status = PageExtractionStatus.FAILED,
                ocrConfidence = 0f,
                included = false,
                errorMessage = "Low memory while processing page $pageNumberOneBased."
            )
        } catch (e: Exception) {
            return ExtractedPageResult(
                pageNumber = pageNumberOneBased,
                text = "",
                status = PageExtractionStatus.FAILED,
                ocrConfidence = 0f,
                included = false,
                errorMessage = "Page $pageNumberOneBased failed: ${e.message ?: "Unreadable page."}"
            )
        }
    }

    private fun isUsableEmbeddedText(text: String): Boolean {
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.length < 25) return false
        val readable = compact.count { it.isLetterOrDigit() }
        val replacementChars = compact.count { it == '\uFFFD' }
        val readableRatio = readable.toFloat() / compact.length.toFloat()
        return readableRatio >= 0.45f && replacementChars < compact.length * 0.15f
    }

    fun cleanupTempFiles() {
        runCatching {
            tempDir.listFiles()?.forEach { it.delete() }
        }
    }

    companion object {
        /**
         * Parses expressions like "1, 3, 5-8" or "2-10" into validated 1-based page numbers.
         */
        fun parsePageSelectionSpec(spec: String, totalPages: Int): Result<List<Int>> {
            val cleaned = spec.trim()
            if (cleaned.isEmpty()) {
                return Result.failure(IllegalArgumentException("Please enter at least one page number or range."))
            }
            val selected = linkedSetOf<Int>()
            val tokens = cleaned.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) {
                return Result.failure(IllegalArgumentException("Invalid page selection format."))
            }

            for (token in tokens) {
                if (token.contains("-") || token.contains("–")) {
                    val parts = token.split("-", "–").map { it.trim() }
                    if (parts.size != 2) {
                        return Result.failure(IllegalArgumentException("Invalid page range \"$token\". Use format like 2-10."))
                    }
                    val start = parts[0].toIntOrNull()
                    val end = parts[1].toIntOrNull()
                    if (start == null || end == null || start < 1 || end < start || end > totalPages) {
                        return Result.failure(
                            IllegalArgumentException("Page range \"$token\" must be within 1–$totalPages with start <= end.")
                        )
                    }
                    for (p in start..end) selected.add(p)
                } else {
                    val page = token.toIntOrNull()
                    if (page == null || page < 1 || page > totalPages) {
                        return Result.failure(
                            IllegalArgumentException("Page \"$token\" is out of range (1–$totalPages).")
                        )
                    }
                    selected.add(page)
                }
            }

            return if (selected.isEmpty()) {
                Result.failure(IllegalArgumentException("No valid pages selected."))
            } else {
                Result.success(selected.sorted())
            }
        }
    }
}
