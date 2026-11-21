package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val isFullImage: Boolean
        get() = left <= 0.01f && top <= 0.01f && right >= 0.99f && bottom >= 0.99f
}

object ImageBitmapUtils {

    fun createCameraCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun resolveDisplayName(context: Context, uri: Uri, fallback: String): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
    }

    suspend fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return@withContext null

            var sampleSize = 1
            val longestSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
            while (longestSide / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return source
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun cropBitmap(source: Bitmap, rect: NormalizedCropRect): Bitmap {
        if (rect.isFullImage) return source
        val leftPx = (rect.left.coerceIn(0f, 0.95f) * source.width).roundToInt()
        val topPx = (rect.top.coerceIn(0f, 0.95f) * source.height).roundToInt()
        val rightPx = (rect.right.coerceIn(rect.left + 0.05f, 1f) * source.width).roundToInt()
        val bottomPx = (rect.bottom.coerceIn(rect.top + 0.05f, 1f) * source.height).roundToInt()

        val width = (rightPx - leftPx).coerceAtLeast(1).coerceAtMost(source.width - leftPx)
        val height = (bottomPx - topPx).coerceAtLeast(1).coerceAtMost(source.height - topPx)
        return Bitmap.createBitmap(source, leftPx, topPx, width, height)
    }
}
