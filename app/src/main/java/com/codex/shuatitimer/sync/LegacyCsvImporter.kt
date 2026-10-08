package com.codex.shuatitimer.sync

import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.StudyModule
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

/** Import the CSV produced by earlier Tike versions, without changing the original file. */
object LegacyCsvImporter {
    private val header = listOf("ID", "科目", "模式", "开始时间", "结束时间", "有效时长(毫秒)",
        "暂停时长(毫秒)", "目标时长(毫秒)", "计划题数", "实际题数", "正确题数", "正确率", "备注")
    private val dateFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)

    fun decode(text: String, zone: ZoneId = ZoneId.systemDefault()): SyncBundle {
        require(text.toByteArray(Charsets.UTF_8).size <= SyncCodec.MAX_BYTES) { "CSV 文件不能超过 10 MB" }
        val rows = parse(text.removePrefix("\uFEFF"))
        require(rows.firstOrNull() == header) { "请选择旧版题刻导出的原始 CSV 文件" }
        require(rows.size <= 50_001) { "记录数量过多" }
        val sessions = rows.drop(1).mapIndexed { index, values ->
            require(values.size == header.size) { "CSV 第 ${index + 2} 条记录的列数不正确" }
            fun number(column: Int): Long = values[column].toLongOrNull()
                ?: throw IllegalArgumentException("CSV 第 ${index + 2} 条记录的${header[column]}无效")
            fun optionalLong(column: Int): Long? = if (values[column].isBlank()) null else number(column)
            fun count(column: Int): Int? = optionalLong(column)?.let {
                require(it in 0..Int.MAX_VALUE.toLong()) { "CSV 题数无效" }; it.toInt()
            }
            require(number(0) > 0) { "CSV 记录 ID 无效" }
            val mode = when (values[2]) {
                "正计时" -> "COUNT_UP"
                "倒计时" -> "COUNT_DOWN"
                else -> throw IllegalArgumentException("CSV 计时模式无效")
            }
            val start = LocalDateTime.parse(values[3], dateFormat).atZone(zone).toInstant().toEpochMilli()
            val end = LocalDateTime.parse(values[4], dateFormat).atZone(zone).toInstant().toEpochMilli()
            // CSV has no global ID. Stable immutable fields make repeated imports idempotent.
            val identity = listOf("tike-legacy-csv-v1", values[0], values[1], values[2], values[3], values[5], values[6], values[7])
                .joinToString("") { "${it.length}:$it" }
            Session(
                startWallClock = start, endWallClock = end, effectiveElapsedMs = number(5),
                accumulatedPausedMs = number(6), mode = mode, targetDurationMs = optionalLong(7),
                plannedQuestionCount = count(8), subject = values[1], questionCount = count(9),
                correctCount = count(10), note = values[12].takeIf { it.isNotEmpty() },
                syncId = UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8)).toString(), updatedAt = 0
            )
        }
        return SyncBundle(sessions, sessions.map { StudyModule(it.subject) }.distinctBy { it.name }).also(SyncCodec::validate)
    }

    private fun parse(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var index = 0
        fun finishField() { row.add(field.toString()); field.setLength(0); closedQuote = false }
        fun finishRow() {
            finishField()
            result.add(row.toList()); row.clear()
            require(result.size <= 50_001) { "记录数量过多" }
        }
        while (index < text.length) {
            val ch = text[index]
            if (quoted) {
                if (ch == '"') {
                    if (index + 1 < text.length && text[index + 1] == '"') { field.append('"'); index++ }
                    else { quoted = false; closedQuote = true }
                } else field.append(ch)
            } else {
                when (ch) {
                    '"' -> { require(field.isEmpty() && !closedQuote) { "CSV 引号格式无效" }; quoted = true }
                    ',' -> finishField()
                    '\r', '\n' -> {
                        finishRow()
                        if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    }
                    else -> { require(!closedQuote) { "CSV 引号后存在无效字符" }; field.append(ch) }
                }
            }
            index++
        }
        require(!quoted) { "CSV 文件引号未闭合，文件可能损坏" }
        if (field.isNotEmpty() || row.isNotEmpty() || closedQuote) finishRow()
        return result
    }
}
