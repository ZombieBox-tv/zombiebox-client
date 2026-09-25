package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.QualityInventory
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.QualityOption
import io.github.diegog0477.zombiebox.client.features.playback.domain.repository.QualityRepository
import io.github.diegog0477.zombiebox.client.features.playback.presentation.viewmodel.QualityViewModel
import org.junit.Assert.*
import org.junit.Test

class QualityViewModelTest {
    private class FakeRepository : QualityRepository {
        var requestedSession = ""
        var selectedQualityId = ""
        var selectedPositionMs = 0

        override fun qualities(session: String): QualityInventory {
            requestedSession = session
            return QualityInventory(
                selectedId = "auto",
                options =
                    listOf(
                        QualityOption("auto", "Auto"),
                        QualityOption("1080p", "1080p", 1920, 1080),
                        QualityOption("720p", "720p", 1280, 720),
                    ),
            )
        }

        override fun select(session: String, qualityId: String, positionMs: Int): PlaybackPlan {
            requestedSession = session
            selectedQualityId = qualityId
            selectedPositionMs = positionMs
            return PlaybackPlan(
                sessionId = "$session-$qualityId",
                url = "/stream/$qualityId",
                mime = "video/mp4",
                mode = "DIRECT",
                timelineOffsetMs = 0,
                resumePositionMs = positionMs,
                live = false,
                seekable = true,
            )
        }
    }

    @Test
    fun inventoryLoadsForAttachedSession() {
        val repo = FakeRepository()
        var inventory: QualityInventory? = null
        val model = QualityViewModel(repo, { it() }, { it() })

        model.attach("session-1")
        model.inventory({ inventory = it }, { throw it })

        assertEquals("session-1", repo.requestedSession)
        assertNotNull(inventory)
        assertEquals("auto", inventory?.selectedId)
        assertEquals(3, inventory?.options?.size)
    }

    @Test
    fun staleInventoryRequestDiscardedOnSessionChange() {
        val repo = FakeRepository()
        val queue = mutableListOf<() -> Unit>()
        val model = QualityViewModel(repo, { queue.add(it) }, { it() })

        var received: QualityInventory? = null
        model.attach("session-1")
        model.inventory({ received = it }, { throw it })

        // Attach new session before background work finishes
        model.attach("session-2")
        queue.removeAt(0)()

        assertNull("Stale inventory should not be delivered", received)
    }

    @Test
    fun selectQualityCarriesPositionAndSwitchesSession() {
        val repo = FakeRepository()
        val model = QualityViewModel(repo, { it() }, { it() })

        model.attach("session-1")
        var plan: PlaybackPlan? = null
        model.select(
            qualityId = "720p",
            positionMs = 42000,
            done = { plan = it },
            failed = { throw it },
        )

        assertNotNull(plan)
        assertEquals("session-1-720p", plan?.sessionId)
        assertEquals(42000, plan?.resumePositionMs)
        assertEquals("720p", repo.selectedQualityId)
        assertEquals(42000, repo.selectedPositionMs)
    }

    @Test
    fun closedModelIgnoresRequests() {
        val repo = FakeRepository()
        val model = QualityViewModel(repo, { it() }, { it() })

        model.attach("session-1")
        model.close()

        var called = false
        model.inventory({ called = true }, { called = true })
        model.select("1080p", 0, { called = true }, { called = true })

        assertFalse(called)
    }
}
