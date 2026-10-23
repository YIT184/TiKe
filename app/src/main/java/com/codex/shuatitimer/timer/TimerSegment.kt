package com.codex.shuatitimer.timer

/** A lap boundary uses the same effective clock as the parent timer, excluding pauses. */
data class SegmentBoundary(val elapsedMs: Long, val wallClock: Long, val pausedMs: Long)

data class TimerSegment(
    val number: Int,
    val startElapsedMs: Long,
    val durationMs: Long,
    val startWallClock: Long,
    val endWallClock: Long,
    val pausedMs: Long
)

/** The unfinished tail is included once; a zero-length tail after a split is omitted. */
fun TimerStateStore.Snapshot.segmentsAt(finishWallClock: Long): List<TimerSegment> {
    val origin = SegmentBoundary(0L, startWallClock, 0L)
    val ends = segmentBoundaries.toMutableList()
    if (ends.isEmpty() || effectiveElapsedMs > ends.last().elapsedMs) {
        ends += SegmentBoundary(effectiveElapsedMs, finishWallClock, accumulatedPausedMs)
    } else if (ends.isNotEmpty()) {
        // A pause followed immediately by finishing still belongs to the final real segment.
        ends[ends.lastIndex] = ends.last().copy(wallClock = finishWallClock, pausedMs = accumulatedPausedMs)
    }
    return (listOf(origin) + ends).zipWithNext().mapIndexed { index, (start, end) ->
        TimerSegment(index + 1, start.elapsedMs, end.elapsedMs - start.elapsedMs,
            start.wallClock, end.wallClock, (end.pausedMs - start.pausedMs).coerceAtLeast(0L))
    }
}
