package com.codex.shuatitimer.util

import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatisticsCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun dailySummaries_includeEmptyDaysAndAggregateSessions() {
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = listOf(
            session(todayStart + 60_000L, 600_000L, 10, 8),
            session(todayStart + 120_000L, 300_000L, 5, 4)
        )

        val summaries = StatisticsCalculator.computeDailySummaries(sessions, 7, zone)
        val today = summaries.last()

        assertEquals(7, summaries.size)
        assertEquals(900_000L, today.totalDurationMs)
        assertEquals(2, today.sessionCount)
        assertEquals(15, today.totalQuestions)
        assertEquals(12, today.totalCorrect)
    }

    @Test
    fun overallStats_calculateAccuracyAndSecondsPerQuestion() {
        val items = listOf(session(1L, 120_000L, 4, 3))
        val stats = StatisticsCalculator.computeOverallStats(
            todaySessions = items,
            last7DaysSessions = items,
            allTimeTotalCount = 1,
            allTimeTotalDuration = 120_000L,
            allTimeTotalQuestions = 4,
            allTimeTotalCorrect = 3
        )

        assertNotNull(stats.accuracy)
        assertEquals(0.75, stats.accuracy!!, 0.0001)
        assertEquals(30.0, stats.avgSecondsPerQuestion!!, 0.0001)
    }

    private fun session(start: Long, duration: Long, questions: Int, correct: Int) = Session(
        startWallClock = start,
        endWallClock = start + duration,
        effectiveElapsedMs = duration,
        mode = TimerMode.COUNT_UP.name,
        subject = "资料分析",
        questionCount = questions,
        correctCount = correct
    )
}
