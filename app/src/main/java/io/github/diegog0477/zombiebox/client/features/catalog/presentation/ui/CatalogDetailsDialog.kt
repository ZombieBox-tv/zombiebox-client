package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import java.text.DateFormat
import java.util.Date

/** Bounded semantic details with contextual actions and remote-friendly scrolling. */
class CatalogDetailsDialog(private val activity: Activity) {
    fun show(
        item: MediaItem,
        artwork: View?,
        play: () -> Unit,
        startOver: () -> Unit,
        closed: (() -> Unit)?,
        favorite: (() -> Unit)? = null,
    ): AlertDialog {
        val ui = TvWidgets(activity)
        val content =
            ui.column().apply {
                setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
                setBackgroundColor(ui.background)
                artwork?.let { addView(it, LinearLayout.LayoutParams(-1, ui.dp(160))) }
                addView(
                    ui.text(ui.serviceTitle(item.provider), 14f, ui.providerAccent(item.provider))
                )
                if (item.subtitle.isNotEmpty()) addView(ui.text(item.subtitle, 16f, ui.muted))
                if (item.durationMs > 0)
                    addView(
                        ui.text(
                            activity.getString(
                                R.string.catalog_progress,
                                ui.formatTime(item.positionMs),
                                ui.formatTime(item.durationMs),
                            ),
                            14f,
                            ui.muted,
                        )
                    )
                if (item.description.isNotEmpty())
                    addView(ui.text(item.description.take(12000), 16f))
                if (favorite != null)
                    addView(
                        ui.action(
                            activity.getString(
                                if (item.favorite) R.string.remove_iptv_favorite
                                else R.string.add_iptv_favorite
                            ),
                            ui.providerAccent("iptv"),
                        ) {
                            favorite()
                        }
                    )
                for (programme in item.programmes.take(8)) {
                    val time =
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(programme.start * 1000))
                    addView(ui.text("$time · ${programme.title}", 14f, ui.muted))
                }
            }
        val scroll =
            ScrollView(activity).apply {
                isFocusable = true
                addView(content)
            }
        val builder =
            AlertDialog.Builder(activity).setTitle(item.title).setView(scroll).setNegativeButton(
                R.string.close
            ) { _, _ ->
                closed?.invoke()
            }
        if (item.playable) {
            builder.setPositiveButton(
                if (item.positionMs > 0 && item.kind != "channel") R.string.resume_content
                else R.string.play
            ) { _, _ ->
                play()
            }
            if (item.kind != "channel")
                builder.setNeutralButton(R.string.start_over) { _, _ -> startOver() }
        }
        return builder.create().also { dialog ->
            dialog.setOnCancelListener { closed?.invoke() }
            dialog.show()
        }
    }
}
