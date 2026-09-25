package io.github.diegog0477.zombiebox.client.features.youtube.domain.repository

import io.github.diegog0477.zombiebox.client.features.youtube.domain.model.RelatedPage

interface YouTubeRelatedRepository {
    fun page(videoId: String, cursor: String): RelatedPage
}
