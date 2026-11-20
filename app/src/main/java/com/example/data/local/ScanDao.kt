package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM capture_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<CaptureSessionEntity>>

    @Query("SELECT * FROM capture_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): CaptureSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: CaptureSessionEntity)

    @Query("DELETE FROM capture_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM capture_targets WHERE sessionId = :sessionId ORDER BY targetId ASC")
    suspend fun getTargetsForSession(sessionId: String): List<CaptureTargetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTargets(targets: List<CaptureTargetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTarget(target: CaptureTargetEntity)

    @Query("DELETE FROM capture_targets WHERE sessionId = :sessionId")
    suspend fun deleteTargetsForSession(sessionId: String)
}
