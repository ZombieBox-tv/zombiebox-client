package io.github.diegog0477.zombiebox.client.features.diagnostics.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioTrackProbePolicyTest {
    @Test
    fun computesPlaybackHeadAdvanceAcrossSignedIntegerWrap() {
        assertEquals(22L, AudioTrackProbePolicy.frameDelta(Int.MAX_VALUE - 10, Int.MIN_VALUE + 11))
    }

    @Test
    fun materialProgressRequiresAtLeastHalfASecondOfFrames() {
        assertEquals(
            AudioTrackProbePolicy.MIN_MATERIAL_HEAD_ADVANCE_FRAMES,
            AudioTrackProbePolicy.frameDelta(0, 22_050),
        )
        assertEquals(22_049L, AudioTrackProbePolicy.frameDelta(0, 22_049))
    }

    @Test
    fun successfulPlaybackHeadIsPersistedAsAdvancingProbeEvidence() {
        val result = AudioTrackProbePolicy.result("audio-track-pcm-stream", 22_050, 44_100)
        assertEquals("PASS", result.status)
        assertEquals(500, result.positionMs)
        assertEquals(
            "UNKNOWN",
            AudioTrackProbePolicy.result("audio-track-pcm-stream", 22_049, 44_100).status,
        )
    }
}
