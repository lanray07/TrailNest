package com.trailnest.app.domain

data class RecordingDraft(val id: String, val startedAt: Long, val activeMillis: Long = 0,
    val metres: Double = 0.0, val running: Boolean = false, val interrupted: Boolean = false, val hasFix: Boolean = false)

/** Monotonic active time; pauses and long location gaps break distance segments. */
class WalkRecording(var draft: RecordingDraft, now: Long) {
    private var checkpoint = now
    private var segmentStartedAt = now
    private var previous: Pair<GeoPoint, Long>? = null
    fun tick(now: Long): RecordingDraft {
        if (draft.running) draft = draft.copy(activeMillis = draft.activeMillis + (now - checkpoint).coerceAtLeast(0))
        checkpoint = now
        if (previous == null || now - previous!!.second > 15_000) draft = draft.copy(hasFix = false)
        return draft
    }
    fun resume(now: Long) { checkpoint = now; segmentStartedAt = now; previous = null; draft = draft.copy(running = true, interrupted = false, hasFix = false) }
    fun pause(now: Long) { tick(now); previous = null; draft = draft.copy(running = false, hasFix = false) }
    fun fix(point: GeoPoint, accuracy: Float, measuredAt: Long, now: Long): Boolean {
        if (!draft.running || !accuracy.isFinite() || accuracy !in 0f..40f || measuredAt < segmentStartedAt || measuredAt > now || now - measuredAt > 15_000) return false
        val last = previous
        if (last != null && measuredAt <= last.second) return false
        val distance = last?.let { TrailMath.distance(it.first, point) } ?: 0.0
        val gap = last?.let { measuredAt - it.second } ?: 0L
        if (last != null && gap <= 30_000 && distance > 3 && distance / (gap / 1000.0) <= 6.0)
            draft = draft.copy(metres = draft.metres + distance)
        previous = point to measuredAt
        tick(now); draft = draft.copy(hasFix = true)
        return true
    }
}
