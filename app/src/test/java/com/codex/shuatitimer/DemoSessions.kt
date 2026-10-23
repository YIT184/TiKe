package com.codex.shuatitimer

import com.codex.shuatitimer.data.model.Session

/** Artificial fixtures for tests and README screenshots; never seeded into the app. */
internal fun demoSessions(now: Long = System.currentTimeMillis()): List<Session> = listOf(
    Session(startWallClock = now - 20 * 60_000L, endWallClock = now - 8 * 60_000L,
        effectiveElapsedMs = 720_000L, mode = "COUNT_DOWN", targetDurationMs = 900_000L,
        subject = "资料分析", questionCount = 20, correctCount = 17, note = "演示记录", syncId = "00000000-0000-0000-0000-000000000001"),
    Session(startWallClock = now - 50 * 60_000L, endWallClock = now - 35 * 60_000L,
        effectiveElapsedMs = 900_000L, mode = "COUNT_UP", subject = "判断推理",
        questionCount = 15, correctCount = 12, note = "演示记录", syncId = "00000000-0000-0000-0000-000000000002"),
    Session(startWallClock = now - 80 * 60_000L, endWallClock = now - 75 * 60_000L,
        effectiveElapsedMs = 300_000L, mode = "COUNT_UP", subject = "言语理解",
        questionCount = 10, correctCount = 9, note = "演示记录", syncId = "00000000-0000-0000-0000-000000000003")
)
