package com.codex.shuatitimer.util

import com.codex.shuatitimer.data.model.Session
import org.junit.Assert.*
import org.junit.Test

class ModuleStatisticsTest {
    private fun row(module: String, ms: Long, questions: Int?, correct: Int?) = Session(
        startWallClock = 1, endWallClock = 2, effectiveElapsedMs = ms,
        mode = "COUNT_UP", subject = module, questionCount = questions, correctCount = correct
    )
    @Test fun separatesModulesAndWeightsByQuestionsExcludingMissingInputs() {
        val data = listOf(
            row("资料", 100_000, 10, 8), row("资料", 600_000, 30, 15),
            row("资料", 200_000, 10, null), row("资料", 5_000_000, null, null),
            row("资料", 5_000_000, 0, 0), row("言语", 120_000, 20, 20)
        )
        val stats = StatisticsCalculator.computeSubjectStats(data).associateBy { it.subject }
        assertEquals(18.0, stats.getValue("资料").avgSecondsPerQuestion!!, 0.001)
        assertEquals(23.0 / 40, stats.getValue("资料").accuracy!!, 0.001)
        assertEquals(6.0, stats.getValue("言语").avgSecondsPerQuestion!!, 0.001)
        assertEquals(1.0, stats.getValue("言语").accuracy!!, 0.001)
    }
    @Test fun unknownValuesStayUnknownAndDeletedRecordsDoNotCount() {
        val data = listOf(row("模块", 100, 0, 0), row("已删", 100, 1, 1).copy(deleted = true))
        val stats = StatisticsCalculator.computeSubjectStats(data)
        assertEquals(1, stats.size)
        assertNull(stats.single().accuracy)
        assertNull(stats.single().avgSecondsPerQuestion)
    }
}
