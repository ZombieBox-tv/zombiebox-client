package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogViewport
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.SearchState
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.viewmodel.SearchViewModel

/** Recycled results with provider labels and accents; query entry never takes result focus. */
class SearchDialog(private val activity: Activity, private val model: SearchViewModel) {
    fun show(
        draft: String,
        selected: (MediaItem) -> Unit,
        capture: (() -> String) -> Unit,
        closed: () -> Unit,
    ): AlertDialog {
        val ui = TvWidgets(activity)
        val input =
            EditText(activity).apply {
                setSingleLine(true)
                setTextColor(android.graphics.Color.WHITE)
                setHintTextColor(ui.muted)
                setBackgroundDrawable(ui.box(ui.panel, ui.muted))
                setPadding(ui.dp(12), 0, ui.dp(12), 0)
                filters = arrayOf(android.text.InputFilter.LengthFilter(100))
                setText(draft)
                setHint(R.string.search_all_hint)
            }
        val status = ui.text("", 14f, ui.muted)
        val list = ListView(activity).apply { itemsCanFocus = false }
        var items = emptyList<MediaItem>()
        var navigating = false
        fun remember(selectedIndex: Int = list.selectedItemPosition) {
            if (items.isEmpty()) return
            val first = list.firstVisiblePosition
            model.rememberViewport(
                CatalogViewport(
                    items.getOrNull(selectedIndex)?.id ?: model.bookmark.viewport.selectedId,
                    items.getOrNull(first)?.id ?: "",
                    list.getChildAt(0)?.top ?: 0,
                    if (selectedIndex >= first && selectedIndex < first + list.childCount)
                        list.getChildAt(selectedIndex - first)?.top
                    else null,
                ),
                list.hasFocus() || navigating,
            )
        }
        val adapter =
            object : BaseAdapter() {
                override fun getCount() = items.size

                override fun getItem(position: Int) = items[position]

                override fun getItemId(position: Int) = position.toLong()

                override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                    val row =
                        recycled as? LinearLayout
                            ?: ui.column().apply {
                                setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6))
                                minimumHeight = ui.dp(56)
                                addView(ui.text("", 17f).apply { setSingleLine(true) })
                                addView(ui.text("", 12f).apply { setSingleLine(true) })
                            }
                    val item = items[position]
                    (row.getChildAt(0) as TextView).text = item.title
                    (row.getChildAt(1) as TextView).apply {
                        text =
                            ui.serviceTitle(item.provider) +
                                " · " +
                                if (item.browseId.isNotEmpty())
                                    activity.getString(R.string.catalog_folder)
                                else item.subtitle
                        setTextColor(ui.providerAccent(item.provider))
                    }
                    return row
                }
            }
        list.adapter = adapter
        list.selector =
            android.graphics.drawable.StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_focused),
                    ui.box(ui.panel, ui.providerAccent("")),
                )
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    ui.box(ui.panel, ui.providerAccent("")),
                )
                addState(intArrayOf(), ui.box(android.graphics.Color.TRANSPARENT))
            }
        // The focused row must remain readable on legacy ListView implementations.
        list.setDrawSelectorOnTop(false)
        val content =
            ui.column().apply {
                setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
                addView(input)
                addView(status)
                addView(list, LinearLayout.LayoutParams(-1, ui.dp(280)))
            }
        val dialog =
            AlertDialog.Builder(activity)
                .setTitle(R.string.search_all)
                .setView(content)
                .setPositiveButton(R.string.search, null)
                .setNegativeButton(R.string.close, null)
                .create()
        val render: (SearchState) -> Unit = { state ->
            items =
                state.sections.flatMap { section ->
                    section.items +
                        if (section.more)
                            listOf(
                                MediaItem(
                                    "search-more-" + section.provider,
                                    section.provider,
                                    activity.getString(
                                        R.string.search_more,
                                        ui.serviceTitle(section.provider),
                                    ),
                                    kind = "search_more",
                                    playable = false,
                                )
                            )
                        else emptyList()
                }
            adapter.notifyDataSetChanged()
            if (state.phase == "READY" && model.bookmark.resultsFocused) {
                val bookmark = model.bookmark
                list.post {
                    if (dialog.isShowing && model.bookmark == bookmark) {
                        val selectedIndex =
                            items.indexOfFirst { it.id == bookmark.viewport.selectedId }
                        val first =
                            items
                                .indexOfFirst { it.id == bookmark.viewport.firstVisibleId }
                                .coerceAtLeast(0)
                        list.requestFocus()
                        if (selectedIndex >= 0 && bookmark.viewport.selectedTop != null)
                            list.setSelectionFromTop(selectedIndex, bookmark.viewport.selectedTop)
                        else list.setSelectionFromTop(first, bookmark.viewport.firstTop)
                    }
                }
            }
            status.text =
                when (state.phase) {
                    "IDLE" -> activity.getString(R.string.search_all_hint)
                    "WAITING",
                    "LOADING" -> activity.getString(R.string.loading)
                    "ERROR" -> activity.getString(R.string.error_network)
                    else -> {
                        val unavailable =
                            state.sections
                                .filter { it.state == "UNAVAILABLE" }
                                .map { ui.serviceTitle(it.provider) }
                        val text =
                            if (items.isEmpty()) activity.getString(R.string.catalog_empty)
                            else
                                activity.getString(
                                    R.string.search_count,
                                    state.sections.sumOf { it.items.size },
                                )
                        text +
                            if (unavailable.isEmpty()) ""
                            else
                                "\n" +
                                    activity.getString(
                                        R.string.search_partial,
                                        unavailable.joinToString(", "),
                                    )
                    }
                }
        }
        model.observer = render
        list.setOnItemClickListener { _, _, index, _ ->
            items.getOrNull(index)?.let {
                navigating = true
                remember(index)
                dialog.dismiss()
                selected(it)
            }
        }
        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    model.edit(s?.toString() ?: "")
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        )
        dialog.setOnDismissListener {
            // Android delivers dismissal asynchronously; an older dialog must not
            // detach the observer or clear the return path of its replacement.
            if (model.observer === render) {
                model.dismiss()
                if (!navigating) closed()
            }
        }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            model.edit(input.text.toString(), true)
        }
        capture {
            remember()
            input.text.toString().take(100)
        }
        model.open(draft)
        return dialog
    }
}
