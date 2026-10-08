package com.codex.shuatitimer.timer

import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OvertimeTimingTest {
    @Test
    fun countdownContinuesPastZeroAndReportsOnlyTheExcessAsOvertime() {
        val snapshot = TimerStateStore.Snapshot(
            active = true,
            running = true,
            mode = TimerMode.COUNT_DOWN,
            effectiveElapsedMs = 1_245_000L,
            targetDurationMs = 1_200_000L
        )

        assertTrue(snapshot.running)
        assertFalse(snapshot.completed)
        assertEquals(45_000L, snapshot.displayDurationMs)
        assertEquals(45_000L, snapshot.overtimeDurationMs)
    }

    @Test
    fun savedCountdownSessionKeepsActualDurationAndDerivesOvertimeAfterSyncOrExport() {
        val session = Session(
            startWallClock = 1L,
            endWallClock = 1_245_001L,
            effectiveElapsedMs = 1_245_000L,
            mode = TimerMode.COUNT_DOWN.name,
            targetDurationMs = 1_200_000L,
            subject = "资料分析"
        )

        assertEquals(1_245_000L, session.effectiveElapsedMs)
        assertEquals(45_000L, session.overtimeDurationMs)
    }

    @Test
    fun countUpNeverReportsOvertimeEvenWhenAStaleTargetExists() {
        val countUp = TimerStateStore.Snapshot(
            mode = TimerMode.COUNT_UP,
            effectiveElapsedMs = 1_245_000L,
            targetDurationMs = 1_200_000L
        )
        val countUpSession = Session(
            startWallClock = 1L,
            endWallClock = 2L,
            effectiveElapsedMs = 1_245_000L,
            mode = TimerMode.COUNT_UP.name,
            targetDurationMs = 1_200_000L,
            subject = "资料分析"
        )

        assertEquals(0L, countUp.overtimeDurationMs)
        assertEquals(0L, countUpSession.overtimeDurationMs)
    }
}
