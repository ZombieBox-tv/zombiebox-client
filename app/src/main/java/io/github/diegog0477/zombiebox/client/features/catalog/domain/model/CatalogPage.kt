package io.github.diegog0477.zombiebox.client.features.catalog.domain.model

import io.github.diegog0477.zombiebox.client.core.model.MediaItem

data class CatalogPage(val items: List<MediaItem>, val nextOffset: Int, val title: String = "")

data class CatalogLocation(
    val provider: String,
    val parent: String = "",
    val query: String = "",
    val offset: Int = 0,
    val favoritesOnly: Boolean = false,
)

data class CatalogScreen(
    val location: CatalogLocation,
    val page: CatalogPage,
    val viewport: CatalogViewport = CatalogViewport(),
)

/** Only semantic IDs and scroll geometry survive a screen transition; never Views/bitmaps. */
data class CatalogViewport(
    val selectedId: String = "",
    val firstVisibleId: String = "",
    val firstTop: Int = 0,
    val selectedTop: Int? = null,
)

data class CatalogBookmark(val location: CatalogLocation, val viewport: CatalogViewport)

/** The currently visible overlay; its underlying catalog path is saved separately. */
data class CatalogOverlay(
    val kind: String = "",
    val draft: String = "",
    val guideTime: Long = 0,
    val selectedChannel: String = "",
)

/** Playback returns to an owned catalog surface, never a retained window or media DTO. */
data class CatalogPlaybackReturn(
    val overlay: CatalogOverlay = CatalogOverlay(),
    val detailId: String = "",
)
