package io.github.diegog0477.zombiebox.client.features.mirroring.presentation.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvTheme
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.mirroring.presentation.viewmodel.ReceiverViewModel

/** Full-screen TV receiver selector. Focus and the saved provider are separate states. */
@Suppress("DEPRECATION")
class MediaReceiverDialog(
    private val activity: Activity,
    private val model: ReceiverViewModel,
    private val error: (Exception) -> Unit,
    private val selected: (String) -> Unit = {},
) {
    fun show() {
        val previousFocus = activity.currentFocus
        model.readMediaProvider(
            { provider -> if (!activity.isFinishing) showSelector(provider, previousFocus) },
            error,
        )
    }

    private fun showSelector(provider: String, previousFocus: View?) {
        val ui = TvWidgets(activity)
        val providers = listOf("", "spotify", "airplay", "auto", "universal")
        val optionIds =
            intArrayOf(
                R.id.media_receiver_disabled,
                R.id.media_receiver_spotify,
                R.id.media_receiver_airplay,
                R.id.media_receiver_auto,
                R.id.media_receiver_universal,
            )
        val labels =
            listOf(
                R.string.disabled,
                R.string.spotify,
                R.string.airplay,
                R.string.media_receiver_auto,
                R.string.media_receiver_universal,
            )
        val currentIndex = providers.indexOf(provider).coerceAtLeast(0)
        val screen =
            ui.column().apply {
                setBackgroundColor(TvTheme.shellBackground)
                setPadding(ui.dp(24), ui.dp(18), ui.dp(24), ui.dp(18))
            }
        val content = ui.column()
        screen.addView(
            content,
            LinearLayout.LayoutParams(
                    minOf(activity.resources.displayMetrics.widthPixels - ui.dp(48), ui.dp(860)),
                    -1,
                )
                .apply { gravity = Gravity.CENTER_HORIZONTAL },
        )
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(screen)
        dialog.setOnDismissListener {
            previousFocus?.post {
                if (!activity.isFinishing && previousFocus.windowToken != null) {
                    previousFocus.requestFocus()
                }
            }
        }

        val header = ui.row()
        header.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.media_receiver)
                textSize = 27f
                typeface = TvTypography.semibold(activity)
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        val close =
            TvTheme.createDarkButton(activity, ui, activity.getString(R.string.close)) {
                dialog.dismiss()
            }
        close.id = R.id.media_receiver_close
        header.addView(close)
        content.addView(header, LinearLayout.LayoutParams(-1, -2))

        val options = ui.column()
        val rows = ArrayList<View>(providers.size)
        var saving = false
        providers.forEachIndexed { index, choice ->
            val row = optionRow(ui, labels[index], choice, index == currentIndex)
            row.id = optionIds[index]
            row.setOnClickListener {
                if (saving || choice == provider) {
                    if (choice == provider) dialog.dismiss()
                    return@setOnClickListener
                }
                saving = true
                rows.forEach { it.isEnabled = false }
                model.selectMediaProvider(
                    choice,
                    { failure ->
                        saving = false
                        rows.forEach { it.isEnabled = true }
                        if (dialog.isShowing) row.requestFocus()
                        error(failure)
                    },
                ) {
                    dialog.dismiss()
                    selected(choice)
                }
            }
            rows.add(row)
            options.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) },
            )
        }
        rows.forEachIndexed { index, row ->
            row.nextFocusUpId = if (index == 0) close.id else rows[index - 1].id
            if (index < rows.lastIndex) row.nextFocusDownId = rows[index + 1].id
        }
        close.nextFocusDownId = rows.first().id

        val scroll = ScrollView(activity).apply { isFillViewport = false }
        scroll.addView(options)
        content.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(24) },
        )

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(TvTheme.shellBackground))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
        rows[currentIndex].requestFocus()
    }

    private fun optionRow(ui: TvWidgets, label: Int, provider: String, isSelected: Boolean): View {
        val color = ui.providerAccent(provider)
        val radius = ui.dp(10).toFloat()
        val normal =
            TvTheme.roundedBox(
                radius,
                if (isSelected) Color.rgb(25, 37, 38) else TvTheme.cardBackground,
                ui.dp(1),
                if (isSelected) color else TvTheme.cardBorder,
            )
        val focused = TvTheme.roundedBox(radius, TvTheme.buttonFocused, ui.dp(2), Color.WHITE)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(58)
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12))
            isFocusable = true
            isClickable = true
            this.isSelected = isSelected
            contentDescription = activity.getString(label)
            setBackgroundDrawable(
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), focused)
                    addState(intArrayOf(android.R.attr.state_pressed), focused)
                    addState(intArrayOf(), normal)
                }
            )
            addView(
                View(activity).apply {
                    setBackgroundDrawable(
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(if (isSelected) color else Color.TRANSPARENT)
                            setStroke(ui.dp(2), if (isSelected) color else ui.muted)
                        }
                    )
                },
                LinearLayout.LayoutParams(ui.dp(17), ui.dp(17)).apply { rightMargin = ui.dp(18) },
            )
            addView(
                TextView(activity).apply {
                    text = activity.getString(label)
                    textSize = 17f
                    typeface =
                        if (isSelected) TvTypography.semibold(activity)
                        else TvTypography.regular(activity)
                    setTextColor(Color.WHITE)
                }
            )
        }
    }
}
