package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Bounded semantic details dialog styled for 10-foot TV experience with Barlow typography, provider
 * accents, contextual playback actions, and remote-friendly scrolling.
 */
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
        val provider = item.provider
        val accent = ui.providerAccent(provider)
        val displayMetrics = activity.resources.displayMetrics
        val widthDp = displayMetrics.widthPixels / displayMetrics.density
        val isDockedTv = widthDp >= 720f

        val isPoster =
            provider in listOf("plex", "stremio", "jellyfin") &&
                (item.kind in listOf("movie", "series", "show") ||
                    (item.kind.isEmpty() && item.imageUrl.isNotEmpty()))

        var dialogRef: AlertDialog? = null

        // Top tag / breadcrumb
        val tagRow =
            ui.row().apply {
                addView(
                    ui.text(ui.serviceTitle(provider).uppercase(Locale.US), 12f, accent).apply {
                        typeface = ui.bold
                    }
                )
                if (item.category.isNotEmpty()) {
                    addView(ui.text("·  " + item.category, 12f, ui.muted))
                }
            }

        val actionsRow =
            ui.row().apply {
                setPadding(0, ui.dp(12), 0, ui.dp(6))
                if (item.playable) {
                    val playLabel =
                        if (item.positionMs > 0 && item.kind != "channel") {
                            R.string.resume_content
                        } else {
                            R.string.play
                        }
                    addView(
                        ui.primary(playLabel) {
                            dialogRef?.dismiss()
                            play()
                        }
                    )
                    if (item.positionMs > 0 && item.kind != "channel") {
                        addView(
                            ui.button(R.string.start_over) {
                                dialogRef?.dismiss()
                                startOver()
                            }
                        )
                    }
                }
                if (favorite != null) {
                    val favLabel =
                        if (item.favorite) R.string.remove_iptv_favorite
                        else R.string.add_iptv_favorite
                    addView(
                        ui.action(activity.getString(favLabel), ui.providerAccent("iptv")) {
                            favorite()
                        }
                    )
                }
                addView(
                    ui.button(R.string.close) {
                        dialogRef?.dismiss()
                        closed?.invoke()
                    }
                )
            }

        // Details column
        val metaColumn =
            ui.column().apply {
                addView(tagRow)
                addView(
                    ui.text(item.title, 24f).apply {
                        typeface = ui.bold
                        maxLines = 2
                        setPadding(ui.dp(2), ui.dp(4), ui.dp(2), ui.dp(4))
                    }
                )
                if (item.subtitle.isNotEmpty()) {
                    addView(ui.text(item.subtitle, 15f, ui.muted))
                }
                if (item.durationMs > 0) {
                    val progressText =
                        if (item.positionMs > 0) {
                            activity.getString(
                                R.string.catalog_progress,
                                ui.formatTime(item.positionMs),
                                ui.formatTime(item.durationMs),
                            )
                        } else {
                            ui.formatTime(item.durationMs)
                        }
                    addView(ui.text(progressText, 14f, ui.muted))
                    if (item.positionMs > 0) {
                        addView(ui.progress(item.positionMs, item.durationMs))
                    }
                }
                addView(actionsRow)
                if (item.description.isNotEmpty()) {
                    addView(
                        ui.text(item.description.take(12000), 15f).apply {
                            setPadding(ui.dp(2), ui.dp(8), ui.dp(2), ui.dp(12))
                        }
                    )
                }
                for (programme in item.programmes.take(8)) {
                    val time =
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(programme.start * 1000))
                    addView(ui.text("$time · ${programme.title}", 14f, ui.muted))
                }
            }

        // Layout composition: docked landscape 2-column or portrait single column
        val rootLayout: View =
            if (isDockedTv) {
                ui.row().apply {
                    gravity = Gravity.TOP
                    setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(20))
                    setBackgroundColor(ui.background)
                    val artWidth =
                        if (artwork == null) 0 else if (isPoster) ui.dp(220) else ui.dp(300)
                    if (artwork != null) {
                        val artHeight = if (isPoster) ui.dp(330) else ui.dp(169)
                        val artFrame =
                            FrameLayout(activity).apply {
                                addView(artwork, FrameLayout.LayoutParams(-1, -1))
                                setBackgroundDrawable(
                                    ui.box(Color.rgb(18, 24, 26), Color.rgb(36, 46, 50))
                                )
                                setPadding(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                            }
                        val artCol =
                            ui.column().apply {
                                addView(artFrame, LinearLayout.LayoutParams(artWidth, artHeight))
                            }
                        addView(
                            artCol,
                            LinearLayout.LayoutParams(-2, -2).apply { rightMargin = ui.dp(24) },
                        )
                    }
                    val scroll =
                        object : ScrollView(activity) {
                                override fun onMeasure(
                                    widthMeasureSpec: Int,
                                    heightMeasureSpec: Int,
                                ) {
                                    val cap =
                                        (resources.displayMetrics.heightPixels * 0.76f).toInt()
                                    val parentLimit =
                                        if (
                                            View.MeasureSpec.getMode(heightMeasureSpec) ==
                                                View.MeasureSpec.UNSPECIFIED
                                        )
                                            cap
                                        else View.MeasureSpec.getSize(heightMeasureSpec)
                                    super.onMeasure(
                                        widthMeasureSpec,
                                        View.MeasureSpec.makeMeasureSpec(
                                            minOf(cap, parentLimit),
                                            View.MeasureSpec.AT_MOST,
                                        ),
                                    )
                                }
                            }
                            .apply {
                                isFocusable = true
                                addView(metaColumn)
                            }
                    val artGap = if (artwork == null) 0 else ui.dp(24)
                    val detailWidth =
                        (displayMetrics.widthPixels - ui.dp(48) - artWidth - artGap).coerceAtLeast(
                            ui.dp(280)
                        )
                    addView(scroll, LinearLayout.LayoutParams(detailWidth, -2))
                }
            } else {
                val singleCol =
                    ui.column().apply {
                        setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
                        setBackgroundColor(ui.background)
                        if (artwork != null) {
                            val artHeight = if (isPoster) ui.dp(240) else ui.dp(160)
                            addView(
                                artwork,
                                LinearLayout.LayoutParams(-1, artHeight).apply {
                                    bottomMargin = ui.dp(12)
                                },
                            )
                        }
                        addView(metaColumn)
                    }
                ScrollView(activity).apply {
                    isFocusable = true
                    addView(singleCol)
                }
            }

        rootLayout.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        return AlertDialog.Builder(activity).setView(rootLayout).create().also { dialog ->
            dialogRef = dialog
            dialog.setOnCancelListener { closed?.invoke() }
            dialog.show()
            dialog.window?.let { window ->
                window.setBackgroundDrawable(ColorDrawable(ui.background))
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        }
    }
}
