package com.codex.shuatitimer.sync

import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.*
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement
import javax.crypto.spec.SecretKeySpec

/** Version 2: explicit receiver approval, ECDH pairing and a fresh authenticated key per connection. */
class DeviceSync(private val store: PairingStore, private val approvalTimeoutMs: Long = 60_000) {
    @Volatile private var stopped = false
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var peer: Socket? = null

    fun stop() {
        stopped = true
        runCatching { listener?.close() }
        runCatching { peer?.close() }
    }

    suspend fun host(
        ready: (Int) -> Unit,
        approve: suspend (PairingRequest) -> Boolean,
        exchange: suspend (SyncBundle) -> SyncBundle,
        completed: (String) -> Unit,
        failed: (Exception) -> Unit
    ) = withContext(Dispatchers.IO) {
        checkRunning()
        ServerSocket(0).use { server ->
            listener = server
            checkRunning()
            server.soTimeout = 1000
            ready(server.localPort)
            while (!stopped) {
                currentCoroutineContext().ensureActive()
                val connection = try { server.accept() } catch (_: SocketTimeoutException) { continue }
                connection.use { socket ->
                    peer = socket
                    try {
                        checkRunning()
                        require(socket.inetAddress.isSiteLocalAddress || socket.inetAddress.isLinkLocalAddress || socket.inetAddress.isLoopbackAddress)
                        socket.soTimeout = 15_000
                        receive(socket, approve, exchange, completed)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { if (!stopped) failed(e) }
                    finally { peer = null }
                }
            }
        }
    }

    private suspend fun receive(
        socket: Socket, approve: suspend (PairingRequest) -> Boolean,
        exchange: suspend (SyncBundle) -> SyncBundle, completed: (String) -> Unit
    ) {
        val helloText = readHello(socket)
        val hello = JSONObject(helloText)
        val remote = identity(hello)
        require(remote.id != store.identity().id) { "不能与本机配对" }
        require(hello.getString("target") == store.identity().id) { "设备已变化，请重新搜索" }
        nonce(hello)
        val pairing = hello.getString("mode") == "pair"
        require(pairing || hello.getString("mode") == "sync")
        val saved = store.find(remote.id)
        if (!pairing && saved == null) {
            writeHello(socket, JSONObject().put("error", "配对已失效，请取消配对后重新连接").toString())
            return
        }
        val keys = if (pairing) newKeys() else null
        val reply = hello(store.identity()).apply { keys?.let { put("key", encode(it.public.encoded)) } }
        val replyText = reply.toString()
        writeHello(socket, replyText)
        val base = if (pairing) shared(keys!!, hello.getString("key")) else decode(saved!!.secret)
        val session = sessionKey(base, helloText, replyText)
        val secret = if (pairing) encode(session.encoded) else saved!!.secret
        if (pairing && !withTimeoutOrNull(approvalTimeoutMs) { approve(PairingRequest(remote.name, verification(session))) }.let { it == true }) {
            LanSync.writeFrame(socket, session, "v2-ready", "rejected")
            return
        }
        checkRunning()
        LanSync.writeFrame(socket, session, "v2-ready", "approved")
        val incoming = SyncCodec.decode(LanSync.readFrame(socket, session, "v2-request"))
        checkRunning()
        val result = exchange(incoming)
        currentCoroutineContext().ensureActive()
        checkRunning()
        store.save(PairedDevice(remote.id, remote.name, secret))
        LanSync.writeFrame(socket, session, "v2-response", SyncCodec.encode(result))
        completed(remote.name)
    }

    suspend fun connect(device: NearbyDevice, bundle: SyncBundle, waiting: (PairingRequest) -> Unit): SyncBundle = withContext(Dispatchers.IO) {
        checkRunning()
        require(device.port in 1..65535)
        val address = InetAddress.getByName(device.address)
        require(address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress) { "请连接同一 Wi-Fi" }
        val saved = store.find(device.id)
        val keys = if (saved == null) newKeys() else null
        val hello = hello(store.identity()).put("target", device.id).put("mode", if (keys == null) "sync" else "pair")
        keys?.let { hello.put("key", encode(it.public.encoded)) }
        val helloText = hello.toString()
        Socket().use { socket ->
            peer = socket
            checkRunning()
            socket.connect(InetSocketAddress(address, device.port), 10_000)
            socket.soTimeout = 75_000
            writeHello(socket, helloText)
            val replyText = readHello(socket)
            val reply = JSONObject(replyText)
            require(!reply.has("error")) { reply.optString("error") }
            val remote = identity(reply)
            require(remote.id == device.id) { "设备已变化，请重新搜索" }
            nonce(reply)
            val base = keys?.let { shared(it, reply.getString("key")) } ?: decode(saved!!.secret)
            val session = sessionKey(base, helloText, replyText)
            if (keys != null) waiting(PairingRequest(remote.name, verification(session)))
            val status = LanSync.readFrame(socket, session, "v2-ready")
            require(status == "approved") { "对方已拒绝或配对超时" }
            currentCoroutineContext().ensureActive()
            checkRunning()
            socket.soTimeout = 20_000
            LanSync.writeFrame(socket, session, "v2-request", SyncCodec.encode(bundle))
            val result = SyncCodec.decode(LanSync.readFrame(socket, session, "v2-response"))
            currentCoroutineContext().ensureActive()
            checkRunning()
            store.save(PairedDevice(remote.id, remote.name, if (keys != null) encode(session.encoded) else saved!!.secret))
            result
        }
    }

    private fun checkRunning() = check(!stopped) { "连接已关闭" }

    companion object {
        private val random = SecureRandom()
        private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
        private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
        private fun hello(identity: DeviceIdentity) = JSONObject().put("version", 2)
            .put("id", identity.id).put("name", identity.name)
            .put("nonce", encode(ByteArray(32).also(random::nextBytes)))
        private fun identity(json: JSONObject): DeviceIdentity {
            require(json.getInt("version") == 2) { "请将两台设备的题刻更新到同一版本" }
            val id = json.getString("id")
            require(id.matches(Regex("[a-zA-Z0-9-]{1,64}")))
            val name = json.getString("name")
            require(name.length in 1..32 && name.none { it.isISOControl() })
            return DeviceIdentity(id, name)
        }
        private fun nonce(json: JSONObject) = require(decode(json.getString("nonce")).size == 32)
        private fun newKeys() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        private fun shared(keys: KeyPair, remote: String): ByteArray {
            val encoded = decode(remote)
            require(encoded.size in 64..256)
            val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
            val remoteParams = (publicKey as java.security.interfaces.ECPublicKey).params
            val localParams = (keys.public as java.security.interfaces.ECPublicKey).params
            require(remoteParams.curve == localParams.curve && remoteParams.order == localParams.order &&
                remoteParams.generator == localParams.generator && remoteParams.cofactor == localParams.cofactor)
            return KeyAgreement.getInstance("ECDH").apply { init(keys.private); doPhase(publicKey, true) }.generateSecret()
        }
        private fun sessionKey(secret: ByteArray, request: String, reply: String): SecretKeySpec {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret, "HmacSHA256"))
            return SecretKeySpec(mac.doFinal(("tike-device-v2\u0000$request\u0000$reply").toByteArray(Charsets.UTF_8)), "AES")
        }
        private fun verification(key: SecretKeySpec): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(key.encoded)
            val value = java.nio.ByteBuffer.wrap(bytes).int.toLong() and 0xffffffffL
            return (value % 1_000_000).toString().padStart(6, '0')
        }
        private fun writeHello(socket: Socket, text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            require(bytes.size <= 4096)
            DataOutputStream(socket.getOutputStream()).apply { writeInt(bytes.size); write(bytes); flush() }
        }
        private fun readHello(socket: Socket): String {
            val input = DataInputStream(socket.getInputStream())
            val size = input.readInt()
            require(size in 1..4096) { "连接版本不兼容" }
            return ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
        }
    }
}
