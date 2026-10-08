package com.codex.shuatitimer.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.util.UUID

data class DeviceIdentity(val id: String, val name: String)
data class PairedDevice(val id: String, val name: String, val secret: String)
data class NearbyDevice(val id: String, val name: String, val address: String, val port: Int)
data class PairingRequest(val name: String, val verification: String)

/** Private credentials never enter record exports, Android backups or discovery broadcasts. */
class PairingStore(directory: File, defaultName: String) {
    private val file = File(directory, "paired-devices.json")
    private val state: JSONObject

    init {
        directory.mkdirs()
        state = if (file.exists()) {
            file.bufferedReader().use { JSONObject(it.readText()) }
        } else JSONObject().put("id", UUID.randomUUID().toString())
            .put("name", defaultName.take(32).ifBlank { "我的设备" }).put("peers", JSONArray())
        persist()
    }

    @Synchronized fun identity() = DeviceIdentity(state.getString("id"), state.getString("name"))
    @Synchronized fun devices(): List<PairedDevice> = state.getJSONArray("peers").let { peers ->
        (0 until peers.length()).map { peers.getJSONObject(it) }.map {
            PairedDevice(it.getString("id"), it.getString("name"), it.getString("secret"))
        }
    }
    @Synchronized fun find(id: String) = devices().firstOrNull { it.id == id }
    @Synchronized fun save(device: PairedDevice) {
        val peers = devices().filterNot { it.id == device.id } + device
        state.put("peers", JSONArray().apply {
            peers.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("secret", it.secret)) }
        })
        persist()
    }
    @Synchronized fun forget(id: String) {
        state.put("peers", JSONArray().apply {
            devices().filterNot { it.id == id }.forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("secret", it.secret))
            }
        })
        persist()
    }
    @Synchronized fun rename(name: String) {
        require(name.trim().length in 1..32 && name.none { it.isISOControl() }) { "设备名称请输入 1 至 32 个字" }
        state.put("name", name.trim())
        persist()
    }
    private fun persist() {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(state.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try { Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }
}
