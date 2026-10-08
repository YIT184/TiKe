package com.codex.shuatitimer.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codex.shuatitimer.data.db.AppDatabase
import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.repository.SessionRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanSyncTest {
    @Test fun encryptedSocketExchangeMergesBothPeersAndRejectsWrongCode() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val a = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val b = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val left = SessionRepository(a)
        val right = SessionRepository(b)
        val host = LanSync()
        val client = LanSync()
        val ready = CompletableDeferred<HostDetails>()
        val rejected = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val row = Session(startWallClock = 100, endWallClock = 200, effectiveElapsedMs = 100,
            mode = "COUNT_UP", subject = "资料")
        left.insert(row)
        right.addModule("自定义模块")
        val hostJob = launch(Dispatchers.IO) {
            try {
                host.host(
                    onReady = { ready.complete(it) },
                    exchange = { right.merge(it); right.snapshot() },
                    onComplete = { completed.complete(Unit) },
                    onRejected = { rejected.complete(Unit) }
                )
            } catch (e: Exception) { if (isActive) throw e }
        }
        try {
            val details = withTimeout(15_000) { ready.await() }
            val endpoint = "127.0.0.1:${details.port}"
            try {
                client.connect(endpoint, "AAAAAAAAAAAA", left.snapshot())
                fail("Wrong pairing key must fail")
            } catch (_: java.io.EOFException) { }
            withTimeout(5_000) { rejected.await() }
            assertTrue(right.snapshot().sessions.isEmpty())
            val incoming = client.connect(endpoint, details.code, left.snapshot())
            left.merge(incoming)
            withTimeout(5_000) { completed.await() }
            assertEquals(SyncCodec.encode(left.snapshot()), SyncCodec.encode(right.snapshot()))
            val repeated = client.connect(endpoint, details.code, left.snapshot())
            assertEquals(0, left.merge(repeated))
        } finally {
            hostJob.cancel(); host.stop(); client.stop(); hostJob.join()
            a.close(); b.close()
        }
    }

    @Test fun rejectsPublicAddressesAndInvalidPairingCode() = runBlocking<Unit> {
        val client = LanSync()
        try {
            client.connect("8.8.8.8:1234", "AAAAAAAAAAAA", SyncBundle(emptyList(), emptyList()))
            fail("Public IP must not be contacted")
        } catch (_: IllegalArgumentException) { }
        assertThrows(IllegalArgumentException::class.java) { LanSync.keyFor("123") }
    }

    @Test fun stoppedTransportCannotReopenReceiverOrClient() = runBlocking<Unit> {
        val transport = LanSync()
        transport.stop()
        try {
            transport.host(onReady = { fail("Stopped receiver must not become ready") },
                exchange = { it }, onComplete = {}, onRejected = {})
            fail("Stopped host should reject startup")
        } catch (_: IllegalStateException) { }
        try {
            transport.connect("127.0.0.1:1234", "AAAAAAAAAAAA", SyncBundle(emptyList(), emptyList()))
            fail("Stopped client should reject startup")
        } catch (_: IllegalStateException) { }
    }
}
