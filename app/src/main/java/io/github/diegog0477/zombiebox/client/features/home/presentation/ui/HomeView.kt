package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.RemoteFocus
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.core.ui.WindowedRow
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.ui.ArtworkImageView
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel.ArtworkViewModel
import io.github.diegog0477.zombiebox.client.features.diagnostics.platform.HardwareMemory
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeBudget
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeScope
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeSnapshot

data class HomeActions(
    val navigate: (String, String) -> Unit,
    val play: (MediaItem) -> Unit,
    val details: (MediaItem) -> Unit,
    val settings: () -> Unit,
    val search: () -> Unit,
    val searchProvider: (String) -> Unit,
    val devices: () -> Unit,
    val youtubeReceiver: () -> Unit,
    val catalog: (String) -> Unit,
    val mirrorReceiver: () -> Unit,
    val providers: () -> Unit,
    val pair: () -> Unit,
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
    private val navigation = LinkedHashMap<String, Button>()
    private var heroStatus: TextView? = null
    private val heapMb =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
    private val physicalMb = HardwareMemory.physicalMb()

    fun status(loading: Boolean, failed: Boolean) {
        heroStatus?.apply {
            visibility = if (loading || failed) VISIBLE else GONE
            setText(if (loading) R.string.home_refreshing else R.string.home_stale)
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
    ) {
        this.scope = scope
        focusRows.clear()
        content = this
        removeAllViews()
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val header = ui.row()
        val navigationFrame = ui.column()
        header.addView(navigationFrame, LinearLayout.LayoutParams(0, -2, 1f))
        if (widthDp >= 900f) header.addView(HomeClock(context, ui))
        content.addView(header, LinearLayout.LayoutParams(-1, -2))
        val nav = horizontal(navigationFrame, "navigation")
        nav.addView(
            ui.text(context.getString(R.string.brand), 25f).apply {
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
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, ui.dp(24), 0)
            }
        )
        navigation.clear()
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
        nav.addView(ui.action(context.getString(R.string.devices), ui.green, actions.devices))
        nav.addView(headerAction(R.string.search, false, actions.search))
        nav.addView(headerAction(R.string.settings, true, actions.settings))
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
                typeface = Typeface.DEFAULT_BOLD
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
                        ?: context.getString(R.string.welcome_detail),
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
        } else heroActions.addView(ui.button(R.string.configure_services) { actions.settings() })
        if (scope.provider == "youtube") {
            heroActions.addView(
                ui.action(
                    context.getString(
                        R.string.search_provider,
                        context.getString(R.string.youtube),
                    ),
                    ui.providerAccent("youtube"),
                ) {
                    actions.searchProvider("youtube")
                }
            )
            heroActions.addView(ui.button(R.string.view_all) { actions.catalog("youtube") })
            heroActions.addView(ui.button(R.string.youtube_receiver) { actions.youtubeReceiver() })
        }
        focusRows.add(Pair("hero", heroActions))
        hero.addView(heroActions)
        heroStatus = ui.text("", 12f, ui.muted).apply { visibility = GONE }
        hero.addView(heroStatus)
        content.addView(
            heroFrame,
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, ui.dp(14), 0, ui.dp(4)) },
        )
        if (tv && widthDp >= 900f) {
            val body = ui.row().apply { gravity = Gravity.TOP }
            val rows = ui.column()
            body.addView(rows, LinearLayout.LayoutParams(0, -2, 1f))
            body.addView(
                HomeStatusPanel(context, ui),
                LinearLayout.LayoutParams(ui.dp(200), -2).apply {
                    leftMargin = ui.dp(18)
                    topMargin = ui.dp(20)
                },
            )
            addView(body, LinearLayout.LayoutParams(-1, -2))
            content = rows
        }
        if (snapshot.sections.none { it.id == "continue" }) renderServices(snapshot)
        for (section in snapshot.sections.sortedBy { if (it.id == "continue") 0 else 1 }) {
            title(
                if (section.id == "continue") context.getString(R.string.continue_watching)
                else ui.serviceTitle(section.id)
            )
            val keys =
                section.items.map { "item:" + section.id + ":" + it.id } +
                    if (section.id != "continue") listOf("all:" + section.id) else emptyList()
            val line =
                WindowedRow(context, keys, HomeBudget.rowCapacity(heapMb, physicalMb, tv)) { index
                    ->
                    if (index == section.items.size) {
                        ui.button(R.string.view_all) { actions.catalog(section.id) }
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
                                typeface = Typeface.DEFAULT_BOLD
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
            content.addView(
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(line)
                }
            )
            focusRows.add(Pair("section:" + section.id, line))
            if (!tv && keys.size > line.capacity)
                content.addView(
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
        content.addView(
            ui.text(
                context.getString(
                    if (tv) R.string.docked_description else R.string.handheld_description
                ),
                14f,
                ui.muted,
            )
        )
        focus.rebuild(focusRows + Pair("transport", bottom), !full)
    }

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
                    setBackgroundDrawable(ui.focusBackground(ui.providerAccent(id)))
                    addView(
                        ui.row().apply {
                            addView(
                                ServiceMarkView(context, id, ui.providerAccent(id)),
                                LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)),
                            )
                            addView(
                                ui.column().apply {
                                    addView(
                                        ui.text(ui.serviceTitle(id), 16f).apply {
                                            typeface = Typeface.DEFAULT_BOLD
                                            setSingleLine(true)
                                        }
                                    )
                                    addView(
                                        ui.text(
                                            if (id == "rebrowser" && module.state == "STARTING")
                                                context.getString(R.string.opens_on_demand)
                                            else ui.localizedState(module.state),
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
