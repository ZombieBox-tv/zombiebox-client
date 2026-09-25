package io.github.diegog0477.zombiebox.client.features.youtube.domain.model

import io.github.diegog0477.zombiebox.client.core.model.MediaItem

data class RelatedPage(
    val videoId: String,
    val currentVideo: MediaItem?,
    val items: List<MediaItem>,
    val nextCursor: String,
)
