package com.codex.shuatitimer.service

import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.timer.TimerStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerNotificationChronometerTest {
    @Test
    fun countUpUsesElapsedStartAsSystemChronometerBase() {
        val config = TimerNotificationChronometer.from(
            TimerStateStore.Snapshot(
                active = true,
                running = true,
                mode = TimerMode.COUNT_UP,
                effectiveElapsedMs = 90_000L
            ),
            nowWallClock = 1_000_000L
        )

        assertTrue(config.enabled)
        assertFalse(config.countDown)
        assertEquals(910_000L, config.whenMillis)
    }

    @Test
    fun countDownUsesRemainingTimeAsSystemChronometerTarget() {
        val config = TimerNotificationChronometer.from(
            TimerStateStore.Snapshot(
                active = true,
                running = true,
                mode = TimerMode.COUNT_DOWN,
                effectiveElapsedMs = 120_000L,
                targetDurationMs = 300_000L
            ),
            nowWallClock = 1_000_000L
        )

        assertTrue(config.enabled)
        assertTrue(config.countDown)
        assertEquals(1_180_000L, config.whenMillis)
    }

    @Test
    fun overtimeCountdownSwitchesToAnOvertimeStopwatch() {
        val config = TimerNotificationChronometer.from(
            TimerStateStore.Snapshot(
                active = true,
                running = true,
                mode = TimerMode.COUNT_DOWN,
                effectiveElapsedMs = 345_000L,
                targetDurationMs = 300_000L
            ),
            nowWallClock = 1_000_000L
        )

        assertTrue(config.enabled)
        assertFalse(config.countDown)
        assertEquals(955_000L, config.whenMillis)
    }

    @Test
    fun pausedTimerDoesNotUseAChronometer() {
        val config = TimerNotificationChronometer.from(
            TimerStateStore.Snapshot(active = true, running = false),
            nowWallClock = 1_000_000L
        )

        assertFalse(config.enabled)
    }
}
