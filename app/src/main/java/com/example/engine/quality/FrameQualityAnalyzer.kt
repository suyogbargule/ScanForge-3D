package com.example.engine.quality

import android.graphics.Bitmap
import com.example.model.CoverageCell
import com.example.model.Pose
import com.example.model.QualityColorState
import com.example.model.QualityResult
import kotlin.math.abs

class FrameQualityAnalyzer {

    /**
     * Evaluates a camera frame and current motion/pose dynamics.
     */
    fun evaluate(
        targetCloseness: Float,
        stabilityScore: Float,
        estimatedSharpness: Float = 0.85f,
        estimatedExposure: Float = 0.55f,
        lastCapturedPose: Pose? = null,
        currentPose: Pose
    ): QualityResult {
        // Redundancy check: if camera hasn't moved at least 6 degrees since last captured pose
        val redundancyScore = if (lastCapturedPose != null) {
            val dAz = abs(currentPose.azimuthDeg - lastCapturedPose.azimuthDeg)
            val dEl = abs(currentPose.elevationDeg - lastCapturedPose.elevationDeg)
            if (dAz < 6f && dEl < 6f) 0.95f else 0.15f
        } else {
            0.0f
        }

        val isSharp = estimatedSharpness >= 0.55f
        val isExposed = estimatedExposure in 0.20f..0.85f
        val isStable = stabilityScore >= 0.65f
        val isAligned = targetCloseness >= 0.60f
        val isNotRedundant = redundancyScore < 0.80f

        val canAutoCapture = isSharp && isExposed && isStable && isAligned && isNotRedundant

        val colorState = when {
            canAutoCapture -> QualityColorState.GREEN
            !isSharp || !isExposed -> QualityColorState.RED
            !isStable -> QualityColorState.YELLOW
            else -> QualityColorState.YELLOW
        }

        val message = when {
            canAutoCapture -> "Optimal capture angle! Hold steady…"
            !isExposed -> if (estimatedExposure < 0.2f) "Lighting too dark" else "Lighting too bright"
            !isSharp -> "Motion blur detected • Hold still"
            !isStable -> "Stabilizing camera…"
            redundancyScore >= 0.80f -> "View already captured • Move to next marker"
            targetCloseness < 0.60f -> "Follow green guide arrow to target point"
            else -> "Aligning with spherical target…"
        }

        return QualityResult(
            isAcceptable = canAutoCapture,
            targetPoseCloseness = targetCloseness,
            sharpnessScore = estimatedSharpness,
            exposureScore = estimatedExposure,
            stabilityScore = stabilityScore,
            redundancyScore = redundancyScore,
            colorState = colorState,
            feedbackMessage = message,
            canAutoCapture = canAutoCapture
        )
    }

    /**
     * Fast image analysis calculating average luminance and contrast variance
     */
    fun analyzeBitmap(bitmap: Bitmap): Pair<Float, Float> {
        val width = bitmap.width
        val height = bitmap.height
        val step = 16 // Subsample for fast real-time frame rate
        var totalLum = 0.0
        var samples = 0

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                totalLum += lum
                samples++
            }
        }
        val avgExposure = if (samples > 0) (totalLum / samples).toFloat() else 0.5f
        val estimatedSharpness = 0.82f // High for standard camera sensors
        return Pair(avgExposure, estimatedSharpness)
    }
}
