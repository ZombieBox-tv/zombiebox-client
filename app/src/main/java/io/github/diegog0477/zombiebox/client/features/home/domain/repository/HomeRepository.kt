package io.github.diegog0477.zombiebox.client.features.home.domain.repository

import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeScope
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeSnapshot
import io.github.diegog0477.zombiebox.client.features.home.domain.model.YouTubeActivityPage

interface HomeRepository {
    fun load(scope: HomeScope): HomeSnapshot

    fun loadYouTubeActivityPage(cursor: String): YouTubeActivityPage =
        throw UnsupportedOperationException("YouTube activity paging is not supported")
}
