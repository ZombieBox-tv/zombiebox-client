package io.github.diegog0477.zombiebox.client.features.companion.presentation.viewmodel

import io.github.diegog0477.zombiebox.client.core.presentation.ScreenTasks
import io.github.diegog0477.zombiebox.client.features.companion.domain.repository.CompanionRepository
import io.github.diegog0477.zombiebox.shared.companion.*

class CompanionViewModel(
    private val repository: CompanionRepository,
    private val tasks: ScreenTasks,
    private val now: () -> Long,
    private val scope: () -> String = { "" },
) {
    private data class DecisionKey(val scope: String, val requestId: String)

    private var polling = false
    private var closed = false
    private var pollGeneration = 0L
    private val decisionsInFlight = mutableSetOf<DecisionKey>()
    private val decisionsAwaitingRefresh = mutableMapOf<DecisionKey, Long>()

    fun invite(done: (PairingInvitation) -> Unit, failed: (Exception) -> Unit) =
        tasks.run({ repository.invite() }, done, failed)

    fun inventory(done: (CompanionInventory) -> Unit, failed: (Exception) -> Unit) =
        tasks.run({ repository.inventory() }, done, failed)

    fun decide(
        id: String,
        accept: Boolean,
        failed: (Exception) -> Unit,
        ignore24h: Boolean = false,
        done: () -> Unit = {},
    ) {
        if (closed) return
        val target = scope()
        val key = DecisionKey(target, id)
        if (!decisionsInFlight.add(key)) return
        tasks.run(
            { repository.decide(id, accept, ignore24h) },
            {
                decisionsInFlight.remove(key)
                decisionsAwaitingRefresh[key] = pollGeneration + 1
                done()
            },
            { error ->
                decisionsInFlight.remove(key)
                failed(error)
            },
        )
    }

    fun revoke(id: String, done: () -> Unit, failed: (Exception) -> Unit) =
        tasks.run({ repository.revoke(id) }, { done() }, failed)

    fun poll(
        active: Boolean,
        commands: (List<RemoteCommand>) -> Unit,
        pending: (List<PairingRequest>) -> Unit,
        inputId: String = "",
    ) {
        if (polling) return
        polling = true
        val started = now()
        val target = scope()
        val generation = ++pollGeneration
        tasks.run(
            { Pair(repository.poll(active, inputId), repository.inventory()) },
            { result ->
                polling = false
                if (scope() != target) return@run
                val elapsed = now() - started
                commands(
                    result.first.map {
                        it.copy(remainingMs = (it.remainingMs - elapsed).coerceAtLeast(0))
                    }
                )
                val requestIds = result.second.requests.mapTo(mutableSetOf()) { it.id }
                val iterator = decisionsAwaitingRefresh.iterator()
                while (iterator.hasNext()) {
                    val (key, refreshGeneration) = iterator.next()
                    if (
                        key.scope == target &&
                            generation >= refreshGeneration &&
                            key.requestId !in requestIds
                    )
                        iterator.remove()
                }
                pending(
                    result.second.requests.filter { request ->
                        val key = DecisionKey(target, request.id)
                        key !in decisionsInFlight && key !in decisionsAwaitingRefresh
                    }
                )
            },
            { polling = false },
        )
    }

    fun acknowledge(id: String, status: String) =
        tasks.run({ repository.acknowledge(id, status) }, {}, {})

    fun close() {
        closed = true
        tasks.close()
    }
}
