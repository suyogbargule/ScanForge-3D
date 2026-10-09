package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ScanSessionEntity>>

    @Query("SELECT * FROM scan_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ScanSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScanSessionEntity)

    @Update
    suspend fun updateSession(session: ScanSessionEntity)

    @Query("DELETE FROM scan_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM captured_frames WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getFramesForSession(sessionId: String): Flow<List<FrameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrame(frame: FrameEntity)

    @Query("DELETE FROM captured_frames WHERE sessionId = :sessionId")
    suspend fun deleteFramesForSession(sessionId: String)
}
