package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogPage
import io.github.diegog0477.zombiebox.client.features.catalog.domain.repository.CatalogRepository
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.ReceiverPlan
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.repository.ReceiverRepository
import io.github.diegog0477.zombiebox.client.features.mirroring.presentation.viewmodel.BackgroundReceptionViewModel
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.*
import io.github.diegog0477.zombiebox.client.features.playback.domain.repository.PlaybackRepository
import io.github.diegog0477.zombiebox.client.features.playback.presentation.viewmodel.PlaybackSessionViewModel
import org.junit.Assert.*
import org.junit.Test

class BackgroundReceptionViewModelTest {
    private class Fixture {
        var clock = 0L
        var unavailable = false
        var remote: ReceiverPlan? = null
        var standby = 0
        var claim = ""
        var rearms = 0
        val work = ArrayList<() -> Unit>()
        val played = ArrayList<String>()
        val stopped = ArrayList<String>()
        val session =
            PlaybackSessionViewModel(
                object : PlaybackRepository {
                    override fun start(itemId: String, mode: String, positionMs: Int?) =
                        PlaybackPlan(
                            "restored-$itemId",
                            "http://gateway/local",
                            "video/mp4",
                            mode,
                            positionMs ?: 0,
                        )

                    override fun progress(sessionId: String, progress: PlaybackProgress) {}

                    override fun stop(sessionId: String) {
                        stopped.add(sessionId)
                    }
                },
                object : CatalogRepository {
                    override fun page(
                        provider: String,
                        query: String,
                        offset: Int,
                        parent: String,
                    ) = CatalogPage(emptyList(), -1)
                },
                { stopped.add(it) },
                { it() },
                { it() },
            )
        val model =
            BackgroundReceptionViewModel(
                object : ReceiverRepository {
                    override fun active(): ReceiverPlan? {
                        if (unavailable) error("transport unavailable")
                        return remote
                    }

                    override fun mediaProvider() = claim

                    override fun rearmMediaProvider(provider: String) {
                        assertEquals("auto", provider)
                        rearms++
                        claim = provider
                    }

                    override fun selectMediaProvider(provider: String) =
                        error("must not reclaim authority")

                    override fun command(action: String) = error("unexpected command")

                    override fun stop(sessionId: String) = error("session owns revocation")

                    override fun handoffEnabled() = error("unexpected settings read")

                    override fun setHandoffEnabled(enabled: Boolean) =
                        error("must not change consent")

                    override fun enabled() = error("unexpected settings read")

                    override fun setEnabled(enabled: Boolean) = error("must not change consent")
                },
                session,
                { work.add(it) },
                { it() },
                {
                    PlaybackPlan(
                        it.sessionId,
                        "http://gateway" + it.path,
                        it.mime,
                        it.mode,
                        0,
                        live = it.live,
                        seekable = it.seekable,
                    )
                },
                { plan, _ -> played.add(plan.sessionId) },
                { item, _ -> item?.let(session::metadata) },
                { standby++ },
                "Cast",
                { clock },
            )

        init {
            session.play = { plan, _ -> played.add(plan.sessionId) }
        }

        fun incoming(id: String = "incoming", provider: String = "spotify", live: Boolean = true) =
            ReceiverPlan(
                id,
                "/v1/streams/$id",
                "audio/aac",
                MediaItem(id, provider, id, kind = "audio"),
                fullscreen = false,
                live = live,
            )

        fun poll() {
            model.tick()
            if (work.isNotEmpty()) work.removeAt(0)()
        }

        fun arm() {
            claim = "auto"
            model.configure(mediaProvider = "auto")
            model.foreground(false)
        }
    }

    @Test
    fun idleReceptionRequiresArmingAndSurvivesWithoutScreenObservers() {
        val f = Fixture()
        f.model.foreground(false)
        f.remote = f.incoming()
        f.model.tick()
        assertTrue(f.work.isEmpty())
        f.model.configure(castEnabled = true)
        f.model.observer = null
        f.poll()
        assertEquals(listOf("incoming"), f.played)
        assertTrue(f.session.state.incoming)
        assertEquals(1, f.standby)
    }

    @Test
    fun lateBackgroundResponseCannotReplaceForegroundOrNewPlayback() {
        val f = Fixture()
        f.arm()
        f.remote = f.incoming()
        f.model.tick()
        f.model.tick()
        assertEquals(1, f.work.size)
        f.model.foreground(true)
        f.work.removeAt(0)()
        assertTrue(f.played.isEmpty())
        f.model.foreground(false)
        f.model.tick()
        f.session.adopt(
            PlaybackPlan("manual", "url", "video/mp4", "DIRECT_PLAY", 0),
            MediaItem("manual", "local", "Manual"),
        )
        f.work.removeAt(0)()
        assertEquals("manual", f.session.state.plan?.sessionId)
        assertTrue(f.played.isEmpty())
    }

    @Test
    fun networkFailurePreservesConfirmedSessionAndDoesNotReclaimReceiver() {
        val f = Fixture()
        f.arm()
        f.remote = f.incoming()
        f.poll()
        f.unavailable = true
        f.poll()
        assertTrue(f.model.state.unavailable)
        assertEquals("incoming", f.session.state.plan?.sessionId)
        assertTrue(f.stopped.isEmpty())
        f.unavailable = false
        f.remote = null
        f.poll()
        assertNull(f.session.state.plan)
        assertFalse(f.model.state.unavailable)
        assertTrue(f.model.state.enabled)
    }

    @Test
    fun lostClaimRearmsConfiguredReceiverWithoutTakingAnotherOwner() {
        val f = Fixture()
        f.arm()
        f.claim = ""
        f.poll()
        assertEquals(1, f.rearms)
        assertEquals("auto", f.claim)
        f.claim = "airplay"
        f.poll()
        assertEquals(1, f.rearms)
    }

    @Test
    fun receiverReplacementsRestoreOriginalPausedQueueOnce() {
        val f = Fixture()
        val local = MediaItem("local", "local", "Local")
        val next = MediaItem("next", "local", "Next")
        f.session.adopt(
            PlaybackPlan("local-session", "url", "video/mp4", "DIRECT_PLAY", 0),
            local,
            listOf(local, next),
            paused = true,
        )
        f.session.mediaState("PAUSED", 1234, 9000)
        f.arm()
        f.remote = f.incoming()
        f.poll()
        f.remote = f.incoming("replacement", "airplay")
        f.poll()
        f.remote = null
        f.poll()
        assertEquals("restored-local", f.session.state.plan?.sessionId)
        assertEquals("PAUSED", f.session.state.progress.state)
        assertEquals(1234, f.session.state.progress.positionMs)
        assertEquals(listOf(next), f.session.state.queue)
        assertEquals(listOf("incoming", "replacement", "restored-local"), f.played)
        f.poll()
        assertEquals(3, f.played.size)
    }

    @Test
    fun retriesAreBoundedUntilSixtySecondsOfHealthyPlayback() {
        val f = Fixture()
        f.arm()
        f.remote = f.incoming()
        f.poll()
        repeat(6) {
            f.session.mediaState("FAILED", 0, 0)
            f.clock += 20_000
            f.poll()
        }
        assertEquals(4, f.played.size)
        f.session.mediaState("PLAYING", 0, 0)
        f.poll()
        f.clock += 60_000
        f.poll()
        f.session.mediaState("FAILED", 0, 0)
        f.poll()
        assertEquals(5, f.played.size)
    }

    @Test
    fun completedFilesWaitForQueueAdvanceAndNeverReplayTheSameItem() {
        val f = Fixture()
        f.arm()
        f.remote = f.incoming(live = false)
        f.poll()
        f.session.mediaState("ENDED", 9000, 9000)
        repeat(4) {
            f.clock += 20_000
            f.poll()
        }
        assertEquals(1, f.played.size)
        f.remote = f.incoming("next", live = false)
        f.poll()
        assertEquals(listOf("incoming", "next"), f.played)
    }

    @Test
    fun profileResetAndCloseFencePendingResults() {
        val f = Fixture()
        f.arm()
        f.remote = f.incoming()
        f.model.tick()
        f.model.reset()
        f.work.removeAt(0)()
        assertFalse(f.model.state.enabled)
        assertTrue(f.played.isEmpty())
        f.model.configure(castEnabled = true)
        f.model.tick()
        f.model.close()
        f.work.removeAt(0)()
        assertTrue(f.played.isEmpty())
    }

    @Test
    fun externalFallbackCannotLaunchAnActivityInBackground() {
        val f = Fixture()
        f.arm()
        f.remote = f.incoming().copy(mode = "EXTERNAL_PLAYER")
        f.poll()
        assertTrue(f.model.state.unavailable)
        assertTrue(f.played.isEmpty())
        assertNull(f.session.state.plan)
    }
}
