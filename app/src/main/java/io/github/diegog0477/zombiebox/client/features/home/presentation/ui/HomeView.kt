package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.RemoteFocus
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.core.ui.WindowedRow
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.ui.ArtworkImageView
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel.ArtworkViewModel
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogPage
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui.CatalogUiPolicy
import io.github.diegog0477.zombiebox.client.features.diagnostics.platform.HardwareMemory
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeBudget
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeScope
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeSnapshot
import io.github.diegog0477.zombiebox.client.features.home.domain.model.YouTubeActivityPage

data class HomeActions(
    val navigate: (String, String) -> Unit,
    val play: (MediaItem) -> Unit,
    val details: (MediaItem) -> Unit,
    val settings: () -> Unit,
    val search: () -> Unit,
    val searchProvider: (String) -> Unit,
    val devices: () -> Unit,
    val youtubeReceiver: () -> Unit,
    val airplayPairing: () -> Unit,
    val airplayAudioOptions: () -> Unit,
    val airplayConfigure: () -> Unit,
    val catalog: (String) -> Unit,
    val mirrorReceiver: () -> Unit,
    val providers: () -> Unit,
    val pair: () -> Unit,
    val playPause: () -> Unit = {},
    val next: () -> Unit = {},
    val previous: () -> Unit = {},
    val expand: () -> Unit = {},
    val spotifyAuthorize: () -> Unit = {},
    val spotifyReceive: () -> Unit = {},
    val airplayReceive: () -> Unit = {},
    val loadYouTubePage: (Int, (CatalogPage?, Exception?) -> Unit) -> Unit = { _, done ->
        done(null, null)
    },
    val loadYouTubeActivityPage: (String, (YouTubeActivityPage?, Exception?) -> Unit) -> Unit =
        { _, done ->
            done(null, null)
        },
    val refreshHomeFocus: (Boolean) -> Unit = {},
)

/** Home composition and D-pad focus. Receives semantic content and user-action callbacks. */
@Suppress("DEPRECATION")
class HomeView(
    context: Context,
    private val artwork: ArtworkViewModel,
    private val decoder:
        io.github.diegog0477.zombiebox.client.features.artwork.platform.ArtworkDecoder,
    private val actions: HomeActions,
) : LinearLayout(context) {
    val focus = RemoteFocus()
    private var content: LinearLayout = this

    private val focusRows = ArrayList<Pair<String, ViewGroup>>()
    private var scope = HomeScope()
    private var airPlayPlaybackStopped = false
    private val navigation = LinkedHashMap<String, Button>()
    private var heroStatus: TextView? = null
    private var playbackFeedbackView: TextView? = null
    private var playbackFeedbackMessage: Int? = null
    private val heapMb =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
    private val physicalMb = HardwareMemory.physicalMb()

    private var nowPlayingRail: NowPlayingRailView? = null
    private var compactTransport: CompactTransportBar? = null
    private var rightRailContainer: FrameLayout? = null
    private var spotifyPageView: SpotifyPageView? = null
    private var airPlayPageView: AirPlayPageView? = null
    private var youtubePageView: YouTubePageView? = null
    private var rememberedMainFocus: String? = null
    private var currentPlayback: HomePlaybackSession = HomePlaybackSession()
    private var isDockedLandscape: Boolean = false
    private var isFullscreen: Boolean = false
    private val youtubeAutoPageFocusListener =
        ViewTreeObserver.OnGlobalFocusChangeListener { _, focused ->
            youtubePageView?.onFocusChanged(focused)
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(youtubeAutoPageFocusListener)
    }

    override fun onDetachedFromWindow() {
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnGlobalFocusChangeListener(youtubeAutoPageFocusListener)
        }
        super.onDetachedFromWindow()
    }

    fun isRightRailActive(): Boolean =
        isDockedLandscape && currentPlayback.active && nowPlayingRail != null

    fun isRightRailFocused(): Boolean = nowPlayingRail?.hasFocusWithin() == true

    fun focusRightRail(): Boolean {
        if (!isRightRailActive()) return false
        rememberedMainFocus = focus.selectedKey
        val target = nowPlayingRail?.primaryControl() ?: return false
        target.requestFocus()
        return true
    }

    fun restoreMainContentFocus(): Boolean {
        val key = rememberedMainFocus
        rememberedMainFocus = null
        if (key != null) {
            focus.remember(key)
        }
        focus.restore()
        return true
    }

    fun moveRightRailFocus(keyCode: Int, current: View?): View? =
        nowPlayingRail?.moveFocus(keyCode, current)

    fun setPlaybackSession(playback: HomePlaybackSession) {
        val wasActive = currentPlayback.active
        val wasSpotify = currentPlayback.item?.provider == "spotify"
        val wasAirPlay = wasActive && currentPlayback.item?.provider == "airplay"
        currentPlayback = playback
        val isAirPlay = playback.active && playback.item?.provider == "airplay"
        if (isAirPlay) {
            airPlayPlaybackStopped = false
        } else if (wasAirPlay || (playback.item?.provider == "airplay" && !playback.active)) {
            airPlayPlaybackStopped = true
        }
        spotifyPageView?.updatePlaybackSession(playback)
        airPlayPageView?.updatePlaybackSession(playback, suppressAirPlaySnapshot(playback))
        if (isDockedLandscape) {
            rightRailContainer?.let { container ->
                if (playback.active && nowPlayingRail != null) {
                    nowPlayingRail?.update(playback)
                    return@let
                }
                if (!playback.active && nowPlayingRail == null) return@let
                val railHadFocus = nowPlayingRail?.hasFocusWithin() == true
                container.removeAllViews()
                if (playback.active) {
                    val rail =
                        NowPlayingRailView(
                            context,
                            ui,
                            artwork,
                            decoder,
                            TransportActions(
                                actions.playPause,
                                actions.next,
                                actions.previous,
                                actions.expand,
                            ),
                        )
                    rail.update(playback)
                    container.addView(rail, FrameLayout.LayoutParams(-1, -2))
                    nowPlayingRail = rail
                } else {
                    nowPlayingRail = null
                    container.addView(
                        HomeStatusPanel(context, ui),
                        FrameLayout.LayoutParams(-1, -2),
                    )
                    if (railHadFocus) post { restoreMainContentFocus() }
                }
            }
        } else {
            compactTransport?.let { transport ->
                if (playback.active) {
                    transport.visibility = VISIBLE
                    transport.update(playback)
                } else {
                    transport.visibility = GONE
                }
            }
        }
        val spotifyActive = playback.active && playback.item?.provider == "spotify"
        if (wasActive != playback.active || wasSpotify != spotifyActive) {
            focusRows.removeAll { it.first == "spotify:playback" }
            if (spotifyActive && scope.provider == "spotify") {
                spotifyPageView?.playbackControls()?.let {
                    focusRows.add(Pair("spotify:playback", it))
                }
            }
            rebuildFocus(!isFullscreen)
        }
    }

    fun videoDockRect(relativeTo: View): Rect? =
        if (isRightRailActive()) nowPlayingRail?.videoDockRect(relativeTo) else null

    fun updatePlaybackProgress(status: String, positionMs: Int, durationMs: Int) {
        currentPlayback =
            currentPlayback.copy(state = status, positionMs = positionMs, durationMs = durationMs)
        nowPlayingRail?.updateProgress(status, positionMs, durationMs)
        compactTransport?.updateProgress(status, positionMs, durationMs)
        spotifyPageView?.updateProgress(status, positionMs, durationMs)
        airPlayPageView?.updatePlaybackSession(
            currentPlayback,
            suppressAirPlaySnapshot(currentPlayback),
        )
    }

    private fun suppressAirPlaySnapshot(playback: HomePlaybackSession): Boolean =
        airPlayPlaybackStopped || (playback.active && playback.item?.provider != "airplay")

    private fun airPlaySnapshotAudio(snapshot: HomeSnapshot): MediaItem? =
        snapshot.sections
            .asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { it.provider == "airplay" && it.id == "airplay-audio" }

    fun status(loading: Boolean, failed: Boolean) {
        heroStatus?.apply {
            visibility = if (loading || failed) VISIBLE else GONE
            setText(if (loading) R.string.home_refreshing else R.string.home_stale)
        }
    }

    fun playbackFeedback(message: Int?) {
        playbackFeedbackMessage = message
        playbackFeedbackView?.apply {
            visibility = if (message == null) GONE else VISIBLE
            if (message != null) setText(message)
        }
    }

    fun selectScope(scope: HomeScope) {
        this.scope = scope
        navigation.forEach { (provider, tab) -> tab.isSelected = provider == scope.provider }
    }

    private val ui = TvWidgets(context) { accent() }

    private fun accent(): Int = ui.providerAccent(scope.provider)

    init {
        orientation = VERTICAL
    }

    private fun horizontal(parent: LinearLayout, id: String = ""): LinearLayout {
        val scroll = HorizontalScrollView(context)
        scroll.isHorizontalScrollBarEnabled = false
        val line = ui.row()
        scroll.addView(line)
        parent.addView(scroll)
        if (id.isNotEmpty()) focusRows.add(Pair(id, line))
        return line
    }

    private fun title(label: String) {
        content.addView(ui.text(label, 18f).apply { setPadding(ui.dp(4), ui.dp(14), 0, ui.dp(8)) })
    }

    fun render(
        snapshot: HomeSnapshot,
        scope: HomeScope,
        tv: Boolean,
        bottom: ViewGroup,
        full: Boolean,
    ) = render(snapshot, scope, tv, full, currentPlayback)

    fun render(
        snapshot: HomeSnapshot,
        scope: HomeScope,
        tv: Boolean,
        full: Boolean = false,
        playback: HomePlaybackSession = currentPlayback,
    ) {
        val wasActiveAirPlay = currentPlayback.active && currentPlayback.item?.provider == "airplay"
        this.scope = scope
        this.currentPlayback = playback
        val isActiveAirPlay = playback.active && playback.item?.provider == "airplay"
        if (isActiveAirPlay) {
            airPlayPlaybackStopped = false
        } else if (wasActiveAirPlay || (playback.item?.provider == "airplay" && !playback.active)) {
            airPlayPlaybackStopped = true
        }
        if (airPlaySnapshotAudio(snapshot)?.playable == false) airPlayPlaybackStopped = false
        this.isFullscreen = full
        focusRows.clear()
        navigation.clear()
        nowPlayingRail = null
        compactTransport = null
        rightRailContainer = null
        spotifyPageView = null
        airPlayPageView = null
        youtubePageView = null
        removeAllViews()

        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        isDockedLandscape = tv && widthDp >= 900f

        // Top Navigation Header
        val header = ui.row()
        val navigationFrame = ui.column()
        header.addView(navigationFrame, LinearLayout.LayoutParams(0, -2, 1f))
        if (widthDp >= 900f) header.addView(HomeClock(context, ui))
        addView(header, LinearLayout.LayoutParams(-1, -2))

        val nav = horizontal(navigationFrame, "navigation")
        nav.addView(
            ui.text(context.getString(R.string.brand), 21f).apply {
                val label = android.text.SpannableString(text)
                val suffix = label.toString().lastIndexOf("tv")
                if (suffix >= 0)
                    label.setSpan(
                        android.text.style.ForegroundColorSpan(ui.green),
                        suffix,
                        label.length,
                        0,
                    )
                text = label
                typeface = ui.bold
                setPadding(0, 0, ui.dp(16), 0)
            }
        )
        val home =
            ui.navigation(context.getString(R.string.home), "", scope.provider.isEmpty()) {
                actions.navigate("", "")
            }
        navigation[""] = home
        nav.addView(home)
        for (id in arrayOf("youtube", "plex", "stremio", "spotify", "iptv", "airplay")) {
            val tab =
                ui.navigation(ui.serviceTitle(id), id, scope.provider == id) {
                    actions.navigate(id, "")
                }
            navigation[id] = tab
            nav.addView(tab)
        }
        nav.addView(
            ui.action(context.getString(R.string.devices), ui.green, actions.devices).apply {
                textSize = 13f
                setPadding(ui.dp(9), ui.dp(6), ui.dp(9), ui.dp(6))
            }
        )
        nav.addView(headerAction(R.string.search, false, actions.search))
        nav.addView(headerAction(R.string.settings, true, actions.settings))

        // Main content column
        val mainColumn = ui.column()

        if (isDockedLandscape) {
            val body = ui.row().apply { gravity = Gravity.TOP }
            val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
            scroll.addView(mainColumn, FrameLayout.LayoutParams(-1, -2))
            body.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))

            val railFrame = FrameLayout(context)
            rightRailContainer = railFrame
            if (playback.active) {
                val rail =
                    NowPlayingRailView(
                        context,
                        ui,
                        artwork,
                        decoder,
                        TransportActions(
                            actions.playPause,
                            actions.next,
                            actions.previous,
                            actions.expand,
                        ),
                    )
                rail.update(playback)
                railFrame.addView(rail, FrameLayout.LayoutParams(-1, -2))
                nowPlayingRail = rail
            } else {
                railFrame.addView(HomeStatusPanel(context, ui), FrameLayout.LayoutParams(-1, -2))
            }
            body.addView(
                railFrame,
                LinearLayout.LayoutParams(ui.dp(260), -2).apply {
                    leftMargin = ui.dp(18)
                    topMargin = ui.dp(14)
                },
            )
            addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
            scroll.addView(mainColumn, FrameLayout.LayoutParams(-1, -2))
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

            val transport =
                CompactTransportBar(
                    context,
                    ui,
                    artwork,
                    decoder,
                    TransportActions(
                        actions.playPause,
                        actions.next,
                        actions.previous,
                        actions.expand,
                    ),
                )
            compactTransport = transport
            if (playback.active) {
                transport.visibility = VISIBLE
                transport.update(playback)
            } else {
                transport.visibility = GONE
            }
            addView(transport, LinearLayout.LayoutParams(-1, -2))
        }

        content = mainColumn

        val statusView = ui.text("", 12f, ui.muted).apply { visibility = GONE }
        heroStatus = statusView
        content.addView(statusView)
        playbackFeedbackView = ui.text("", 15f, ui.muted)
        content.addView(playbackFeedbackView)
        playbackFeedback(playbackFeedbackMessage)

        when (scope.provider) {
            "youtube" -> {
                val page = YouTubePageView(context, ui, artwork, decoder, actions)
                youtubePageView = page
                page.render(
                    parent = mainColumn,
                    snapshot = snapshot,
                    isHomeFeed = scope.query.isBlank(),
                    isDockedLandscape = isDockedLandscape,
                    widthDp = widthDp,
                    focusRows = focusRows,
                )
            }
            "plex",
            "stremio",
            "jellyfin" -> {
                ProviderPosterPageView(
                        context = context,
                        ui = ui,
                        artwork = artwork,
                        decoder = decoder,
                        actions = actions,
                        provider = scope.provider,
                    )
                    .render(
                        parent = mainColumn,
                        snapshot = snapshot,
                        isDockedLandscape = isDockedLandscape,
                        widthDp = widthDp,
                        heapMb = heapMb,
                        physicalMb = physicalMb,
                        tv = tv,
                        focusRows = focusRows,
                    )
            }
            "airplay" -> {
                val page = AirPlayPageView(context, ui, actions)
                airPlayPageView = page
                page.render(mainColumn, snapshot, focusRows)
                page.updatePlaybackSession(
                    currentPlayback,
                    suppressAirPlaySnapshot(currentPlayback),
                )
            }
            "spotify" -> {
                val page = SpotifyPageView(context, ui, actions, artwork, decoder)
                spotifyPageView = page
                page.render(mainColumn, snapshot, currentPlayback, focusRows)
            }
            "iptv" -> {
                renderProviderScope(
                    parent = mainColumn,
                    snapshot = snapshot,
                    provider = scope.provider,
                    tv = tv,
                )
            }
            else -> {
                renderHomeScope(parent = mainColumn, snapshot = snapshot, tv = tv)
            }
        }

        content.addView(
            ui.text(
                context.getString(
                    if (tv) R.string.docked_description else R.string.handheld_description
                ),
                14f,
                ui.muted,
            )
        )

        rebuildFocus(!full)
    }

    fun rebuildFocus(restore: Boolean = false) {
        focus.rebuild(focusRows + playbackFocusRows(currentPlayback), restore && !isFullscreen)
        if (scope.provider == "youtube") {
            findViewWithTag<View>("youtube:load-more")?.setOnFocusChangeListener { view, focused ->
                if (focused) view.performClick()
            }
        }
    }

    private fun playbackFocusRows(playback: HomePlaybackSession): List<Pair<String, ViewGroup>> =
        if (!isDockedLandscape && playback.active && compactTransport != null)
            listOf(Pair("transport", compactTransport!!.controlsContainer()))
        else emptyList()

    private fun headerAction(label: Int, settings: Boolean, click: () -> Unit): ImageButton =
        ImageButton(context).apply {
            contentDescription = context.getString(label)
            tag = "button:$label"
            setImageDrawable(HeaderIcon(settings, Color.WHITE))
            setPadding(ui.dp(13), ui.dp(13), ui.dp(13), ui.dp(13))
            setBackgroundDrawable(
                android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), ui.box(ui.panel, ui.green))
                    addState(intArrayOf(android.R.attr.state_pressed), ui.box(ui.panel, ui.green))
                    addState(intArrayOf(), ui.box(Color.TRANSPARENT))
                }
            )
            isFocusable = true
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(ui.dp(48), ui.dp(48))
        }

    private fun renderHomeScope(parent: LinearLayout, snapshot: HomeSnapshot, tv: Boolean) {
        val heroFrame = FrameLayout(context).apply { minimumHeight = ui.dp(240) }
        val hero = ui.column()
        hero.setPadding(ui.dp(24), ui.dp(18), ui.dp(24), ui.dp(18))
        hero.setBackgroundDrawable(
            GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(12, 27, 24), Color.rgb(14, 25, 27), ui.background),
                )
                .apply { cornerRadius = ui.dp(10).toFloat() }
        )
        val featured = snapshot.hero
        val hasReadyService =
            snapshot.modules.any {
                it.id != "local" && it.state in listOf("READY", "HEALTHY", "STARTING")
            }
        if (featured != null && featured.imageUrl.isNotEmpty()) {
            heroFrame.addView(artImage(featured.imageUrl, true), FrameLayout.LayoutParams(-1, -1))
            hero.setBackgroundDrawable(
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.argb(240, 10, 15, 16), Color.argb(100, 10, 15, 16)),
                )
            )
        }
        heroFrame.addView(hero, FrameLayout.LayoutParams(-1, -2))
        hero.addView(
            ui.text(context.getString(R.string.tagline), 12f, ui.providerAccent(scope.provider))
        )
        hero.addView(
            ui.text(featured?.title ?: context.getString(R.string.welcome), 32f).apply {
                typeface = ui.bold
                maxLines = 2
            }
        )
        if (featured != null) {
            hero.addView(
                ui.text(
                        listOf(ui.serviceTitle(featured.provider), featured.subtitle)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        13f,
                        ui.muted,
                    )
                    .apply { maxLines = 1 }
            )
        }
        hero.addView(
            ui.text(
                    featured?.description?.takeIf { it.isNotEmpty() }
                        ?: context.getString(
                            if (hasReadyService) R.string.home_ready_detail
                            else R.string.welcome_detail
                        ),
                    16f,
                    ui.muted,
                )
                .apply { maxLines = 2 }
        )
        val heroActions = ui.row()
        if (featured != null) {
            if (featured.playable)
                heroActions.addView(
                    ui.primary(
                        if (featured.positionMs > 0) R.string.resume_content else R.string.play
                    ) {
                        actions.play(featured)
                    }
                )
            heroActions.addView(ui.button(R.string.more_info) { actions.details(featured) })
        } else if (!hasReadyService) {
            heroActions.addView(ui.button(R.string.configure_services) { actions.settings() })
        }
        if (heroActions.childCount > 0) {
            focusRows.add(Pair("hero", heroActions))
            hero.addView(heroActions)
        }
        parent.addView(
            heroFrame,
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, ui.dp(14), 0, ui.dp(4)) },
        )

        if (snapshot.sections.none { it.id == "continue" }) renderServices(snapshot)
        for (section in snapshot.sections.sortedBy { if (it.id == "continue") 0 else 1 }) {
            title(
                if (section.id == "continue") context.getString(R.string.continue_watching)
                else ui.serviceTitle(section.id)
            )
            val sectionModule = snapshot.modules.firstOrNull { it.id == section.id }
            val isReady =
                sectionModule == null ||
                    sectionModule.state == "READY" ||
                    sectionModule.state == "HEALTHY"
            val hasCatalog =
                CatalogUiPolicy.supportsCatalog(section.id) &&
                    isReady &&
                    !CatalogUiPolicy.isReceiverOnly(section.id)
            val keys =
                section.items.map { "item:" + section.id + ":" + it.id } +
                    if (hasCatalog) listOf("all:" + section.id) else emptyList()
            val line =
                WindowedRow(context, keys, HomeBudget.rowCapacity(heapMb, physicalMb, tv)) { index
                    ->
                    if (index == section.items.size) {
                        ui.button(R.string.view_all) {
                            if (CatalogUiPolicy.supportsCatalog(section.id)) {
                                actions.catalog(section.id)
                            }
                        }
                    } else {
                        val item = section.items[index]
                        val card =
                            FrameLayout(context).apply {
                                tag = "item:" + section.id + ":" + item.id
                            }
                        val cardContent = ui.column()
                        if (item.imageUrl.isNotEmpty()) {
                            card.addView(
                                artImage(item.imageUrl, false),
                                FrameLayout.LayoutParams(-1, -1),
                            )
                            cardContent.setBackgroundDrawable(
                                GradientDrawable(
                                    GradientDrawable.Orientation.BOTTOM_TOP,
                                    intArrayOf(
                                        Color.argb(240, 10, 15, 16),
                                        Color.argb(90, 10, 15, 16),
                                    ),
                                )
                            )
                        }
                        card.addView(cardContent, FrameLayout.LayoutParams(-1, -1))
                        card.setPadding(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                        cardContent.setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6))
                        cardContent.gravity = Gravity.BOTTOM
                        card.setBackgroundDrawable(
                            ui.focusBackground(ui.providerAccent(item.provider))
                        )
                        card.isFocusable = true
                        card.isClickable = true
                        cardContent.addView(
                            ui.text(
                                ui.serviceTitle(item.provider),
                                12f,
                                ui.providerAccent(item.provider),
                            )
                        )
                        cardContent.addView(
                            ui.text(item.title, 19f).apply {
                                maxLines = 2
                                typeface = ui.bold
                            }
                        )
                        if (item.subtitle.isNotEmpty())
                            cardContent.addView(
                                ui.text(item.subtitle, 12f, ui.muted).apply { maxLines = 1 }
                            )
                        if (item.positionMs > 0)
                            cardContent.addView(
                                ui.text(
                                    context.getString(
                                        R.string.resume_at,
                                        ui.formatTime(item.positionMs),
                                    ),
                                    12f,
                                    ui.muted,
                                )
                            )
                        if (item.positionMs > 0 && item.durationMs > 0) {
                            cardContent.addView(ui.progress(item.positionMs, item.durationMs))
                        }
                        card.setOnClickListener { actions.details(item) }
                        card.layoutParams =
                            LinearLayout.LayoutParams(ui.dp(230), ui.dp(130)).apply {
                                setMargins(ui.dp(3), ui.dp(3), ui.dp(8), ui.dp(3))
                            }
                        card
                    }
                }
            parent.addView(
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(line)
                }
            )
            focusRows.add(Pair("section:" + section.id, line))
            if (!tv && keys.size > line.capacity)
                parent.addView(
                    ui.row().apply {
                        addView(
                            ui.button(R.string.previous_page) {
                                line.page(false)
                                (line.parent as? HorizontalScrollView)?.scrollTo(0, 0)
                            }
                        )
                        addView(
                            ui.button(R.string.next_page) {
                                line.page(true)
                                (line.parent as? HorizontalScrollView)?.scrollTo(0, 0)
                            }
                        )
                    }
                )
            if (section.id == "continue") renderServices(snapshot)
        }
    }

    private fun renderProviderScope(
        parent: LinearLayout,
        snapshot: HomeSnapshot,
        provider: String,
        tv: Boolean,
    ) {
        val featured = snapshot.hero
        if (featured != null && (featured.provider == provider || featured.provider.isEmpty())) {
            val heroFrame = FrameLayout(context).apply { minimumHeight = ui.dp(210) }
            val hero =
                ui.column().apply {
                    setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16))
                    setBackgroundDrawable(
                        GradientDrawable(
                                GradientDrawable.Orientation.LEFT_RIGHT,
                                intArrayOf(
                                    Color.rgb(12, 27, 24),
                                    Color.rgb(14, 25, 27),
                                    ui.background,
                                ),
                            )
                            .apply { cornerRadius = ui.dp(10).toFloat() }
                    )
                }
            if (featured.imageUrl.isNotEmpty()) {
                heroFrame.addView(
                    artImage(featured.imageUrl, true),
                    FrameLayout.LayoutParams(-1, -1),
                )
                hero.setBackgroundDrawable(
                    GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(Color.argb(240, 10, 15, 16), Color.argb(100, 10, 15, 16)),
                    )
                )
            }
            heroFrame.addView(hero, FrameLayout.LayoutParams(-1, -2))
            hero.addView(ui.text(ui.serviceTitle(provider), 12f, ui.providerAccent(provider)))
            hero.addView(
                ui.text(featured.title, 26f).apply {
                    typeface = ui.bold
                    maxLines = 2
                }
            )
            if (featured.subtitle.isNotEmpty()) {
                hero.addView(ui.text(featured.subtitle, 13f, ui.muted).apply { maxLines = 1 })
            }
            val heroActions = ui.row().apply { setPadding(0, ui.dp(8), 0, 0) }
            if (featured.playable) {
                heroActions.addView(
                    ui.primary(
                        if (featured.positionMs > 0) R.string.resume_content else R.string.play
                    ) {
                        actions.play(featured)
                    }
                )
            }
            heroActions.addView(ui.button(R.string.more_info) { actions.details(featured) })
            hero.addView(heroActions)
            parent.addView(
                heroFrame,
                LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, ui.dp(6), 0, ui.dp(8)) },
            )
            focusRows.add(Pair("$provider:hero", heroActions))
        }

        val serviceState = snapshot.modules.firstOrNull { it.id == provider }?.state
        val canBrowse = CatalogUiPolicy.canBrowseLibrary(provider, serviceState)

        val sectionsWithItems = snapshot.sections.filter { it.items.isNotEmpty() }
        if (sectionsWithItems.isEmpty() && featured == null) {
            val emptyState = ProviderEmptyStateView(context, ui, provider, actions, serviceState)
            parent.addView(
                emptyState,
                LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, ui.dp(14), 0, ui.dp(14)) },
            )
            emptyState.attachActions(focusRows)
            return
        }

        for (section in sectionsWithItems) {
            title(if (section.id == provider) ui.serviceTitle(provider) else section.id)
            val keys =
                section.items.map { "item:$provider:${section.id}:${it.id}" } +
                    if (canBrowse) listOf("all:$provider:${section.id}") else emptyList()
            val line =
                WindowedRow(context, keys, HomeBudget.rowCapacity(heapMb, physicalMb, tv)) { index
                    ->
                    if (index == section.items.size) {
                        ui.button(R.string.view_all) {
                            if (CatalogUiPolicy.supportsCatalog(provider)) {
                                actions.catalog(provider)
                            }
                        }
                    } else {
                        val item = section.items[index]
                        val card =
                            FrameLayout(context).apply {
                                tag = "item:$provider:${section.id}:${item.id}"
                            }
                        val cardContent = ui.column()
                        if (item.imageUrl.isNotEmpty()) {
                            card.addView(
                                artImage(item.imageUrl, false),
                                FrameLayout.LayoutParams(-1, -1),
                            )
                            cardContent.setBackgroundDrawable(
                                GradientDrawable(
                                    GradientDrawable.Orientation.BOTTOM_TOP,
                                    intArrayOf(
                                        Color.argb(240, 10, 15, 16),
                                        Color.argb(90, 10, 15, 16),
                                    ),
                                )
                            )
                        }
                        card.addView(cardContent, FrameLayout.LayoutParams(-1, -1))
                        card.setPadding(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                        cardContent.setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6))
                        cardContent.gravity = Gravity.BOTTOM
                        card.setBackgroundDrawable(ui.focusBackground(ui.providerAccent(provider)))
                        card.isFocusable = true
                        card.isClickable = true
                        cardContent.addView(
                            ui.text(
                                ui.serviceTitle(item.provider),
                                12f,
                                ui.providerAccent(item.provider),
                            )
                        )
                        cardContent.addView(
                            ui.text(item.title, 19f).apply {
                                maxLines = 2
                                typeface = ui.bold
                            }
                        )
                        if (item.subtitle.isNotEmpty())
                            cardContent.addView(
                                ui.text(item.subtitle, 12f, ui.muted).apply { maxLines = 1 }
                            )
                        if (item.positionMs > 0 && item.durationMs > 0)
                            cardContent.addView(ui.progress(item.positionMs, item.durationMs))
                        card.setOnClickListener { actions.details(item) }
                        card.layoutParams =
                            LinearLayout.LayoutParams(ui.dp(230), ui.dp(130)).apply {
                                setMargins(ui.dp(3), ui.dp(3), ui.dp(8), ui.dp(3))
                            }
                        card
                    }
                }
            parent.addView(
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(line)
                }
            )
            focusRows.add(Pair("section:$provider:${section.id}", line))
        }
    }

    private fun renderServices(snapshot: HomeSnapshot) {
        title(context.getString(R.string.apps_content))
        val services = horizontal(content, "services")
        for (module in snapshot.modules) {
            val id = module.id
            val tile =
                ui.column().apply {
                    tag = "service:$id"
                    isFocusable = true
                    isClickable = true
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6))
                    setBackgroundDrawable(ui.serviceCardBackground(id))
                    addView(
                        ui.row().apply {
                            addView(
                                ServiceMarkView(context, id, ui.providerAccent(id)),
                                LinearLayout.LayoutParams(
                                        ui.dp(if (id == "plex") 78 else 42),
                                        ui.dp(42),
                                    )
                                    .apply { rightMargin = ui.dp(8) },
                            )
                            addView(
                                ui.column().apply {
                                    if (id != "plex")
                                        addView(
                                            ui.text(ui.serviceTitle(id), 16f).apply {
                                                typeface = ui.bold
                                                setSingleLine(true)
                                            }
                                        )
                                    addView(
                                        ui.text(
                                            if (id == "rebrowser" && module.state == "STARTING")
                                                context.getString(R.string.opens_on_demand)
                                            else ui.localizedServiceState(id, module.state),
                                            11f,
                                            ui.muted,
                                        )
                                    )
                                }
                            )
                        }
                    )
                    setOnClickListener {
                        if (id == "android_mirror") actions.mirrorReceiver()
                        else if (module.state == "DISABLED") actions.providers()
                        else actions.navigate(id, "")
                    }
                }
            services.addView(
                tile,
                LinearLayout.LayoutParams(ui.dp(165), ui.dp(76)).apply {
                    setMargins(ui.dp(3), ui.dp(3), ui.dp(6), ui.dp(3))
                },
            )
        }
        if (snapshot.modules.isEmpty())
            services.addView(ui.button(R.string.connect_gateway) { actions.pair() })
    }

    private fun artImage(path: String, hero: Boolean): ImageView =
        ArtworkImageView(context, decoder).apply { bind(artwork, path, hero) }
}
