package com.example.model

enum class QualityColorState {
    GREEN,  // Target pose + stability + quality acceptable -> Ready / Auto-Capture
    YELLOW, // Move / hold steady / adjust position
    RED     // Unsuitable frame (extreme blur, poor lighting, unstable)
}

data class QualityResult(
    val isAcceptable: Boolean = false,
    val targetPoseCloseness: Float = 0.5f, // 0..1
    val sharpnessScore: Float = 0.7f,       // 0..1
    val exposureScore: Float = 0.8f,        // 0..1
    val stabilityScore: Float = 0.85f,      // 0..1
    val redundancyScore: Float = 0.1f,      // 0..1 (low redundancy is good)
    val colorState: QualityColorState = QualityColorState.YELLOW,
    val feedbackMessage: String = "Align camera with target marker",
    val canAutoCapture: Boolean = false
) {
    val overallScore: Float
        get() = ((sharpnessScore + exposureScore + stabilityScore + (1f - redundancyScore) + targetPoseCloseness) / 5f).coerceIn(0f, 1f)
}
