package io.github.diegog0477.zombiebox.client.features.airplay.presentation.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.airplay.domain.repository.AirPlayPairingUnavailable
import io.github.diegog0477.zombiebox.client.features.airplay.presentation.viewmodel.AirPlayPairingViewModel

/** A dark, D-pad-focusable panel; the PIN exists only while this dialog is visible. */
class AirPlayPairingDialog(
    private val activity: Activity,
    private val model: AirPlayPairingViewModel,
) {
    fun show() {
        val ui = TvWidgets(activity)
        val panel =
            ui.column().apply {
                setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(20))
                setBackgroundDrawable(ui.box(ui.panel, ui.providerAccent("airplay")))
            }
        panel.addView(ui.text(activity.getString(R.string.airplay_pairing_title), 24f))
        panel.addView(ui.text(activity.getString(R.string.airplay_pairing_detail), 15f, ui.muted))
        val pin =
            ui.text(activity.getString(R.string.loading), 38f).apply {
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(20), 0, ui.dp(20))
                isSaveEnabled = false
            }
        panel.addView(pin, LinearLayout.LayoutParams(-1, -2))
        val close = ui.button(R.string.close) {}
        panel.addView(close)
        val dialog = Dialog(activity)
        close.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(panel)
        dialog.window?.apply { setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }
        dialog.setOnDismissListener {
            pin.text = ""
            model.close()
        }
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.72f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        close.requestFocus()
        model.load(
            { value -> pin.text = value },
            { failure ->
                pin.setText(
                    when ((failure as? AirPlayPairingUnavailable)?.reason) {
                        AirPlayPairingUnavailable.Reason.UPDATE_GATEWAY ->
                            R.string.airplay_pairing_update
                        AirPlayPairingUnavailable.Reason.DISABLED ->
                            R.string.airplay_pairing_disabled
                        else -> R.string.airplay_pairing_unavailable
                    }
                )
                pin.textSize = 16f
            },
        )
    }
}
