package io.github.diegog0477.zombiebox.client.features.settings.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvKeyboardOverlay
import io.github.diegog0477.zombiebox.client.core.ui.TvTheme
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.settings.domain.model.MediaPreferences
import io.github.diegog0477.zombiebox.client.features.settings.presentation.viewmodel.SettingsViewModel

@Suppress("DEPRECATION")
class MediaLanguageDialog(
    private val activity: Activity,
    private val model: SettingsViewModel,
    private val failed: (Exception) -> Unit,
) {
    fun show(value: MediaPreferences): AlertDialog {
        val ui = TvWidgets(activity)
        val card =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(14))
                setBackgroundDrawable(
                    TvTheme.roundedBox(
                        ui.dp(12).toFloat(),
                        TvTheme.cardBackground,
                        ui.dp(1),
                        TvTheme.cardBorder,
                    )
                )
            }
        card.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.media_languages)
                textSize = 21f
                typeface = TvTypography.semibold(activity)
                setTextColor(Color.WHITE)
                setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(6))
            }
        )
        card.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.media_language_help)
                textSize = 13f
                typeface = TvTypography.regular(activity)
                setTextColor(ui.muted)
                setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(12))
            }
        )

        val form = ui.column().apply { setPadding(0, 0, 0, ui.dp(8)) }

        fun field(label: Int, languages: List<String>): EditText {
            form.addView(
                TextView(activity).apply {
                    text = activity.getString(label)
                    textSize = 14f
                    typeface = TvTypography.semibold(activity)
                    setTextColor(ui.muted)
                    setPadding(ui.dp(2), ui.dp(6), ui.dp(2), ui.dp(4))
                }
            )
            val text =
                TvTheme.createDarkInputField(activity, ui).apply {
                    setText(languages.joinToString(", "))
                    setOnKeyListener { _, keyCode, event ->
                        if (
                            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                keyCode == KeyEvent.KEYCODE_ENTER
                        ) {
                            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                                TvKeyboardOverlay.show(activity, this, activity.getString(label))
                            true
                        } else false
                    }
                }
            form.addView(
                text,
                LinearLayout.LayoutParams(-1, ui.dp(48)).apply { setMargins(0, 0, 0, ui.dp(8)) },
            )
            return text
        }

        val audio = field(R.string.audio_language_order, value.audioLanguages)
        val subtitles = field(R.string.subtitle_language_order, value.subtitleLanguages)

        form.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.subtitles)
                textSize = 14f
                typeface = TvTypography.semibold(activity)
                setTextColor(ui.muted)
                setPadding(ui.dp(2), ui.dp(6), ui.dp(2), ui.dp(4))
            }
        )

        val modes = listOf("off", "forced", "auto", "always")
        val labels =
            listOf(
                    R.string.subtitle_mode_off,
                    R.string.subtitle_mode_forced,
                    R.string.subtitle_mode_auto,
                    R.string.subtitle_mode_always,
                )
                .map { activity.getString(it) }
        var currentModeIndex = modes.indexOf(value.subtitleMode).coerceAtLeast(0)
        val modeButtons = ArrayList<Button>()
        val modeRow =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, ui.dp(2), 0, ui.dp(8))
            }

        fun updateModeButtons() {
            modeButtons.forEachIndexed { idx, btn ->
                val isSelected = idx == currentModeIndex
                btn.typeface =
                    if (isSelected) TvTypography.semibold(activity)
                    else TvTypography.regular(activity)
                val radius = ui.dp(6).toFloat()
                val normalFill = if (isSelected) Color.rgb(24, 34, 37) else TvTheme.buttonBackground
                val normalStroke = if (isSelected) ui.green else TvTheme.buttonBorder
                val strokeColor = if (isSelected) ui.green else Color.WHITE
                val normalBox = TvTheme.roundedBox(radius, normalFill, ui.dp(1), normalStroke)
                val focusedBox =
                    TvTheme.roundedBox(radius, TvTheme.buttonFocused, ui.dp(2), strokeColor)
                btn.setBackgroundDrawable(
                    StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused), focusedBox)
                        addState(intArrayOf(android.R.attr.state_pressed), focusedBox)
                        addState(intArrayOf(), normalBox)
                    }
                )
                btn.setTextColor(if (isSelected) ui.green else Color.WHITE)
            }
        }

        labels.forEachIndexed { idx, label ->
            val btn =
                Button(activity).apply {
                    text = label
                    textSize = 13f
                    isFocusable = true
                    setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6))
                    setOnClickListener {
                        currentModeIndex = idx
                        updateModeButtons()
                    }
                }
            modeButtons.add(btn)
            modeRow.addView(
                btn,
                LinearLayout.LayoutParams(0, ui.dp(42), 1f).apply {
                    setMargins(ui.dp(2), 0, ui.dp(2), 0)
                },
            )
        }
        updateModeButtons()
        form.addView(modeRow)

        var dialogRef: AlertDialog? = null
        val cancelBtn =
            TvTheme.createDarkButton(activity, ui, activity.getString(R.string.cancel)) {
                dialogRef?.dismiss()
            }
        lateinit var saveBtn: Button
        saveBtn =
            TvTheme.createPrimaryButton(activity, ui, activity.getString(R.string.save)) {
                fun languages(text: EditText) =
                    text.text
                        .toString()
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                val selected =
                    MediaPreferences(
                        languages(audio),
                        languages(subtitles),
                        modes[currentModeIndex],
                    )
                if (!selected.valid()) {
                    audio.error = activity.getString(R.string.media_language_invalid)
                    return@createPrimaryButton
                }
                saveBtn.isEnabled = false
                model.saveMediaPreferences(
                    selected,
                    { dialogRef?.dismiss() },
                    { error ->
                        saveBtn.isEnabled = true
                        failed(error)
                    },
                )
            }
        val footer =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                setPadding(0, ui.dp(8), 0, 0)
                addView(cancelBtn)
                addView(saveBtn)
            }

        val scroll = TvTheme.boundedScroll(activity).apply { addView(form) }
        card.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        card.addView(footer, LinearLayout.LayoutParams(-1, -2))
        val dialog = AlertDialog.Builder(activity).setView(card).create()
        dialogRef = dialog
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width =
            (activity.resources.displayMetrics.widthPixels * 0.78f).toInt().coerceAtMost(ui.dp(560))
        dialog.window?.setLayout(width, -2)
        return dialog
    }
}
