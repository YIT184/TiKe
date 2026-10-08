package com.codex.shuatitimer.util

import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.TimerMode
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    @Test
    fun export_includesBomAndEscapesNotes() {
        val session = Session(
            id = 7,
            startWallClock = 1_700_000_000_000L,
            endWallClock = 1_700_001_800_000L,
            effectiveElapsedMs = 1_800_000L,
            accumulatedPausedMs = 30_000L,
            mode = TimerMode.COUNT_DOWN.name,
            targetDurationMs = 1_800_000L,
            plannedQuestionCount = 20,
            subject = "资料分析",
            questionCount = 20,
            correctCount = 18,
            note = "逗号, 换行\n和\"引号\""
        )

        val csv = CsvExporter.export(listOf(session))

        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("资料分析"))
        assertTrue(csv.contains("\"逗号, 换行\n和\"\"引号\"\"\""))
        assertTrue(csv.contains("90.00%"))
    }
}
