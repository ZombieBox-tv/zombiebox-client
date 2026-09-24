package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

enum class PlaybackSymbol {
    PLAY,
    PAUSE,
    PREVIOUS,
    NEXT,
    EXPAND,
}

/** Universal accessible media symbols, code-drawn for API 9+ without vector or raster assets. */
class PlaybackSymbolDrawable(var symbol: PlaybackSymbol, var color: Int = Color.WHITE) :
    Drawable() {
    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = this@PlaybackSymbolDrawable.color
        }
    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = this@PlaybackSymbolDrawable.color
        }

    override fun draw(canvas: Canvas) {
        fillPaint.color = color
        strokePaint.color = color
        val size = minOf(bounds.width(), bounds.height()).toFloat()
        if (size <= 0f) return

        val save = canvas.save()
        canvas.translate(
            bounds.left + (bounds.width() - size) / 2f,
            bounds.top + (bounds.height() - size) / 2f,
        )
        canvas.scale(size / 24f, size / 24f)

        when (symbol) {
            PlaybackSymbol.PLAY -> {
                val path =
                    Path().apply {
                        moveTo(7f, 5f)
                        lineTo(19f, 12f)
                        lineTo(7f, 19f)
                        close()
                    }
                canvas.drawPath(path, fillPaint)
            }
            PlaybackSymbol.PAUSE -> {
                canvas.drawRect(6f, 5f, 10f, 19f, fillPaint)
                canvas.drawRect(14f, 5f, 18f, 19f, fillPaint)
            }
            PlaybackSymbol.PREVIOUS -> {
                canvas.drawRect(5f, 5f, 7.5f, 19f, fillPaint)
                val path =
                    Path().apply {
                        moveTo(19f, 5f)
                        lineTo(8f, 12f)
                        lineTo(19f, 19f)
                        close()
                    }
                canvas.drawPath(path, fillPaint)
            }
            PlaybackSymbol.NEXT -> {
                val path =
                    Path().apply {
                        moveTo(5f, 5f)
                        lineTo(16f, 12f)
                        lineTo(5f, 19f)
                        close()
                    }
                canvas.drawPath(path, fillPaint)
                canvas.drawRect(16.5f, 5f, 19f, 19f, fillPaint)
            }
            PlaybackSymbol.EXPAND -> {
                // Corner brackets
                canvas.drawLine(4f, 9f, 4f, 4f, strokePaint)
                canvas.drawLine(4f, 4f, 9f, 4f, strokePaint)
                canvas.drawLine(15f, 4f, 20f, 4f, strokePaint)
                canvas.drawLine(20f, 4f, 20f, 9f, strokePaint)
                canvas.drawLine(4f, 15f, 4f, 20f, strokePaint)
                canvas.drawLine(4f, 20f, 9f, 20f, strokePaint)
                canvas.drawLine(15f, 20f, 20f, 20f, strokePaint)
                canvas.drawLine(20f, 20f, 20f, 15f, strokePaint)
            }
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(filter: ColorFilter?) {
        fillPaint.colorFilter = filter
        strokePaint.colorFilter = filter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
