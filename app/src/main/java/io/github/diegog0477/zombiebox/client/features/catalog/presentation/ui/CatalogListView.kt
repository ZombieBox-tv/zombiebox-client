package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogScreen
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogViewport

/** API-9 list recycling keeps only visible rows alive; no off-screen artwork allocation. */
@Suppress("DEPRECATION")
class CatalogListView(context: Context, private val screen: CatalogScreen, accent: Int) :
    ListView(context) {
    private val ui = TvWidgets(context)

    init {
        dividerHeight = ui.dp(3)
        itemsCanFocus = false
        setSelector(
            StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), ui.box(ui.panel, accent))
                addState(intArrayOf(android.R.attr.state_focused), ui.box(ui.panel, accent))
                addState(intArrayOf(), ui.box(Color.TRANSPARENT))
            }
        )
        setDrawSelectorOnTop(true)
        adapter =
            object : BaseAdapter() {
                override fun getCount() = screen.page.items.size

                override fun getItem(position: Int) = screen.page.items[position]

                override fun getItemId(position: Int) = position.toLong()

                override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                    val row =
                        recycled as? LinearLayout
                            ?: ui.column().apply {
                                setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4))
                                minimumHeight = ui.dp(56)
                                addView(ui.text("", 17f).apply { setSingleLine(true) })
                                addView(ui.text("", 12f, ui.muted).apply { setSingleLine(true) })
                            }
                    val item = getItem(position)
                    (row.getChildAt(0) as TextView).text =
                        if (item.favorite) "★ ${item.title}" else item.title
                    (row.getChildAt(1) as TextView).text =
                        if (item.browseId.isNotEmpty()) context.getString(R.string.catalog_folder)
                        else item.subtitle
                    return row
                }
            }
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
        if (selected >= 0 && viewport.selectedTop != null)
            setSelectionFromTop(selected, viewport.selectedTop)
        else setSelectionFromTop(first, viewport.firstTop)
    }
}
