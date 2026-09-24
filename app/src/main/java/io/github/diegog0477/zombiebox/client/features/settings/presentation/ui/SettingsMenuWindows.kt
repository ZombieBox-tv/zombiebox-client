package io.github.diegog0477.zombiebox.client.features.settings.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.settings.presentation.viewmodel.SettingsNavigation

/** Keep the parent list alive beneath child dialogs, preserving native D-pad focus. */
class SettingsMenuWindows(private val activity: Activity, val navigation: SettingsNavigation) {
    private val windows = LinkedHashMap<String, AlertDialog>()
    private val lists = HashMap<String, ListView>()
    private val itemKeys = HashMap<String, List<String>>()
    private val ui = TvWidgets(activity)

    fun show(
        route: String,
        title: Int,
        labels: Array<String>,
        keys: List<String> = emptyList(),
        selected: (Int) -> Unit,
    ) {
        windows[route]
            ?.takeIf { it.isShowing }
            ?.let {
                return
            }
        navigation.open(route)
        val list =
            ListView(activity).apply {
                divider = null
                setBackgroundColor(ui.background)
                selector = ui.box(ui.panel, ui.green)
                setDrawSelectorOnTop(false)
                adapter =
                    object :
                        ArrayAdapter<String>(
                            activity,
                            android.R.layout.simple_list_item_1,
                            labels,
                        ) {
                        override fun getView(
                            position: Int,
                            convertView: View?,
                            parent: ViewGroup,
                        ): View {
                            val row =
                                (convertView as? TextView)
                                    ?: ui.text("", 17f).apply {
                                        minimumHeight = ui.dp(54)
                                        setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12))
                                    }
                            row.text = labels[position]
                            row.setTextColor(Color.WHITE)
                            return row
                        }
                    }
            }
        val content =
            ui.column().apply {
                setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
                setBackgroundDrawable(ui.box(ui.panel, ui.muted))
                addView(
                    ui.text(activity.getString(title), 23f).apply {
                        setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(14))
                    }
                )
                addView(
                    list,
                    LinearLayout.LayoutParams(-1, ui.dp((labels.size * 54).coerceAtMost(420))),
                )
            }
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        content.addView(ui.button(R.string.close) { dialog.dismiss() })
        windows[route] = dialog
        lists[route] = list
        itemKeys[route] = keys
        dialog.setOnDismissListener {
            if (windows[route] === dialog) {
                val descendants = windows.keys.dropWhile { it != route }.drop(1)
                descendants.forEach {
                    lists.remove(it)
                    windows.remove(it)?.dismiss()
                }
                windows.remove(route)
                lists.remove(route)
                navigation.close(route)
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.76f)
                .toInt()
                .coerceAtMost(ui.dp(580)),
            -2,
        )
        list.setOnItemClickListener { _, _, index, _ ->
            navigation.activate(route, index, itemKeys[route]?.getOrNull(index) ?: "")
            selected(index)
        }
        list.requestFocus()
    }

    fun restoreSelection(route: String, index: Int, key: String = "") {
        val list = lists[route] ?: return
        val byKey = itemKeys[route]?.indexOf(key) ?: -1
        val selection =
            (if (byKey >= 0) byKey else index).coerceIn(0, (list.count - 1).coerceAtLeast(0))
        navigation.select(route, selection, itemKeys[route]?.getOrNull(selection) ?: "")
        list.setSelection(selection)
        list.requestFocus()
    }

    fun snapshot(): List<SettingsNavigation.Menu> {
        for (route in windows.keys) {
            val index = lists[route]?.selectedItemPosition ?: -1
            if (index >= 0) navigation.select(route, index, itemKeys[route]?.getOrNull(index) ?: "")
        }
        return navigation.path
    }

    fun close() {
        val closing = windows.values.toList()
        windows.clear()
        lists.clear()
        itemKeys.clear()
        navigation.reset()
        closing.reversed().forEach { it.dismiss() }
    }
}
