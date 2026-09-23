package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.presentation.ScreenTasks
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.*
import io.github.diegog0477.zombiebox.client.features.catalog.domain.repository.CatalogRepository
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.viewmodel.CatalogViewModel
import org.junit.Assert.*
import org.junit.Test

class CatalogViewModelTest {
    private val folder =
        MediaItem(
            id = "folder",
            provider = "plex",
            title = "Library",
            browseId = "opaque",
            playable = false,
        )

    private class Repository(val folder: MediaItem) : CatalogRepository {
        val requests = mutableListOf<CatalogLocation>()
        var fail = false

        override fun favoritePage(query: String, offset: Int): CatalogPage {
            requests.add(
                CatalogLocation("iptv", query = query, offset = offset, favoritesOnly = true)
            )
            return CatalogPage(listOf(folder.copy(provider = "iptv")), -1, "Favorites")
        }

        override fun iptvPage(
            query: String,
            offset: Int,
            favoritesOnly: Boolean,
            category: String,
        ): CatalogPage {
            if (favoritesOnly) return favoritePage(query, offset)
            if (category.isEmpty()) return page("iptv", query, offset)
            requests.add(
                CatalogLocation("iptv", query = query, offset = offset, category = category)
            )
            return CatalogPage(listOf(folder.copy(provider = "iptv", category = category)), -1)
        }

        override fun page(
            provider: String,
            query: String,
            offset: Int,
            parent: String,
        ): CatalogPage {
            requests.add(CatalogLocation(provider, parent, query, offset))
            if (fail) throw IllegalStateException("unavailable")
            return CatalogPage(listOf(folder), if (offset == 0) 80 else 100, "Library")
        }
    }

    @Test
    fun iptvFavoritesKeepTheirFilterAcrossSearchAndBack() {
        val repository = Repository(folder)
        val model = CatalogViewModel(repository, ScreenTasks({ it() }, { it() }))
        model.open("iptv", "", {}, { throw it })
        model.openIptvFavorites({}, { throw it })
        assertTrue(model.screen!!.location.favoritesOnly)
        model.search("news", {}, { throw it })
        assertEquals("news", repository.requests.last().query)
        assertTrue(repository.requests.last().favoritesOnly)
        assertTrue(model.back {})
        assertTrue(model.screen!!.location.favoritesOnly)
        assertTrue(model.back {})
        assertFalse(model.screen!!.location.favoritesOnly)
    }

    @Test
    fun iptvCategorySurvivesSearchAndBack() {
        val repository = Repository(folder)
        val model = CatalogViewModel(repository, ScreenTasks({ it() }, { it() }))
        model.open("iptv", "", {}, { throw it })
        model.openIptvCategory("News", {}, { throw it })
        assertEquals("News", model.screen!!.location.category)
        model.search("local", {}, { throw it })
        assertEquals("News", repository.requests.last().category)
        assertEquals("local", repository.requests.last().query)
        assertTrue(model.back {})
        assertEquals("News", model.screen!!.location.category)
        assertTrue(model.back {})
        assertEquals("", model.screen!!.location.category)
    }

    @Test
    fun recreationReloadsSemanticPathAndBackViewport() {
        val repository = Repository(folder)
        val first = CatalogViewModel(repository, ScreenTasks({ it() }, { it() }))
        first.open("plex", "", {}, { throw it })
        first.rememberViewport(CatalogViewport("folder", "folder", -22))
        first.enter(folder, {}, { throw it })
        first.next({}, { throw it })
        val bookmarks = first.bookmarks()
        first.close()
        val restored = CatalogViewModel(repository, ScreenTasks({ it() }, { it() }))
        restored.restore(bookmarks, {}, { throw it })
        assertEquals(80, restored.screen!!.location.offset)
        restored.back({}, { throw it })
        assertEquals("opaque", restored.screen!!.location.parent)
        restored.back({}, { throw it })
        assertEquals(-22, restored.screen!!.viewport.firstTop)
        assertFalse(restored.canBack)
    }

    @Test
    fun nestedBackRestoresStableFocusAndScrollWithoutFetchingAgain() {
        val repository = Repository(folder)
        val model = CatalogViewModel(repository, ScreenTasks({ it() }, { it() }))
        model.open("plex", "", {}, { throw it })
        val viewport = CatalogViewport("folder", "folder", -12, -12)
        model.rememberViewport(viewport)
        model.enter(folder, {}, { throw it })
        assertEquals("opaque", model.screen!!.location.parent)
        assertTrue(model.back {})
        assertEquals(viewport, model.screen!!.viewport)
        assertEquals(2, repository.requests.size)
        assertFalse(model.canBack)
    }

    @Test
    fun nextUsesReturnedOffsetAndFailedLoadPreservesPreviousScreen() {
        val repository = Repository(folder)
        val model = CatalogViewModel(repository, ScreenTasks({ it() }, { it() }))
        model.open("plex", "", {}, { throw it })
        model.next({}, { throw it })
        assertEquals(80, model.screen!!.location.offset)
        val before = model.screen
        repository.fail = true
        var failed = false
        model.next({ fail("unexpected success") }, { failed = true })
        assertTrue(failed)
        assertEquals(before, model.screen)
        model.back {}
        assertEquals(0, model.screen!!.location.offset)
    }

    @Test
    fun cancelledAndSupersededRequestsCannotOverwriteNavigation() {
        val queue = mutableListOf<() -> Unit>()
        val model = CatalogViewModel(Repository(folder), ScreenTasks({ queue.add(it) }, { it() }))
        model.open("plex", "", { fail("stale open") }, { throw it })
        model.open("jellyfin", "", {}, { throw it })
        queue.removeAt(1)()
        queue.removeAt(0)()
        assertEquals("jellyfin", model.screen!!.location.provider)
        model.enter(folder, { fail("cancelled request") }, { throw it })
        model.cancelPending()
        queue.removeAt(0)()
        assertEquals("", model.screen!!.location.parent)
        assertFalse(model.canBack)
    }

    @Test
    fun searchKeepsProviderAndParentScopeAndResetsPage() {
        val repository = Repository(folder)
        val model = CatalogViewModel(repository, ScreenTasks({ it() }, { it() }))
        model.open("plex", "", {}, { throw it })
        model.enter(folder, {}, { throw it })
        model.next({}, { throw it })
        model.search("nature", {}, { throw it })
        assertEquals(CatalogLocation("plex", "opaque", "nature", 0), repository.requests.last())
    }

    @Test
    fun refreshPreservesLocationViewportAndBackHistory() {
        val repository = Repository(folder)
        val model = CatalogViewModel(repository, ScreenTasks({ it() }, { it() }))
        model.open("plex", "", {}, { throw it })
        model.enter(folder, {}, { throw it })
        val viewport = CatalogViewport("folder", "folder", -12, -12)
        model.rememberViewport(viewport)
        val location = model.screen!!.location
        model.refresh({}, { throw it })
        assertEquals(location, model.screen!!.location)
        assertEquals(viewport, model.screen!!.viewport)
        model.back {}
        assertEquals("", model.screen!!.location.parent)
        assertFalse(model.canBack)
    }

    @Test
    fun guidePagesKeepScopeAndReturnFocusWithoutLeavingTheFolder() {
        val repository = Repository(folder)
        val model = CatalogViewModel(repository, ScreenTasks({ it() }, { it() }))
        model.open("iptv", "sports", {}, { throw it })
        model.rememberViewport(CatalogViewport(selectedId = "channel-7"))
        model.next({}, { throw it })
        assertTrue(model.canPreviousPage)
        assertTrue(model.previousPage({}, { throw it }))
        assertEquals("channel-7", model.screen!!.viewport.selectedId)
        assertEquals("sports", model.screen!!.location.query)
        assertFalse(model.previousPage({}, { throw it }))
        model.enter(folder, {}, { throw it })
        assertFalse(model.canPreviousPage)
        assertFalse(model.previousPage({}, { throw it }))
        assertEquals("opaque", model.screen!!.location.parent)
    }

    @Test
    fun restoredGuidePageFailureKeepsCurrentPageAndHistoryForRetry() {
        val repository = Repository(folder)
        val model = CatalogViewModel(repository, ScreenTasks({ it() }, { it() }))
        model.restore(
            listOf(
                CatalogBookmark(CatalogLocation("iptv"), CatalogViewport(selectedId = "first")),
                CatalogBookmark(
                    CatalogLocation("iptv", offset = 80),
                    CatalogViewport(selectedId = "second"),
                ),
            ),
            {},
            { throw it },
        )
        repository.fail = true
        var failures = 0
        model.previousPage({}, { failures++ })
        assertEquals(1, failures)
        assertEquals(80, model.screen!!.location.offset)
        assertEquals(2, model.bookmarks().size)
        repository.fail = false
        model.previousPage({}, { throw it })
        assertEquals(0, model.screen!!.location.offset)
        assertEquals("first", model.screen!!.viewport.selectedId)
    }

    @Test
    fun closingGuideFencesAnInFlightPageChange() {
        val repository = Repository(folder)
        val work = ArrayList<() -> Unit>()
        val model = CatalogViewModel(repository, ScreenTasks({ work.add(it) }, { it() }))
        model.open("iptv", "", {}, { throw it })
        work.removeAt(0)()
        var displayed = false
        model.next({ displayed = true }, { throw it })
        model.cancelPending()
        work.removeAt(0)()
        assertFalse(displayed)
        assertEquals(0, model.screen!!.location.offset)
    }

    @Test
    fun playbackReturnSurvivesPendingRestoreAndIsConsumedOnce() {
        val repository = Repository(folder)
        val work = ArrayList<() -> Unit>()
        val model = CatalogViewModel(repository, ScreenTasks({ work.add(it) }, { it() }))
        val point =
            CatalogPlaybackReturn(
                CatalogOverlay("guide", guideTime = 123456, selectedChannel = "channel-7")
            )
        val path =
            listOf(
                CatalogBookmark(
                    CatalogLocation("iptv", offset = 80),
                    CatalogViewport(selectedId = "channel-7"),
                )
            )
        model.rememberPlaybackReturn(point)
        model.restore(path, {}, { throw it })
        assertEquals(path, model.bookmarks())
        assertEquals(point, model.playbackReturn)
        // Back may happen before the recreated Activity's page request completes.
        assertEquals(point, model.takePlaybackReturn())
        assertNull(model.takePlaybackReturn())
        work.removeAt(0)()
        assertEquals("channel-7", model.screen!!.viewport.selectedId)
        assertNull(model.playbackReturn)
    }

    @Test
    fun failedInitialRestoreRetainsTheWholePathForRetry() {
        val repository = Repository(folder).apply { fail = true }
        val model = CatalogViewModel(repository, ScreenTasks({ it() }, { it() }))
        val path =
            listOf(
                CatalogBookmark(CatalogLocation("plex"), CatalogViewport()),
                CatalogBookmark(
                    CatalogLocation("plex", parent = "series", offset = 80),
                    CatalogViewport("episode"),
                ),
            )
        val point = CatalogPlaybackReturn(detailId = "episode")
        model.rememberPlaybackReturn(point)
        var failed = false
        model.restore(path, { fail("unexpected load") }, { failed = true })
        assertTrue(failed)
        assertEquals(path, model.bookmarks())
        assertEquals(point, model.playbackReturn)
        repository.fail = false
        model.restore(model.bookmarks(), {}, { throw it })
        assertEquals(path, model.bookmarks())
        assertEquals(point, model.takePlaybackReturn())
    }

    @Test
    fun newProviderAndProfileResetDiscardOldPlaybackReturn() {
        val model = CatalogViewModel(Repository(folder), ScreenTasks({ it() }, { it() }))
        model.rememberPlaybackReturn(CatalogPlaybackReturn(detailId = "old"))
        model.open("jellyfin", "", {}, { throw it })
        assertNull(model.playbackReturn)
        model.rememberPlaybackReturn(CatalogPlaybackReturn(CatalogOverlay("home_search", "query")))
        model.dismiss()
        assertNull(model.playbackReturn)
    }
}
