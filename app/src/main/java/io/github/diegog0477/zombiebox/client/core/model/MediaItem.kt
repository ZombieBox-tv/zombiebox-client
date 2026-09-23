package io.github.diegog0477.zombiebox.client.core.model

data class MediaItem(
    val id: String,
    val provider: String,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val positionMs: Int = 0,
    val programmes: List<Programme> = emptyList(),
    val imageUrl: String = "",
    val durationMs: Int = 0,
    val browseId: String = "",
    val playable: Boolean = true,
    val kind: String = "",
    val guideState: String = "",
    val favorite: Boolean = false,
)
