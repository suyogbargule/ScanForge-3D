package com.example.engine.quality

import com.example.model.CoverageCell
import com.example.model.Pose
import com.example.model.QualityResult

data class CaptureDecision(
    val shouldCapture: Boolean,
    val matchedCell: CoverageCell?,
    val quality: QualityResult,
    val reason: String
)

class FrameSelector(
    private val qualityAnalyzer: FrameQualityAnalyzer
) {
    private var lastCaptureTimestamp = 0L
    private val minimumCaptureIntervalMs = 900L // Prevent rapid double triggering

    fun shouldCapture(
        targetCell: CoverageCell?,
        targetCloseness: Float,
        stability: Float,
        currentPose: Pose,
        lastCapturedPose: Pose?,
        sharpness: Float = 0.85f,
        exposure: Float = 0.55f,
        currentTimeMs: Long = System.currentTimeMillis()
    ): CaptureDecision {
        if (currentTimeMs - lastCaptureTimestamp < minimumCaptureIntervalMs) {
            val quality = qualityAnalyzer.evaluate(
                targetCloseness = targetCloseness,
                stabilityScore = stability,
                estimatedSharpness = sharpness,
                estimatedExposure = exposure,
                lastCapturedPose = lastCapturedPose,
                currentPose = currentPose
            )
            return CaptureDecision(
                shouldCapture = false,
                matchedCell = targetCell,
                quality = quality,
                reason = "Debouncing"
            )
        }

        val quality = qualityAnalyzer.evaluate(
            targetCloseness = targetCloseness,
            stabilityScore = stability,
            estimatedSharpness = sharpness,
            estimatedExposure = exposure,
            lastCapturedPose = lastCapturedPose,
            currentPose = currentPose
        )

        return if (quality.canAutoCapture && targetCell != null && !targetCell.isCaptured) {
            lastCaptureTimestamp = currentTimeMs
            CaptureDecision(
                shouldCapture = true,
                matchedCell = targetCell,
                quality = quality,
                reason = "Auto-capture criteria satisfied"
            )
        } else {
            CaptureDecision(
                shouldCapture = false,
                matchedCell = targetCell,
                quality = quality,
                reason = quality.feedbackMessage
            )
        }
    }

    fun markManualCapture() {
        lastCaptureTimestamp = System.currentTimeMillis()
    }
}
