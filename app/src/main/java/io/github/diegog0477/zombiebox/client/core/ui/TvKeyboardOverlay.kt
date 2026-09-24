package io.github.diegog0477.zombiebox.client.core.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import io.github.diegog0477.zombiebox.client.R

/** Local D-pad keyboard for focused Client form fields on TV devices. */
object TvKeyboardOverlay {
    fun show(activity: Activity, field: EditText, label: String): Dialog {
        val ui = TvWidgets(activity)
        val panel =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(22), ui.dp(18), ui.dp(22), ui.dp(18))
                setBackgroundDrawable(
                    TvTheme.roundedBox(
                        ui.dp(12).toFloat(),
                        TvTheme.shellBackground,
                        ui.dp(1),
                        TvTheme.cardBorder,
                    )
                )
                addView(
                    ui.text(label, 22f).apply {
                        typeface = ui.bold
                        setPadding(0, 0, 0, ui.dp(12))
                    }
                )
            }
        val dialog = Dialog(activity)
        val keyboard =
            TvDpadKeyboard(activity).apply {
                attachTarget(field)
                maxTextLength = 2048
                setOnDone { dialog.dismiss() }
            }
        panel.addView(keyboard, LinearLayout.LayoutParams(-1, -2))
        panel.addView(
            ui.button(R.string.close) { dialog.dismiss() },
            LinearLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.RIGHT
                topMargin = ui.dp(8)
            },
        )
        dialog.setContentView(panel)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.88f)
                    .toInt()
                    .coerceAtMost(ui.dp(760)),
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
        }
        keyboard.focusDefaultKey()
        return dialog
    }
}
