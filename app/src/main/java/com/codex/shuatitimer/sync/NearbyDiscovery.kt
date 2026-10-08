package com.codex.shuatitimer.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper

/** Callbacks are serialized; older Android versions allow only one resolution at a time. */
class NearbyDiscovery(context: Context, private val identity: DeviceIdentity) {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var registered = false
    private var discovery: NsdManager.DiscoveryListener? = null
    private var registration: NsdManager.RegistrationListener? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private val present = mutableSetOf<String>()
    private val devices = linkedMapOf<String, NearbyDevice>()
    private var resolving = false
    private var changed: (List<NearbyDevice>) -> Unit = {}
    private var failed: (String) -> Unit = {}

    fun start(port: Int, changed: (List<NearbyDevice>) -> Unit, failed: (String) -> Unit) {
        check(!closed)
        this.changed = changed
        this.failed = failed
        val registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = main.post {
                registered = true
                if (closed) unregister()
            }.let { Unit }
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) = report("无法让其他设备发现本机，请重新搜索")
            override fun onServiceUnregistered(info: NsdServiceInfo) { registered = false }
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) { registered = false }
        }
        registration = registrationListener
        val info = NsdServiceInfo().apply {
            serviceName = "Tike-${identity.id.take(8)}"
            serviceType = TYPE
            this.port = port
            setAttribute("id", identity.id)
            setAttribute("name", identity.name)
            setAttribute("version", "2")
        }
        try { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener) }
        catch (_: Exception) { report("无法让其他设备发现本机，请重新搜索") }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
                report("暂时无法搜索，请检查 Wi-Fi 后重试")
            }
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
            override fun onServiceFound(service: NsdServiceInfo) { main.post {
                if (!closed && service.serviceType.trimEnd('.') == TYPE && present.size < 100 && present.add(service.serviceName)) {
                    queue.addLast(service)
                    resolveNext()
                }
            } }
            override fun onServiceLost(service: NsdServiceInfo) { main.post {
                if (!closed) {
                    present.remove(service.serviceName)
                    devices.remove(service.serviceName)
                    publish()
                }
            } }
        }
        discovery = listener
        try { manager.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
        catch (_: Exception) { report("暂时无法搜索，请检查 Wi-Fi 后重试") }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (closed || resolving) return
        val service = queue.removeFirstOrNull() ?: return
        if (service.serviceName !in present) { resolveNext(); return }
        resolving = true
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, error: Int) { main.post {
                resolving = false
                resolveNext()
            } }
            override fun onServiceResolved(info: NsdServiceInfo) { main.post {
                resolving = false
                if (!closed && service.serviceName in present) {
                    val id = info.attributes["id"]?.toString(Charsets.UTF_8).orEmpty()
                    val name = info.attributes["name"]?.toString(Charsets.UTF_8).orEmpty()
                    val address = info.host
                    if (id != identity.id && id.matches(Regex("[a-zA-Z0-9-]{1,64}")) && name.length in 1..32 &&
                        name.none { it.isISOControl() } && info.attributes["version"]?.toString(Charsets.UTF_8) == "2" &&
                        address != null && (address.isSiteLocalAddress || address.isLinkLocalAddress) && info.port in 1..65535) {
                        devices[service.serviceName] = NearbyDevice(id, name, address.hostAddress!!, info.port)
                        publish()
                    }
                }
                resolveNext()
            } }
        }
        try { manager.resolveService(service, listener) }
        catch (_: Exception) { resolving = false; resolveNext() }
    }
    private fun publish() = changed(devices.values.distinctBy { it.id }.sortedBy { it.name })
    private fun report(message: String) { main.post { if (!closed) failed(message) } }
    private fun unregister() {
        if (registered) registration?.let { runCatching { manager.unregisterService(it) } }
        registered = false
    }
    fun close() {
        closed = true
        discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }
        unregister()
        queue.clear()
        devices.clear()
        present.clear()
    }
    companion object { const val TYPE = "_tike-sync._tcp" }
}
