package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogScreen
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogViewport

enum class CatalogCardType {
    LANDSCAPE_16_9,
    PORTRAIT_POSTER,
    CHANNEL,
    FOLDER_FALLBACK,
}

/**
 * TV catalog grid with bounded recycling, adaptive column layouts for 16:9 videos and portrait
 * posters, and non-obscuring focus outlines.
 */
@Suppress("DEPRECATION")
class CatalogListView(
    context: Context,
    private val screen: CatalogScreen,
    private val accent: Int,
    private val artwork: (MediaItem) -> View? = { null },
) : GridView(context) {

    constructor(
        context: Context,
        screen: CatalogScreen,
        accent: Int,
    ) : this(context, screen, accent, { null })

    private val ui = TvWidgets(context)
    private val cardType = detectCardType()
    private val cardSpacing = ui.dp(12)
    private var currentColumns = 0

    init {
        currentColumns = calculateColumns(cardType)
        numColumns = currentColumns
        stretchMode = STRETCH_COLUMN_WIDTH
        horizontalSpacing = cardSpacing
        verticalSpacing = cardSpacing
        setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
        clipToPadding = false

        val selectorDrawable =
            StateListDrawable().apply {
                val focusBorder =
                    GradientDrawable().apply {
                        setColor(Color.TRANSPARENT)
                        cornerRadius = ui.dp(8).toFloat()
                        setStroke(ui.dp(3), accent)
                    }
                addState(intArrayOf(android.R.attr.state_focused), focusBorder)
                addState(intArrayOf(android.R.attr.state_pressed), focusBorder)
                addState(intArrayOf(android.R.attr.state_selected), focusBorder)
                addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
            }
        selector = selectorDrawable
        isDrawSelectorOnTop = true

        adapter =
            object : BaseAdapter() {
                override fun getCount() = screen.page.items.size

                override fun getItem(position: Int) = screen.page.items[position]

                override fun getItemId(position: Int) = position.toLong()

                override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                    val card =
                        (recycled as? CatalogCardView) ?: CatalogCardView(context, ui, accent)
                    val item = getItem(position)
                    val cardWidth = computeCardWidth()
                    card.bind(item, cardType, cardWidth, artwork)
                    return card
                }
            }
    }

    private fun detectCardType(): CatalogCardType {
        val provider = screen.location.provider
        val items = screen.page.items
        if (provider == "iptv" || (items.isNotEmpty() && items.all { it.kind == "channel" })) {
            return CatalogCardType.CHANNEL
        }
        if (provider in listOf("plex", "stremio", "jellyfin")) {
            if (items.isNotEmpty() && items.all { it.kind == "episode" }) {
                return CatalogCardType.LANDSCAPE_16_9
            }
            if (
                items.any {
                    it.kind in listOf("movie", "series", "show") ||
                        (it.kind.isEmpty() && it.imageUrl.isNotEmpty())
                }
            ) {
                return CatalogCardType.PORTRAIT_POSTER
            }
        }
        if (provider == "youtube" || items.any { it.kind == "video" || it.kind == "episode" }) {
            return CatalogCardType.LANDSCAPE_16_9
        }
        if (items.any { it.imageUrl.isNotEmpty() }) {
            return CatalogCardType.LANDSCAPE_16_9
        }
        return CatalogCardType.FOLDER_FALLBACK
    }

    private fun calculateColumns(type: CatalogCardType): Int {
        val displayMetrics = context.resources.displayMetrics
        val widthDp = displayMetrics.widthPixels / displayMetrics.density
        return when (type) {
            CatalogCardType.PORTRAIT_POSTER -> {
                when {
                    widthDp >= 1050f -> 6
                    widthDp >= 800f -> 5
                    widthDp >= 550f -> 4
                    else -> 3
                }
            }
            CatalogCardType.LANDSCAPE_16_9 -> {
                when {
                    widthDp >= 1050f -> 4
                    widthDp >= 750f -> 3
                    else -> 2
                }
            }
            CatalogCardType.CHANNEL,
            CatalogCardType.FOLDER_FALLBACK -> {
                when {
                    widthDp >= 1050f -> 4
                    widthDp >= 750f -> 3
                    else -> 2
                }
            }
        }
    }

    private fun computeCardWidth(): Int {
        val available =
            (width - paddingLeft - paddingRight).takeIf { it > 0 }
                ?: (context.resources.displayMetrics.widthPixels - ui.dp(48))
        val totalSpacing = cardSpacing * (currentColumns - 1)
        return ((available - totalSpacing) / currentColumns.coerceAtLeast(1)).coerceAtLeast(
            ui.dp(120)
        )
    }

    fun viewport(selected: Int = selectedItemPosition): CatalogViewport {
        val first = firstVisiblePosition
        return CatalogViewport(
            screen.page.items.getOrNull(selected)?.id ?: screen.viewport.selectedId,
            screen.page.items.getOrNull(first)?.id ?: "",
            getChildAt(0)?.top ?: 0,
            if (selected >= first && selected < first + childCount)
                getChildAt(selected - first)?.top
            else null,
        )
    }

    fun restoreViewport() {
        val viewport = screen.viewport
        val selected = screen.page.items.indexOfFirst { it.id == viewport.selectedId }
        val first =
            screen.page.items.indexOfFirst { it.id == viewport.firstVisibleId }.coerceAtLeast(0)
        requestFocus()
        if (selected >= 0) {
            setSelection(selected)
        } else if (first >= 0 && first < screen.page.items.size) {
            setSelection(first)
        } else if (screen.page.items.isNotEmpty()) {
            setSelection(0)
        }
    }
}
