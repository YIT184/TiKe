package com.codex.shuatitimer.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codex.shuatitimer.data.db.AppDatabase
import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.StudyModule
import com.codex.shuatitimer.data.repository.SessionRepository
import com.codex.shuatitimer.util.CsvExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncRepositoryTest {
    private lateinit var leftDb: AppDatabase
    private lateinit var rightDb: AppDatabase
    private lateinit var left: SessionRepository
    private lateinit var right: SessionRepository

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        leftDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        rightDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        left = SessionRepository(leftDb)
        right = SessionRepository(rightDb)
    }
    @After fun close() { leftDb.close(); rightDb.close() }

    private fun record(subject: String = "资料分析") = Session(
        startWallClock = 1_000, endWallClock = 61_000, effectiveElapsedMs = 60_000,
        mode = "COUNT_UP", subject = subject, questionCount = 10, correctCount = 8
    )

    @Test fun segmentBatchIsAtomicRetrySafeAndKeepsItsMetadataAcrossSync() = runBlocking {
        left.addModule("资料分析")
        val group = "8183f986-e03a-4b09-b6cb-a26de88da427"
        val records = listOf(
            record().copy(segmentGroupId = group, segmentIndex = 1, segmentCount = 2),
            record("新模块").copy(segmentGroupId = group, segmentIndex = 2, segmentCount = 2, segmentStartElapsedMs = 60_000)
        )
        try {
            left.insertSegments(records)
            fail("Unassigned module should reject the whole batch")
        } catch (_: IllegalArgumentException) { }
        assertTrue(left.allSessions.first().isEmpty())
        left.addModule("新模块")
        left.insertSegments(records)
        left.insertSegments(records)
        assertEquals(2, left.allSessions.first().size)
        right.merge(SyncCodec.decode(SyncCodec.encode(left.snapshot())))
        assertEquals(0, right.merge(left.snapshot()))
        val restored = right.allSessions.first().sortedBy { it.segmentIndex }
        assertEquals(listOf("资料分析", "新模块"), restored.map { it.subject })
        assertEquals(listOf(1, 2), restored.map { it.segmentIndex })
        assertEquals(120_000L, restored.sumOf { it.effectiveElapsedMs })
        assertEquals(60_000L, restored.last().segmentStartElapsedMs)
    }

    @Test fun mergesDifferentDevicesWithoutCollidingLocalIdsAndIsIdempotent() = runBlocking {
        left.insert(record())
        right.insert(record("言语理解"))
        assertEquals(1L, left.snapshot().sessions.single().id)
        assertEquals(1L, right.snapshot().sessions.single().id)
        right.merge(SyncCodec.decode(SyncCodec.encode(left.snapshot())))
        left.merge(right.snapshot())
        assertEquals(2, left.allSessions.first().size)
        assertEquals(2, right.allSessions.first().size)
        assertEquals(0, left.merge(right.snapshot()))
        assertEquals(0, right.merge(left.snapshot()))
    }

    @Test fun deletionSurvivesStaleFilesAndClearAllPropagates() = runBlocking {
        left.insert(record())
        val stale = left.snapshot()
        right.merge(stale)
        right.delete(right.allSessions.first().single())
        left.merge(right.snapshot())
        left.merge(stale)
        assertTrue(left.allSessions.first().isEmpty())
        left.insert(record())
        right.merge(left.snapshot())
        left.deleteAll()
        right.merge(left.snapshot())
        assertTrue(right.allSessions.first().isEmpty())
        assertEquals(2, right.snapshot().sessions.size)
    }

    @Test fun modulesPersistSyncAndDeleteWithoutRemovingHistory() = runBlocking {
        assertNull(left.addModule("  图形推理  "))
        assertNotNull(left.addModule("图形推理"))
        assertNotNull(left.addModule(" "))
        left.insert(record("图形推理"))
        val stale = left.snapshot()
        right.merge(stale)
        left.deleteModule("图形推理")
        right.merge(left.snapshot())
        right.merge(stale)
        right.preserveLegacyModule("图形推理")
        assertTrue(right.activeModules.first().isEmpty())
        assertEquals(1, right.allSessions.first().size)
        assertNull(right.addModule("图形推理"))
        left.merge(right.snapshot())
        assertEquals(listOf("图形推理"), left.activeModules.first().map { it.name })
    }

    @Test fun updatesAdvancePastRemoteClockAndStaleEditorCannotResurrect() = runBlocking {
        val remote = record().copy(updatedAt = System.currentTimeMillis() + 86_400_000)
        left.merge(SyncBundle(listOf(remote), emptyList()))
        val editor = left.allSessions.first().single()
        left.update(editor.copy(correctCount = 9))
        assertTrue(left.snapshot().sessions.single().updatedAt > remote.updatedAt)
        left.delete(editor)
        left.update(editor.copy(correctCount = 10))
        assertTrue(left.allSessions.first().isEmpty())
    }

    @Test fun tiesConvergeAndDeletionWinsTie() = runBlocking {
        val first = record().copy(updatedAt = 10, note = "A")
        val second = first.copy(note = "B")
        left.merge(SyncBundle(listOf(first), emptyList()))
        right.merge(SyncBundle(listOf(second), emptyList()))
        val l = left.snapshot(); val r = right.snapshot()
        left.merge(r); right.merge(l)
        assertEquals(SyncCodec.encode(left.snapshot()), SyncCodec.encode(right.snapshot()))
        left.merge(SyncBundle(listOf(first.copy(deleted = true)), emptyList()))
        assertTrue(left.allSessions.first().isEmpty())
    }

    @Test fun invalidImportChangesNothing() = runBlocking {
        left.insert(record())
        val before = SyncCodec.encode(left.snapshot())
        try {
            left.merge(SyncBundle(listOf(record(), record().copy(correctCount = 11)), listOf(StudyModule("新模块"))))
            fail("Should reject invalid data")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, SyncCodec.encode(left.snapshot()))
    }

    @Test fun legacyCsvReimportDoesNotDuplicateOverwriteEditsOrRestoreDeletions() = runBlocking {
        val bundle = LegacyCsvImporter.decode(CsvExporter.export(listOf(record("旧模块").copy(id = 1))))
        left.merge(bundle)
        assertEquals(0, left.merge(bundle))
        val imported = left.allSessions.first().single()
        left.update(imported.copy(note = "新版修改"))
        left.merge(bundle)
        assertEquals("新版修改", left.allSessions.first().single().note)
        left.delete(imported)
        left.merge(bundle)
        assertTrue(left.allSessions.first().isEmpty())
    }
}
