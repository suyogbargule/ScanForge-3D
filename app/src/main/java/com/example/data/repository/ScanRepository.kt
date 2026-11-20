package com.example.data.repository

import com.example.data.local.CaptureSessionEntity
import com.example.data.local.CaptureTargetEntity
import com.example.data.local.ScanDao
import com.example.model.CaptureSessionSummary
import com.example.model.CaptureTarget
import com.example.model.SessionStateStatus
import com.example.model.SphereLayer
import com.example.model.SphereTargetBuilder
import com.example.model.TargetStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class ScanRepository(
    private val scanDao: ScanDao
) {
    fun observeAllSessions(): Flow<List<CaptureSessionSummary>> {
        return scanDao.getAllSessions().map { list ->
            list.map { it.toSummary() }
        }
    }

    suspend fun getSession(sessionId: String): CaptureSessionSummary? = withContext(Dispatchers.IO) {
        scanDao.getSessionById(sessionId)?.toSummary()
    }

    suspend fun saveSession(summary: CaptureSessionSummary, targets: List<CaptureTarget>) = withContext(Dispatchers.IO) {
        scanDao.upsertSession(summary.toEntity())
        scanDao.upsertTargets(targets.map { it.toEntity(summary.sessionId) })
    }

    suspend fun updateSessionSummary(summary: CaptureSessionSummary) = withContext(Dispatchers.IO) {
        scanDao.upsertSession(summary.toEntity())
    }

    suspend fun saveTarget(sessionId: String, target: CaptureTarget) = withContext(Dispatchers.IO) {
        scanDao.upsertTarget(target.toEntity(sessionId))
    }

    suspend fun loadTargets(sessionId: String): List<CaptureTarget> = withContext(Dispatchers.IO) {
        val entities = scanDao.getTargetsForSession(sessionId)
        if (entities.isEmpty()) {
            SphereTargetBuilder.buildTargets()
        } else {
            entities.map { it.toDomain() }
        }
    }

    suspend fun deleteSession(sessionId: String, sessionDirPath: String) = withContext(Dispatchers.IO) {
        scanDao.deleteTargetsForSession(sessionId)
        scanDao.deleteSession(sessionId)
        try {
            File(sessionDirPath).deleteRecursively()
        } catch (_: Exception) {
        }
    }

    private fun CaptureSessionEntity.toSummary(): CaptureSessionSummary {
        return CaptureSessionSummary(
            sessionId = sessionId,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            yawOffsetDeg = yawOffsetDeg,
            status = try {
                SessionStateStatus.valueOf(status)
            } catch (_: Exception) {
                SessionStateStatus.INCOMPLETE
            },
            completedCount = completedCount,
            flaggedCount = flaggedCount,
            coveragePercent = coveragePercent,
            thumbnailPath = thumbnailPath,
            sessionDirPath = sessionDirPath,
            fovHDeg = fovHDeg,
            fovVDeg = fovVDeg
        )
    }

    private fun CaptureSessionSummary.toEntity(): CaptureSessionEntity {
        return CaptureSessionEntity(
            sessionId = sessionId,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            yawOffsetDeg = yawOffsetDeg,
            status = status.name,
            completedCount = completedCount,
            flaggedCount = flaggedCount,
            coveragePercent = coveragePercent,
            thumbnailPath = thumbnailPath,
            sessionDirPath = sessionDirPath,
            fovHDeg = fovHDeg,
            fovVDeg = fovVDeg
        )
    }

    private fun CaptureTarget.toEntity(sessionId: String): CaptureTargetEntity {
        return CaptureTargetEntity(
            sessionId = sessionId,
            targetId = id,
            layer = layer.name,
            yawDeg = yawDeg,
            pitchDeg = pitchDeg,
            status = status.name,
            imagePath = imagePath,
            thumbPath = thumbPath,
            rotationMatrixCsv = rotationMatrix?.joinToString(","),
            quaternionCsv = quaternion?.joinToString(","),
            angleErrorDeg = angleErrorDeg,
            blurScore = blurScore,
            exposureWarning = exposureWarning,
            textureWarning = textureWarning,
            qualityNote = qualityNote,
            timestamp = timestamp,
            captureOrder = captureOrder,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            checksumSha256 = checksumSha256
        )
    }

    private fun CaptureTargetEntity.toDomain(): CaptureTarget {
        return CaptureTarget(
            id = targetId,
            layer = try { SphereLayer.valueOf(layer) } catch (_: Exception) { SphereLayer.CENTRE },
            yawDeg = yawDeg,
            pitchDeg = pitchDeg,
            status = try { TargetStatus.valueOf(status) } catch (_: Exception) { TargetStatus.REMAINING },
            imagePath = imagePath,
            thumbPath = thumbPath,
            rotationMatrix = rotationMatrixCsv?.split(",")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 9 }?.toFloatArray(),
            quaternion = quaternionCsv?.split(",")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }?.toFloatArray(),
            angleErrorDeg = angleErrorDeg,
            blurScore = blurScore,
            exposureWarning = exposureWarning,
            textureWarning = textureWarning,
            qualityNote = qualityNote,
            timestamp = timestamp,
            captureOrder = captureOrder,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            checksumSha256 = checksumSha256
        )
    }
}
