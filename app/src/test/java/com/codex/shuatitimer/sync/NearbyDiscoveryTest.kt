package com.codex.shuatitimer.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNsdManager
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [FakeNsdManager::class])
class NearbyDiscoveryTest {
    @Test fun announcesNoSecretSerializesResolutionAndDropsLostOrStoppedCallbacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val fake = Shadow.extract<FakeNsdManager>(manager)
        val search = NearbyDiscovery(context, DeviceIdentity("self-id", "手机"))
        var visible = emptyList<NearbyDevice>()
        search.start(12345, { visible = it }, { fail(it) })
        assertEquals(setOf("id", "name", "version"), fake.advertised!!.attributes.keys)
        fake.registration!!.onServiceRegistered(fake.advertised)
        val first = service("one", "peer-one", "平板", 12346)
        val second = service("two", "peer-two", "备用手机", 12347)
        fake.discovery!!.onServiceFound(first)
        fake.discovery!!.onServiceFound(second)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, fake.resolutions.size)
        fake.resolutions[0].second.onServiceResolved(first)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("peer-one"), visible.map { it.id })
        assertEquals(2, fake.resolutions.size)
        fake.discovery!!.onServiceLost(first)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(visible.isEmpty())
        search.close()
        fake.resolutions[1].second.onServiceResolved(second)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(visible.isEmpty())
        assertTrue(fake.stopped)
        assertTrue(fake.unregistered)
    }

    @Test fun ignoresOwnDeviceAndPublicOrIncompatibleAdvertisements() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fake = Shadow.extract<FakeNsdManager>(context.getSystemService(Context.NSD_SERVICE))
        val search = NearbyDiscovery(context, DeviceIdentity("self-id", "手机"))
        var visible = emptyList<NearbyDevice>()
        search.start(12345, { visible = it }, {})
        val own = service("one", "self-id", "手机", 12345)
        val public = service("two", "public", "外部", 12345).apply { host = InetAddress.getByName("8.8.8.8") }
        val old = service("old", "old-id", "旧版", 12345).apply { setAttribute("version", "1") }
        listOf(own, public, old).forEachIndexed { index, info ->
            fake.discovery!!.onServiceFound(info)
            shadowOf(Looper.getMainLooper()).idle()
            fake.resolutions[index].second.onServiceResolved(info)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue(visible.isEmpty())
        search.close()
        fake.registration!!.onServiceRegistered(fake.advertised)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Late registration must also be unregistered", fake.unregistered)
    }

    private fun service(service: String, id: String, name: String, port: Int) = NsdServiceInfo().apply {
        serviceName = service; serviceType = NearbyDiscovery.TYPE; this.port = port
        host = InetAddress.getByName("192.168.1.8")
        setAttribute("id", id); setAttribute("name", name); setAttribute("version", "2")
    }
}

@Implements(NsdManager::class)
class FakeNsdManager : ShadowNsdManager() {
    var advertised: NsdServiceInfo? = null
    var registration: NsdManager.RegistrationListener? = null
    var discovery: NsdManager.DiscoveryListener? = null
    val resolutions = mutableListOf<Pair<NsdServiceInfo, NsdManager.ResolveListener>>()
    var stopped = false
    var unregistered = false
    @Implementation fun registerService(info: NsdServiceInfo, protocol: Int, listener: NsdManager.RegistrationListener) {
        advertised = info; registration = listener
    }
    @Implementation fun unregisterService(listener: NsdManager.RegistrationListener) { unregistered = true }
    @Implementation fun discoverServices(type: String, protocol: Int, listener: NsdManager.DiscoveryListener) { discovery = listener }
    @Implementation fun stopServiceDiscovery(listener: NsdManager.DiscoveryListener) { stopped = true }
    @Implementation fun resolveService(info: NsdServiceInfo, listener: NsdManager.ResolveListener) { resolutions.add(info to listener) }
}
