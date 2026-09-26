package io.github.diegog0477.zombiebox.client.features.home.domain.model

import io.github.diegog0477.zombiebox.client.core.model.MediaItem

data class HomeSnapshot(
    val hero: MediaItem? = null,
    val sections: List<MediaSection> = emptyList(),
    val modules: List<ServiceModule> = emptyList(),
    val youtubeNextOffset: Int = -1,
    val youtubeActivityFeed: Boolean = false,
    val youtubeNextCursor: String = "",
)

data class YouTubeActivityPage(val items: List<MediaItem>, val nextCursor: String)
