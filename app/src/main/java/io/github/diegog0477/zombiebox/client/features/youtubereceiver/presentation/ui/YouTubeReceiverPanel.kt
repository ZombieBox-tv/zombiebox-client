package io.github.diegog0477.zombiebox.client.features.youtubereceiver.presentation.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvTheme
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.domain.model.YouTubePlaybackIssue
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.domain.model.YouTubeReception

/** The TV code changes with the receiver lease, so the panel stays open and updates in place. */
@Suppress("DEPRECATION")
class YouTubeReceiverPanel(
    private val activity: Activity,
    private val onEnable: () -> Unit,
    private val onDisable: () -> Unit,
    private val onClosed: (YouTubeReceiverPanel) -> Unit,
) {
    private val ui = TvWidgets(activity) { activity.resources.getColor(R.color.accent_youtube) }
    private val accent = ui.providerAccent("youtube")
    private var dialog: Dialog? = null
    private lateinit var stateLabel: TextView
    private lateinit var codeCard: LinearLayout
    private lateinit var codeLabel: TextView
    private lateinit var playbackIssueLabel: TextView
    private lateinit var enableButton: Button
    private lateinit var disableButton: Button

    val visible: Boolean
        get() = dialog?.isShowing == true

    fun show(state: YouTubeReception) {
        if (visible || activity.isFinishing) return
        val previousFocus = activity.currentFocus
        val page = FrameLayout(activity).apply { setBackgroundColor(TvTheme.shellBackground) }
        val content = ui.column().apply { setPadding(ui.dp(24), ui.dp(22), ui.dp(24), ui.dp(22)) }
        val scroll = ScrollView(activity).apply { addView(content) }
        val width =
            minOf(activity.resources.displayMetrics.widthPixels, ui.dp(850))
                .coerceAtLeast(ui.dp(240))
        page.addView(scroll, FrameLayout.LayoutParams(width, -1, Gravity.CENTER_HORIZONTAL))

        val panel = Dialog(activity)
        panel.requestWindowFeature(Window.FEATURE_NO_TITLE)
        panel.setContentView(page)
        panel.setOnDismissListener {
            if (dialog === panel) dialog = null
            onClosed(this)
            previousFocus?.post {
                if (!activity.isFinishing && previousFocus.windowToken != null) {
                    previousFocus.requestFocus()
                }
            }
        }
        dialog = panel

        val header = ui.row()
        val titles = ui.column()
        titles.addView(
            ui.text(activity.getString(R.string.youtube), 13f, accent).apply {
                typeface = TvTypography.semibold(activity)
            }
        )
        titles.addView(
            ui.text(activity.getString(R.string.youtube_receiver), 29f).apply {
                typeface = TvTypography.semibold(activity)
            }
        )
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ui.button(R.string.close) { panel.dismiss() })
        content.addView(header)

        val card =
            ui.column().apply {
                setPadding(ui.dp(22), ui.dp(18), ui.dp(22), ui.dp(20))
                setBackgroundDrawable(ui.box(Color.rgb(17, 23, 25), ui.providerAccent("youtube")))
            }
        stateLabel =
            ui.text("", 15f).apply {
                typeface = TvTypography.semibold(activity)
                setPadding(0, 0, 0, ui.dp(10))
            }
        card.addView(stateLabel)
        codeCard =
            ui.column().apply {
                setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16))
                setBackgroundDrawable(ui.box(TvTheme.inputBackground, TvTheme.inputBorder))
            }
        codeLabel =
            ui.text("", 26f).apply {
                typeface = TvTypography.semibold(activity)
                isSaveEnabled = false
            }
        codeCard.addView(codeLabel)
        card.addView(codeCard, LinearLayout.LayoutParams(-1, -2))
        card.addView(
            ui.text(activity.getString(R.string.youtube_receiver_detail), 16f, ui.muted).apply {
                setPadding(0, ui.dp(16), 0, 0)
            }
        )
        playbackIssueLabel =
            ui.text("", 17f, Color.WHITE).apply {
                typeface = TvTypography.semibold(activity)
                setPadding(0, ui.dp(18), 0, 0)
                visibility = View.GONE
            }
        card.addView(playbackIssueLabel)
        content.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = ui.dp(22)
                bottomMargin = ui.dp(20)
            },
        )

        val actions = ui.row()
        enableButton =
            ui.primary(R.string.enable) {
                stateLabel.setText(R.string.loading)
                onEnable()
            }
        disableButton =
            ui.button(R.string.disable) {
                stateLabel.setText(R.string.loading)
                onDisable()
            }
        actions.addView(enableButton)
        actions.addView(disableButton)
        content.addView(actions)

        panel.show()
        panel.window?.apply {
            setBackgroundDrawable(ColorDrawable(TvTheme.shellBackground))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
        update(state)
        if (enableButton.visibility == View.VISIBLE) enableButton.requestFocus()
        else disableButton.requestFocus()
    }

    fun update(state: YouTubeReception) {
        if (!visible) return
        playbackIssueLabel.visibility = if (state.playbackIssue == null) View.GONE else View.VISIBLE
        playbackIssueLabel.setText(
            when (state.playbackIssue) {
                YouTubePlaybackIssue.EXTERNAL_PLAYER_REQUIRED ->
                    R.string.youtube_receiver_playback_unsupported
                YouTubePlaybackIssue.UNAVAILABLE -> R.string.youtube_receiver_playback_unavailable
                null -> R.string.youtube_receiver_playback_unavailable
            }
        )
        stateLabel.setText(
            when {
                state.failed -> R.string.unavailable
                state.enabled -> R.string.enabled
                else -> R.string.disabled
            }
        )
        stateLabel.setTextColor(
            when {
                state.failed -> accent
                state.enabled -> ui.green
                else -> ui.muted
            }
        )
        codeCard.visibility = if (state.enabled || state.failed) View.VISIBLE else View.GONE
        codeLabel.text =
            when {
                state.failed -> activity.getString(R.string.unavailable)
                state.receiver?.code?.isNotEmpty() == true ->
                    activity.getString(R.string.youtube_tv_code, state.receiver.code)
                else -> activity.getString(R.string.loading)
            }
        val wasEnableFocused = enableButton.hasFocus()
        val wasDisableFocused = disableButton.hasFocus()
        enableButton.visibility = if (!state.enabled || state.failed) View.VISIBLE else View.GONE
        disableButton.visibility = if (state.enabled) View.VISIBLE else View.GONE
        if (wasEnableFocused && enableButton.visibility != View.VISIBLE)
            disableButton.requestFocus()
        if (wasDisableFocused && disableButton.visibility != View.VISIBLE)
            enableButton.requestFocus()
    }
}
