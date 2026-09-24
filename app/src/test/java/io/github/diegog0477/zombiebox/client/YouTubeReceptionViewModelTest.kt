package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogPage
import io.github.diegog0477.zombiebox.client.features.catalog.domain.repository.CatalogRepository
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.*
import io.github.diegog0477.zombiebox.client.features.playback.domain.repository.PlaybackRepository
import io.github.diegog0477.zombiebox.client.features.playback.presentation.viewmodel.*
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.domain.model.*
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.domain.repository.*
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.presentation.viewmodel.*
import org.junit.Assert.*
import org.junit.Test

class YouTubeReceptionViewModelTest {
    private class Fixture(deliver: ((() -> Unit) -> Unit) = { it() }) {
        var clock = 0L
        var opens = 0
        var expired = false
        var unavailable = false
        var leaseClosed = false
        var resolutionFails = false
        var resolutionMode = "DIRECT_PLAY"
        var remote = YouTubeReceiver("lease", "READY", "123456")
        val receipts = ArrayList<ReceiverFeedback>()
        val stopped = ArrayList<String>()
        val played = ArrayList<String>()
        val repository =
            object : PlaybackRepository {
                override fun start(itemId: String, mode: String, positionMs: Int?) =
                    plan(itemId, positionMs ?: 0)

                override fun receive(
                    itemId: String,
                    receiverId: String,
                    positionMs: Int?,
                ): PlaybackPlan {
                    assertEquals("lease", receiverId)
                    if (resolutionFails) error("upstream unavailable")
                    return plan(itemId, positionMs ?: 0).copy(mode = resolutionMode)
                }

                override fun progress(sessionId: String, progress: PlaybackProgress) {}

                override fun stop(sessionId: String) {
                    stopped.add(sessionId)
                }
            }
        val session =
            PlaybackSessionViewModel(
                repository,
                object : CatalogRepository {
                    override fun page(
                        provider: String,
                        query: String,
                        offset: Int,
                        parent: String,
                    ) = CatalogPage(emptyList(), 0)
                },
                { stopped.add(it) },
                { it() },
                { it() },
            )
        val receiver =
            YouTubeReceiverViewModel(
                object : YouTubeReceiverRepository {
                    override fun open(): YouTubeReceiver {
                        opens++
                        if (unavailable) error("network")
                        return remote
                    }

                    override fun poll(id: String): YouTubeReceiver {
                        if (expired) throw ReceiverExpired()
                        return remote
                    }

                    override fun feedback(id: String, value: ReceiverFeedback) {
                        receipts.add(value)
                    }

                    override fun close(id: String) {
                        leaseClosed = true
                    }
                },
                { it() },
                { it() },
            )
        val reception =
            YouTubeReceptionViewModel(
                receiver,
                PlaybackViewModel(repository, { it() }, deliver),
                session,
                object : YouTubePlaybackControl {
                    override fun play(plan: PlaybackPlan, item: MediaItem) {
                        played.add(item.id)
                    }

                    override fun pause() {
                        session.mediaState("PAUSED", 0, 0)
                    }

                    override fun resume() = true

                    override fun seek(positionMs: Int) {}

                    override fun volume(value: Int, muted: Boolean, done: (Boolean) -> Unit) {
                        done(true)
                    }
                },
                "YouTube",
                { clock },
            )

        init {
            session.observer = { state ->
                reception.playbackState(
                    state.progress.state,
                    state.progress.positionMs,
                    state.progress.durationMs,
                )
            }
        }

        fun command(id: String, action: String, item: String = "video") {
            remote = remote.copy(command = YouTubeCommand(id, action, item))
            reception.tick()
        }

        companion object {
            fun plan(id: String, position: Int = 0) =
                PlaybackPlan(id, "http://local/$id", "video/mp4", "DIRECT_PLAY", position)
        }
    }

    @Test
    fun screenObserversCanDetachAndReattachWithoutClosingTheReceiver() {
        val f = Fixture()
        f.reception.enable()
        f.reception.observer = null
        f.command("play", "play")
        assertEquals(listOf("video"), f.played)
        assertTrue(f.session.state.incoming)
        f.reception.playbackState("BUFFERING", 0, 0)
        f.reception.playbackState("PLAYING", 300, 9000)
        f.reception.tick()
        assertTrue(f.receipts.any { it.commandId == "play" && it.success })
        var restored: YouTubeReception? = null
        f.reception.observer = { restored = it }
        f.reception.tick()
        assertEquals("lease", restored?.receiver?.id)
        assertFalse(f.leaseClosed)
    }

    @Test
    fun revokedLeaseStopsOwnedPlaybackButDoesNotReclaimOwnership() {
        val f = Fixture()
        f.reception.enable()
        f.command("play", "play")
        f.expired = true
        f.reception.tick()
        assertFalse(f.reception.state.enabled)
        assertNull(f.session.state.plan)
        f.clock = 999999L
        repeat(10) { f.reception.tick() }
        assertEquals(1, f.opens)
    }

    @Test
    fun disableDiscardsResolutionDeliveredAfterConsentEnded() {
        val callbacks = ArrayList<() -> Unit>()
        val f = Fixture { callbacks.add(it) }
        f.reception.enable()
        f.command("play", "play")
        f.reception.disable()
        callbacks.forEach { it() }
        assertTrue(f.played.isEmpty())
        assertTrue(f.stopped.contains("video"))
        assertNull(f.session.state.plan)
        assertNull(f.reception.state.playbackIssue)
    }

    @Test
    fun standbyInvalidatesOldCommandWithoutRevokingUniversalListener() {
        val callbacks = ArrayList<() -> Unit>()
        val f = Fixture { callbacks.add(it) }
        f.reception.enable()
        f.command("play", "play")
        f.reception.standby()
        callbacks.forEach { it() }
        assertTrue(f.played.isEmpty())
        assertTrue(f.reception.state.enabled)
        assertFalse(f.leaseClosed)
        assertNull(f.reception.state.playbackIssue)
    }

    @Test
    fun stopRestoresInterruptedLocalQueueWithoutAScreen() {
        val f = Fixture()
        val local = MediaItem("local", "plex", "Local")
        val next = MediaItem("next", "plex", "Next")
        f.session.adopt(Fixture.plan("local"), local, listOf(local, next))
        f.session.mediaState("PAUSED", 3000, 9000)
        f.reception.enable()
        f.command("play", "play")
        f.command("stop", "stop")
        assertEquals("local", f.session.state.item?.id)
        assertEquals("PAUSED", f.session.state.progress.state)
        assertEquals(listOf(next), f.session.state.queue)
        assertTrue(f.reception.state.enabled)
        f.reception.tick()
        assertTrue(f.receipts.any { it.commandId == "stop" && it.success })
    }

    @Test
    fun focusOrPlatformFailureProducesNegativeAcknowledgement() {
        val f = Fixture()
        f.reception.enable()
        f.command("play", "play")
        f.session.mediaState("FAILED", 0, 0)
        assertEquals(YouTubePlaybackIssue.UNAVAILABLE, f.reception.state.playbackIssue)
        assertEquals(1L, f.reception.state.playbackIssueRevision)
        f.reception.tick()
        assertTrue(f.receipts.any { it.commandId == "play" && !it.success })
    }

    @Test
    fun failedResolutionReportsVisibleIssueAndNegativeFeedback() {
        val f = Fixture()
        f.resolutionFails = true
        f.reception.enable()
        f.command("first", "play")
        assertEquals(YouTubePlaybackIssue.UNAVAILABLE, f.reception.state.playbackIssue)
        assertEquals(1L, f.reception.state.playbackIssueRevision)
        assertTrue(f.played.isEmpty())
        f.reception.tick()
        assertTrue(f.receipts.any { it.commandId == "first" && !it.success })

        f.resolutionFails = false
        f.command("second", "play")
        assertNull(f.reception.state.playbackIssue)
        assertEquals(listOf("video"), f.played)
    }

    @Test
    fun externalPlanReportsItsOwnReasonWithoutStartingPlayer() {
        val f = Fixture()
        f.resolutionMode = "EXTERNAL_PLAYER"
        f.reception.enable()
        f.command("external", "play")
        assertEquals(YouTubePlaybackIssue.EXTERNAL_PLAYER_REQUIRED, f.reception.state.playbackIssue)
        assertEquals(listOf("video"), f.stopped)
        assertTrue(f.played.isEmpty())
        f.reception.tick()
        assertTrue(f.receipts.any { it.commandId == "external" && !it.success })
    }

    @Test
    fun failedOpenRetriesAreBoundedAndManualEnableCanRetry() {
        val f = Fixture()
        f.unavailable = true
        f.reception.enable()
        repeat(10) {
            f.clock += 60000
            f.reception.tick()
        }
        assertEquals(3, f.opens)
        assertTrue(f.reception.state.failed)
        f.unavailable = false
        f.reception.enable()
        assertNotNull(f.reception.state.receiver)
        assertEquals(4, f.opens)
    }
}
