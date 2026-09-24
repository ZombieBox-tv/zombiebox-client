package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.PlaybackContext
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.ReceiverChange
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.ReceiverPlan
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.repository.ReceiverRepository
import io.github.diegog0477.zombiebox.client.features.mirroring.presentation.viewmodel.ReceiverViewModel
import org.junit.Assert.*
import org.junit.Test

class ReceiverViewModelTest {
    private class Repo : ReceiverRepository {
        val stopped = mutableListOf<String>()

        var plan: ReceiverPlan? =
            ReceiverPlan("session", "/v1/streams/session", "application/vnd.apple.mpegurl")

        override fun active() = plan

        override fun stop(sessionId: String) {
            stopped.add(sessionId)
        }

        override fun mediaProvider() = selectedProvider

        var selectedProvider = ""

        override fun selectMediaProvider(provider: String) {
            selectedProvider = provider
        }

        override fun command(action: String) {}

        override fun handoffEnabled() = false

        override fun setHandoffEnabled(enabled: Boolean) {}

        override fun enabled() = true

        override fun setEnabled(enabled: Boolean) {}
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
