package io.github.diegog0477.zombiebox.client.features.playback.domain.model

data class QualityOption(val id: String, val label: String, val width: Int = 0, val height: Int = 0)

data class QualityInventory(val selectedId: String, val options: List<QualityOption>)
