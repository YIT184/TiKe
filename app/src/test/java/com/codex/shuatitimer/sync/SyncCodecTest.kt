package com.codex.shuatitimer.sync

import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.StudyModule
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncCodecTest {
    private val record = Session(startWallClock = 1, endWallClock = 2, effectiveElapsedMs = 1,
        mode = "COUNT_UP", subject = "测试", note = "中文\n\"备注\"")
    @Test fun roundTripKeepsUnicodeNullsAndTombstonesWithoutLocalId() {
        val bundle = SyncBundle(listOf(record.copy(deleted = true)), listOf(StudyModule("模块", 5, true)))
        assertEquals(bundle, SyncCodec.decode(SyncCodec.encode(bundle)))
        assertEquals(0L, SyncCodec.decode(SyncCodec.encode(bundle.copy(sessions = listOf(record.copy(id = 999))))).sessions.single().id)
    }
    @Test fun rejectsUnknownVersionDuplicateIdsOverflowAndInvalidCounts() {
        val valid = SyncCodec.encode(SyncBundle(listOf(record), emptyList()))
        assertThrows(IllegalArgumentException::class.java) { SyncCodec.decode(JSONObject(valid).put("version", 9).toString()) }
        assertThrows(IllegalArgumentException::class.java) { SyncCodec.encode(SyncBundle(listOf(record, record), emptyList())) }
        val root = JSONObject(valid)
        root.getJSONArray("sessions").getJSONObject(0).put("questionCount", Long.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { SyncCodec.decode(root.toString()) }
        root.getJSONArray("sessions").getJSONObject(0).put("questionCount", 1.5)
        assertThrows(IllegalArgumentException::class.java) { SyncCodec.decode(root.toString()) }
        assertThrows(IllegalArgumentException::class.java) { SyncCodec.encode(SyncBundle(listOf(record.copy(questionCount = 0, correctCount = 1)), emptyList())) }
    }
    @Test fun rejectsOversizedStreamBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncCodec.read(java.io.ByteArrayInputStream(ByteArray(SyncCodec.MAX_BYTES + 1)))
        }
    }
}
