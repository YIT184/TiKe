package com.codex.shuatitimer.util

import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.TimerMode
import java.io.StringWriter
import java.util.Locale

/**
 * Exports sessions to UTF-8 CSV with BOM for Excel compatibility.
 * Properly escapes commas, double quotes, and newlines in values.
 */
object CsvExporter {

    private const val BOM = "\uFEFF"

    private val HEADER = listOf(
        "ID",
        "科目",
        "模式",
        "开始时间",
        "结束时间",
        "有效时长(毫秒)",
        "暂停时长(毫秒)",
        "目标时长(毫秒)",
        "计划题数",
        "实际题数",
        "正确题数",
        "正确率",
        "备注"
    )

    /**
     * Export a list of sessions to a CSV string with UTF-8 BOM.
     */
    fun export(sessions: List<Session>): String {
        val writer = StringWriter()
        writer.write(BOM)
        writer.write(HEADER.joinToString(",") { escapeCsvField(it) })
        writer.write("\r\n")

        for (session in sessions) {
            val row = listOf(
                session.id.toString(),
                session.subject,
                if (session.mode == TimerMode.COUNT_UP.name) "正计时" else "倒计时",
                TimeUtils.formatDateTime(session.startWallClock),
                TimeUtils.formatDateTime(session.endWallClock),
                session.effectiveElapsedMs.toString(),
                session.accumulatedPausedMs.toString(),
                session.targetDurationMs?.toString() ?: "",
                session.plannedQuestionCount?.toString() ?: "",
                session.questionCount?.toString() ?: "",
                session.correctCount?.toString() ?: "",
                session.accuracy?.let { String.format(Locale.US, "%.2f%%", it * 100) } ?: "",
                if (session.recoveredFromScreenshot) {
                    listOfNotNull(session.note, "[截图恢复：开始时间精确到分钟，用时精确到秒；结束时间为估算，暂停时长未知按0保存。]").joinToString("\n")
                } else session.note ?: ""
            )
            writer.write(row.joinToString(",") { escapeCsvField(it) })
            writer.write("\r\n")
        }

        return writer.toString()
    }

    /**
     * Escape a CSV field value. Wraps in double quotes if the value contains
     * commas, double quotes, or newlines. Doubles any internal double quotes.
     */
    fun escapeCsvField(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }
}
