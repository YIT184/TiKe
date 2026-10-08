package com.codex.shuatitimer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.util.UUID
import kotlin.math.max

/**
 * Persistent session record representing a completed study/practice session.
 */
@Entity(tableName = "sessions", indices = [Index(value = ["syncId"], unique = true)])
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Wall-clock timestamp when the session started (epoch millis). */
    val startWallClock: Long,

    /** Wall-clock timestamp when the session ended (epoch millis). */
    val endWallClock: Long,

    /** Actual elapsed time excluding paused durations, in milliseconds. */
    val effectiveElapsedMs: Long,

    /** Total accumulated paused time in milliseconds (0 if never paused). */
    val accumulatedPausedMs: Long = 0,

    /** Timer mode: "COUNT_UP" or "COUNT_DOWN". */
    val mode: String,

    /** Target duration in milliseconds (only meaningful for COUNT_DOWN). */
    val targetDurationMs: Long? = null,

    /** Optional planned number of questions. */
    val plannedQuestionCount: Int? = null,

    /** Study subject/category display name. */
    val subject: String,

    /** Optional actual number of questions answered. */
    val questionCount: Int? = null,

    /** Optional number of correct answers. Must not exceed questionCount. */
    val correctCount: Int? = null,

    /** Optional free-text note. */
    val note: String? = null,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val recoveredFromScreenshot: Boolean = false
) {
    /**
     * Computed accuracy as a fraction (0.0 to 1.0), or null when not computable.
     */
    val accuracy: Double?
        get() {
            val q = questionCount ?: return null
            val c = correctCount ?: return null
            if (q == 0) return null
            return c.toDouble() / q.toDouble()
        }

    /**
     * Average seconds per answered question, or null when not computable.
     */
    val avgSecondsPerQuestion: Double?
        get() {
            val q = questionCount ?: return null
            if (q == 0) return null
            return (effectiveElapsedMs / 1000.0) / q
        }

    /** The saved amount of time a countdown session exceeded its target duration. */
    val overtimeDurationMs: Long
        get() = if (mode == "COUNT_DOWN") {
            max(0L, effectiveElapsedMs - (targetDurationMs ?: Long.MAX_VALUE))
        } else {
            0L
        }
}
