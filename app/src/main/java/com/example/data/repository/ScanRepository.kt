package com.example.data.repository

import android.content.Context
import com.example.data.local.FrameEntity
import com.example.data.local.ScanDao
import com.example.data.local.ScanSessionEntity
import com.example.model.Frame
import com.example.model.Pose
import com.example.model.ScanSession
import com.example.model.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class ScanRepository(
    private val scanDao: ScanDao,
    private val context: Context
) {
    fun getAllSessions(): Flow<List<ScanSession>> {
        return scanDao.getAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getSession(sessionId: String): ScanSession? = withContext(Dispatchers.IO) {
        scanDao.getSessionById(sessionId)?.toDomain()
    }

    suspend fun saveSession(session: ScanSession) = withContext(Dispatchers.IO) {
        scanDao.insertSession(session.toEntity())
    }

    suspend fun updateSession(session: ScanSession) = withContext(Dispatchers.IO) {
        scanDao.updateSession(session.toEntity())
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        scanDao.deleteSession(sessionId)
        scanDao.deleteFramesForSession(sessionId)
        // Clean up session directory in app-private storage
        val sessionDir = File(context.filesDir, "scans/$sessionId")
        if (sessionDir.exists()) {
            sessionDir.deleteRecursively()
        }
    }

    fun getFramesForSession(sessionId: String): Flow<List<Frame>> {
        return scanDao.getFramesForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun saveFrame(frame: Frame) = withContext(Dispatchers.IO) {
        scanDao.insertFrame(frame.toEntity())
    }

    fun getSessionDirectory(sessionId: String): File {
        val dir = File(context.filesDir, "scans/$sessionId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun ScanSessionEntity.toDomain(): ScanSession {
        return ScanSession(
            id = id,
            objectName = objectName,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = try { SessionStatus.valueOf(status) } catch (e: Exception) { SessionStatus.COMPLETED },
            frameCount = frameCount,
            coveragePercentage = coveragePercentage,
            assetPath = assetPath,
            pointCount = pointCount,
            reconstructionDurationMs = reconstructionDurationMs,
            notes = notes
        )
    }

    private fun ScanSession.toEntity(): ScanSessionEntity {
        return ScanSessionEntity(
            id = id,
            objectName = objectName,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = status.name,
            frameCount = frameCount,
            coveragePercentage = coveragePercentage,
            assetPath = assetPath,
            pointCount = pointCount,
            reconstructionDurationMs = reconstructionDurationMs,
            notes = notes
        )
    }

    private fun FrameEntity.toDomain(): Frame {
        return Frame(
            id = id,
            sessionId = sessionId,
            localPath = localPath,
            timestamp = timestamp,
            pose = Pose.fromSpherical(azimuthDeg, elevationDeg, distance),
            qualityScore = qualityScore,
            sharpness = sharpness,
            exposure = exposure,
            coverageCellId = coverageCellId,
            accepted = accepted
        )
    }

    private fun Frame.toEntity(): FrameEntity {
        return FrameEntity(
            id = id,
            sessionId = sessionId,
            localPath = localPath,
            timestamp = timestamp,
            azimuthDeg = pose.azimuthDeg,
            elevationDeg = pose.elevationDeg,
            distance = pose.distance,
            qualityScore = qualityScore,
            sharpness = sharpness,
            exposure = exposure,
            coverageCellId = coverageCellId,
            accepted = accepted
        )
    }
}
