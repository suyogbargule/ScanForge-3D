package com.example.model

enum class SessionStatus {
    RECORDING,
    PAUSED,
    READY_FOR_RECONSTRUCTION,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class ScanSession(
    val id: String,
    val objectName: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val status: SessionStatus = SessionStatus.RECORDING,
    val frameCount: Int = 0,
    val coveragePercentage: Float = 0f,
    val assetPath: String? = null,
    val pointCount: Int = 0,
    val reconstructionDurationMs: Long = 0L,
    val notes: String = ""
)
