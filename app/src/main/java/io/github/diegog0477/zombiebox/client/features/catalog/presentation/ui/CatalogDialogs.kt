package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvDpadKeyboard
import io.github.diegog0477.zombiebox.client.core.ui.TvTheme
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
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
import io.github.diegog0477.zombiebox.client.features.home.presentation.ui.ServiceMarkView
import java.util.Locale

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
        val previousOverlay = overlay
        overlay = null
        previousOverlay?.dismiss()
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
    private var categoryDialog: AlertDialog? = null
    private var detail: AlertDialog? = null
    val visible: Boolean
        get() =
            browser?.isShowing == true ||
                categoryDialog?.isShowing == true ||
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
        val targetProvider = path.lastOrNull()?.location?.provider
        if (targetProvider != null && !CatalogUiPolicy.supportsCatalog(targetProvider)) {
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
        categoryDialog?.dismiss()
        categoryDialog = null
        model.rememberPlaybackReturn(null)
        searchOrigin = false
        val previousOverlay = overlay
        overlay = null
        previousOverlay?.dismiss()
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
        if (!CatalogUiPolicy.supportsCatalog(provider)) return
        searchOrigin = false
        load { model.open(provider, query(), ::showPage, ::loadFailed) }
    }

    fun promptProviderSearch(provider: String) {
        if (!CatalogUiPolicy.supportsSearch(provider)) return
        searchOrigin = false
        showProviderSearch(provider, "", false)
    }

    private fun showProviderSearch(provider: String, draft: String, returnToCatalog: Boolean) {
        if (!CatalogUiPolicy.supportsSearch(provider)) return
        val previousOverlay = overlay
        overlay = null
        previousOverlay?.dismiss()
        browser?.dismiss()
        val accent = ui.providerAccent(provider)
        val serviceTitle = ui.serviceTitle(provider)
        val heading = activity.getString(R.string.search_provider, serviceTitle)
        val mark = ServiceMarkView(activity, provider, accent)
        var dialogRef: AlertDialog? = null
        val closeAction =
            Button(activity).apply {
                text = activity.getString(R.string.close)
                typeface = ui.bold
                textSize = 14f
                setTextColor(Color.WHITE)
                isFocusable = true
                setBackgroundDrawable(ui.focusBackground(accent))
                setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(6))
                setOnClickListener { dialogRef?.dismiss() }
            }
        val header =
            ui.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    mark,
                    LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)).apply {
                        rightMargin = ui.dp(14)
                    },
                )
                val titleCol =
                    ui.column().apply {
                        addView(
                            ui.text(serviceTitle.uppercase(Locale.US), 12f, accent).apply {
                                typeface = ui.bold
                            }
                        )
                        addView(
                            ui.text(heading, 22f).apply {
                                typeface = ui.bold
                                setSingleLine(true)
                                ellipsize = TextUtils.TruncateAt.END
                            }
                        )
                    }
                addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))
                addView(closeAction, LinearLayout.LayoutParams(-2, ui.dp(40)))
            }

        val input =
            EditText(activity).apply {
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(ui.muted)
                setBackgroundDrawable(
                    android.graphics.drawable.StateListDrawable().apply {
                        addState(
                            intArrayOf(android.R.attr.state_focused),
                            TvTheme.roundedBox(
                                ui.dp(8).toFloat(),
                                Color.rgb(26, 34, 37),
                                ui.dp(2),
                                accent,
                            ),
                        )
                        addState(
                            intArrayOf(),
                            TvTheme.roundedBox(
                                ui.dp(8).toFloat(),
                                TvTheme.inputBackground,
                                ui.dp(1),
                                TvTheme.inputBorder,
                            ),
                        )
                    }
                )
                setPadding(ui.dp(14), 0, ui.dp(14), 0)
                filters = arrayOf(android.text.InputFilter.LengthFilter(100))
                hint = heading
                typeface = TvTypography.regular(activity)
                setText(draft)
            }

        var submitted = false
        fun submit() {
            val phrase = input.text.toString().trim()
            if (!CatalogUiPolicy.isValidSearchQuery(phrase)) {
                input.requestFocus()
                input.setSelection(input.text?.length ?: 0)
                return
            }
            submitted = true
            dialogRef?.dismiss()
            load {
                if (returnToCatalog) model.search(phrase, ::showPage, ::loadFailed)
                else model.open(provider, phrase, ::showPage, ::loadFailed)
            }
        }

        val searchAction =
            Button(activity).apply {
                text = activity.getString(R.string.search)
                typeface = ui.bold
                textSize = 14f
                setTextColor(Color.WHITE)
                isFocusable = true
                setBackgroundDrawable(ui.focusBackground(accent))
                setPadding(ui.dp(18), ui.dp(6), ui.dp(18), ui.dp(6))
                setOnClickListener { submit() }
            }

        val inputRow =
            ui.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    input,
                    LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply { rightMargin = ui.dp(10) },
                )
                addView(searchAction, LinearLayout.LayoutParams(-2, ui.dp(48)))
            }

        val instruction =
            ui.text(
                activity.getString(R.string.search_provider_instruction, serviceTitle),
                13f,
                ui.muted,
            )

        val keyboard =
            TvDpadKeyboard(activity).apply {
                attachTarget(input)
                maxTextLength = 100
                setOnDone { submit() }
            }
        keyboard.nextUpFocusView = input

        val cancelAction = ui.button(R.string.cancel) { dialogRef?.dismiss() }
        keyboard.nextDownFocusView = cancelAction

        input.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        keyboard.focusDefaultKey()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        closeAction.requestFocus()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (input.selectionStart == (input.text?.length ?: 0)) {
                            searchAction.requestFocus()
                            true
                        } else false
                    }
                    else -> false
                }
            } else false
        }

        searchAction.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        input.requestFocus()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        keyboard.focusDefaultKey()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        closeAction.requestFocus()
                        true
                    }
                    else -> false
                }
            } else false
        }

        closeAction.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                input.requestFocus()
                true
            } else false
        }

        cancelAction.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                keyboard.focusDefaultKey()
                true
            } else false
        }

        val page =
            ui.column().apply {
                setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(20))
                addView(header)
                addView(inputRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
                addView(
                    instruction,
                    LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = ui.dp(6)
                        leftMargin = ui.dp(4)
                    },
                )
                addView(keyboard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(16) })
                addView(
                    ui.row().apply {
                        gravity = Gravity.CENTER
                        addView(cancelAction, LinearLayout.LayoutParams(ui.dp(180), ui.dp(44)))
                    },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(16) },
                )
            }

        val scroll =
            ScrollView(activity).apply {
                isFillViewport = true
                setBackgroundColor(TvTheme.shellBackground)
                addView(page)
            }

        val frame =
            FrameLayout(activity).apply {
                setBackgroundColor(TvTheme.shellBackground)
                val width = minOf(activity.resources.displayMetrics.widthPixels, ui.dp(820))
                addView(scroll, FrameLayout.LayoutParams(width, -1, Gravity.CENTER_HORIZONTAL))
            }

        val dialog = AlertDialog.Builder(activity).setView(frame).create()
        dialogRef = dialog
        overlayCapture = { CatalogOverlay("provider_search", input.text.toString().take(100)) }
        dialog.setOnDismissListener {
            if (overlay === dialog) {
                overlay = null
                if (!submitted && returnToCatalog) model.screen?.let(::showPage)
            }
        }
        overlay = dialog
        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(TvTheme.shellBackground))
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        frame.post { if (dialog.isShowing) keyboard.focusDefaultKey() }
    }

    private fun load(work: () -> Unit) {
        browser?.dismiss()
        val cancelAction = ui.button(R.string.cancel) { cancelLoad() }
        val page =
            ui.column().apply {
                setBackgroundColor(ui.background)
                gravity = Gravity.CENTER
                setPadding(ui.dp(32), ui.dp(32), ui.dp(32), ui.dp(32))
                addView(
                    ui.text(activity.getString(R.string.loading), 26f).apply {
                        typeface = ui.bold
                        gravity = Gravity.CENTER
                    }
                )
                addView(
                    cancelAction,
                    LinearLayout.LayoutParams(ui.dp(220), ui.dp(52)).apply { topMargin = ui.dp(24) },
                )
            }
        browser =
            AlertDialog.Builder(activity).setView(page).create().also { dialog ->
                dialog.setOnCancelListener { cancelLoad() }
                dialog.show()
                dialog.window?.let { window ->
                    window.setBackgroundDrawable(ColorDrawable(ui.background))
                    window.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
                page.post { if (dialog.isShowing) cancelAction.requestFocus() }
            }
        work()
    }

    private fun cancelLoad() {
        browser?.dismiss()
        browser = null
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
        val provider = screen.location.provider
        if (!CatalogUiPolicy.supportsCatalog(provider)) {
            browser?.dismiss()
            browser = null
            return
        }
        categoryDialog?.dismiss()
        categoryDialog = null
        detailItemId = ""
        detail?.dismiss()
        detail = null
        browser?.dismiss()
        val accent = ui.providerAccent(provider)
        val list = CatalogListView(activity, screen, accent, artwork)
        fun remember() {
            model.rememberViewport(list.viewport())
        }
        captureViewport = ::remember

        val content =
            ui.column().apply {
                setBackgroundColor(ui.background)
                setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16))

                val header =
                    ui.row().apply {
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 0, 0, ui.dp(12))

                        val titleCol =
                            ui.column().apply {
                                val tagRow =
                                    ui.row().apply {
                                        addView(
                                            ui.text(
                                                    ui.serviceTitle(provider).uppercase(Locale.US),
                                                    12f,
                                                    accent,
                                                )
                                                .apply { typeface = ui.bold }
                                        )
                                        if (screen.location.category.isNotEmpty()) {
                                            addView(
                                                ui.text(
                                                    "·  " + screen.location.category,
                                                    12f,
                                                    ui.muted,
                                                )
                                            )
                                        }
                                        if (screen.location.query.isNotEmpty()) {
                                            addView(
                                                ui.text(
                                                    "·  \"" + screen.location.query + "\"",
                                                    12f,
                                                    ui.muted,
                                                )
                                            )
                                        }
                                    }
                                addView(tagRow)
                                val titleText =
                                    if (screen.location.favoritesOnly)
                                        activity.getString(R.string.iptv_favorites)
                                    else screen.page.title.ifEmpty { ui.serviceTitle(provider) }
                                addView(
                                    ui.text(titleText, 22f).apply {
                                        typeface = ui.bold
                                        setSingleLine(true)
                                        ellipsize = TextUtils.TruncateAt.END
                                        setPadding(0, ui.dp(2), 0, 0)
                                    }
                                )
                            }
                        addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))

                        val actionsLine =
                            ui.row().apply {
                                if (provider == "iptv") {
                                    addView(
                                        ui.button(R.string.guide) {
                                            remember()
                                            openGuide(model.screen ?: screen)
                                        }
                                    )
                                    if (!screen.location.favoritesOnly) {
                                        addView(
                                            ui.button(R.string.iptv_favorites) {
                                                remember()
                                                load {
                                                    model.openIptvFavorites(
                                                        ::showPage,
                                                        ::loadFailed,
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    if (screen.page.categories.isNotEmpty()) {
                                        addView(
                                            ui.button(R.string.iptv_categories) {
                                                remember()
                                                chooseIptvCategory(screen)
                                            }
                                        )
                                    }
                                }
                                if (provider == "youtube" && screen.location.parent.isEmpty()) {
                                    addView(
                                        ui.button(R.string.youtube_account) {
                                            youtubeAccount.show()
                                        }
                                    )
                                }
                                if (CatalogUiPolicy.supportsSearch(provider)) {
                                    addView(
                                        ui.action(activity.getString(R.string.search), accent) {
                                            remember()
                                            searchPage(screen)
                                        }
                                    )
                                }
                                if (model.canBack || searchOrigin) {
                                    addView(ui.button(R.string.back) { back() })
                                }
                                if (screen.page.nextOffset >= 0) {
                                    addView(
                                        ui.primary(R.string.next_page) {
                                            remember()
                                            load { model.next(::showPage, ::loadFailed) }
                                        }
                                    )
                                }
                                addView(
                                    ui.button(R.string.close) {
                                        searchOrigin = false
                                        model.dismiss()
                                        browser?.dismiss()
                                    }
                                )
                            }
                        val actionsScroll =
                            HorizontalScrollView(activity).apply {
                                isHorizontalScrollBarEnabled = false
                                addView(actionsLine)
                            }
                        addView(actionsScroll, LinearLayout.LayoutParams(-2, -2))
                    }
                addView(header, LinearLayout.LayoutParams(-1, -2))

                if (screen.page.items.isEmpty()) {
                    val emptyBox =
                        ui.column().apply {
                            gravity = Gravity.CENTER
                            setPadding(ui.dp(24), ui.dp(48), ui.dp(24), ui.dp(48))
                            addView(
                                ui.text(activity.getString(R.string.catalog_empty), 16f, ui.muted)
                                    .apply { gravity = Gravity.CENTER }
                            )
                            val cta =
                                when (
                                    CatalogUiPolicy.resolveEmptyCta(
                                        provider = provider,
                                        hasQuery = screen.location.query.isNotEmpty(),
                                        hasCategory = screen.location.category.isNotEmpty(),
                                        favoritesOnly = screen.location.favoritesOnly,
                                        canBack = model.canBack || searchOrigin,
                                    )
                                ) {
                                    CatalogUiPolicy.EmptyCta.BROWSE_LIBRARY -> {
                                        ui.action(
                                            activity.getString(R.string.browse_library),
                                            accent,
                                        ) {
                                            remember()
                                            load {
                                                model.open(provider, "", ::showPage, ::loadFailed)
                                            }
                                        }
                                    }
                                    CatalogUiPolicy.EmptyCta.IPTV_ALL_CATEGORIES -> {
                                        ui.action(
                                            activity.getString(R.string.iptv_all_categories),
                                            accent,
                                        ) {
                                            remember()
                                            load {
                                                model.openIptvCategory("", ::showPage, ::loadFailed)
                                            }
                                        }
                                    }
                                    CatalogUiPolicy.EmptyCta.BACK -> {
                                        ui.button(R.string.back) { back() }
                                    }
                                    CatalogUiPolicy.EmptyCta.NONE -> null
                                }
                            cta?.let { button ->
                                button.layoutParams =
                                    LinearLayout.LayoutParams(-2, ui.dp(48)).apply {
                                        topMargin = ui.dp(16)
                                    }
                                addView(button)
                            }
                            post { if (browser?.isShowing == true) cta?.requestFocus() }
                        }
                    addView(emptyBox, LinearLayout.LayoutParams(-1, -1))
                } else {
                    addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
                }
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
        content.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        browser =
            AlertDialog.Builder(activity).setView(content).create().also { dialog ->
                dialog.setOnCancelListener { back() }
                dialog.show()
                dialog.window?.let { window ->
                    window.setBackgroundDrawable(ColorDrawable(ui.background))
                    window.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
                list.restoreViewport()
            }
    }

    private fun chooseIptvCategory(screen: CatalogScreen) {
        val categories = listOf("") + screen.page.categories
        val labels =
            listOf(activity.getString(R.string.iptv_all_categories)) + screen.page.categories
        categoryDialog?.dismiss()
        val actions = ui.column()
        var initialFocus: View? = null
        for (index in labels.indices) {
            val button =
                ui.action(labels[index], ui.providerAccent("iptv")) {
                    categoryDialog?.dismiss()
                    categoryDialog = null
                    load { model.openIptvCategory(categories[index], ::showPage, ::loadFailed) }
                }
            button.maxLines = 2
            actions.addView(
                button,
                LinearLayout.LayoutParams(-1, ui.dp(56)).apply { topMargin = ui.dp(6) },
            )
            if (categories[index] == screen.location.category) initialFocus = button
        }
        actions.addView(
            ui.button(R.string.cancel) {
                categoryDialog?.dismiss()
                categoryDialog = null
            },
            LinearLayout.LayoutParams(-1, ui.dp(56)).apply { topMargin = ui.dp(18) },
        )
        val page =
            ui.column().apply {
                setBackgroundColor(ui.background)
                setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24))
                addView(
                    ui.text(activity.getString(R.string.iptv_categories), 28f).apply {
                        typeface = ui.bold
                        setPadding(0, 0, 0, ui.dp(16))
                    }
                )
                addView(
                    ScrollView(activity).apply { addView(actions) },
                    LinearLayout.LayoutParams(-1, 0, 1f),
                )
            }
        val frame =
            FrameLayout(activity).apply {
                setBackgroundColor(ui.background)
                val width = minOf(activity.resources.displayMetrics.widthPixels, ui.dp(820))
                addView(page, FrameLayout.LayoutParams(width, -1, Gravity.CENTER_HORIZONTAL))
            }
        categoryDialog =
            AlertDialog.Builder(activity).setView(frame).create().also { dialog ->
                dialog.setOnCancelListener { categoryDialog = null }
                dialog.show()
                dialog.window?.let { window ->
                    window.setBackgroundDrawable(ColorDrawable(ui.background))
                    window.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
                val focus = initialFocus
                frame.post { if (dialog.isShowing) focus?.requestFocus() }
            }
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
        if (!CatalogUiPolicy.supportsSearch(screen.location.provider)) return
        showProviderSearch(screen.location.provider, draft, true)
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
