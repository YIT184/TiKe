package com.codex.shuatitimer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.shuatitimer.data.db.AppDatabase
import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.Subject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FreshDatabaseTest {
    @Test fun newInstallsHaveDefaultModulesAndNoPracticeRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.getInstance(context)
        try {
            assertTrue(database.sessionDao().getAllSessions().first().isEmpty())
            assertEquals(Subject.entries.filter { it != Subject.CUSTOM }.map { it.displayName }.toSet(),
                database.moduleDao().observeActive().first().map { it.name }.toSet())
            assertFalse(context.assets.list("").orEmpty().any { it.startsWith("restored-sessions-") })
        } finally { close(database) }
    }

    @Test fun reopeningThePublicBuildPreservesOnlyUserCreatedRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var database = AppDatabase.getInstance(context)
        try {
            database.sessionDao().insert(Session(startWallClock = 1_000L, endWallClock = 301_000L,
                effectiveElapsedMs = 300_000L, mode = "COUNT_UP", subject = "资料分析",
                questionCount = 10, correctCount = 8, note = "测试记录"))
            close(database)
            database = AppDatabase.getInstance(context)
            val rows = database.sessionDao().getAllSessions().first()
            assertEquals(1, rows.size)
            assertEquals("测试记录", rows.single().note)
        } finally { close(database) }
    }

    private fun close(database: AppDatabase) {
        database.close()
        AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, null)
    }
}
