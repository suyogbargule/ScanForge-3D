package com.example.model

data class Frame(
    val id: String,
    val sessionId: String,
    val localPath: String,
    val timestamp: Long,
    val pose: Pose,
    val qualityScore: Float,
    val sharpness: Float,
    val exposure: Float,
    val coverageCellId: String,
    val accepted: Boolean = true
)
