package io.github.diegog0477.zombiebox.client.core.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import java.util.Locale

/** Shared legacy-safe view primitives and provider colors; no data access. */
@Suppress("DEPRECATION")
class TvWidgets(
    private val context: Context,
    private val accent: () -> Int = { context.resources.getColor(R.color.accent_zombie) },
) {
    val panel = Color.rgb(24, 31, 33)
    val muted = Color.rgb(167, 180, 186)
    val background = Color.rgb(10, 15, 16)
    val green
        get() = context.resources.getColor(R.color.accent_zombie)

    val bold
        get() = TvTypography.semibold(context)

    fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun row() =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    fun text(value: String, size: Float, color: Int = Color.WHITE) =
        TextView(context).apply {
            text = value
            textSize = size
            typeface = TvTypography.regular(context)
            setTextColor(color)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

    fun box(color: Int, stroke: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), stroke)
        }

    fun focusBackground(accent: Int = accent()): StateListDrawable =
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), box(Color.rgb(32, 39, 42), accent))
            addState(intArrayOf(android.R.attr.state_pressed), box(Color.rgb(32, 39, 42), accent))
            addState(intArrayOf(), box(panel))
        }

    /** Contextual low-contrast surfaces give service cards their own identity. */
    fun serviceCardBackground(id: String): StateListDrawable {
        val color = providerAccent(id)
        val orientation =
            when (id) {
                "plex",
                "spotify" -> GradientDrawable.Orientation.RIGHT_LEFT
                "youtube",
                "stremio" -> GradientDrawable.Orientation.TL_BR
                else -> GradientDrawable.Orientation.LEFT_RIGHT
            }
        fun mix(base: Int, overlay: Int, fraction: Float): Int =
            Color.rgb(
                (Color.red(base) * (1f - fraction) + Color.red(overlay) * fraction).toInt(),
                (Color.green(base) * (1f - fraction) + Color.green(overlay) * fraction).toInt(),
                (Color.blue(base) * (1f - fraction) + Color.blue(overlay) * fraction).toInt(),
            )
        fun surface(focused: Boolean): GradientDrawable =
            GradientDrawable(
                    orientation,
                    intArrayOf(
                        mix(panel, color, if (focused) 0.24f else 0.14f),
                        mix(panel, color, if (focused) 0.11f else 0.04f),
                    ),
                )
                .apply {
                    cornerRadius = dp(9).toFloat()
                    setStroke(
                        dp(if (focused) 2 else 1),
                        if (focused) color else mix(panel, color, 0.27f),
                    )
                }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), surface(true))
            addState(intArrayOf(android.R.attr.state_pressed), surface(true))
            addState(intArrayOf(), surface(false))
        }
    }

    fun action(label: String, accent: Int = accent(), click: () -> Unit) =
        Button(context).apply {
            text = label
            textSize = 14f
            typeface = bold
            setTextColor(Color.WHITE)
            isFocusable = true
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setBackgroundDrawable(focusBackground(accent))
            tag = "action:$label"
            setOnClickListener { click() }
            layoutParams =
                LinearLayout.LayoutParams(-2, dp(44)).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
        }

    fun button(label: Int, click: () -> Unit) =
        action(context.getString(label), click = click).apply { tag = "button:$label" }

    /** Selection identifies the current section; focus identifies the remote's next action. */
    fun navigation(label: String, provider: String, selected: Boolean, click: () -> Unit) =
        action(label, providerAccent(provider), click).apply {
            val color = providerAccent(provider)
            tag = "nav:$provider"
            isSelected = selected
            textSize = 13f
            setPadding(dp(9), dp(6), dp(9), dp(6))
            setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                    intArrayOf(this@TvWidgets.background, Color.WHITE),
                )
            )
            setBackgroundDrawable(
                StateListDrawable().apply {
                    addState(
                        intArrayOf(android.R.attr.state_selected, android.R.attr.state_focused),
                        box(color, Color.WHITE),
                    )
                    addState(intArrayOf(android.R.attr.state_selected), box(color))
                    addState(intArrayOf(android.R.attr.state_focused), box(panel, color))
                    addState(intArrayOf(android.R.attr.state_pressed), box(panel, color))
                    addState(intArrayOf(), box(Color.TRANSPARENT))
                }
            )
        }

    fun primary(label: Int, click: () -> Unit) =
        button(label, click).apply {
            setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(muted, this@TvWidgets.background),
                )
            )
            setBackgroundDrawable(
                StateListDrawable().apply {
                    addState(intArrayOf(-android.R.attr.state_enabled), box(panel))
                    addState(intArrayOf(android.R.attr.state_focused), box(accent(), Color.WHITE))
                    addState(intArrayOf(android.R.attr.state_pressed), box(accent(), Color.WHITE))
                    addState(intArrayOf(), box(accent()))
                }
            )
        }

    fun progress(positionMs: Int, durationMs: Int): View =
        object : View(context) {
            private val paint = Paint()
            private val fraction =
                (positionMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)

            init {
                layoutParams =
                    LinearLayout.LayoutParams(-1, dp(3)).apply {
                        setMargins(dp(4), dp(4), dp(4), 0)
                    }
                isFocusable = false
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                paint.color = Color.rgb(60, 70, 72)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.color = accent()
                canvas.drawRect(0f, 0f, width * fraction, height.toFloat(), paint)
            }
        }

    fun formatTime(ms: Int): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }

    fun providerAccent(id: String): Int =
        context.resources.getColor(
            when (id) {
                "youtube" -> R.color.accent_youtube
                "plex" -> R.color.accent_plex
                "stremio" -> R.color.accent_stremio
                "jellyfin" -> R.color.accent_jellyfin
                "iptv" -> R.color.accent_iptv
                "spotify" -> R.color.accent_spotify
                "airplay" -> R.color.accent_airplay
                "rebrowser" -> R.color.accent_browser
                else -> R.color.accent_zombie
            }
        )

    fun serviceTitle(id: String): String =
        context.getString(
            when (id) {
                "local" -> R.string.local_library
                "youtube" -> R.string.youtube
                "plex" -> R.string.plex
                "jellyfin" -> R.string.jellyfin
                "stremio" -> R.string.stremio
                "spotify" -> R.string.spotify
                "iptv" -> R.string.iptv
                "airplay" -> R.string.airplay
                "android_mirror" -> R.string.android_mirror
                "rebrowser" -> R.string.browser
                else -> R.string.apps_content
            }
        )

    fun localizedState(state: String): String =
        context.getString(
            when (state) {
                "HEALTHY" -> R.string.ready
                "STARTING",
                "BUFFERING" -> R.string.loading
                "DISABLED" -> R.string.disabled
                "PLAYING" -> R.string.playing
                "PAUSED" -> R.string.paused
                "ENDED" -> R.string.ended
                "STOPPED" -> R.string.stopped
                "NEEDS_SETUP",
                "UNCONFIGURED" -> R.string.needs_setup
                else -> R.string.unavailable
            }
        )

    /** A listening mirror relay remains idle until a sender starts publishing. */
    fun localizedServiceState(provider: String, state: String): String =
        if (provider == "android_mirror" && state == "STARTING") {
            context.getString(R.string.mirror_waiting_for_stream)
        } else {
            localizedState(state)
        }
}
