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
import io.github.diegog0477.zombiebox.client.core.ui.TvDpadKeyboard
import io.github.diegog0477.zombiebox.client.core.ui.TvTheme
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogViewport
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.SearchState
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.viewmodel.SearchViewModel

/** Recycled results with provider labels and accents; query entry never takes result focus. */
@Suppress("DEPRECATION")
class SearchDialog(private val activity: Activity, private val model: SearchViewModel) {
    fun show(
        draft: String,
        selected: (MediaItem) -> Unit,
        capture: (() -> String) -> Unit,
        closed: () -> Unit,
    ): AlertDialog {
        val ui = TvWidgets(activity)
        val isLandscape =
            activity.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
                activity.resources.displayMetrics.widthPixels >
                    activity.resources.displayMetrics.heightPixels

        val input =
            EditText(activity).apply {
                setSingleLine(true)
                setTextColor(android.graphics.Color.WHITE)
                setHintTextColor(ui.muted)
                setBackgroundDrawable(
                    android.graphics.drawable.StateListDrawable().apply {
                        addState(
                            intArrayOf(android.R.attr.state_focused),
                            TvTheme.roundedBox(
                                ui.dp(8).toFloat(),
                                android.graphics.Color.rgb(26, 34, 37),
                                ui.dp(2),
                                ui.green,
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
                setPadding(ui.dp(12), 0, ui.dp(12), 0)
                filters = arrayOf(android.text.InputFilter.LengthFilter(100))
                setText(draft)
                setHint(R.string.search_all_hint)
                typeface = TvTypography.regular(activity)
            }

        var items = emptyList<MediaItem>()
        val list = ListView(activity).apply { itemsCanFocus = false }

        val searchAction =
            Button(activity).apply {
                text = activity.getString(R.string.search)
                typeface = ui.bold
                textSize = 14f
                setTextColor(android.graphics.Color.WHITE)
                isFocusable = true
                setBackgroundDrawable(ui.focusBackground(ui.green))
                setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(6))
                setOnClickListener { model.edit(input.text.toString(), immediate = true) }
            }

        val keyboard =
            TvDpadKeyboard(activity).apply {
                attachTarget(input)
                maxTextLength = 100
                setOnDone {
                    model.edit(input.text.toString(), immediate = true)
                    if (items.isNotEmpty()) {
                        list.requestFocus()
                    }
                }
            }

        var dialogRef: AlertDialog? = null
        val closeAction =
            Button(activity).apply {
                text = activity.getString(R.string.close)
                typeface = ui.bold
                textSize = 14f
                setTextColor(android.graphics.Color.WHITE)
                isFocusable = true
                setBackgroundDrawable(ui.focusBackground(ui.green))
                setOnClickListener { dialogRef?.dismiss() }
            }

        keyboard.nextRightFocusView = list
        keyboard.nextUpFocusView = input

        input.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == android.view.KeyEvent.ACTION_DOWN &&
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
            ) {
                keyboard.focusDefaultKey()
                true
            } else false
        }

        searchAction.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        input.requestFocus()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        list.requestFocus()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        keyboard.focusDefaultKey()
                        true
                    }
                    else -> false
                }
            } else false
        }

        list.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == android.view.KeyEvent.ACTION_DOWN &&
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
            ) {
                keyboard.focusDefaultKey()
                true
            } else false
        }

        val status = ui.text("", 14f, ui.muted)
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
                                addView(
                                    ui.text("", 16f, android.graphics.Color.WHITE).apply {
                                        setSingleLine(true)
                                        typeface = TvTypography.semibold(activity)
                                    }
                                )
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

        val rootView: View =
            if (isLandscape) {
                val leftPane =
                    ui.column().apply {
                        val inputRow =
                            ui.row().apply {
                                addView(
                                    input,
                                    LinearLayout.LayoutParams(0, ui.dp(44), 1.0f).apply {
                                        rightMargin = ui.dp(6)
                                    },
                                )
                                addView(searchAction, LinearLayout.LayoutParams(-2, ui.dp(44)))
                            }
                        addView(inputRow)
                        addView(
                            keyboard,
                            LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                )
                                .apply { topMargin = ui.dp(8) },
                        )
                    }
                val rightPane =
                    ui.column().apply {
                        setPadding(ui.dp(4), 0, 0, 0)
                        addView(status)
                        addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
                    }
                val body =
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        addView(
                            leftPane,
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1.15f,
                            ),
                        )
                        addView(
                            rightPane,
                            LinearLayout.LayoutParams(
                                    0,
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    1.0f,
                                )
                                .apply { leftMargin = ui.dp(14) },
                        )
                    }
                ui.column().apply {
                    setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
                    setBackgroundDrawable(
                        TvTheme.roundedBox(
                            ui.dp(12).toFloat(),
                            TvTheme.shellBackground,
                            ui.dp(1),
                            TvTheme.cardBorder,
                        )
                    )
                    addView(
                        ui.row().apply {
                            addView(
                                ui.text(
                                        activity.getString(R.string.search_all),
                                        19f,
                                        android.graphics.Color.WHITE,
                                    )
                                    .apply { typeface = ui.bold },
                                LinearLayout.LayoutParams(0, -2, 1f),
                            )
                            addView(closeAction)
                        }
                    )
                    addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
                }
            } else {
                val portraitContent =
                    ui.column().apply {
                        setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
                        setBackgroundDrawable(
                            TvTheme.roundedBox(
                                ui.dp(10).toFloat(),
                                TvTheme.shellBackground,
                                ui.dp(1),
                                TvTheme.cardBorder,
                            )
                        )
                        addView(
                            ui.row().apply {
                                addView(
                                    ui.text(
                                            activity.getString(R.string.search_all),
                                            18f,
                                            android.graphics.Color.WHITE,
                                        )
                                        .apply { typeface = ui.bold },
                                    LinearLayout.LayoutParams(0, -2, 1f),
                                )
                                addView(closeAction)
                            }
                        )
                        val inputRow =
                            ui.row().apply {
                                addView(
                                    input,
                                    LinearLayout.LayoutParams(0, ui.dp(44), 1.0f).apply {
                                        rightMargin = ui.dp(6)
                                    },
                                )
                                addView(searchAction, LinearLayout.LayoutParams(-2, ui.dp(44)))
                            }
                        addView(inputRow)
                        addView(status)
                        addView(
                            keyboard,
                            LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                )
                                .apply { topMargin = ui.dp(8) },
                        )
                        addView(
                            list,
                            LinearLayout.LayoutParams(-1, ui.dp(220)).apply { topMargin = ui.dp(8) },
                        )
                    }
                ScrollView(activity).apply { addView(portraitContent) }
            }

        val dialog = AlertDialog.Builder(activity).setView(rootView).create()
        dialogRef = dialog
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
        dialog.window?.let { window ->
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        if (draft.isEmpty()) {
            rootView.post {
                if (dialog.isShowing && !model.bookmark.resultsFocused) {
                    keyboard.focusDefaultKey()
                }
            }
        }
        capture {
            remember()
            input.text.toString().take(100)
        }
        model.open(draft)
        return dialog
    }
}
