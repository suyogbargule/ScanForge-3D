package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey val id: String,
    val objectName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val frameCount: Int,
    val coveragePercentage: Float,
    val assetPath: String?,
    val pointCount: Int,
    val reconstructionDurationMs: Long,
    val notes: String
)

@Entity(tableName = "captured_frames")
data class FrameEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val localPath: String,
    val timestamp: Long,
    val azimuthDeg: Float,
    val elevationDeg: Float,
    val distance: Float,
    val qualityScore: Float,
    val sharpness: Float,
    val exposure: Float,
    val coverageCellId: String,
    val accepted: Boolean
)
