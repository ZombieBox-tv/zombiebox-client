package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

@Suppress("DEPRECATION")
object GuideTheme {
    val bgDark = Color.rgb(10, 15, 16)
    val panelDark = Color.rgb(16, 21, 23)
    val rulerBg = Color.rgb(14, 19, 21)
    val rulerBorder = Color.rgb(32, 41, 44)

    val cardNormalBg = Color.rgb(26, 33, 36)
    val cardNormalBorder = Color.rgb(42, 53, 56)
    val cardFocusedBg = Color.WHITE
    val cardFocusedBorder = Color.rgb(66, 165, 245)
    val cardFocusedText = Color.rgb(15, 20, 22)

    val badgeNormalBg = Color.rgb(18, 23, 25)
    val badgeNormalBorder = Color.rgb(36, 45, 48)
    val badgeActiveRowBg = Color.rgb(28, 38, 42)
    val badgeActiveRowBorder = Color.rgb(66, 165, 245)

    val textWhite = Color.WHITE
    val textMuted = Color.rgb(167, 180, 186)
    val textSubtle = Color.rgb(120, 134, 140)
    val accentIptv = Color.rgb(66, 165, 245)
    val accentProgress = Color.rgb(255, 69, 58)
    val accentStale = Color.rgb(255, 159, 10)

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun roundedBox(
        radius: Float,
        fillColor: Int,
        strokeWidth: Int = 0,
        strokeColor: Int = Color.TRANSPARENT,
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radius
            if (strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }

    fun buttonBackground(context: Context, accent: Int = accentIptv): StateListDrawable {
        val radius = dp(context, 6).toFloat()
        val normal =
            roundedBox(radius, Color.rgb(24, 31, 33), dp(context, 1), Color.rgb(45, 55, 58))
        val focused = roundedBox(radius, Color.rgb(36, 46, 50), dp(context, 2), accent)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_pressed), focused)
            addState(intArrayOf(), normal)
        }
    }
}
