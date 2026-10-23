package com.codex.shuatitimer.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.sync.SyncBundle
import com.codex.shuatitimer.sync.SyncCodec
import com.codex.shuatitimer.util.StatisticsCalculator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TimerSegmentsTest {
    private lateinit var context: Context
    private lateinit var store: TimerStateStore
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = TimerStateStore(context)
        store.clear()
    }
    private fun advance(ms: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(ms))

    @Test fun segmentsSurviveRecreationAndExcludeEveryPauseWithoutLosingTime() {
        store.start(TimerMode.COUNT_UP, "资料分析", null, null)
        advance(5_000); store.split()
        advance(2_000); store.pause()
        advance(10_000); store.resume()
        advance(3_000); store.split()
        advance(4_000)
        val snapshot = TimerStateStore(context).pause()
        val segments = snapshot.segmentsAt(System.currentTimeMillis())
        assertEquals(listOf(5_000L, 5_000L, 4_000L), segments.map { it.durationMs })
        assertEquals(14_000L, segments.sumOf { it.durationMs })
        assertEquals(10_000L, segments.sumOf { it.pausedMs })
        assertEquals(4_000L, snapshot.currentSegmentDurationMs)
        assertEquals(snapshot.sessionId, store.snapshot().sessionId)
        assertEquals(snapshot.segmentBoundaries, store.snapshot().segmentBoundaries)
    }

    @Test fun doubleTapPausedTapUndoAndZeroLengthTailDoNotProducePhantomSegments() {
        store.start(TimerMode.COUNT_UP, "资料分析", null, null)
        assertTrue(store.split().segmentBoundaries.isEmpty())
        advance(1_000); store.split(); store.split()
        assertEquals(1, store.snapshot().segmentBoundaries.size)
        assertEquals(1, store.pause().segmentsAt(System.currentTimeMillis()).size)
        advance(5_000)
        assertEquals(1, store.split().segmentBoundaries.size)
        assertTrue(store.undoSplit().segmentBoundaries.isEmpty())
        assertEquals(1_000L, store.snapshot().currentSegmentDurationMs)
        store.resume(); advance(1_000)
        assertEquals(2_000L, store.split().segmentBoundaries.single().elapsedMs)
        store.clear()
        store.start(TimerMode.COUNT_UP, "资料分析", null, null)
        assertTrue(store.snapshot().segmentBoundaries.isEmpty())
    }

    @Test fun assignedModulesSyncAndStatisticsConserveTimeAndCountdownOvertime() {
        store.start(TimerMode.COUNT_DOWN, "资料分析", 10_000L, 20)
        advance(4_000); store.split()
        advance(4_000); store.split()
        advance(3_000); store.split()
        advance(2_000); store.split()
        advance(2_000)
        val snapshot = store.pause()
        val assignments = listOf("资料分析", "新建模块", "资料分析", "新建模块", "资料分析").map { SegmentResult(it) }
        val sessions = SegmentSessionBuilder.build(snapshot, System.currentTimeMillis(), assignments,
            listOf("资料分析", "新建模块"), "套题复盘")
        assertEquals(listOf(0L, 0L, 1_000L, 2_000L, 2_000L), sessions.map { it.overtimeDurationMs })
        assertEquals(snapshot.overtimeDurationMs, sessions.sumOf { it.overtimeDurationMs })
        assertEquals(15_000L, sessions.sumOf { it.effectiveElapsedMs })
        assertEquals(5, sessions.map { it.syncId }.toSet().size)
        assertEquals(1, sessions.map { it.segmentGroupId }.toSet().size)
        assertTrue(sessions.all { it.questionCount == null && it.correctCount == null && it.plannedQuestionCount == null })
        val restored = SyncCodec.decode(SyncCodec.encode(SyncBundle(sessions, emptyList()))).sessions.sortedBy { it.segmentIndex }
        assertEquals(sessions, restored)
        val stats = StatisticsCalculator.computeSubjectStats(restored)
        assertEquals(9_000L, stats.single { it.subject == "资料分析" }.durationMs)
        assertEquals(6_000L, stats.single { it.subject == "新建模块" }.durationMs)
        assertTrue(stats.all { it.accuracy == null && it.avgSecondsPerQuestion == null })
        assertEquals(sessions.map { it.syncId }, SegmentSessionBuilder.build(snapshot, System.currentTimeMillis(), assignments,
            listOf("资料分析", "新建模块"), "").map { it.syncId })
    }

    @Test fun unassignedDeletedModulesAndInvalidGradesCannotBeSaved() {
        val snapshot = store.start(TimerMode.COUNT_UP, "资料分析", null, null)
        fun build(result: SegmentResult) = SegmentSessionBuilder.build(snapshot, System.currentTimeMillis(), listOf(result), listOf("资料分析"), "")
        assertThrows(IllegalArgumentException::class.java) { build(SegmentResult()) }
        assertThrows(IllegalArgumentException::class.java) { build(SegmentResult("被删除模块")) }
        assertThrows(IllegalArgumentException::class.java) { build(SegmentResult("资料分析", "2", "3")) }
        assertThrows(IllegalArgumentException::class.java) { build(SegmentResult("资料分析", "", "1")) }
        val session = build(SegmentResult("资料分析", "10", "8")).single()
        assertEquals(0.8, session.accuracy!!, 0.001)
        assertNull(session.segmentGroupId)
    }
}
