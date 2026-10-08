package com.codex.shuatitimer.sync

import com.codex.shuatitimer.data.model.Session
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceSyncTest {
    @get:Rule val files = TemporaryFolder()
    private val empty = SyncBundle(emptyList(), emptyList())

    @Test fun approvalGatesDataThenCredentialsSurviveRestartAndRevocationRequiresNewApproval() = runBlocking {
        val leftDir = files.newFolder("left")
        val rightDir = files.newFolder("right")
        val left = PairingStore(leftDir, "我的手机")
        val right = PairingStore(rightDir, "我的平板")
        val ready = CompletableDeferred<Int>()
        val requested = CompletableDeferred<PairingRequest>()
        val waiting = CompletableDeferred<PairingRequest>()
        val decision = CompletableDeferred<Boolean>()
        val approvals = AtomicInteger()
        val exchanged = AtomicInteger()
        val host = DeviceSync(right)
        val job = launch(Dispatchers.IO) {
            try { host.host({ ready.complete(it) }, {
                approvals.incrementAndGet(); requested.complete(it); decision.await()
            }, { exchanged.incrementAndGet(); it }, {}, { throw it }) }
            catch (e: Exception) { if (isActive) throw e }
        }
        val client = DeviceSync(left)
        try {
            val target = NearbyDevice(right.identity().id, right.identity().name, "127.0.0.1", withTimeout(5000) { ready.await() })
            val bundle = SyncBundle(listOf(Session(startWallClock = 100, endWallClock = 200,
                effectiveElapsedMs = 100, mode = "COUNT_UP", subject = "判断推理")), emptyList())
            val result = async(Dispatchers.IO) { client.connect(target, bundle) { waiting.complete(it) } }
            val incoming = withTimeout(5000) { requested.await() }
            assertEquals(incoming.verification, withTimeout(5000) { waiting.await() }.verification)
            assertEquals("我的手机", incoming.name)
            assertEquals(0, exchanged.get())
            assertTrue(left.devices().isEmpty()); assertTrue(right.devices().isEmpty())
            decision.complete(true)
            assertEquals(bundle.sessions, withTimeout(5000) { result.await() }.sessions)
            val reopened = PairingStore(leftDir, "不会覆盖名称")
            assertEquals("我的手机", reopened.identity().name)
            assertEquals(left.identity().id, reopened.identity().id)
            assertEquals(left.devices().single().secret, right.devices().single().secret)
            val originalSecret = left.devices().single().secret
            DeviceSync(reopened).connect(target, empty) { fail("Saved pairing must not ask again") }
            assertEquals(1, approvals.get()); assertEquals(2, exchanged.get())
            right.forget(left.identity().id)
            try { DeviceSync(reopened).connect(target, empty) {}; fail("Revoked pairing must fail") }
            catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("配对已失效")) }
            assertEquals(2, exchanged.get())
            reopened.forget(target.id)
            DeviceSync(reopened).connect(target, empty) {}
            assertEquals(2, approvals.get())
            assertNotEquals(originalSecret, reopened.devices().single().secret)
            assertEquals(reopened.devices().single().secret, right.devices().single().secret)
        } finally { client.stop(); job.cancel(); host.stop(); job.join() }
    }

    @Test fun rejectingPairingSendsNoRecordsAndSavesNoCredentials() = runBlocking {
        val left = PairingStore(files.newFolder(), "手机")
        val right = PairingStore(files.newFolder(), "平板")
        val ready = CompletableDeferred<Int>()
        val host = DeviceSync(right)
        val job = launch(Dispatchers.IO) {
            try { host.host({ ready.complete(it) }, { false }, { fail("Rejected peer cannot exchange records"); it }, {}, { throw it }) }
            catch (e: Exception) { if (isActive) throw e }
        }
        try {
            val target = NearbyDevice(right.identity().id, "平板", "127.0.0.1", withTimeout(5000) { ready.await() })
            try { DeviceSync(left).connect(target, empty) {}; fail("Expected rejection") }
            catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("拒绝")) }
            assertTrue(left.devices().isEmpty()); assertTrue(right.devices().isEmpty())
        } finally { job.cancel(); host.stop(); job.join() }
    }

    @Test fun stoppedTransportAndPublicAddressesAreRejected() = runBlocking<Unit> {
        val store = PairingStore(files.newFolder(), "手机")
        val stopped = DeviceSync(store).also { it.stop() }
        try { stopped.connect(NearbyDevice("peer", "平板", "127.0.0.1", 1), empty) {}; fail() }
        catch (_: IllegalStateException) {}
        try { DeviceSync(store).connect(NearbyDevice("peer", "平板", "8.8.8.8", 1), empty) {}; fail() }
        catch (_: IllegalArgumentException) {}
        store.rename("工作手机")
        assertEquals("工作手机", PairingStore(storeDirectory(), "备用").identity().name)
    }

    @Test fun expiredApprovalNeverSavesOrExchangesData() = runBlocking {
        val left = PairingStore(files.newFolder(), "手机")
        val right = PairingStore(files.newFolder(), "平板")
        val ready = CompletableDeferred<Int>()
        val host = DeviceSync(right, approvalTimeoutMs = 50)
        val job = launch(Dispatchers.IO) {
            try { host.host({ ready.complete(it) }, { delay(10_000); true }, { fail("Expired approval cannot merge"); it }, {}, { throw it }) }
            catch (e: Exception) { if (isActive) throw e }
        }
        try {
            val target = NearbyDevice(right.identity().id, "平板", "127.0.0.1", withTimeout(5000) { ready.await() })
            try { DeviceSync(left).connect(target, empty) {}; fail("Expected timeout rejection") }
            catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("超时")) }
            assertTrue(left.devices().isEmpty()); assertTrue(right.devices().isEmpty())
        } finally { job.cancel(); host.stop(); job.join() }
    }

    private fun storeDirectory() = files.root.listFiles()!!.single()
}
