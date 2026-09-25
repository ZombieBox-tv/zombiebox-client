package io.github.diegog0477.zombiebox.client.features.playback.presentation.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.QualityInventory
import kotlin.math.min

/** A TV-sized, D-pad navigable quality panel. The gateway supplies only verified choices. */
@Suppress("DEPRECATION")
class QualityDialog(private val context: Context) {
    fun show(value: QualityInventory, select: (String) -> Unit) {
        val ui = TvWidgets(context)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val panel =
            ui.column().apply {
                setPadding(ui.dp(22), ui.dp(18), ui.dp(22), ui.dp(18))
                setBackgroundDrawable(ui.box(ui.panel, ui.muted))
            }
        panel.addView(
            ui.text(context.getString(R.string.quality), 22f).apply {
                typeface = TvTypography.semibold(context)
                setPadding(ui.dp(8), 0, ui.dp(8), ui.dp(14))
            }
        )

        val list = ui.column()
        var selectedView: View? = null
        if (value.options.isEmpty()) {
            list.addView(ui.text(context.getString(R.string.qualities_unavailable), 15f, ui.muted))
        } else {
            for (option in value.options) {
                val selected = option.id.equals(value.selectedId, ignoreCase = true)
                val label =
                    if (option.id.equals("auto", ignoreCase = true)) {
                        context.getString(R.string.quality_auto)
                    } else {
                        option.label
                    }
                val row =
                    TextView(context).apply {
                        text = (if (selected) "✓  " else "    ") + label
                        textSize = 18f
                        typeface = TvTypography.regular(context)
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER_VERTICAL
                        isFocusable = true
                        isClickable = true
                        minHeight = ui.dp(50)
                        setPadding(ui.dp(12), ui.dp(5), ui.dp(12), ui.dp(5))
                        setBackgroundDrawable(ui.focusBackground())
                        setOnClickListener {
                            dialog.dismiss()
                            select(option.id)
                        }
                    }
                if (selected) selectedView = row
                list.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
        }
        panel.addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(list)
            },
            LinearLayout.LayoutParams(-1, -2),
        )

        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.72f }
        }
        dialog.show()
        dialog.window?.setLayout(
            min(ui.dp(460), context.resources.displayMetrics.widthPixels - ui.dp(48)),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        (selectedView ?: list.getChildAt(0))?.requestFocus()
    }
}
