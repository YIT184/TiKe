package com.codex.shuatitimer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.codex.shuatitimer.data.model.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    /** Insert a session and return its generated id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: Session): Long

    /** Update an existing session. */
    @Update
    suspend fun update(session: Session)

    /** Delete a single session. */
    @Delete
    suspend fun delete(session: Session)

    /** Get all sessions ordered newest-first. */
    @Query("SELECT * FROM sessions WHERE deleted = 0 ORDER BY startWallClock DESC")
    fun getAllSessions(): Flow<List<Session>>

    /** Get a single session by id. */
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): Session?

    /** Get sessions that started today (local timezone - approximate using 24h window from now). */
    @Query("SELECT * FROM sessions WHERE deleted = 0 AND startWallClock >= :todayStartMs ORDER BY startWallClock DESC")
    fun getTodaySessions(todayStartMs: Long): Flow<List<Session>>

    /** Get sessions that started within the last N days. */
    @Query("SELECT * FROM sessions WHERE deleted = 0 AND startWallClock >= :sinceMs ORDER BY startWallClock DESC")
    fun getSessionsSince(sinceMs: Long): Flow<List<Session>>

    /** Delete all sessions. */
    @Query("UPDATE sessions SET deleted = 1, updatedAt = :revision WHERE deleted = 0")
    suspend fun markAllDeleted(revision: Long)

    @Query("SELECT * FROM sessions ORDER BY syncId")
    suspend fun allForSync(): List<Session>

    @Query("SELECT * FROM sessions WHERE syncId = :syncId")
    suspend fun findBySyncId(syncId: String): Session?

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM sessions")
    suspend fun maxRevision(): Long

    /** Get total count of all sessions. */
    @Query("SELECT COUNT(*) FROM sessions WHERE deleted = 0")
    suspend fun getTotalCount(): Int

    /** Get count of sessions since a timestamp. */
    @Query("SELECT COUNT(*) FROM sessions WHERE deleted = 0 AND startWallClock >= :sinceMs")
    suspend fun getCountSince(sinceMs: Long): Int

    /** Get sum of effectiveElapsedMs across all sessions. */
    @Query("SELECT COALESCE(SUM(effectiveElapsedMs), 0) FROM sessions WHERE deleted = 0")
    suspend fun getTotalDuration(): Long

    /** Get sum of effectiveElapsedMs for sessions since a timestamp. */
    @Query("SELECT COALESCE(SUM(effectiveElapsedMs), 0) FROM sessions WHERE deleted = 0 AND startWallClock >= :sinceMs")
    suspend fun getDurationSince(sinceMs: Long): Long

    /** Get total question count across all sessions. */
    @Query("SELECT COALESCE(SUM(questionCount), 0) FROM sessions WHERE deleted = 0")
    suspend fun getTotalQuestions(): Long

    /** Get total correct count across all sessions. */
    @Query("SELECT COALESCE(SUM(correctCount), 0) FROM sessions WHERE deleted = 0")
    suspend fun getTotalCorrect(): Long

    /** Get non-null list of sessions with questions for per-day aggregation. */
    @Query("SELECT * FROM sessions WHERE deleted = 0 AND startWallClock >= :sinceMs ORDER BY startWallClock ASC")
    suspend fun getSessionsSinceList(sinceMs: Long): List<Session>
}
