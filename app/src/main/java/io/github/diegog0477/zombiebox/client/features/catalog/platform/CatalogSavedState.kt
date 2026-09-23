package io.github.diegog0477.zombiebox.client.features.catalog.platform

import android.os.Bundle
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.*

/** Small semantic bookmarks only; never bitmaps, DTOs, tokens or full pages in Binder. */
object CatalogSavedState {
    fun writePlaybackReturn(target: Bundle, value: CatalogPlaybackReturn?) {
        if (value == null) return
        target.putBundle(
            "playbackReturn",
            Bundle().apply {
                writeOverlay(this, value.overlay)
                putString("detail", value.detailId.take(200))
            },
        )
    }

    fun readPlaybackReturn(source: Bundle?): CatalogPlaybackReturn? =
        source?.getBundle("playbackReturn")?.let {
            CatalogPlaybackReturn(readOverlay(it), (it.getString("detail") ?: "").take(200))
        }

    fun writeSearch(target: Bundle, value: SearchBookmark?) {
        if (value == null) return
        target.putBundle(
            "searchReturn",
            Bundle().apply {
                putString("query", value.query.take(100))
                putBoolean("focused", value.resultsFocused)
                putString("selected", value.viewport.selectedId.take(200))
                putString("first", value.viewport.firstVisibleId.take(200))
                putInt("top", value.viewport.firstTop)
                value.viewport.selectedTop?.let { putInt("selectedTop", it) }
            },
        )
    }

    fun readSearch(source: Bundle?): SearchBookmark? =
        source?.getBundle("searchReturn")?.let {
            SearchBookmark(
                (it.getString("query") ?: "").take(100),
                CatalogViewport(
                    (it.getString("selected") ?: "").take(200),
                    (it.getString("first") ?: "").take(200),
                    it.getInt("top"),
                    if (it.containsKey("selectedTop")) it.getInt("selectedTop") else null,
                ),
                it.getBoolean("focused"),
            )
        }

    fun writeOverlay(target: Bundle, value: CatalogOverlay) {
        target.putString("overlayKind", value.kind.take(24))
        target.putString("overlayDraft", value.draft.take(256))
        target.putLong("overlayTime", value.guideTime)
        target.putString("overlayChannel", value.selectedChannel.take(200))
    }

    fun readOverlay(source: Bundle?): CatalogOverlay =
        CatalogOverlay(
            source?.getString("overlayKind") ?: "",
            (source?.getString("overlayDraft") ?: "").take(256),
            source?.getLong("overlayTime") ?: 0,
            (source?.getString("overlayChannel") ?: "").take(200),
        )

    fun write(target: Bundle, path: List<CatalogBookmark>) {
        val entries = path.takeLast(25)
        target.putInt("catalogCount", entries.size)
        entries.forEachIndexed { index, bookmark ->
            target.putBundle(
                "catalog:$index",
                Bundle().apply {
                    putString("provider", bookmark.location.provider.take(40))
                    putString("parent", bookmark.location.parent.take(200))
                    putString("query", bookmark.location.query.take(256))
                    putInt("offset", bookmark.location.offset)
                    putBoolean("favoritesOnly", bookmark.location.favoritesOnly)
                    putString("selected", bookmark.viewport.selectedId.take(200))
                    putString("first", bookmark.viewport.firstVisibleId.take(200))
                    putInt("top", bookmark.viewport.firstTop)
                    bookmark.viewport.selectedTop?.let { putInt("selectedTop", it) }
                },
            )
        }
    }

    fun read(source: Bundle?): List<CatalogBookmark> =
        (0 until (source?.getInt("catalogCount") ?: 0).coerceIn(0, 25)).mapNotNull { index ->
            source?.getBundle("catalog:$index")?.let {
                CatalogBookmark(
                    CatalogLocation(
                        it.getString("provider") ?: "",
                        it.getString("parent") ?: "",
                        it.getString("query") ?: "",
                        it.getInt("offset").coerceAtLeast(0),
                        it.getBoolean("favoritesOnly"),
                    ),
                    CatalogViewport(
                        it.getString("selected") ?: "",
                        it.getString("first") ?: "",
                        it.getInt("top"),
                        if (it.containsKey("selectedTop")) it.getInt("selectedTop") else null,
                    ),
                )
            }
        }
}
