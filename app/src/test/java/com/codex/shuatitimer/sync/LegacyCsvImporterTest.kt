package com.codex.shuatitimer.sync

import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.util.CsvExporter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyCsvImporterTest {
    private val row = Session(id = 7, startWallClock = 1_700_000_000_123, endWallClock = 1_700_001_800_456,
        effectiveElapsedMs = 1_790_123, accumulatedPausedMs = 10_456, mode = "COUNT_DOWN", targetDurationMs = 1_800_000,
        plannedQuestionCount = 20, subject = "自定义模块", questionCount = 18, correctCount = 15, note = "逗号,\r\n换行和\"引号\"")

    @Test fun importsOriginalExportPreservingDataAndMinutePrecision() {
        val imported = LegacyCsvImporter.decode(CsvExporter.export(listOf(row))).sessions.single()
        assertEquals(row.subject, imported.subject)
        assertEquals(row.effectiveElapsedMs, imported.effectiveElapsedMs)
        assertEquals(row.accumulatedPausedMs, imported.accumulatedPausedMs)
        assertEquals(row.targetDurationMs, imported.targetDurationMs)
        assertEquals(row.plannedQuestionCount, imported.plannedQuestionCount)
        assertEquals(row.questionCount, imported.questionCount)
        assertEquals(row.correctCount, imported.correctCount)
        assertEquals(row.note, imported.note)
        assertEquals(row.startWallClock / 60_000 * 60_000, imported.startWallClock)
        assertEquals(row.endWallClock / 60_000 * 60_000, imported.endWallClock)
        assertEquals(0L, imported.id)
    }

    @Test fun repeatedImportsHaveStableIdsEvenAcrossTimeZonesAndEditedNotes() {
        val text = CsvExporter.export(listOf(row))
        val first = LegacyCsvImporter.decode(text)
        val repeated = LegacyCsvImporter.decode(text)
        val otherZone = LegacyCsvImporter.decode(text, ZoneId.of("UTC"))
        val edited = LegacyCsvImporter.decode(CsvExporter.export(listOf(row.copy(note = "改备注", correctCount = 16))))
        assertEquals(first, repeated)
        assertEquals(first.sessions.single().syncId, otherZone.sessions.single().syncId)
        assertEquals(first.sessions.single().syncId, edited.sessions.single().syncId)
        assertEquals(listOf("自定义模块"), first.modules.map { it.name })
    }

    @Test fun supportsEmptyExportsAndMissingOptionalValues() {
        assertTrue(LegacyCsvImporter.decode(CsvExporter.export(emptyList())).sessions.isEmpty())
        val source = row.copy(mode = "COUNT_UP", targetDurationMs = null, plannedQuestionCount = null,
            questionCount = null, correctCount = null, note = null)
        val imported = LegacyCsvImporter.decode(CsvExporter.export(listOf(source))).sessions.single()
        assertNull(imported.questionCount)
        assertNull(imported.correctCount)
        assertNull(imported.note)
    }

    @Test fun rejectsBrokenQuotingUnexpectedHeadersAndInvalidRows() {
        assertThrows(IllegalArgumentException::class.java) { LegacyCsvImporter.decode("name,count\na,1") }
        val text = CsvExporter.export(listOf(row))
        assertThrows(IllegalArgumentException::class.java) { LegacyCsvImporter.decode(text.dropLast(3)) }
        assertThrows(IllegalArgumentException::class.java) {
            LegacyCsvImporter.decode(CsvExporter.export(listOf(row.copy(questionCount = -1))))
        }
    }
}
