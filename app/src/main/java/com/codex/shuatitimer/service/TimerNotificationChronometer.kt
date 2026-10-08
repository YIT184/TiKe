package com.codex.shuatitimer.service

import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.timer.TimerStateStore

/** Describes the system chronometer shown by SystemUI inside the ongoing notification. */
internal object TimerNotificationChronometer {
    data class Config(
        val enabled: Boolean,
        val countDown: Boolean = false,
        val whenMillis: Long = 0L
    )

    fun from(
        snapshot: TimerStateStore.Snapshot,
        nowWallClock: Long = System.currentTimeMillis()
    ): Config {
        if (!snapshot.active || !snapshot.running || snapshot.completed) return Config(false)

        return if (snapshot.mode == TimerMode.COUNT_DOWN && !snapshot.hasReachedTarget) {
            Config(
                enabled = true,
                countDown = true,
                whenMillis = nowWallClock + snapshot.displayDurationMs
            )
        } else {
            Config(
                enabled = true,
                countDown = false,
                whenMillis = nowWallClock - if (snapshot.mode == TimerMode.COUNT_DOWN) {
                    snapshot.overtimeDurationMs
                } else {
                    snapshot.effectiveElapsedMs
                }
            )
        }
    }
}
