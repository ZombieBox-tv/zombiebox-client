package io.github.diegog0477.zombiebox.client.features.diagnostics.presentation.viewmodel

import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeAsset
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeClockUnavailable
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeResult
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.repository.ProbePlayback
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.repository.ProbeRepository

/** Sequential explicit diagnostics; cancel/late callbacks never publish stale evidence. */
class ProbeViewModel(
    private val repository: ProbeRepository,
    private val playback: ProbePlayback,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) {
    var state = ProbeState()
        private set

    var observer: (ProbeState) -> Unit = {}
    private var generation = 0
    private var closed = false

    private fun publish(value: ProbeState) {
        state = value
        observer(value)
    }

    fun start() {
        if (closed || state.running) return
        val run = ++generation
        publish(ProbeState(running = true))
        execute {
            val assets =
                try {
                    repository.assets()
                } catch (_: ProbeClockUnavailable) {
                    deliver {
                        if (!closed && generation == run)
                            publish(ProbeState(failed = true, clockUnavailable = true))
                    }
                    return@execute
                } catch (_: Exception) {
                    emptyList()
                }
            deliver {
                if (!closed && generation == run) {
                    if (assets.isEmpty()) publish(ProbeState(failed = true))
                    else next(run, assets, 0, emptyList())
                }
            }
        }
    }

    private fun next(run: Int, assets: List<ProbeAsset>, index: Int, results: List<ProbeResult>) {
        if (closed || run != generation) return
        if (index == assets.size) {
            publish(ProbeState(running = true, results = results))
            execute {
                var clockUnavailable = false
                val saved =
                    try {
                        repository.save(results)
                        true
                    } catch (_: ProbeClockUnavailable) {
                        clockUnavailable = true
                        false
                    } catch (_: Exception) {
                        false
                    }
                deliver {
                    if (!closed && run == generation)
                        publish(
                            ProbeState(
                                results = results,
                                saved = saved,
                                failed = !saved,
                                clockUnavailable = clockUnavailable,
                            )
                        )
                }
            }
            return
        }
        val prerequisite = assets[index].requires
        if (
            prerequisite.isNotEmpty() &&
                results.none { it.id == prerequisite && it.status == "PASS" && !it.stalled }
        ) {
            next(
                run,
                assets,
                index + 1,
                results +
                    ProbeResult(
                        assets[index].id,
                        "UNKNOWN",
                        detail = "prerequisite_unmet:$prerequisite",
                    ),
            )
            return
        }
        publish(ProbeState(running = true, current = assets[index].id, results = results))
        playback.start(assets[index]) { result ->
            deliver { next(run, assets, index + 1, results + result) }
        }
    }

    fun cancel() {
        generation++
        playback.cancel()
        if (!closed) publish(state.copy(running = false, current = ""))
    }

    fun close() {
        closed = true
        cancel()
        observer = {}
    }
}
