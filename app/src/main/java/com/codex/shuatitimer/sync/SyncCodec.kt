package com.codex.shuatitimer.sync

import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.StudyModule
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

data class SyncBundle(val sessions: List<Session>, val modules: List<StudyModule>)

object SyncCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_ITEMS = 50_000
    private const val MAX_TIME = 32_503_680_000_000L

    fun read(input: InputStream): SyncBundle = decode(readText(input))

    fun readText(input: InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            require(output.size() + size <= MAX_BYTES) { "同步文件不能超过 10 MB" }
            output.write(buffer, 0, size)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    fun encode(bundle: SyncBundle): String {
        validate(bundle)
        val sessions = JSONArray()
        bundle.sessions.sortedBy { it.syncId }.forEach { sessions.put(sessionJson(it)) }
        val modules = JSONArray()
        bundle.modules.sortedBy { it.name }.forEach {
            modules.put(JSONObject().put("name", it.name).put("updatedAt", it.updatedAt).put("deleted", it.deleted))
        }
        val result = JSONObject().put("format", "tike-sync").put("version", 1)
            .put("sessions", sessions).put("modules", modules).toString()
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "同步数据超过 10 MB" }
        return result
    }

    fun decode(text: String): SyncBundle {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "同步文件不能超过 10 MB" }
        val root = JSONObject(text.removePrefix("\uFEFF"))
        require(root.getString("format") == "tike-sync" && root.requiredLong("version") == 1L) { "文件格式或版本不支持" }
        val rows = root.getJSONArray("sessions")
        val names = root.getJSONArray("modules")
        require(rows.length() <= MAX_ITEMS && names.length() <= MAX_ITEMS) { "同步条目过多" }
        val sessions = (0 until rows.length()).map { index ->
            val j = rows.getJSONObject(index)
            Session(
                startWallClock = j.requiredLong("startWallClock"), endWallClock = j.requiredLong("endWallClock"),
                effectiveElapsedMs = j.requiredLong("effectiveElapsedMs"), accumulatedPausedMs = j.requiredLong("accumulatedPausedMs"),
                mode = j.getString("mode"), targetDurationMs = j.nullableLong("targetDurationMs"),
                plannedQuestionCount = j.nullableInt("plannedQuestionCount"), subject = j.getString("subject"),
                questionCount = j.nullableInt("questionCount"), correctCount = j.nullableInt("correctCount"),
                note = if (j.isNull("note")) null else j.getString("note"),
                syncId = j.getString("syncId"), updatedAt = j.requiredLong("updatedAt"), deleted = j.getBoolean("deleted"),
                recoveredFromScreenshot = j.optBoolean("recoveredFromScreenshot", false),
                segmentGroupId = if (j.isNull("segmentGroupId")) null else j.getString("segmentGroupId"),
                segmentIndex = j.nullableInt("segmentIndex"),
                segmentCount = j.nullableInt("segmentCount"),
                segmentStartElapsedMs = if (j.has("segmentStartElapsedMs")) j.requiredLong("segmentStartElapsedMs") else 0L
            )
        }
        val modules = (0 until names.length()).map { index ->
            val j = names.getJSONObject(index)
            StudyModule(j.getString("name"), j.requiredLong("updatedAt"), j.getBoolean("deleted"))
        }
        return SyncBundle(sessions, modules).also(::validate)
    }

    private fun JSONObject.requiredLong(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long) { "同步文件中的数字无效：$key" }
        return (value as Number).toLong()
    }
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else requiredLong(key)
    private fun JSONObject.nullableInt(key: String): Int? = nullableLong(key)?.let {
        require(it in 0..Int.MAX_VALUE.toLong()) { "题数无效" }; it.toInt()
    }

    fun validate(bundle: SyncBundle) {
        require(bundle.sessions.size <= MAX_ITEMS && bundle.modules.size <= MAX_ITEMS) { "同步条目过多" }
        require(bundle.sessions.map { it.syncId }.toSet().size == bundle.sessions.size) { "文件包含重复记录标识" }
        require(bundle.modules.map { it.name }.toSet().size == bundle.modules.size) { "文件包含重复模块" }
        bundle.sessions.forEach { s ->
            require(s.syncId.matches(Regex("[a-zA-Z0-9-]{16,64}"))) { "记录标识无效" }
            require(s.subject.isNotBlank() && s.subject.length <= 100) { "记录模块无效" }
            require(s.updatedAt in 0..MAX_TIME && s.startWallClock in 0..MAX_TIME && s.endWallClock in 0..MAX_TIME) { "记录时间无效" }
            require(s.effectiveElapsedMs in 0..MAX_TIME && s.accumulatedPausedMs in 0..MAX_TIME) { "记录时长无效" }
            require(s.mode == "COUNT_UP" || s.mode == "COUNT_DOWN") { "计时模式无效" }
            require(s.targetDurationMs == null || s.targetDurationMs in 1..MAX_TIME) { "倒计时时长无效" }
            require(s.plannedQuestionCount == null || s.plannedQuestionCount >= 0) { "计划题数无效" }
            require(s.questionCount == null || s.questionCount >= 0) { "实际题数无效" }
            require(s.correctCount == null || (s.questionCount != null && s.correctCount in 0..s.questionCount)) { "正确题数无效" }
            require((s.note?.length ?: 0) <= 10_000) { "备注过长" }
            require(s.segmentStartElapsedMs in 0..MAX_TIME && s.effectiveElapsedMs <= MAX_TIME - s.segmentStartElapsedMs) { "分段时间无效" }
            if (s.segmentGroupId != null) {
                require(s.segmentGroupId.matches(Regex("[a-zA-Z0-9-]{16,64}")) &&
                    s.segmentCount != null && s.segmentCount in 2..1000 &&
                    s.segmentIndex != null && s.segmentIndex in 1..s.segmentCount) { "分段信息无效" }
            } else require(s.segmentIndex == null && s.segmentCount == null && s.segmentStartElapsedMs == 0L) { "分段信息不完整" }
        }
        bundle.modules.forEach {
            require(it.name.isNotBlank() && it.name == it.name.trim() && it.name.length <= 100 && it.name.none(Char::isISOControl)) { "模块名称无效" }
            require(it.updatedAt in 0..MAX_TIME) { "模块版本无效" }
        }
    }

    fun remoteWins(local: Session, remote: Session): Boolean = when {
        remote.updatedAt != local.updatedAt -> remote.updatedAt > local.updatedAt
        remote.deleted != local.deleted -> remote.deleted
        else -> sessionJson(remote).toString() > sessionJson(local).toString()
    }

    private fun sessionJson(s: Session) = JSONObject()
        .put("syncId", s.syncId).put("updatedAt", s.updatedAt).put("deleted", s.deleted)
        .put("startWallClock", s.startWallClock).put("endWallClock", s.endWallClock)
        .put("effectiveElapsedMs", s.effectiveElapsedMs).put("accumulatedPausedMs", s.accumulatedPausedMs)
        .put("mode", s.mode).put("targetDurationMs", s.targetDurationMs ?: JSONObject.NULL)
        .put("plannedQuestionCount", s.plannedQuestionCount ?: JSONObject.NULL).put("subject", s.subject)
        .put("questionCount", s.questionCount ?: JSONObject.NULL).put("correctCount", s.correctCount ?: JSONObject.NULL)
        .put("note", s.note ?: JSONObject.NULL).put("recoveredFromScreenshot", s.recoveredFromScreenshot)
        .put("segmentGroupId", s.segmentGroupId ?: JSONObject.NULL)
        .put("segmentIndex", s.segmentIndex ?: JSONObject.NULL).put("segmentCount", s.segmentCount ?: JSONObject.NULL)
        .put("segmentStartElapsedMs", s.segmentStartElapsedMs)
}
