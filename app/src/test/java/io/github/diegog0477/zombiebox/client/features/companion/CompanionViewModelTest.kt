package io.github.diegog0477.zombiebox.client.features.companion

import io.github.diegog0477.zombiebox.client.core.presentation.ScreenTasks
import io.github.diegog0477.zombiebox.client.features.companion.domain.repository.CompanionRepository
import io.github.diegog0477.zombiebox.client.features.companion.presentation.viewmodel.CompanionViewModel
import io.github.diegog0477.zombiebox.shared.companion.*
import org.junit.Assert.*
import org.junit.Test

class CompanionViewModelTest {
    private class Fake : CompanionRepository {
        var polls = 0
        var requests = emptyList<PairingRequest>()
        var failNextDecision = false
        var decisions = 0

        override fun invite() = PairingInvitation("123456", byteArrayOf(), 120000)

        override fun inventory() = CompanionInventory(requests, emptyList())

        override fun decide(id: String, accept: Boolean, ignore24h: Boolean) {
            decisions++
            if (failNextDecision) {
                failNextDecision = false
                throw IllegalStateException("decision unavailable")
            }
            requests = requests.filterNot { it.id == id }
        }

        override fun revoke(id: String) {}

        override fun poll(active: Boolean, inputId: String): List<RemoteCommand> {
            polls++
            return listOf(RemoteCommand("a".repeat(32), "OK", "", 500))
        }

        override fun acknowledge(id: String, status: String) {}
    }

    @Test
    fun delayedUiDeliveryCannotExtendCommandLifetime() {
        var clock = 1000L
        val deliveries = mutableListOf<() -> Unit>()
        val repo = Fake()
        val model =
            CompanionViewModel(repo, ScreenTasks({ it() }, { deliveries.add(it) }), { clock })
        var lifetime = -1L
        model.poll(true, { lifetime = it.single().remainingMs }, {})
        model.poll(true, { fail("overlapping poll") }, {})
        assertEquals(1, repo.polls)
        clock += 600
        deliveries.removeAt(0)()
        assertEquals(0L, lifetime)
    }

    @Test
    fun closedTargetDoesNotReceiveLateRemoteInput() {
        val deliveries = mutableListOf<() -> Unit>()
        val model = CompanionViewModel(Fake(), ScreenTasks({ it() }, { deliveries.add(it) }), { 0 })
        model.poll(true, { fail("input after close") }, { fail("consent after close") })
        model.close()
        deliveries.removeAt(0)()
    }

    @Test
    fun changingGatewayDropsOldCommandsAndConsent() {
        var target = "first"
        val deliveries = mutableListOf<() -> Unit>()
        val model =
            CompanionViewModel(
                Fake(),
                ScreenTasks({ it() }, { deliveries.add(it) }),
                { 0 },
                { target },
            )
        model.poll(true, { fail("old target command") }, { fail("old target consent") })
        target = "second"
        deliveries.removeAt(0)()
    }

    @Test
    fun delayedInventoryCannotRepeatSuccessfulDecision() {
        val request = PairingRequest("request-1", "tv", "Phone", "123456", "PENDING")
        val deliveries = mutableListOf<() -> Unit>()
        val repo = Fake().apply { requests = listOf(request) }
        val model =
            CompanionViewModel(
                repo,
                ScreenTasks({ it() }, { deliveries.add(it) }),
                { 0 },
                { "gateway-device" },
            )
        var prompts = 0

        model.poll(true, {}, { prompts += it.size })
        model.decide(request.id, true, { fail("successful decision failed") })

        // The decision finishes before the already-fetched, stale inventory is delivered.
        deliveries.removeAt(1)()
        deliveries.removeAt(0)()
        assertEquals(0, prompts)

        model.poll(true, {}, { prompts += it.size })
        deliveries.removeAt(0)()
        assertEquals(0, prompts)
    }

    @Test
    fun failedDecisionCanBeRetriedExplicitly() {
        val request = PairingRequest("request-2", "tv", "Phone", "654321", "PENDING")
        val deliveries = mutableListOf<() -> Unit>()
        val repo =
            Fake().apply {
                requests = listOf(request)
                failNextDecision = true
            }
        val model = CompanionViewModel(repo, ScreenTasks({ it() }, { deliveries.add(it) }), { 0 })
        var failed = false
        var succeeded = false

        model.decide(request.id, false, { failed = true })
        model.decide(request.id, true, { fail("in-flight decision must not duplicate") })
        deliveries.removeAt(0)()
        assertTrue(failed)
        assertEquals(1, repo.decisions)

        model.decide(
            request.id,
            true,
            { fail("explicit retry failed") },
            done = { succeeded = true },
        )
        deliveries.removeAt(0)()
        assertTrue(succeeded)
        assertEquals(2, repo.decisions)
    }
}
