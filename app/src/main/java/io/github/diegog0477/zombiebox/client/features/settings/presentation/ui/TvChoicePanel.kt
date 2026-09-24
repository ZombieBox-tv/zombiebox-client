package io.github.diegog0477.zombiebox.client.features.settings.presentation.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvTheme
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets

/** A legacy-safe TV page for short settings choices; selection and D-pad focus stay distinct. */
@Suppress("DEPRECATION")
class TvChoicePanel(private val activity: Activity, private val onClosed: () -> Unit = {}) {
    data class Option(
        val label: Int,
        val detail: Int? = null,
        val selected: Boolean = false,
        val action: () -> Unit,
    )

    private val ui = TvWidgets(activity)
    private var dialog: Dialog? = null
    private var optionViews: List<View> = emptyList()
    private var busy = false
    private var previousFocus: View? = null

    val visible: Boolean
        get() = dialog?.isShowing == true

    fun show(
        section: Int,
        title: Int,
        description: Int?,
        options: List<Option>,
        onBack: (() -> Unit)? = null,
    ) {
        if (activity.isFinishing) return
        val existing = dialog
        if (existing == null || !existing.isShowing) {
            previousFocus = activity.currentFocus
        }
        val page =
            ui.column().apply {
                setBackgroundColor(TvTheme.shellBackground)
                setPadding(ui.dp(28), ui.dp(20), ui.dp(28), ui.dp(22))
            }
        val scroll = ScrollView(activity).apply { addView(page) }
        val root =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                setBackgroundColor(TvTheme.shellBackground)
                addView(
                    scroll,
                    LinearLayout.LayoutParams(
                        minOf(activity.resources.displayMetrics.widthPixels, ui.dp(880)),
                        -1,
                    ),
                )
            }
        val panel = existing ?: Dialog(activity).also { dialog = it }
        if (existing == null) {
            panel.requestWindowFeature(Window.FEATURE_NO_TITLE)
            panel.setOnDismissListener {
                dialog = null
                optionViews = emptyList()
                onClosed()
                val focus = previousFocus
                previousFocus = null
                focus?.post {
                    if (!activity.isFinishing && focus.windowToken != null) focus.requestFocus()
                }
            }
        }
        panel.setContentView(root)
        panel.setOnKeyListener { _, keyCode, event ->
            if (
                keyCode == KeyEvent.KEYCODE_BACK &&
                    event.action == KeyEvent.ACTION_UP &&
                    onBack != null
            ) {
                onBack()
                true
            } else {
                false
            }
        }

        val header = ui.row()
        val headings = ui.column()
        headings.addView(
            ui.text(activity.getString(section), 13f, ui.green).apply {
                typeface = TvTypography.semibold(activity)
            }
        )
        headings.addView(
            ui.text(activity.getString(title), 30f).apply {
                typeface = TvTypography.semibold(activity)
            }
        )
        header.addView(headings, LinearLayout.LayoutParams(0, -2, 1f))
        val back =
            TvTheme.createDarkButton(
                activity,
                ui,
                activity.getString(if (onBack == null) R.string.close else R.string.back),
            ) {
                if (onBack == null) panel.dismiss() else onBack()
            }
        header.addView(back)
        page.addView(header)

        if (description != null) {
            page.addView(
                ui.text(activity.getString(description), 16f, ui.muted),
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = ui.dp(15)
                    bottomMargin = ui.dp(18)
                },
            )
        }

        val choices = ui.column()
        optionViews =
            options.map { choice ->
                optionRow(choice).also { row ->
                    row.setOnClickListener { if (!busy) choice.action() }
                    choices.addView(
                        row,
                        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(9) },
                    )
                }
            }
        page.addView(choices)
        busy = false
        if (!panel.isShowing) panel.show()
        panel.window?.apply {
            setBackgroundDrawable(ColorDrawable(TvTheme.shellBackground))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
        val focusIndex = options.indexOfFirst { it.selected }.let { if (it < 0) 0 else it }
        (optionViews.getOrNull(focusIndex) ?: back).requestFocus()
    }

    fun setBusy(value: Boolean) {
        busy = value
        optionViews.forEach { it.isEnabled = !value }
    }

    fun dismiss() = dialog?.dismiss() ?: Unit

    private fun optionRow(choice: Option): View {
        val stroke = if (choice.selected) ui.green else TvTheme.cardBorder
        val normal =
            TvTheme.roundedBox(
                ui.dp(10).toFloat(),
                if (choice.selected) Color.rgb(23, 38, 35) else TvTheme.cardBackground,
                ui.dp(1),
                stroke,
            )
        val focused =
            TvTheme.roundedBox(ui.dp(10).toFloat(), TvTheme.buttonFocused, ui.dp(2), Color.WHITE)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(64)
            setPadding(ui.dp(18), ui.dp(11), ui.dp(18), ui.dp(11))
            isFocusable = true
            isClickable = true
            contentDescription = activity.getString(choice.label)
            setBackgroundDrawable(
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), focused)
                    addState(intArrayOf(android.R.attr.state_pressed), focused)
                    addState(intArrayOf(), normal)
                }
            )
            val mark =
                View(activity).apply {
                    setBackgroundDrawable(
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(if (choice.selected) ui.green else Color.TRANSPARENT)
                            setStroke(ui.dp(2), if (choice.selected) ui.green else ui.muted)
                        }
                    )
                }
            addView(
                mark,
                LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)).apply { rightMargin = ui.dp(17) },
            )
            val texts = ui.column()
            texts.addView(
                ui.text(activity.getString(choice.label), 18f).apply {
                    typeface = TvTypography.semibold(activity)
                }
            )
            choice.detail?.let { texts.addView(ui.text(activity.getString(it), 14f, ui.muted)) }
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }
}
