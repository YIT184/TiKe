package com.codex.shuatitimer.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pure time-formatting and calculation utilities.
 * All time arithmetic uses monotonic/computed values; no android.os API here.
 */
object TimeUtils {

    /**
     * Format a duration in milliseconds as H:MM:SS or M:SS.
     */
    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /**
     * Format a wall-clock timestamp to a human-readable date/time string.
     */
    fun formatDateTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINESE)
        return formatter.format(instant.atZone(zoneId))
    }

    /**
     * Format a wall-clock timestamp to date only.
     */
    fun formatDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINESE)
        return formatter.format(instant.atZone(zoneId))
    }

    /**
     * Get start-of-day epoch millis for the current date in the given zone.
     */
    fun startOfTodayMs(zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val today = LocalDate.now(zoneId)
        return today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Get start-of-day epoch millis for N days ago.
     */
    fun startOfDaysAgoMs(days: Int, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val date = LocalDate.now(zoneId).minusDays(days.toLong())
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Format a duration in milliseconds as a short human-readable string for notifications.
     */
    fun formatDurationShort(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (hours > 0) {
            String.format(Locale.US, "%dh %02dm %02ds", hours, minutes, seconds)
        } else if (minutes > 0) {
            String.format(Locale.US, "%dm %02ds", minutes, seconds)
        } else {
            String.format(Locale.US, "%ds", seconds)
        }
    }
}
