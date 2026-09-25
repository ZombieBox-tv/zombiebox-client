package io.github.diegog0477.zombiebox.client.features.diagnostics.presentation.ui

import android.app.Activity
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.HardwareReport

/** The inventory is rendered inside the diagnostics page, without a nested system dialog. */
class HardwareInventoryDialog(private val activity: Activity) {
    fun content(report: HardwareReport): LinearLayout {
        val ui = TvWidgets(activity)
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val content =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(4), dp(8), dp(16))
            }
        fun label(text: String, heading: Boolean = false) {
            content.addView(
                TextView(activity).apply {
                    this.text = text
                    textSize = if (heading) 18f else 15f
                    typeface =
                        if (heading) TvTypography.semibold(activity)
                        else TvTypography.regular(activity)
                    setTextColor(if (heading) ui.green else Color.WHITE)
                    setPadding(0, dp(if (heading) 18 else 9), 0, dp(if (heading) 6 else 9))
                }
            )
        }
        label(activity.getString(R.string.inventory_disclaimer))
        if (report.inventoryLimited) label(activity.getString(R.string.inventory_limited))
        label(activity.getString(R.string.inventory_displays), true)
        if (report.displays.isEmpty()) label(activity.getString(R.string.inventory_unknown))
        for (display in report.displays) {
            label(
                activity.getString(
                    R.string.inventory_display,
                    display.id,
                    display.width,
                    display.height,
                    display.refreshMilliHz / 1000.0,
                    activity.getString(
                        if (display.activeModeId > 0) R.string.inventory_native_mode
                        else R.string.inventory_logical_mode
                    ),
                )
            )
            for (mode in display.modes) label(
                activity.getString(
                    R.string.inventory_mode,
                    mode.id,
                    mode.width,
                    mode.height,
                    mode.refreshMilliHz / 1000.0,
                )
            )
        }
        for ((title, codecs) in
            listOf(
                R.string.inventory_decoders to report.decoders,
                R.string.inventory_encoders to report.encoders,
            )) {
            label(activity.getString(title), true)
            if (codecs.isEmpty()) label(activity.getString(R.string.inventory_unknown))
            if (codecs.size > 16) label(activity.getString(R.string.inventory_preview))
            for (codec in codecs.take(16)) {
                val acceleration =
                    activity.getString(
                        when (codec.acceleration) {
                            "HARDWARE" -> R.string.inventory_hardware
                            "SOFTWARE" -> R.string.inventory_software
                            else -> R.string.inventory_unknown
                        }
                    )
                label(codec.name + "\n" + codec.types.joinToString(", ") + "\n" + acceleration)
                for (profile in codec.profiles) label(
                    activity.getString(
                        R.string.inventory_profile,
                        profile.mime,
                        profile.profile,
                        profile.level,
                    )
                )
            }
        }
        return content
    }
}
