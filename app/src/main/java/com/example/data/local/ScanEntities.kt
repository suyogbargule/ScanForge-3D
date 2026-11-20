package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "capture_sessions")
data class CaptureSessionEntity(
    @PrimaryKey val sessionId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val yawOffsetDeg: Float,
    val status: String,
    val completedCount: Int,
    val flaggedCount: Int,
    val coveragePercent: Float,
    val thumbnailPath: String?,
    val sessionDirPath: String,
    val fovHDeg: Float,
    val fovVDeg: Float
)

@Entity(tableName = "capture_targets", primaryKeys = ["sessionId", "targetId"])
data class CaptureTargetEntity(
    val sessionId: String,
    val targetId: Int,
    val layer: String,
    val yawDeg: Float,
    val pitchDeg: Float,
    val status: String,
    val imagePath: String?,
    val thumbPath: String?,
    val rotationMatrixCsv: String?,
    val quaternionCsv: String?,
    val angleErrorDeg: Float,
    val blurScore: Float,
    val exposureWarning: Boolean,
    val textureWarning: Boolean,
    val qualityNote: String?,
    val timestamp: Long,
    val captureOrder: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val checksumSha256: String?
)
