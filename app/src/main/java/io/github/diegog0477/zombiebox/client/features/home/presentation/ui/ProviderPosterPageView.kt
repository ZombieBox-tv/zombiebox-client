package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.core.ui.WindowedRow
import io.github.diegog0477.zombiebox.client.features.artwork.platform.ArtworkDecoder
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.ui.ArtworkImageView
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel.ArtworkViewModel
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui.CatalogUiPolicy
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeBudget
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeSnapshot

/**
 * Dedicated TV app-tab presentation for Plex, Stremio, and Jellyfin. Presents film/series artwork
 * as portrait poster rails or multi-row grids using imageUrl as received, with provider accent,
 * Barlow typography, truthful empty states, and deterministic D-pad DOWN navigation.
 */
class ProviderPosterPageView(
    private val context: Context,
    private val ui: TvWidgets,
    private val artwork: ArtworkViewModel,
    private val decoder: ArtworkDecoder,
    private val actions: HomeActions,
    private val provider: String,
) {

    fun render(
        parent: LinearLayout,
        snapshot: HomeSnapshot,
        isDockedLandscape: Boolean,
        widthDp: Float,
        heapMb: Int,
        physicalMb: Int,
        tv: Boolean,
        focusRows: ArrayList<Pair<String, ViewGroup>>,
    ) {
        val accent = ui.providerAccent(provider)

        val serviceState = snapshot.modules.firstOrNull { it.id == provider }?.state

        val isReady = CatalogUiPolicy.isReady(serviceState)
        val canSearch = isReady && CatalogUiPolicy.supportsSearch(provider)
        val canCatalog = CatalogUiPolicy.canBrowseLibrary(provider, serviceState)

        // Keep discovery in the app header. Catalog browsing is available after the posters,
        // while service configuration appears only in a disabled service's empty state.
        val leadRow = ui.row().apply { setPadding(0, ui.dp(10), 0, ui.dp(8)) }
        leadRow.addView(
            ui.text(ui.serviceTitle(provider), 22f).apply { typeface = ui.bold },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        if (canSearch) {
            val searchBtn =
                ImageButton(context).apply {
                    contentDescription =
                        context.getString(R.string.search_provider, ui.serviceTitle(provider))
                    tag = "$provider:action:search"
                    setImageDrawable(HeaderIcon(false, Color.WHITE))
                    setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
                    setBackgroundDrawable(ui.focusBackground(accent))
                    isFocusable = true
                    setOnClickListener { actions.searchProvider(provider) }
                    layoutParams = LinearLayout.LayoutParams(ui.dp(46), ui.dp(46))
                }
            leadRow.addView(searchBtn)
            focusRows.add(Pair("$provider:actions", leadRow))
        }

        parent.addView(leadRow, LinearLayout.LayoutParams(-1, -2))

        // 2. Optional featured hero banner
        val featured = snapshot.hero?.takeIf { it.provider == provider || it.provider.isEmpty() }
        if (featured != null) renderFeaturedHero(parent, featured, focusRows)

        // 3. Filter sections with items
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

        if (sectionsWithItems.size > 1) {
            // Render multiple sections as portrait poster rails (e.g. Continue Watching + Popular)
            for (section in sectionsWithItems) {
                renderPosterRail(
                    parent = parent,
                    sectionId = section.id,
                    items = section.items,
                    heapMb = heapMb,
                    physicalMb = physicalMb,
                    tv = tv,
                    focusRows = focusRows,
                    canCatalog = canCatalog,
                )
            }
        } else if (sectionsWithItems.size == 1) {
            // Render single section as an adaptive multi-row portrait poster grid
            val singleSection = sectionsWithItems.first()
            renderPosterGrid(
                parent = parent,
                sectionId = singleSection.id,
                items = singleSection.items,
                isDockedLandscape = isDockedLandscape,
                widthDp = widthDp,
                focusRows = focusRows,
                canCatalog = canCatalog,
            )
        }
    }

    private fun renderFeaturedHero(
        parent: LinearLayout,
        featured: MediaItem,
        focusRows: ArrayList<Pair<String, ViewGroup>>,
    ) {
        val heroFrame = FrameLayout(context).apply { minimumHeight = ui.dp(210) }
        val heroLayout =
            ui.column().apply {
                setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16))
                setBackgroundDrawable(
                    GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(
                                Color.argb(245, 14, 18, 20),
                                Color.argb(120, 14, 18, 20),
                                ui.background,
                            ),
                        )
                        .apply { cornerRadius = ui.dp(10).toFloat() }
                )
            }

        if (featured.imageUrl.isNotEmpty()) {
            val bgArt =
                ArtworkImageView(context, decoder).apply { bind(artwork, featured.imageUrl, true) }
            heroFrame.addView(bgArt, FrameLayout.LayoutParams(-1, -1))
        }

        heroFrame.addView(heroLayout, FrameLayout.LayoutParams(-1, -2))

        heroLayout.addView(ui.text(ui.serviceTitle(provider), 12f, ui.providerAccent(provider)))
        heroLayout.addView(
            ui.text(featured.title, 26f).apply {
                typeface = ui.bold
                maxLines = 2
            }
        )
        if (featured.subtitle.isNotEmpty()) {
            heroLayout.addView(ui.text(featured.subtitle, 13f, ui.muted).apply { maxLines = 1 })
        }
        if (featured.description.isNotEmpty()) {
            heroLayout.addView(ui.text(featured.description, 14f, ui.muted).apply { maxLines = 2 })
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
        heroLayout.addView(heroActions)

        parent.addView(
            heroFrame,
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, ui.dp(6), 0, ui.dp(8)) },
        )
        focusRows.add(Pair("$provider:hero", heroActions))
    }

    private fun renderPosterGrid(
        parent: LinearLayout,
        sectionId: String,
        items: List<MediaItem>,
        isDockedLandscape: Boolean,
        widthDp: Float,
        focusRows: ArrayList<Pair<String, ViewGroup>>,
        canCatalog: Boolean,
    ) {
        parent.addView(
            ui.text(sectionTitle(sectionId), 18f).apply {
                typeface = ui.bold
                setPadding(ui.dp(4), ui.dp(12), 0, ui.dp(6))
            }
        )

        // Portrait columns: 4 for docked landscape TV, 3 for tablet, 2 for phone
        val columns =
            when {
                isDockedLandscape -> 4
                widthDp >= 600f -> 3
                else -> 2
            }

        val rows = items.chunked(columns)
        for ((rowIndex, rowItems) in rows.withIndex()) {
            val rowContainer =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams =
                        LinearLayout.LayoutParams(-1, -2).apply {
                            setMargins(0, ui.dp(3), 0, ui.dp(4))
                        }
                }

            for (item in rowItems) {
                val card = createGridPosterCard(item, sectionId)
                rowContainer.addView(card)
            }

            if (rowItems.size < columns) {
                for (i in 0 until (columns - rowItems.size)) {
                    val spacer =
                        View(context).apply {
                            layoutParams =
                                LinearLayout.LayoutParams(0, 0, 1f).apply {
                                    setMargins(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(5))
                                }
                        }
                    rowContainer.addView(spacer)
                }
            }

            parent.addView(rowContainer)
            focusRows.add(Pair("$provider:row:$rowIndex", rowContainer))
        }

        // View All Catalog Footer Button
        if (canCatalog) {
            val footerRow = ui.row().apply { setPadding(ui.dp(4), ui.dp(8), 0, ui.dp(12)) }
            val catalogFooterBtn =
                ui.button(R.string.view_all) {
                        if (CatalogUiPolicy.supportsCatalog(provider)) actions.catalog(provider)
                    }
                    .apply { tag = "$provider:footer:catalog" }
            footerRow.addView(catalogFooterBtn)
            parent.addView(footerRow)
            focusRows.add(Pair("$provider:footer", footerRow))
        }
    }

    private fun createGridPosterCard(item: MediaItem, sectionId: String): View {
        val card =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isClickable = true
                tag = "item:$provider:$sectionId:${item.id}"
                setBackgroundDrawable(ui.focusBackground(ui.providerAccent(provider)))
                setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(5))
                layoutParams =
                    LinearLayout.LayoutParams(0, -2, 1f).apply {
                        setMargins(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(5))
                    }
            }

        // 2:3 Portrait Aspect Ratio container
        val posterFrame =
            AspectRatioFrameLayout(context, 2f / 3f).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
        val artView =
            ArtworkImageView(context, decoder).apply { bind(artwork, item.imageUrl, false) }
        posterFrame.addView(artView, FrameLayout.LayoutParams(-1, -1))

        if (item.positionMs > 0 && item.durationMs > 0) {
            val progress = ui.progress(item.positionMs, item.durationMs)
            posterFrame.addView(progress, FrameLayout.LayoutParams(-1, ui.dp(3), Gravity.BOTTOM))
        }
        card.addView(posterFrame)

        // Metadata beneath poster
        val metaColumn = ui.column().apply { setPadding(ui.dp(2), ui.dp(5), ui.dp(2), ui.dp(2)) }
        val titleView =
            ui.text(item.title, 13f).apply {
                maxLines = 1
                typeface = ui.bold
                ellipsize = TextUtils.TruncateAt.END
            }
        metaColumn.addView(titleView)

        val subtitleText = item.subtitle.ifEmpty { ui.serviceTitle(item.provider) }
        val subtitleView =
            ui.text(subtitleText, 11f, ui.muted).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        metaColumn.addView(subtitleView)

        card.addView(metaColumn)
        card.setOnClickListener { actions.details(item) }
        return card
    }

    private fun renderPosterRail(
        parent: LinearLayout,
        sectionId: String,
        items: List<MediaItem>,
        heapMb: Int,
        physicalMb: Int,
        tv: Boolean,
        focusRows: ArrayList<Pair<String, ViewGroup>>,
        canCatalog: Boolean,
    ) {
        parent.addView(
            ui.text(sectionTitle(sectionId), 18f).apply {
                typeface = ui.bold
                setPadding(ui.dp(4), ui.dp(14), 0, ui.dp(8))
            }
        )

        val keys =
            items.map { "item:$provider:$sectionId:${it.id}" } +
                if (canCatalog) listOf("all:$provider:$sectionId") else emptyList()
        val capacity = HomeBudget.rowCapacity(heapMb, physicalMb, tv)

        val line =
            WindowedRow(context, keys, capacity) { index ->
                if (index == items.size) {
                    ui.button(R.string.view_all) {
                            if (CatalogUiPolicy.supportsCatalog(provider)) actions.catalog(provider)
                        }
                        .apply { tag = "all:$provider:$sectionId" }
                } else {
                    val item = items[index]
                    createRailPosterCard(item, sectionId)
                }
            }

        val scroll =
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(line)
            }
        parent.addView(scroll)
        focusRows.add(Pair("section:$provider:$sectionId", line))

        if (!tv && keys.size > line.capacity) {
            parent.addView(
                ui.row().apply {
                    addView(
                        ui.button(R.string.previous_page) {
                            line.page(false)
                            scroll.scrollTo(0, 0)
                        }
                    )
                    addView(
                        ui.button(R.string.next_page) {
                            line.page(true)
                            scroll.scrollTo(0, 0)
                        }
                    )
                }
            )
        }
    }

    private fun createRailPosterCard(item: MediaItem, sectionId: String): View {
        val card =
            FrameLayout(context).apply {
                tag = "item:$provider:$sectionId:${item.id}"
                isFocusable = true
                isClickable = true
                setBackgroundDrawable(ui.focusBackground(ui.providerAccent(provider)))
                setPadding(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                layoutParams =
                    LinearLayout.LayoutParams(ui.dp(135), ui.dp(205)).apply {
                        setMargins(ui.dp(3), ui.dp(3), ui.dp(6), ui.dp(3))
                    }
            }

        if (item.imageUrl.isNotEmpty()) {
            val art =
                ArtworkImageView(context, decoder).apply { bind(artwork, item.imageUrl, false) }
            card.addView(art, FrameLayout.LayoutParams(-1, -1))
        }

        val cardContent =
            ui.column().apply {
                setPadding(ui.dp(6), ui.dp(4), ui.dp(6), ui.dp(6))
                gravity = Gravity.BOTTOM
                setBackgroundDrawable(
                    GradientDrawable(
                        GradientDrawable.Orientation.BOTTOM_TOP,
                        intArrayOf(Color.argb(240, 10, 15, 16), Color.argb(40, 10, 15, 16)),
                    )
                )
            }
        card.addView(cardContent, FrameLayout.LayoutParams(-1, -1))

        val titleView =
            ui.text(item.title, 13f).apply {
                maxLines = 1
                typeface = ui.bold
                ellipsize = TextUtils.TruncateAt.END
            }
        cardContent.addView(titleView)

        if (item.subtitle.isNotEmpty()) {
            val subView =
                ui.text(item.subtitle, 11f, ui.muted).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
            cardContent.addView(subView)
        }

        if (item.positionMs > 0 && item.durationMs > 0) {
            cardContent.addView(ui.progress(item.positionMs, item.durationMs))
        } else if (item.positionMs > 0) {
            cardContent.addView(
                ui.text(
                    context.getString(R.string.resume_at, ui.formatTime(item.positionMs)),
                    11f,
                    ui.muted,
                )
            )
        }

        card.setOnClickListener { actions.details(item) }
        return card
    }

    private fun sectionTitle(sectionId: String): String =
        when (sectionId) {
            "continue" -> context.getString(R.string.continue_watching)
            provider -> ui.serviceTitle(provider)
            else ->
                sectionId.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                }
        }
}
