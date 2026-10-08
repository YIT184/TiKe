package com.codex.shuatitimer.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {
    @Test
    fun formatDuration_handlesMinutesAndHours() {
        assertEquals("0:00", TimeUtils.formatDuration(0L))
        assertEquals("1:05", TimeUtils.formatDuration(65_000L))
        assertEquals("1:01:01", TimeUtils.formatDuration(3_661_000L))
    }

    @Test
    fun formatDuration_neverAddsFractionalSeconds() {
        assertEquals("0:59", TimeUtils.formatDuration(59_999L))
    }
}
