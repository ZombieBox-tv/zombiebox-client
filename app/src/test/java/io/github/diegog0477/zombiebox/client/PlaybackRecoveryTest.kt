package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan
import io.github.diegog0477.zombiebox.client.features.playback.domain.policy.PlaybackRecovery
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryTest {
    @Test
    fun hybridPlaybackFallsBackToTranscode() {
        val recovery = PlaybackRecovery()
        recovery.adopt(PlaybackPlan("session", "/stream", "video/mp4", "HYBRID", 0))

        assertEquals("TRANSCODE", recovery.next(1_200))
    }
}
