package io.github.diegog0477.zombiebox.client.features.diagnostics.platform

import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeResult

/** Small pure policy helpers kept independent of Android for JVM verification. */
internal object AudioTrackProbePolicy {
    const val MIN_MATERIAL_HEAD_ADVANCE_FRAMES = 22_050L
    private const val SAMPLE_RATE = 44_100

    /** AudioTrack exposes a signed Int even though the playback-head counter is unsigned. */
    fun frameDelta(start: Int, end: Int): Long = (end.toLong() - start.toLong()) and 0xffff_ffffL

    fun result(id: String, headFrames: Long, acceptedSamples: Long): ProbeResult {
        val positionMs =
            (headFrames * 1000 / SAMPLE_RATE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val passed = acceptedSamples > 0 && headFrames >= MIN_MATERIAL_HEAD_ADVANCE_FRAMES
        return ProbeResult(
            id,
            if (passed) "PASS" else "UNKNOWN",
            positionMs = positionMs,
            detail =
                if (passed) "playback_head_advanced_frames:$headFrames"
                else "insufficient_playback_head_advance:$headFrames",
        )
    }
}
