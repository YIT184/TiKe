package com.codex.shuatitimer.timer

import com.codex.shuatitimer.data.model.Session
import java.util.UUID

data class SegmentResult(val subject: String = "", val questions: String = "", val correct: String = "")

/** Maps the disjoint effective-time intervals to regular records for statistics and sync. */
object SegmentSessionBuilder {
    fun build(
        snapshot: TimerStateStore.Snapshot,
        finishWallClock: Long,
        results: List<SegmentResult>,
        activeModules: List<String>,
        note: String
    ): List<Session> {
        require(snapshot.active) { "当前没有可保存的计时" }
        val segments = snapshot.segmentsAt(finishWallClock)
        require(results.size == segments.size) { "分段数量已变化，请重新打开记录页面" }
        val groupId = snapshot.sessionId.ifBlank { UUID.nameUUIDFromBytes("timer:${snapshot.startWallClock}".toByteArray()).toString() }
        return segments.zip(results).map { (segment, result) ->
            val prefix = if (segments.size > 1) "第 ${segment.number} 段：" else ""
            require(result.subject in activeModules) { "${prefix}请选择模块" }
            fun count(text: String, label: String): Int? {
                if (text.isBlank()) return null
                return text.toIntOrNull()?.takeIf { it in 0..9_999 }
                    ?: throw IllegalArgumentException("${prefix}${label}需在 0–9999 之间")
            }
            val questions = count(result.questions, "实际题数")
            val correct = count(result.correct, "正确题数")
            require(correct == null || (questions != null && correct <= questions)) { "${prefix}正确题数需在 0 到实际题数之间" }
            Session(
                startWallClock = segment.startWallClock,
                endWallClock = segment.endWallClock,
                effectiveElapsedMs = segment.durationMs,
                accumulatedPausedMs = segment.pausedMs,
                mode = snapshot.mode.name,
                targetDurationMs = snapshot.targetDurationMs,
                plannedQuestionCount = snapshot.plannedQuestionCount.takeIf { segments.size == 1 },
                subject = result.subject,
                questionCount = questions,
                correctCount = correct,
                note = note.trim().take(500).takeIf { it.isNotBlank() },
                syncId = UUID.nameUUIDFromBytes("$groupId:${segment.number}".toByteArray()).toString(),
                segmentGroupId = groupId.takeIf { segments.size > 1 },
                segmentIndex = segment.number.takeIf { segments.size > 1 },
                segmentCount = segments.size.takeIf { segments.size > 1 },
                segmentStartElapsedMs = segment.startElapsedMs
            )
        }
    }
}
