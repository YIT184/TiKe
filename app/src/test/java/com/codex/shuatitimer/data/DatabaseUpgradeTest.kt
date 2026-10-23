package com.codex.shuatitimer.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codex.shuatitimer.data.db.AppDatabase
import com.codex.shuatitimer.data.db.UpgradeBackup
import com.codex.shuatitimer.data.repository.SessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseUpgradeTest {
    @Test fun versionOneUpgradePreservesAllFieldsAndMakesIndependentBackup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = context.getDatabasePath("shuati_timer.db")
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.enableWriteAheadLogging()
            old.execSQL("""CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                startWallClock INTEGER NOT NULL, endWallClock INTEGER NOT NULL,
                effectiveElapsedMs INTEGER NOT NULL, accumulatedPausedMs INTEGER NOT NULL,
                mode TEXT NOT NULL, targetDurationMs INTEGER, plannedQuestionCount INTEGER,
                subject TEXT NOT NULL, questionCount INTEGER, correctCount INTEGER, note TEXT)""")
            old.execSQL("INSERT INTO sessions VALUES (42, 1000, 61000, 55000, 5000, 'COUNT_DOWN', 60000, 20, '旧自定义模块', 18, 15, '原版备注')")
            old.execSQL("INSERT INTO sessions VALUES (99, 2000, 90000, 88000, 0, 'COUNT_UP', NULL, NULL, '资料分析', NULL, NULL, NULL)")
            old.version = 1
            // Exercise backup while the source connection and its WAL are still open.
            UpgradeBackup.preserveVersionOne(context)
        }
        UpgradeBackup.preserveVersionOne(context)
        val backup = File(context.noBackupFilesDir, "before-v2/shuati_timer.db")
        assertTrue(backup.exists())
        val originalBytes = backup.readBytes()
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, "shuati_timer.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).addCallback(AppDatabase.SEED_CALLBACK).build()
        try {
            val rows = upgraded.sessionDao().allForSync()
            assertEquals(2, rows.size)
            val row = rows.single { it.id == 42L }
            assertEquals(1000L, row.startWallClock)
            assertEquals(61000L, row.endWallClock)
            assertEquals(55000L, row.effectiveElapsedMs)
            assertEquals(5000L, row.accumulatedPausedMs)
            assertEquals("COUNT_DOWN", row.mode)
            assertEquals(60000L, row.targetDurationMs)
            assertEquals(20, row.plannedQuestionCount)
            assertEquals("旧自定义模块", row.subject)
            assertEquals(18, row.questionCount)
            assertEquals(15, row.correctCount)
            assertEquals("原版备注", row.note)
            assertNull(row.segmentGroupId)
            assertNull(row.segmentIndex)
            assertEquals(0L, row.segmentStartElapsedMs)
            assertFalse(row.deleted)
            assertTrue(row.syncId.isNotBlank())
            assertEquals(2, rows.map { it.syncId }.toSet().size)
            assertNull(rows.single { it.id == 99L }.questionCount)
            val repo = SessionRepository(upgraded)
            assertTrue(repo.activeModules.first().any { it.name == "旧自定义模块" })
            assertNull(repo.addModule("持久模块"))
            repo.deleteModule("数量关系")
            repo.deleteAll()
        } finally { upgraded.close() }
        UpgradeBackup.preserveVersionOne(context)
        assertArrayEquals(originalBytes, backup.readBytes())
        SQLiteDatabase.openDatabase(backup.path, null, SQLiteDatabase.OPEN_READONLY).use { old ->
            assertEquals(1, old.version)
            old.rawQuery("SELECT COUNT(*) FROM sessions", null).use {
                assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0))
            }
        }
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, "shuati_timer.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).addCallback(AppDatabase.SEED_CALLBACK).build()
        try {
            val active = reopened.moduleDao().observeActive().first().map { it.name }
            assertTrue("持久模块" in active)
            assertFalse("数量关系" in active)
            assertTrue(reopened.sessionDao().getAllSessions().first().isEmpty())
        } finally { reopened.close() }
    }
}
