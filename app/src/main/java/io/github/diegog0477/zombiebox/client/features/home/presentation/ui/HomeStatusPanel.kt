package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets

/** Reference-inspired docked panel. Only actual presentation facts are shown. */
class HomeStatusPanel(context: Context, private val ui: TvWidgets) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setPadding(ui.dp(16), ui.dp(18), ui.dp(16), ui.dp(18))
        setBackgroundDrawable(ui.box(ui.background, ui.panel))
        val heading = ui.row()
        heading.addView(
            ui.text(context.getString(R.string.docked_mode), 18f).apply { typeface = ui.bold },
            LayoutParams(0, -2, 1f),
        )
        heading.addView(
            ui.text(context.getString(R.string.docked_on), 12f, ui.background).apply {
                gravity = Gravity.CENTER
                setBackgroundDrawable(ui.box(ui.green))
            }
        )
        addView(heading)
        addView(ui.text(context.getString(R.string.docked_description), 14f, ui.muted))
        divider()
        for (label in
            listOf(R.string.docked_large_ui, R.string.docked_focus, R.string.docked_touch)) {
            val line = ui.row()
            line.addView(ui.text("✓", 17f, ui.green))
            line.addView(ui.text(context.getString(label), 14f, ui.muted), LayoutParams(0, -2, 1f))
            addView(line)
        }
        divider()
        addView(ui.text(context.getString(R.string.docked_motto), 16f, ui.muted))
        isFocusable = false
    }

    private fun divider() {
        addView(
            View(context).apply { setBackgroundColor(ui.panel) },
            LayoutParams(-1, ui.dp(1)).apply {
                topMargin = ui.dp(16)
                bottomMargin = ui.dp(16)
            },
        )
    }
}
