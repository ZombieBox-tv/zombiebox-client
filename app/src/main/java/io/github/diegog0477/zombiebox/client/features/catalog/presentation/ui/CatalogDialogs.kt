package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogBookmark
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogLocation
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogOverlay
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogPlaybackReturn
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogScreen
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogViewport
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.SearchBookmark
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.viewmodel.CatalogViewModel
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.viewmodel.SearchViewModel

class CatalogDialogs(
    private val activity: Activity,
    private val model: CatalogViewModel,
    private val query: () -> String,
    private val searchModel: SearchViewModel,
    private val play: (MediaItem) -> Unit,
    private val error: (Exception) -> Unit,
    private val artwork: (MediaItem) -> android.view.View?,
    private val startOver: (MediaItem) -> Unit,
) {
    private val ui = TvWidgets(activity)
    private val youtubeAccount =
        YouTubeAccountDialogs(
            activity,
            model,
            { item ->
                load {
                    model.openLocation(
                        CatalogLocation("youtube", parent = item.browseId),
                        ::showPage,
                        ::loadFailed,
                    )
                }
            },
            error,
        )

    private var overlay: AlertDialog? = null
    private var overlayCapture: () -> CatalogOverlay = { CatalogOverlay() }
    private var guide: GuideDialog? = null
    private var searchOrigin = false

    fun searchSnapshot(): SearchBookmark? {
        if (overlay?.isShowing == true) overlayCapture()
        return if (searchOrigin) searchModel.bookmark else null
    }

    fun overlaySnapshot(): CatalogOverlay =
        guide?.snapshot()?.takeIf { it.kind.isNotEmpty() }
            ?: if (overlay?.isShowing == true) overlayCapture() else CatalogOverlay()

    fun search(draft: String = searchModel.state.query.ifEmpty { query() }) {
        guide?.close()
        overlay?.dismiss()
        browser?.dismiss()
        detail?.dismiss()
        detailItemId = ""
        captureViewport = null
        model.dismiss()
        searchOrigin = true
        overlay =
            SearchDialog(activity, searchModel)
                .show(
                    draft,
                    { item ->
                        if (item.kind == "search_more") {
                            load {
                                model.open(
                                    item.provider,
                                    searchModel.state.query,
                                    ::showPage,
                                    ::loadFailed,
                                )
                            }
                        } else if (item.browseId.isNotEmpty()) {
                            load {
                                model.openLocation(
                                    CatalogLocation(item.provider, parent = item.browseId),
                                    ::showPage,
                                    ::loadFailed,
                                )
                            }
                        } else showDetails(item) { search(searchModel.state.query) }
                    },
                    { capture -> overlayCapture = { CatalogOverlay("home_search", capture()) } },
                    { searchOrigin = false },
                )
    }

    private var browser: AlertDialog? = null
    private var detail: AlertDialog? = null
    val visible: Boolean
        get() =
            browser?.isShowing == true ||
                youtubeAccount.visible ||
                detail?.isShowing == true ||
                overlay?.isShowing == true ||
                guide?.visible == true

    var detailItemId: String = ""
        private set

    private var captureViewport: (() -> Unit)? = null

    fun snapshot(): List<CatalogBookmark> {
        if (guide?.visible == true) {
            model.rememberViewport(CatalogViewport(selectedId = guide!!.snapshot().selectedChannel))
        } else captureViewport?.invoke()
        return model.bookmarks()
    }

    fun restore(
        path: List<CatalogBookmark>,
        detailId: String = "",
        display: Boolean = true,
        savedOverlay: CatalogOverlay = CatalogOverlay(),
        savedSearch: SearchBookmark? = null,
    ) {
        searchOrigin = savedSearch != null
        savedSearch?.let(searchModel::restoreBookmark)
        if (path.isEmpty()) {
            if (display && (savedOverlay.kind == "home_search" || savedSearch != null))
                search(
                    if (savedOverlay.kind == "home_search") savedOverlay.draft
                    else savedSearch!!.query
                )
            return
        }
        val work = {
            model.restore(
                path,
                { screen ->
                    if (display) {
                        showReturn(screen, CatalogPlaybackReturn(savedOverlay, detailId))
                    }
                },
                { failure -> if (display) loadFailed(failure) },
            )
        }
        if (display) load(work) else work()
    }

    /** Consumed only by explicit Back/minimize, never by receiver or lifecycle callbacks. */
    fun resumePlayback(): Boolean {
        val target = model.takePlaybackReturn() ?: return false
        val current = model.screen
        if (current == null) {
            if (!searchOrigin) return false
            search(searchModel.bookmark.query)
            return true
        }
        if (current.page.items.isEmpty()) {
            val path = model.bookmarks()
            load {
                model.restore(
                    path,
                    { showReturn(it, target) },
                    { failure ->
                        model.rememberPlaybackReturn(target)
                        loadFailed(failure)
                    },
                )
            }
        } else showReturn(current, target)
        return true
    }

    private fun showReturn(screen: CatalogScreen, target: CatalogPlaybackReturn) {
        showPage(screen)
        when (target.overlay.kind) {
            "home_search" -> search(target.overlay.draft)
            "provider_search" -> searchPage(screen, target.overlay.draft)
            "guide" -> openGuide(screen, target.overlay)
        }
        screen.page.items
            .firstOrNull { it.id == target.detailId }
            ?.let { item ->
                browser?.dismiss()
                showDetails(item) { model.screen?.let(::showPage) }
            }
    }

    fun resume(): Boolean {
        if (resumePlayback()) return true
        val current = model.screen
        if (current == null) {
            if (!searchOrigin) return false
            search(searchModel.bookmark.query)
            return true
        }
        showPage(current)
        return true
    }

    // Only these owned catalog windows accept companion input. Consent/settings/system
    // dialogs are deliberately absent, and focus must belong to the selected window.
    private fun remoteDialog(): AlertDialog? =
        listOf(guide?.activeDialog, overlay, detail, browser).firstOrNull {
            it?.isShowing == true && it.window?.decorView?.hasWindowFocus() == true
        }

    val remoteReady: Boolean
        get() = remoteDialog() != null

    fun remoteTextField(): EditText? = remoteDialog()?.currentFocus as? EditText

    fun remoteKey(key: Int): Boolean {
        val dialog = remoteDialog() ?: return false
        val down =
            dialog.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key))
        val up =
            dialog.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key))
        return down || up
    }

    fun close() {
        youtubeAccount.close()
        model.rememberPlaybackReturn(null)
        searchOrigin = false
        overlay?.dismiss()
        overlay = null
        guide?.close()
        guide = null
        captureViewport = null
        detail?.dismiss()
        detail = null
        browser?.dismiss()
        browser = null
    }

    fun reset() {
        close()
        model.dismiss()
        searchModel.reset()
    }

    fun page(provider: String) {
        searchOrigin = false
        load { model.open(provider, query(), ::showPage, ::loadFailed) }
    }

    private fun load(work: () -> Unit) {
        browser?.dismiss()
        browser =
            AlertDialog.Builder(activity)
                .setMessage(R.string.loading)
                .setNegativeButton(R.string.cancel) { _, _ -> cancelLoad() }
                .create()
                .also {
                    it.setOnCancelListener { cancelLoad() }
                    it.show()
                }
        work()
    }

    private fun cancelLoad() {
        model.cancelPending()
        returnToCatalogOrSearch()
    }

    private fun loadFailed(failure: Exception) {
        browser?.dismiss()
        returnToCatalogOrSearch()
        error(failure)
    }

    private fun returnToCatalogOrSearch() {
        val current = model.screen
        if (current != null) showPage(current)
        else if (searchOrigin) search(searchModel.bookmark.query) else model.dismiss()
    }

    private fun back() {
        if (!model.back(::showPage, ::loadFailed)) {
            if (searchOrigin) search(searchModel.bookmark.query) else model.dismiss()
        }
    }

    private fun showPage(screen: CatalogScreen) {
        detailItemId = ""
        detail?.dismiss()
        detail = null
        browser?.dismiss()
        val provider = screen.location.provider
        val list = CatalogListView(activity, screen, ui.providerAccent(provider))
        fun remember() {
            model.rememberViewport(list.viewport())
        }
        captureViewport = ::remember
        val content =
            ui.column().apply {
                setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
                setBackgroundColor(ui.background)
                addView(
                    ui.text(
                        activity.getString(R.string.catalog_scope, ui.serviceTitle(provider)),
                        14f,
                        ui.providerAccent(provider),
                    )
                )
                if (provider == "iptv")
                    addView(
                        ui.button(R.string.guide) {
                            remember()
                            openGuide(model.screen ?: screen)
                        }
                    )
                if (provider == "youtube" && screen.location.parent.isEmpty())
                    addView(ui.button(R.string.youtube_account) { youtubeAccount.show() })
                if (provider == "iptv" && !screen.location.favoritesOnly)
                    addView(
                        ui.button(R.string.iptv_favorites) {
                            remember()
                            load { model.openIptvFavorites(::showPage, ::loadFailed) }
                        }
                    )
                if (provider == "iptv" && screen.page.categories.isNotEmpty())
                    addView(
                        ui.button(R.string.iptv_categories) {
                            remember()
                            chooseIptvCategory(screen)
                        }
                    )
                if (screen.location.category.isNotEmpty())
                    addView(ui.text(screen.location.category, 14f, ui.providerAccent(provider)))
                if (screen.location.query.isNotEmpty())
                    addView(ui.text(screen.location.query, 14f, ui.muted))
                if (screen.page.items.isEmpty())
                    addView(ui.text(activity.getString(R.string.catalog_empty), 16f, ui.muted))
                else addView(list, LinearLayout.LayoutParams(-1, ui.dp(320)))
                addView(
                    ui.action(activity.getString(R.string.search), ui.providerAccent(provider)) {
                        remember()
                        searchPage(screen)
                    }
                )
            }
        list.setOnItemClickListener { _, _, index, _ ->
            model.rememberViewport(list.viewport(index))
            val item = screen.page.items[index]
            if (item.browseId.isNotEmpty()) load { model.enter(item, ::showPage, ::loadFailed) }
            else {
                browser?.dismiss()
                showDetails(item) { model.screen?.let(::showPage) }
            }
        }
        val builder =
            AlertDialog.Builder(activity)
                .setTitle(
                    if (screen.location.favoritesOnly) activity.getString(R.string.iptv_favorites)
                    else screen.page.title.ifEmpty { ui.serviceTitle(provider) }
                )
                .setView(content)
                .setNegativeButton(R.string.close) { _, _ ->
                    searchOrigin = false
                    model.dismiss()
                }
        if (screen.page.nextOffset >= 0)
            builder.setPositiveButton(R.string.next_page) { _, _ ->
                remember()
                load { model.next(::showPage, ::loadFailed) }
            }
        if (model.canBack || searchOrigin)
            builder.setNeutralButton(R.string.back) { _, _ -> back() }
        browser =
            builder.create().also { dialog ->
                dialog.setOnCancelListener { back() }
                dialog.show()
                list.restoreViewport()
            }
    }

    private fun chooseIptvCategory(screen: CatalogScreen) {
        val categories = listOf("") + screen.page.categories
        val labels =
            listOf(activity.getString(R.string.iptv_all_categories)) + screen.page.categories
        AlertDialog.Builder(activity)
            .setTitle(R.string.iptv_categories)
            .setItems(labels.toTypedArray()) { _, index ->
                load { model.openIptvCategory(categories[index], ::showPage, ::loadFailed) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openGuide(screen: CatalogScreen, saved: CatalogOverlay = CatalogOverlay()) {
        guide?.close()
        lateinit var currentGuide: GuideDialog
        fun page(next: Boolean) {
            val bookmark = currentGuide.snapshot()
            model.rememberViewport(CatalogViewport(selectedId = bookmark.selectedChannel))
            val done: (CatalogScreen) -> Unit = { loaded ->
                if (guide === currentGuide && currentGuide.visible) {
                    showPage(loaded)
                    openGuide(
                        loaded,
                        currentGuide.snapshot().copy(selectedChannel = loaded.viewport.selectedId),
                    )
                }
            }
            val failed: (Exception) -> Unit = { currentGuide.pageFailed() }
            if (next) model.next(done, failed)
            else if (!model.previousPage(done, failed)) currentGuide.pageFailed()
        }
        currentGuide =
            GuideDialog(
                activity,
                screen.page.items,
                { item, returnPoint ->
                    model.rememberViewport(CatalogViewport(selectedId = item.id))
                    captureViewport = null
                    model.rememberPlaybackReturn(CatalogPlaybackReturn(returnPoint))
                    browser?.dismiss()
                    play(item)
                },
                { updated, failed ->
                    model.refresh({ current -> updated(current.page.items) }, { failed() })
                },
                previousPage = if (model.canPreviousPage) ({ page(false) }) else null,
                nextPage =
                    if (screen.page.nextOffset > screen.location.offset) ({ page(true) }) else null,
                dismissed = { if (guide === currentGuide) model.cancelPending() },
            )
        guide = currentGuide
        currentGuide.show(saved)
    }

    private fun searchPage(screen: CatalogScreen, draft: String = screen.location.query) {
        overlay?.dismiss()
        browser?.dismiss()
        val input =
            EditText(activity).apply {
                setSingleLine(true)
                setText(draft)
            }
        overlayCapture = { CatalogOverlay("provider_search", input.text.toString()) }
        overlay =
            AlertDialog.Builder(activity)
                .setTitle(
                    activity.getString(
                        R.string.catalog_scope,
                        ui.serviceTitle(screen.location.provider),
                    )
                )
                .setView(input)
                .setPositiveButton(R.string.search) { _, _ ->
                    load { model.search(input.text.toString(), ::showPage, ::loadFailed) }
                }
                .setNegativeButton(R.string.cancel) { _, _ -> model.screen?.let(::showPage) }
                .create()
                .also {
                    it.setOnCancelListener { model.screen?.let(::showPage) }
                    it.show()
                }
    }

    fun details(item: MediaItem) {
        model.dismiss()
        searchOrigin = false
        showDetails(item, null)
    }

    private fun playDetails(item: MediaItem, fromBeginning: Boolean) {
        val target =
            when {
                model.screen != null -> CatalogPlaybackReturn(detailId = item.id)
                searchOrigin ->
                    CatalogPlaybackReturn(CatalogOverlay("home_search", searchModel.bookmark.query))
                else -> null
            }
        model.rememberPlaybackReturn(target)
        captureViewport = null
        if (fromBeginning) startOver(item) else play(item)
    }

    private fun showDetails(item: MediaItem, closed: (() -> Unit)?) {
        detail?.dismiss()
        detailItemId = item.id
        detail =
            CatalogDetailsDialog(activity)
                .show(
                    item,
                    artwork(item),
                    { playDetails(item, false) },
                    { playDetails(item, true) },
                    closed,
                    if (item.provider == "iptv" && item.kind == "channel") {
                        {
                            detail?.dismiss()
                            model.setIptvFavorite(
                                item,
                                {
                                    if (model.screen != null)
                                        load { model.refresh(::showPage, ::loadFailed) }
                                    else showDetails(item.copy(favorite = !item.favorite), closed)
                                },
                                ::loadFailed,
                            )
                        }
                    } else null,
                )
    }
}
