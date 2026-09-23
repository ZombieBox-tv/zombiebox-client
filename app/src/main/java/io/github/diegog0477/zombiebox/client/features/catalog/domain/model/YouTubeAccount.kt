package io.github.diegog0477.zombiebox.client.features.catalog.domain.model

import io.github.diegog0477.zombiebox.client.core.model.MediaItem

data class YouTubeAccountPrompt(
    val code: String,
    val verificationUrl: String,
    val expiresAt: Long,
    val intervalSeconds: Int,
)

data class YouTubeAccountStatus(
    val configured: Boolean,
    val connected: Boolean,
    val state: String,
    val prompt: YouTubeAccountPrompt? = null,
)

data class YouTubeAccountPage(val items: List<MediaItem>, val nextPageToken: String)
