package com.codex.shuatitimer.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure computation helper for statistics. Works with Session data to compute
 * aggregated stats without Android dependencies.
 */
object StatisticsCalculator {

    fun computeSubjectStats(sessions: List<com.codex.shuatitimer.data.model.Session>): List<com.codex.shuatitimer.SubjectStatistics> =
        sessions.filterNot { it.deleted }.groupBy { it.subject }.map { (name, rows) ->
            val answered = rows.filter { (it.questionCount ?: 0) > 0 }
            val graded = answered.filter { it.correctCount != null }
            com.codex.shuatitimer.SubjectStatistics(
                subject = name,
                durationMs = rows.sumOf { it.effectiveElapsedMs },
                sessionCount = rows.size,
                questionCount = answered.sumOf { it.questionCount!!.toLong() },
                correctCount = graded.sumOf { it.correctCount!!.toLong() },
                gradedQuestionCount = graded.sumOf { it.questionCount!!.toLong() },
                timedQuestionDurationMs = answered.sumOf { it.effectiveElapsedMs }
            )
        }.sortedWith(compareByDescending<com.codex.shuatitimer.SubjectStatistics> { it.durationMs }.thenBy { it.subject })

    data class DailySummary(
        val date: String,           // yyyy-MM-dd
        val totalDurationMs: Long,
        val sessionCount: Int,
        val totalQuestions: Int,
        val totalCorrect: Int
    )

    data class OverallStats(
        val todayTotalDurationMs: Long = 0,
        val todaySessionCount: Int = 0,
        val last7DaysTotalDurationMs: Long = 0,
        val allTimeSessionCount: Int = 0,
        val allTimeTotalDurationMs: Long = 0,
        val totalQuestions: Long = 0,
        val accuracy: Double? = null,
        val avgSecondsPerQuestion: Double? = null
    )

    /**
     * Compute per-day summaries for the last N days.
     */
    fun computeDailySummaries(
        sessions: List<com.codex.shuatitimer.data.model.Session>,
        days: Int = 7,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<DailySummary> {
        val now = LocalDate.now(zoneId)
        val result = mutableMapOf<String, DailySummary>()

        // Initialize empty summaries for the last N days
        for (i in (days - 1) downTo 0) {
            val date = now.minusDays(i.toLong())
            val key = date.toString()
            result[key] = DailySummary(key, 0, 0, 0, 0)
        }

        for (s in sessions) {
            val sessionDate = Instant.ofEpochMilli(s.startWallClock)
                .atZone(zoneId)
                .toLocalDate()
            val key = sessionDate.toString()
            if (result.containsKey(key)) {
                val current = result[key]!!
                result[key] = current.copy(
                    totalDurationMs = current.totalDurationMs + s.effectiveElapsedMs,
                    sessionCount = current.sessionCount + 1,
                    totalQuestions = current.totalQuestions + (s.questionCount ?: 0),
                    totalCorrect = current.totalCorrect + (s.correctCount ?: 0)
                )
            }
        }

        return result.values.toList().sortedBy { it.date }
    }

    /**
     * Compute overall statistics combining today, last-7-days, and all-time data.
     */
    fun computeOverallStats(
        todaySessions: List<com.codex.shuatitimer.data.model.Session>,
        last7DaysSessions: List<com.codex.shuatitimer.data.model.Session>,
        allTimeTotalCount: Int,
        allTimeTotalDuration: Long,
        allTimeTotalQuestions: Long,
        allTimeTotalCorrect: Long,
        allTimeTimedQuestionDurationMs: Long = allTimeTotalDuration,
        allTimeGradedQuestionCount: Long = allTimeTotalQuestions
    ): OverallStats {
        val todayDuration = todaySessions.sumOf { it.effectiveElapsedMs }
        val last7Duration = last7DaysSessions.sumOf { it.effectiveElapsedMs }

        val allTimeAccuracy = if (allTimeGradedQuestionCount > 0) {
            allTimeTotalCorrect.toDouble() / allTimeGradedQuestionCount.toDouble()
        } else null

        val allTimeAvgSec = if (allTimeTotalQuestions > 0 && allTimeTimedQuestionDurationMs > 0) {
            (allTimeTimedQuestionDurationMs / 1000.0) / allTimeTotalQuestions
        } else null

        return OverallStats(
            todayTotalDurationMs = todayDuration,
            todaySessionCount = todaySessions.size,
            last7DaysTotalDurationMs = last7Duration,
            allTimeSessionCount = allTimeTotalCount,
            allTimeTotalDurationMs = allTimeTotalDuration,
            totalQuestions = allTimeTotalQuestions,
            accuracy = allTimeAccuracy,
            avgSecondsPerQuestion = allTimeAvgSec
        )
    }
}
