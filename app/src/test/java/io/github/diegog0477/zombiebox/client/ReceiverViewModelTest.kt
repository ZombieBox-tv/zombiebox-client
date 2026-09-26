package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.PlaybackContext
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.ReceiverChange
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.ReceiverPlan
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.repository.ReceiverRepository
import io.github.diegog0477.zombiebox.client.features.mirroring.presentation.viewmodel.ReceiverPlaybackPolicy
import io.github.diegog0477.zombiebox.client.features.mirroring.presentation.viewmodel.ReceiverPollingPolicy
import io.github.diegog0477.zombiebox.client.features.mirroring.presentation.viewmodel.ReceiverViewModel
import org.junit.Assert.*
import org.junit.Test

class ReceiverViewModelTest {
    @Test
    fun onlyActiveIncomingAirPlayAudioUsesBoundedFastPolling() {
        assertEquals(
            ReceiverPollingPolicy.ACTIVE_AIRPLAY_AUDIO_INTERVAL_MS,
            ReceiverPollingPolicy.intervalMs(activeIncomingAirPlayAudio = true),
        )
        assertEquals(
            ReceiverPollingPolicy.DEFAULT_INTERVAL_MS,
            ReceiverPollingPolicy.intervalMs(activeIncomingAirPlayAudio = false),
        )
    }

    @Test
    fun receiverRefreshDoesNotOverlapWhenFastPollTickArrivesDuringDelivery() {
        val work = mutableListOf<() -> Unit>()
        val ui = mutableListOf<() -> Unit>()
        val model = ReceiverViewModel(Repo(), { work.add(it) }, { ui.add(it) })

        model.refresh()
        model.refresh()
        assertEquals(1, work.size)

        work.removeAt(0)()
        model.refresh()
        assertTrue(work.isEmpty())

        ui.removeAt(0)()
        model.refresh()
        assertEquals(1, work.size)
    }

    private class Repo : ReceiverRepository {
        val stopped = mutableListOf<String>()
        val airplayActions = mutableListOf<String>()

        var plan: ReceiverPlan? =
            ReceiverPlan("session", "/v1/streams/session", "application/vnd.apple.mpegurl")

        var activeFailure: Exception? = null

        override fun active(): ReceiverPlan? {
            activeFailure?.let { throw it }
            return plan
        }

        override fun stop(sessionId: String) {
            stopped.add(sessionId)
        }

        override fun mediaProvider() = selectedProvider

        var selectedProvider = ""

        override fun selectMediaProvider(provider: String) {
            selectedProvider = provider
        }

        override fun command(action: String) {}

        override fun airplayCommand(action: String) {
            airplayActions += action
        }

        override fun handoffEnabled() = false

        override fun setHandoffEnabled(enabled: Boolean) {}

        override fun enabled() = true

        override fun setEnabled(enabled: Boolean) {}
    }

    @Test
    fun airplayTrackCommandIsSentOnceToTheAirplayRepositoryBoundary() {
        val work = mutableListOf<() -> Unit>()
        val ui = mutableListOf<() -> Unit>()
        val repo = Repo()
        val model = ReceiverViewModel(repo, { work.add(it) }, { ui.add(it) })

        model.airplayCommand("next") { fail("AirPlay action failed: $it") }
        assertEquals(1, work.size)
        work.removeAt(0)()
        assertEquals(listOf("next"), repo.airplayActions)
        assertEquals(1, ui.size)
    }

    @Test
    fun dismissedCastCannotRestartFromAnInFlightPoll() {
        val work = mutableListOf<() -> Unit>()
        val ui = mutableListOf<() -> Unit>()
        val repo = Repo()
        val model = ReceiverViewModel(repo, { work.add(it) }, { ui.add(it) })
        var deliveries = 0
        model.observer = { deliveries++ }
        model.refresh()
        work.removeAt(0)()
        model.dismiss("session")
        while (work.isNotEmpty()) work.removeAt(0)()
        ui.removeAt(0)()
        assertEquals(0, deliveries)
        model.refresh()
        work.removeAt(0)()
        ui.removeAt(0)()
        assertEquals(listOf("session"), repo.stopped)
        assertEquals(1, deliveries)
    }

    @Test
    fun closedReceiverDoesNotDeliverLatePlans() {
        val work = mutableListOf<() -> Unit>()
        val model = ReceiverViewModel(Repo(), { work.add(it) }, { it() })
        model.observer = { fail("closed observer called") }
        model.refresh()
        model.close()
        work.removeAt(0)()
    }

    @Test
    fun remoteStopRestoresPausedPlaybackContextButReceiverStopDiscardsIt() {
        val model = ReceiverViewModel(Repo(), { it() }, { it() })
        val previous = PlaybackContext(MediaItem("movie", "local", "Movie"), false, false)
        val cast = ReceiverPlan("cast", "/v1/streams/cast", "application/vnd.apple.mpegurl")
        assertTrue(model.transition(cast, previous) is ReceiverChange.Begin)
        assertNull(model.transition(cast, PlaybackContext(null, true, true)))
        assertEquals(
            ReceiverChange.Restore(previous),
            model.transition(null, PlaybackContext(null, true, true)),
        )
        model.transition(cast, previous)
        model.dismiss("cast")
        assertNull(model.transition(cast, previous))
        assertNull(model.transition(null, previous))
    }

    @Test
    fun fullscreenAudioReceiverSurvivesTrackSessionChangesButNewReceptionUsesItsOwnMode() {
        val current =
            PlaybackContext(
                MediaItem("first", "airplay", "First", kind = "audio"),
                true,
                true,
                "first-session",
            )
        val firstPlan =
            ReceiverPlan(
                "first-session",
                "/stream/first",
                "audio/mpeg",
                current.item,
                fullscreen = false,
            )
        val nextTrack =
            ReceiverPlan(
                "second-session",
                "/stream/second",
                "audio/mpeg",
                MediaItem("second", "airplay", "Second", kind = "audio"),
                fullscreen = false,
            )
        val model = ReceiverViewModel(Repo(), { it() }, { it() })

        assertTrue(
            model.transition(firstPlan, PlaybackContext(null, false, false)) is ReceiverChange.Begin
        )
        assertTrue(model.transition(nextTrack, current) is ReceiverChange.Begin)
        assertTrue(ReceiverPlaybackPolicy.preserveFullscreenForAudioChange(current, nextTrack))
        assertFalse(
            ReceiverPlaybackPolicy.preserveFullscreenForAudioChange(
                current.copy(incomingReceiverSessionId = ""),
                nextTrack,
            )
        )
        assertFalse(
            ReceiverPlaybackPolicy.preserveFullscreenForAudioChange(
                current,
                nextTrack.copy(item = MediaItem("video", "airplay", "Video", kind = "video")),
            )
        )
    }

    @Test
    fun localIncomingAudioProgressIsStableOnlyWithinTheSameReceiverSession() {
        val currentTrack = MediaItem("track", "airplay", "Song", kind = "audio")
        val firstPlan =
            ReceiverPlan(
                "session",
                "/stream/session",
                "audio/mpeg",
                currentTrack,
                fullscreen = false,
            )
        val model = ReceiverViewModel(Repo(), { it() }, { it() })

        assertTrue(
            model.transition(firstPlan, PlaybackContext(null, false, false)) is ReceiverChange.Begin
        )
        assertTrue(
            model.transition(firstPlan.copy(state = "PAUSED"), PlaybackContext(null, false, false))
                is ReceiverChange.Update
        )
        assertTrue(
            model.transition(firstPlan.copy(state = "PLAYING"), PlaybackContext(null, false, false))
                is ReceiverChange.Update
        )
        assertEquals(
            65000,
            ReceiverPlaybackPolicy.positionForIncomingAudio(
                "session",
                "session",
                "session",
                currentTrack,
                65000,
                0,
                holdBackwardCorrection = true,
            ),
        )
        assertEquals(
            0,
            ReceiverPlaybackPolicy.positionForIncomingAudio(
                "new-session",
                "new-session",
                "old-session",
                currentTrack,
                65000,
                0,
                holdBackwardCorrection = true,
            ),
        )
        assertEquals(
            0,
            ReceiverPlaybackPolicy.positionForIncomingAudio(
                "session",
                "session",
                "",
                currentTrack,
                65000,
                0,
                holdBackwardCorrection = true,
            ),
        )
        assertEquals(
            20000,
            ReceiverPlaybackPolicy.positionForIncomingAudio(
                "session",
                "session",
                "session",
                currentTrack,
                65000,
                20000,
                holdBackwardCorrection = false,
            ),
        )
        assertEquals(
            20000,
            ReceiverPlaybackPolicy.positionForIncomingAudio(
                "session",
                "session",
                "session",
                currentTrack,
                65000,
                20000,
                holdBackwardCorrection = true,
                senderPositionKnown = true,
            ),
        )
    }

    @Test
    fun confirmedEmptyPollRetiresRestoredIncomingAudioEvenWhenViewModelSessionWasReset() {
        val model = ReceiverViewModel(Repo(), { it() }, { it() })
        val staleAirPlay =
            PlaybackContext(MediaItem("incoming", "airplay", "Warriors"), false, false, "incoming")

        assertEquals(ReceiverChange.Restore(null), model.transition(null, staleAirPlay))
        assertNull(model.transition(null, staleAirPlay))
    }

    @Test
    fun confirmedEmptyPollDoesNotRetireLocalCatalogPlayback() {
        val model = ReceiverViewModel(Repo(), { it() }, { it() })
        val local = PlaybackContext(MediaItem("movie", "plex", "Movie"), false, true)

        assertNull(model.transition(null, local))
    }

    @Test
    fun transientPollFailureDoesNotRetireIncomingPlayback() {
        val work = mutableListOf<() -> Unit>()
        val ui = mutableListOf<() -> Unit>()
        val repo = Repo().apply { activeFailure = IllegalStateException("network") }
        val model = ReceiverViewModel(repo, { work.add(it) }, { ui.add(it) })
        var deliveries = 0
        model.observer = { deliveries++ }

        model.refresh()
        work.removeAt(0)()
        ui.removeAt(0)()

        assertEquals(0, deliveries)
    }

    @Test
    fun incomingSpotifySessionCanBeRetiredAgainAfterReceiverPlanReturns() {
        val model = ReceiverViewModel(Repo(), { it() }, { it() })
        val staleSpotify =
            PlaybackContext(MediaItem("incoming", "spotify", "Warriors"), false, false, "incoming")
        val plan = ReceiverPlan("incoming", "/stream", "audio/mpeg", staleSpotify.item)

        assertEquals(ReceiverChange.Restore(null), model.transition(null, staleSpotify))
        assertTrue(model.transition(plan, staleSpotify) is ReceiverChange.Begin)
    }

    @Test
    fun metadataAndBoundedReconnectPreserveInterruptedContext() {
        var now = 0L
        val model = ReceiverViewModel(Repo(), { it() }, { it() }, { now })
        val previous = PlaybackContext(MediaItem("movie", "local", "Movie"), false, true)
        var plan =
            ReceiverPlan(
                "music",
                "/v1/streams/music",
                "audio/mpeg",
                MediaItem("spotify-connect", "spotify", "First"),
                false,
            )
        assertTrue(model.transition(plan, previous) is ReceiverChange.Begin)
        plan = plan.copy(item = plan.item!!.copy(title = "Second"))
        assertTrue(model.transition(plan, previous) is ReceiverChange.Update)
        repeat(3) {
            model.playbackState("FAILED")
            assertNull(model.transition(plan, previous))
            now += 16000
            assertTrue(model.transition(plan, previous) is ReceiverChange.Reconnect)
        }
        model.playbackState("FAILED")
        now += 60000
        assertNull(model.transition(plan, previous))
        assertEquals(ReceiverChange.Restore(previous), model.transition(null, previous))
    }

    @Test
    fun completedFileWaitsForGatewayQueueInsteadOfDeletingNextItem() {
        val repo = Repo()
        val model = ReceiverViewModel(repo, { it() }, { it() })
        val previous = PlaybackContext(MediaItem("movie", "local", "Movie"), false, true)
        val plan =
            ReceiverPlan(
                "session",
                "/v1/streams/session",
                "video/mp4",
                live = false,
                seekable = true,
            )
        model.transition(plan, previous)
        var delivered: ReceiverPlan? = plan
        model.observer = { delivered = it }
        model.playbackState("ENDED")
        assertTrue(repo.stopped.isEmpty())
        assertEquals(plan, delivered)
        repo.plan = null
        model.refresh()
        assertNull(delivered)
        assertEquals(ReceiverChange.Restore(previous), model.transition(delivered, previous))
        assertNull(model.transition(plan, previous))
    }

    @Test
    fun selectMediaProviderUpdatesProviderAndTriggersRefresh() {
        val repo = Repo()
        val work = mutableListOf<() -> Unit>()
        val ui = mutableListOf<() -> Unit>()
        val model = ReceiverViewModel(repo, { work.add(it) }, { ui.add(it) })
        var doneCalled = false
        model.selectMediaProvider("spotify", { fail("selection failed") }) { doneCalled = true }
        assertEquals(1, work.size)
        work.removeAt(0)()
        assertEquals("spotify", repo.selectedProvider)
        assertEquals(1, ui.size)
        ui.removeAt(0)()
        assertTrue(doneCalled)
    }
}
