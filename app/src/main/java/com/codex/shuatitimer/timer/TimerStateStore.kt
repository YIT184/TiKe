package com.codex.shuatitimer.timer

import android.content.Context
import android.os.SystemClock
import com.codex.shuatitimer.data.model.TimerMode
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Durable timer state. We persist action boundaries and derive the current value from
 * elapsedRealtime, so the timer does not depend on a UI/service tick running every second.
 */
class TimerStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    data class Snapshot(
        val active: Boolean = false,
        val running: Boolean = false,
        val completed: Boolean = false,
        val mode: TimerMode = TimerMode.COUNT_UP,
        val subject: String = "资料分析",
        val startWallClock: Long = 0L,
        val effectiveElapsedMs: Long = 0L,
        val accumulatedPausedMs: Long = 0L,
        val targetDurationMs: Long? = null,
        val plannedQuestionCount: Int? = null,
        val countdownAlertSent: Boolean = false,
        val sessionId: String = "",
        val segmentBoundaries: List<SegmentBoundary> = emptyList(),
        val alarmRinging: Boolean = false
    ) {
        val currentSegmentDurationMs: Long
            get() = (effectiveElapsedMs - (segmentBoundaries.lastOrNull()?.elapsedMs ?: 0L)).coerceAtLeast(0L)

        val canSplit: Boolean
            get() = active && running && !completed && currentSegmentDurationMs >= 1_000L && segmentBoundaries.size < 999

        val displayDurationMs: Long
            get() {
                if (mode != TimerMode.COUNT_DOWN) return effectiveElapsedMs
                val target = targetDurationMs ?: return 0L
                return if (effectiveElapsedMs <= target) target - effectiveElapsedMs
                else effectiveElapsedMs - target
            }

        /**
         * Time spent past a countdown target. A countdown intentionally keeps running after
         * reaching zero so the saved session can use the actual elapsed duration.
         */
        val overtimeDurationMs: Long
            get() = if (mode == TimerMode.COUNT_DOWN) {
                max(0L, effectiveElapsedMs - (targetDurationMs ?: Long.MAX_VALUE))
            } else {
                0L
            }

        val hasReachedTarget: Boolean
            get() = mode == TimerMode.COUNT_DOWN &&
                targetDurationMs != null && effectiveElapsedMs >= targetDurationMs

        val progress: Float
            get() {
                val target = targetDurationMs ?: return 0f
                if (target <= 0L) return 0f
                return (effectiveElapsedMs.toDouble() / target.toDouble())
                    .coerceIn(0.0, 1.0)
                    .toFloat()
            }
    }

    @Synchronized
    fun start(
        mode: TimerMode,
        subject: String,
        targetDurationMs: Long?,
        plannedQuestionCount: Int?
    ): Snapshot {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        preferences.edit()
            .clear()
            .putBoolean(KEY_ACTIVE, true)
            .putBoolean(KEY_RUNNING, true)
            .putBoolean(KEY_COMPLETED, false)
            .putString(KEY_MODE, mode.name)
            .putString(KEY_SUBJECT, subject)
            .putLong(KEY_START_WALL, nowWall)
            .putLong(KEY_EFFECTIVE_BASE, 0L)
            .putLong(KEY_PAUSED_BASE, 0L)
            .putLong(KEY_RUN_STARTED_ELAPSED, nowElapsed)
            .putLong(KEY_RUN_STARTED_WALL, nowWall)
            .putLong(KEY_TARGET_DURATION, targetDurationMs ?: 0L)
            .putInt(KEY_PLANNED_QUESTIONS, plannedQuestionCount ?: 0)
            .putBoolean(KEY_COUNTDOWN_ALERT_SENT, false)
            .putString(KEY_SESSION_ID, UUID.randomUUID().toString())
            .commit()
        return snapshot(nowElapsed, nowWall)
    }

    @Synchronized
    fun snapshot(
        nowElapsed: Long = SystemClock.elapsedRealtime(),
        nowWall: Long = System.currentTimeMillis()
    ): Snapshot {
        if (!preferences.getBoolean(KEY_ACTIVE, false)) return Snapshot()

        val running = preferences.getBoolean(KEY_RUNNING, false)
        val completed = preferences.getBoolean(KEY_COMPLETED, false)
        val effectiveBase = preferences.getLong(KEY_EFFECTIVE_BASE, 0L)
        val pausedBase = preferences.getLong(KEY_PAUSED_BASE, 0L)
        val effective = if (running) {
            effectiveBase + safeDelta(
                nowElapsed,
                preferences.getLong(KEY_RUN_STARTED_ELAPSED, nowElapsed),
                nowWall,
                preferences.getLong(KEY_RUN_STARTED_WALL, nowWall)
            )
        } else {
            effectiveBase
        }
        val paused = if (!running && !completed) {
            val pausedAtElapsed = preferences.getLong(KEY_PAUSE_STARTED_ELAPSED, 0L)
            if (pausedAtElapsed > 0L) {
                pausedBase + safeDelta(
                    nowElapsed,
                    pausedAtElapsed,
                    nowWall,
                    preferences.getLong(KEY_PAUSE_STARTED_WALL, nowWall)
                )
            } else pausedBase
        } else pausedBase

        val mode = runCatching {
            TimerMode.valueOf(preferences.getString(KEY_MODE, TimerMode.COUNT_UP.name).orEmpty())
        }.getOrDefault(TimerMode.COUNT_UP)
        val target = preferences.getLong(KEY_TARGET_DURATION, 0L).takeIf { it > 0L }
        val planned = preferences.getInt(KEY_PLANNED_QUESTIONS, 0).takeIf { it > 0 }

        return Snapshot(
            active = true,
            running = running,
            completed = completed,
            mode = mode,
            subject = preferences.getString(KEY_SUBJECT, "资料分析").orEmpty().ifBlank { "资料分析" },
            startWallClock = preferences.getLong(KEY_START_WALL, nowWall),
            effectiveElapsedMs = max(0L, effective),
            accumulatedPausedMs = max(0L, paused),
            targetDurationMs = target,
            plannedQuestionCount = planned,
            countdownAlertSent = preferences.getBoolean(KEY_COUNTDOWN_ALERT_SENT, false),
            sessionId = preferences.getString(KEY_SESSION_ID, null)
                ?: UUID.nameUUIDFromBytes("legacy-timer:${preferences.getLong(KEY_START_WALL, nowWall)}".toByteArray()).toString(),
            segmentBoundaries = readBoundaries(effective, paused),
            alarmRinging = preferences.getBoolean(KEY_ALARM_RINGING, false)
        )
    }

    @Synchronized
    fun split(): Snapshot {
        val current = snapshot()
        if (!current.canSplit) return current
        val boundary = SegmentBoundary(current.effectiveElapsedMs, System.currentTimeMillis(), current.accumulatedPausedMs)
        writeBoundaries(current.segmentBoundaries + boundary)
        return snapshot()
    }

    @Synchronized
    fun undoSplit(): Snapshot {
        val current = snapshot()
        if (current.active && !current.completed && current.segmentBoundaries.isNotEmpty()) {
            writeBoundaries(current.segmentBoundaries.dropLast(1))
        }
        return snapshot()
    }

    @Synchronized
    fun setAlarmRinging(ringing: Boolean) {
        if (preferences.getBoolean(KEY_ACTIVE, false)) {
            preferences.edit().putBoolean(KEY_ALARM_RINGING, ringing).commit()
        }
    }

    private fun writeBoundaries(boundaries: List<SegmentBoundary>) {
        val json = JSONArray()
        boundaries.forEach { json.put(JSONObject().put("elapsed", it.elapsedMs).put("wall", it.wallClock).put("paused", it.pausedMs)) }
        preferences.edit().putString(KEY_SEGMENTS, json.toString()).commit()
    }

    private fun readBoundaries(effective: Long, paused: Long): List<SegmentBoundary> = runCatching {
        val json = JSONArray(preferences.getString(KEY_SEGMENTS, "[]"))
        val result = mutableListOf<SegmentBoundary>()
        for (index in 0 until minOf(json.length(), 999)) {
            val item = json.getJSONObject(index)
            val boundary = SegmentBoundary(item.getLong("elapsed"), item.getLong("wall"), item.getLong("paused"))
            if (boundary.elapsedMs <= (result.lastOrNull()?.elapsedMs ?: 0L) || boundary.elapsedMs > effective ||
                boundary.pausedMs !in (result.lastOrNull()?.pausedMs ?: 0L)..paused) break
            result += boundary
        }
        result.toList()
    }.getOrDefault(emptyList())

    @Synchronized
    fun pause(): Snapshot {
        val current = snapshot()
        if (!current.active || !current.running) return current
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        preferences.edit()
            .putBoolean(KEY_RUNNING, false)
            .putLong(KEY_EFFECTIVE_BASE, current.effectiveElapsedMs)
            .putLong(KEY_PAUSED_BASE, current.accumulatedPausedMs)
            .putLong(KEY_PAUSE_STARTED_ELAPSED, nowElapsed)
            .putLong(KEY_PAUSE_STARTED_WALL, nowWall)
            .commit()
        return snapshot(nowElapsed, nowWall)
    }

    @Synchronized
    fun resume(): Snapshot {
        val current = snapshot()
        if (!current.active || current.running || current.completed) return current
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        preferences.edit()
            .putBoolean(KEY_RUNNING, true)
            .putLong(KEY_EFFECTIVE_BASE, current.effectiveElapsedMs)
            .putLong(KEY_PAUSED_BASE, current.accumulatedPausedMs)
            .putLong(KEY_RUN_STARTED_ELAPSED, nowElapsed)
            .putLong(KEY_RUN_STARTED_WALL, nowWall)
            .putLong(KEY_PAUSE_STARTED_ELAPSED, 0L)
            .putLong(KEY_PAUSE_STARTED_WALL, 0L)
            .commit()
        return snapshot(nowElapsed, nowWall)
    }

    /** Claims the countdown target alert once, including across service recreation. */
    @Synchronized
    fun markCountdownAlertSent(): Boolean {
        val current = snapshot()
        if (!current.active || !current.running || !current.hasReachedTarget || current.countdownAlertSent) {
            return false
        }
        return preferences.edit().putBoolean(KEY_COUNTDOWN_ALERT_SENT, true).commit()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().commit()
    }

    private fun safeDelta(
        nowElapsed: Long,
        startedElapsed: Long,
        nowWall: Long,
        startedWall: Long
    ): Long {
        // elapsedRealtime resets after reboot. Wall time is used only for that recovery path.
        return if (nowElapsed >= startedElapsed) {
            nowElapsed - startedElapsed
        } else {
            max(0L, nowWall - startedWall)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "active_timer_state"
        const val KEY_ACTIVE = "active"
        const val KEY_RUNNING = "running"
        const val KEY_COMPLETED = "completed"
        const val KEY_MODE = "mode"
        const val KEY_SUBJECT = "subject"
        const val KEY_START_WALL = "start_wall"
        const val KEY_EFFECTIVE_BASE = "effective_base"
        const val KEY_PAUSED_BASE = "paused_base"
        const val KEY_RUN_STARTED_ELAPSED = "run_started_elapsed"
        const val KEY_RUN_STARTED_WALL = "run_started_wall"
        const val KEY_PAUSE_STARTED_ELAPSED = "pause_started_elapsed"
        const val KEY_PAUSE_STARTED_WALL = "pause_started_wall"
        const val KEY_TARGET_DURATION = "target_duration"
        const val KEY_PLANNED_QUESTIONS = "planned_questions"
        const val KEY_COUNTDOWN_ALERT_SENT = "countdown_alert_sent"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_SEGMENTS = "segments"
        const val KEY_ALARM_RINGING = "alarm_ringing"
    }
}
