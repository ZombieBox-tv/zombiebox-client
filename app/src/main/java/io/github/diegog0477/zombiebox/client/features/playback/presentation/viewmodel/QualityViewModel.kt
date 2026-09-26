package io.github.diegog0477.zombiebox.client.features.playback.presentation.viewmodel

import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.QualityInventory
import io.github.diegog0477.zombiebox.client.features.playback.domain.repository.QualityRepository

class QualityViewModel(
    private val repository: QualityRepository,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) {
    private data class CachedInventory(val sessionId: String, val value: QualityInventory)

    @Volatile private var generation = 0
    @Volatile private var closed = false
    @Volatile private var session = ""
    @Volatile private var cached: CachedInventory? = null

    fun attach(id: String) {
        if (session != id) {
            generation++
            cached = null
        }
        session = id
    }

    fun cachedInventory(): QualityInventory? =
        cached?.takeIf { !closed && it.sessionId == session && session.isNotEmpty() }?.value

    fun inventory(done: (QualityInventory) -> Unit, failed: (Exception) -> Unit) =
        load(done, failed)

    fun load(done: (QualityInventory) -> Unit, failed: (Exception) -> Unit) {
        if (closed || session.isEmpty()) return
        val request = generation
        val id = session
        execute {
            try {
                val inventory = repository.qualities(id)
                deliver {
                    if (isCurrent(request, id)) {
                        cached = CachedInventory(id, inventory)
                        done(inventory)
                    }
                }
            } catch (error: Exception) {
                deliver { if (isCurrent(request, id)) failed(error) }
            }
        }
    }

    fun select(
        qualityId: String,
        positionMs: Int,
        done: (PlaybackPlan) -> Unit,
        failed: (Exception) -> Unit,
    ) {
        if (closed || session.isEmpty()) return
        val request = ++generation
        val id = session
        execute {
            try {
                val plan = repository.select(id, qualityId, positionMs)
                deliver {
                    if (isCurrent(request, id)) {
                        session = plan.sessionId
                        generation++
                        cached = null
                        done(plan)
                    }
                }
            } catch (error: Exception) {
                deliver { if (isCurrent(request, id)) failed(error) }
            }
        }
    }

    fun close() {
        closed = true
        generation++
        session = ""
        cached = null
    }

    private fun isCurrent(request: Int, id: String) =
        !closed && request == generation && id == session
}
