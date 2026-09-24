package io.github.diegog0477.zombiebox.client.features.playback.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Lightweight legacy-safe progress bar drawing position and duration with provider accent. */
class TvPlaybackProgressBar(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()
    private val progressRect = RectF()
    private var progressFraction = 0f
    var accentColor: Int = Color.rgb(76, 239, 105)
        set(value) {
            field = value
            invalidate()
        }

    init {
        isFocusable = false
    }

    fun setProgress(positionMs: Int, durationMs: Int) {
        progressFraction =
            if (durationMs > 0) {
                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            } else 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = h / 2f
        trackRect.set(0f, 0f, w, h)
        paint.color = Color.rgb(42, 53, 56)
        canvas.drawRoundRect(trackRect, radius, radius, paint)

        if (progressFraction > 0f) {
            progressRect.set(0f, 0f, w * progressFraction, h)
            paint.color = accentColor
            canvas.drawRoundRect(progressRect, radius, radius, paint)
        }
    }
}
