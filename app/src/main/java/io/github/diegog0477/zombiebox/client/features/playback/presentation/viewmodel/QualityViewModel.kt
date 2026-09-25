package io.github.diegog0477.zombiebox.client.features.playback.presentation.viewmodel

import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.QualityInventory
import io.github.diegog0477.zombiebox.client.features.playback.domain.repository.QualityRepository

class QualityViewModel(
    private val repository: QualityRepository,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) {
    @Volatile private var generation = 0
    @Volatile private var closed = false
    private var session = ""

    fun attach(id: String) {
        generation++
        session = id
    }

    fun inventory(done: (QualityInventory) -> Unit, failed: (Exception) -> Unit) =
        load(done, failed)

    fun load(done: (QualityInventory) -> Unit, failed: (Exception) -> Unit) {
        if (closed || session.isEmpty()) return
        val request = generation
        val id = session
        execute {
            try {
                val inventory = repository.qualities(id)
                deliver { if (!closed && request == generation) done(inventory) }
            } catch (error: Exception) {
                deliver { if (!closed && request == generation) failed(error) }
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
                    if (!closed && request == generation) {
                        session = plan.sessionId
                        done(plan)
                    }
                }
            } catch (error: Exception) {
                deliver { if (!closed && request == generation) failed(error) }
            }
        }
    }

    fun close() {
        attach("")
        closed = true
    }
}
