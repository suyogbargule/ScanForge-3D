package com.example.engine.quality

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.model.BlurThresholdPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

data class QualityGateResult(
    val passedBlur: Boolean,
    val blurScore: Float,
    val exposureWarning: Boolean,
    val textureWarning: Boolean,
    val imageWidth: Int,
    val imageHeight: Int,
    val warningMessage: String?
)

/**
 * On-device Quality Gate (Section 18):
 * 1. Blur check: variance of the 3x3 Laplacian on subsampled grayscale luminance
 * 2. Exposure check: share of clipped (>248) or very dark (<12) pixels
 * 3. Texture check: local gradient/corner feature count across the frame
 * 4. Thumbnail generation while releasing large image buffers immediately (Section 22.4)
 */
class CaptureQualityGate {

    suspend fun evaluateAndGenerateThumb(
        imageFile: File,
        thumbFile: File,
        blurPreset: BlurThresholdPreset = BlurThresholdPreset.STANDARD
    ): QualityGateResult = withContext(Dispatchers.Default) {
        if (!imageFile.exists() || imageFile.length() <= 0L) {
            return@withContext QualityGateResult(
                passedBlur = false,
                blurScore = 0f,
                exposureWarning = false,
                textureWarning = false,
                imageWidth = 0,
                imageHeight = 0,
                warningMessage = "Image file missing"
            )
        }

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, boundsOpts)
        val fullW = boundsOpts.outWidth.coerceAtLeast(1)
        val fullH = boundsOpts.outHeight.coerceAtLeast(1)

        // Subsample so longest edge is ~360-480px for fast memory-safe evaluation on any Android 11+ phone
        val sampleSize = calculateInSampleSize(fullW, fullH, 400)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bmp = BitmapFactory.decodeFile(imageFile.absolutePath, decodeOpts)
            ?: return@withContext QualityGateResult(
                passedBlur = false,
                blurScore = 0f,
                exposureWarning = false,
                textureWarning = false,
                imageWidth = fullW,
                imageHeight = fullH,
                warningMessage = "Unable to decode image"
            )

        try {
            val metrics = analyzeBitmapMetrics(bmp)

            // Save compact thumbnail for Review grid
            thumbFile.parentFile?.mkdirs()
            val thumbBmp = createThumbnailBitmap(bmp, 220)
            try {
                FileOutputStream(thumbFile).use { out ->
                    thumbBmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    out.flush()
                }
            } finally {
                if (thumbBmp !== bmp) {
                    thumbBmp.recycle()
                }
            }

            val passedBlur = metrics.laplacianVariance >= blurPreset.minLaplacianVariance
            val warningMsg = when {
                !passedBlur -> "Image blurry (score ${metrics.laplacianVariance.toInt()}) • Hold still"
                metrics.exposureWarning && metrics.textureWarning -> "Extreme exposure & low texture — consider retake"
                metrics.exposureWarning -> "Extreme brightness/shadows — reframe slightly"
                metrics.textureWarning -> "Low texture (blank surface) — flagged for review"
                else -> null
            }

            QualityGateResult(
                passedBlur = passedBlur,
                blurScore = metrics.laplacianVariance.toFloat(),
                exposureWarning = metrics.exposureWarning,
                textureWarning = metrics.textureWarning,
                imageWidth = fullW,
                imageHeight = fullH,
                warningMessage = warningMsg
            )
        } finally {
            bmp.recycle()
        }
    }

    data class RawMetrics(
        val laplacianVariance: Double,
        val exposureWarning: Boolean,
        val textureWarning: Boolean,
        val featureCount: Int
    )

    fun analyzeBitmapMetrics(bitmap: Bitmap): RawMetrics {
        val w = bitmap.width
        val h = bitmap.height
        val total = w * h
        if (w < 8 || h < 8) {
            return RawMetrics(100.0, exposureWarning = false, textureWarning = false, featureCount = 100)
        }

        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val lum = IntArray(total)
        for (i in 0 until total) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            lum[i] = (77 * r + 150 * g + 29 * b) shr 8
        }
        return analyzeLuminanceMetrics(lum, w, h)
    }

    fun analyzeLuminanceMetrics(lum: IntArray, w: Int, h: Int): RawMetrics {
        val total = w * h
        if (w < 8 || h < 8 || lum.size < total) {
            return RawMetrics(100.0, exposureWarning = false, textureWarning = false, featureCount = 100)
        }

        var clippedBright = 0
        var veryDark = 0
        for (i in 0 until total) {
            val y = lum[i]
            if (y >= 248) clippedBright++
            else if (y <= 12) veryDark++
        }

        // 1. Variance of 3x3 Laplacian
        var lapSum = 0.0
        var lapSqSum = 0.0
        var lapCount = 0
        // 2. Local texture / corner feature count
        var strongFeatures = 0

        for (y in 1 until h - 1) {
            val rowOffset = y * w
            for (x in 1 until w - 1) {
                val idx = rowOffset + x
                val center = lum[idx]
                val lap = lum[idx - w] + lum[idx + w] + lum[idx - 1] + lum[idx + 1] - (4 * center)
                lapSum += lap
                lapSqSum += lap.toDouble() * lap.toDouble()
                lapCount++

                if ((x and 1) == 0 && (y and 1) == 0) {
                    val gx = abs(lum[idx + 1] - lum[idx - 1])
                    val gy = abs(lum[idx + w] - lum[idx - w])
                    if (gx > 18 && gy > 18) {
                        strongFeatures++
                    }
                }
            }
        }

        val mean = if (lapCount > 0) lapSum / lapCount else 0.0
        val variance = if (lapCount > 0) (lapSqSum / lapCount) - (mean * mean) else 0.0

        val clippedRatio = clippedBright.toFloat() / total
        val darkRatio = veryDark.toFloat() / total
        val exposureWarn = clippedRatio > 0.42f || darkRatio > 0.50f
        val textureWarn = strongFeatures < 20

        return RawMetrics(
            laplacianVariance = variance.coerceAtLeast(0.0),
            exposureWarning = exposureWarn,
            textureWarning = textureWarn,
            featureCount = strongFeatures
        )
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        var longest = maxOf(width, height)
        while (longest / inSampleSize > maxDim * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun createThumbnailBitmap(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        val scale = maxEdge.toFloat() / maxOf(w, h).coerceAtLeast(1)
        if (scale >= 1f) return src
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }
}
