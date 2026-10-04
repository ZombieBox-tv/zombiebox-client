package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.home.domain.model.PlaybackValidationReason

class PlaybackValidationBanner(
    context: Context,
    private val ui: TvWidgets,
    provider: String,
    reason: PlaybackValidationReason,
    runPlaybackCheck: () -> Unit,
) : LinearLayout(context) {
    init {
        val narrow =
            context.resources.displayMetrics.widthPixels /
                context.resources.displayMetrics.density < 500f
        orientation = if (narrow) VERTICAL else HORIZONTAL
        gravity = if (narrow) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        setPadding(ui.dp(14), ui.dp(8), ui.dp(10), ui.dp(8))
        setBackgroundDrawable(ui.serviceCardBackground(provider))

        val copy = ui.column()
        copy.addView(
            ui.text(context.getString(R.string.playback_validation_title), 16f).apply {
                typeface = ui.bold
                setPadding(ui.dp(3), ui.dp(2), ui.dp(3), ui.dp(2))
            }
        )
        copy.addView(
            ui.text(
                    context.getString(
                        if (reason == PlaybackValidationReason.EXPIRED)
                            R.string.playback_validation_expired
                        else R.string.playback_validation_refresh
                    ),
                    13f,
                    Color.WHITE,
                )
                .apply {
                    maxLines = 2
                    setPadding(ui.dp(3), ui.dp(2), ui.dp(8), ui.dp(2))
                }
        )
        addView(copy, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(
            ui.action(
                    context.getString(R.string.playback_validation_action),
                    ui.providerAccent(provider),
                    runPlaybackCheck,
                )
                .apply { tag = FOCUS_KEY }
        )
        if (narrow) {
            copy.layoutParams = LayoutParams(-1, LayoutParams.WRAP_CONTENT)
            getChildAt(1).layoutParams =
                LayoutParams(-2, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.RIGHT }
        }
    }

    companion object {
        const val FOCUS_ROW_ID = "home:playback-validation"
        const val FOCUS_KEY = "home:playback-validation:run"
    }
}
