package com.codex.shuatitimer.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class HostDetails(val addresses: List<String>, val port: Int, val code: String)

/** Explicit, foreground-only peer exchange. Records never pass through a cloud server. */
class LanSync {
    @Volatile private var server: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var stopped = false

    fun stop() {
        stopped = true
        runCatching { server?.close() }
        runCatching { socket?.close() }
    }

    suspend fun host(
        onReady: (HostDetails) -> Unit,
        exchange: suspend (SyncBundle) -> SyncBundle,
        onComplete: () -> Unit,
        onRejected: () -> Unit
    ) = withContext(Dispatchers.IO) {
        check(!stopped) { "同步已停止，请重新开启" }
        ServerSocket(0).use { listener ->
            server = listener
            currentCoroutineContext().ensureActive()
            check(!stopped) { "同步已停止，请重新开启" }
            listener.soTimeout = 1_000
            val code = newCode()
            val key = keyFor(code)
            val addresses = localAddresses()
            require(addresses.isNotEmpty()) { "未找到局域网地址，请先连接 Wi-Fi 或热点" }
            currentCoroutineContext().ensureActive()
            check(!stopped) { "同步已停止，请重新开启" }
            onReady(HostDetails(addresses, listener.localPort, code.chunked(4).joinToString("-")))
            val deadline = System.nanoTime() + 10L * 60 * 1_000_000_000
            var rejected = 0
            while (!listener.isClosed && System.nanoTime() < deadline) {
                currentCoroutineContext().ensureActive()
                val peer = try { listener.accept() } catch (_: SocketTimeoutException) { continue }
                peer.use {
                    socket = peer
                    currentCoroutineContext().ensureActive()
                    check(!stopped) { "同步已停止，请重新开启" }
                    peer.soTimeout = 15_000
                    val incoming = try {
                        check(peer.inetAddress.isSiteLocalAddress || peer.inetAddress.isLoopbackAddress)
                        SyncCodec.decode(readFrame(peer, key, "request"))
                    } catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        rejected++
                        onRejected()
                        if (rejected >= 5) throw IllegalStateException("连接验证多次失败，请重新开启同步")
                        null
                    }
                    if (incoming != null) {
                        val reply = exchange(incoming)
                        writeFrame(peer, key, "response", SyncCodec.encode(reply))
                        onComplete()
                    }
                }
                socket = null
            }
        }
    }

    suspend fun connect(endpoint: String, code: String, bundle: SyncBundle): SyncBundle = withContext(Dispatchers.IO) {
        check(!stopped) { "同步已停止，请重新连接" }
        val parts = endpoint.trim().split(':')
        require(parts.size == 2 && parts[0].matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))) { "请输入对方显示的地址和端口，例如 192.168.1.8:12345" }
        require(parts[0].split('.').all { it.toInt() in 0..255 }) { "IP 地址无效" }
        val address = InetAddress.getByName(parts[0])
        require(address.isSiteLocalAddress || address.isLoopbackAddress) { "仅支持局域网地址" }
        val port = parts[1].toIntOrNull()
        require(port != null && port in 1..65535) { "端口无效" }
        val key = keyFor(code)
        Socket().use { peer ->
            socket = peer
            currentCoroutineContext().ensureActive()
            check(!stopped) { "同步已停止，请重新连接" }
            peer.soTimeout = 20_000
            peer.connect(InetSocketAddress(address, port), 10_000)
            writeFrame(peer, key, "request", SyncCodec.encode(bundle))
            val result = SyncCodec.decode(readFrame(peer, key, "response"))
            currentCoroutineContext().ensureActive()
            result
        }
    }

    companion object {
        private const val MAGIC = 0x544B5331
        private val random = SecureRandom()
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun newCode(): String = (1..12).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

        fun keyFor(rawCode: String): SecretKeySpec {
            val code = rawCode.uppercase(java.util.Locale.ROOT).filterNot { it == '-' || it.isWhitespace() }
            require(code.length == 12 && code.all { it in ALPHABET }) { "请输入对方显示的 12 位配对码" }
            val spec = PBEKeySpec(code.toCharArray(), "tike-lan-sync-v1".toByteArray(), 60_000, 256)
            return try {
                SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
            } finally { spec.clearPassword() }
        }

        fun localAddresses(): List<String> = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }
            .filter { it is Inet4Address && it.isSiteLocalAddress }.mapNotNull { it.hostAddress }.distinct()

        internal fun writeFrame(peer: Socket, key: SecretKeySpec, direction: String, text: String) {
            val plain = text.toByteArray(Charsets.UTF_8)
            require(plain.size <= SyncCodec.MAX_BYTES)
            val nonce = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.updateAAD("tike-v1-$direction".toByteArray())
            val encrypted = cipher.doFinal(plain)
            DataOutputStream(peer.getOutputStream()).apply {
                writeInt(MAGIC); writeInt(encrypted.size); write(nonce); write(encrypted); flush()
            }
        }

        internal fun readFrame(peer: Socket, key: SecretKeySpec, direction: String): String {
            val input = DataInputStream(peer.getInputStream())
            require(input.readInt() == MAGIC) { "对方版本不兼容" }
            val size = input.readInt()
            require(size in 16..SyncCodec.MAX_BYTES + 16) { "同步数据大小无效" }
            val nonce = ByteArray(12).also(input::readFully)
            val encrypted = ByteArray(size).also(input::readFully)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.updateAAD("tike-v1-$direction".toByteArray())
            return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }
    }
}
