package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.playback.presentation.ui.AudioTrackControlPolicy
import io.github.diegog0477.zombiebox.client.features.playback.presentation.ui.PlayerChromeSections
import org.junit.Assert.*
import org.junit.Test

class PlayerChromeSectionsTest {
    @Test
    fun activeReceiverTrackControlsIgnoreTransientPlaybackStatusAndQueueState() {
        val active =
            AudioTrackControlPolicy.isActiveReceiverAudio(
                incoming = true,
                provider = "airplay",
                kind = "audio",
                sessionId = "airplay-session",
                activeReceiverSessionId = "airplay-session",
            )

        listOf("PLAYING", "BUFFERING", "PAUSED").forEach { status ->
            // Status is intentionally absent from the availability predicate.
            assertTrue("track controls stay active while $status", active)
            assertTrue(AudioTrackControlPolicy.canShowNext(false, active))
        }
        assertFalse(AudioTrackControlPolicy.canShowNext(false, false))
        assertTrue(AudioTrackControlPolicy.canShowNext(true, false))
    }

    @Test
    fun receiverTrackControlsRequireTheActiveIncomingAudioSession() {
        assertFalse(
            AudioTrackControlPolicy.isActiveReceiverAudio(
                incoming = false,
                provider = "airplay",
                kind = "audio",
                sessionId = "airplay-session",
                activeReceiverSessionId = "airplay-session",
            )
        )
        assertFalse(
            AudioTrackControlPolicy.isActiveReceiverAudio(
                incoming = true,
                provider = "airplay",
                kind = "video",
                sessionId = "airplay-session",
                activeReceiverSessionId = "airplay-session",
            )
        )
        assertFalse(
            AudioTrackControlPolicy.isActiveReceiverAudio(
                incoming = true,
                provider = "airplay",
                kind = "audio",
                sessionId = "old-session",
                activeReceiverSessionId = "airplay-session",
            )
        )
    }

    @Test
    fun lowerPanelOpensOnDemandAndSurvivesRelatedPagination() {
        val state = PlayerChromeSections()
        state.bind("video-a", related = true, details = true)
        assertFalse(state.expanded)
        assertTrue(state.expand())
        state.bind("video-a", related = true, details = true)
        assertTrue(state.expanded)
        assertTrue(state.collapse())
        assertFalse(state.expanded)
        state.bind("video-a", related = true, details = true)
        assertFalse(state.expanded)
    }

    @Test
    fun newVideoAndUnavailableContentResetThePanel() {
        val state = PlayerChromeSections()
        state.bind("video-a", related = true, details = false)
        assertTrue(state.expand())
        state.bind("video-b", related = true, details = false)
        assertFalse(state.expanded)
        assertTrue(state.expand())
        state.bind("video-b", related = false, details = false)
        assertFalse(state.expanded)
        assertFalse(state.expand())
    }
}
