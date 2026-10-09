package com.example.model

enum class Layer(val displayName: String, val typicalElevationDeg: Float, val defaultTargetViews: Int) {
    TOP("Top Surface", 75f, 1),
    UPPER_RING("Upper Ring", 40f, 8),
    MIDDLE_RING("Middle Ring", 0f, 14),
    LOWER_RING("Lower Ring", -30f, 8),
    BOTTOM("Bottom Surface", -70f, 1)
}

enum class CellStatus {
    PENDING,
    RECOMMENDED,
    CAPTURED,
    POOR_COVERAGE
}

data class CoverageCell(
    val id: String,
    val layer: Layer,
    val azimuthDeg: Float,
    val elevationDeg: Float,
    val status: CellStatus = CellStatus.PENDING,
    val capturedFrameId: String? = null,
    val confidence: Float = 0f
) {
    val isCaptured: Boolean get() = status == CellStatus.CAPTURED
}
