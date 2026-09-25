package io.github.diegog0477.zombiebox.client.features.playback.domain.repository

import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.QualityInventory

interface QualityRepository {
    fun qualities(session: String): QualityInventory

    fun select(session: String, qualityId: String, positionMs: Int): PlaybackPlan
}
