package io.github.diegog0477.zombiebox.client.features.playback.platform

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackProgress
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingAirPlayControlPolicyTest {
    @Test
    fun toggleIsOnlySentForAReportedPlayingOrPausedState() {
        assertEquals("playpause", IncomingAirPlayControlPolicy.actionFor("PLAYING", "toggle"))
        assertEquals("playpause", IncomingAirPlayControlPolicy.actionFor("PAUSED", "toggle"))
        assertNull(IncomingAirPlayControlPolicy.actionFor("BUFFERING", "toggle"))
        assertNull(IncomingAirPlayControlPolicy.actionFor("", "toggle"))
    }

    @Test
    fun playAndPauseCommandsAreIdempotentAgainstReportedState() {
        assertEquals("playpause", IncomingAirPlayControlPolicy.actionFor("PAUSED", "play"))
        assertEquals("playpause", IncomingAirPlayControlPolicy.actionFor("PLAYING", "pause"))
        assertNull(IncomingAirPlayControlPolicy.actionFor("PLAYING", "play"))
        assertNull(IncomingAirPlayControlPolicy.actionFor("PAUSED", "pause"))
        assertNull(IncomingAirPlayControlPolicy.actionFor("BUFFERING", "pause"))
    }

    @Test
    fun systemControlsUseReportedAirPlayStateAndRemainUnavailableWhenUnknown() {
        val session = incomingAirPlaySession("PLAYING")

        val paused = IncomingAirPlayControlPolicy.systemPlayback(session, "PAUSED")
        assertEquals("PAUSED", paused?.status)
        assertTrue(paused?.canPause == true)

        val unknown = IncomingAirPlayControlPolicy.systemPlayback(session, "BUFFERING")
        assertNull(unknown)

        val local = session.copy(incoming = false)
        assertNull(IncomingAirPlayControlPolicy.systemPlayback(local, "PLAYING"))
        assertFalse(IncomingAirPlayControlPolicy.isIncomingAirPlay(local))
    }

    private fun incomingAirPlaySession(status: String) =
        PlaybackSession(
            plan =
                PlaybackPlan(
                    sessionId = "airplay-session",
                    url = "/v1/streams/airplay-session",
                    mime = "audio/mp4",
                    mode = "DIRECT_PLAY",
                    resumePositionMs = 0,
                    live = true,
                    seekable = false,
                ),
            item = MediaItem("track", "airplay", "Track", kind = "audio"),
            progress = PlaybackProgress(status, 0, 0),
            incoming = true,
        )
}
