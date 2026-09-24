package io.github.diegog0477.zombiebox.client.features.playback.presentation.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

enum class TvPlayerSymbol {
    PLAY,
    PAUSE,
    PREVIOUS,
    NEXT,
    SEEK_BACK,
    SEEK_FORWARD,
    AUDIO_TRACKS,
    SUBTITLES,
    MINIMIZE,
    EXTERNAL,
    STOP,
}

/** Code-drawn TV player symbols for API 9+ compatibility without XML vectors or raster images. */
class TvPlayerSymbolDrawable(var symbol: TvPlayerSymbol, var color: Int = Color.WHITE) :
    Drawable() {
    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = this@TvPlayerSymbolDrawable.color
        }
    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = this@TvPlayerSymbolDrawable.color
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
            TvPlayerSymbol.PLAY -> {
                val path =
                    Path().apply {
                        moveTo(7f, 5f)
                        lineTo(19f, 12f)
                        lineTo(7f, 19f)
                        close()
                    }
                canvas.drawPath(path, fillPaint)
            }
            TvPlayerSymbol.PAUSE -> {
                canvas.drawRect(6f, 5f, 10f, 19f, fillPaint)
                canvas.drawRect(14f, 5f, 18f, 19f, fillPaint)
            }
            TvPlayerSymbol.PREVIOUS -> {
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
            TvPlayerSymbol.NEXT -> {
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
            TvPlayerSymbol.SEEK_BACK -> {
                val p1 =
                    Path().apply {
                        moveTo(13f, 6f)
                        lineTo(4.5f, 12f)
                        lineTo(13f, 18f)
                        close()
                    }
                canvas.drawPath(p1, fillPaint)
                val p2 =
                    Path().apply {
                        moveTo(20.5f, 6f)
                        lineTo(12f, 12f)
                        lineTo(20.5f, 18f)
                        close()
                    }
                canvas.drawPath(p2, fillPaint)
            }
            TvPlayerSymbol.SEEK_FORWARD -> {
                val p1 =
                    Path().apply {
                        moveTo(3.5f, 6f)
                        lineTo(12f, 12f)
                        lineTo(3.5f, 18f)
                        close()
                    }
                canvas.drawPath(p1, fillPaint)
                val p2 =
                    Path().apply {
                        moveTo(11f, 6f)
                        lineTo(19.5f, 12f)
                        lineTo(11f, 18f)
                        close()
                    }
                canvas.drawPath(p2, fillPaint)
            }
            TvPlayerSymbol.AUDIO_TRACKS -> {
                val speaker =
                    Path().apply {
                        moveTo(4f, 9f)
                        lineTo(8f, 9f)
                        lineTo(13f, 5f)
                        lineTo(13f, 19f)
                        lineTo(8f, 15f)
                        lineTo(4f, 15f)
                        close()
                    }
                canvas.drawPath(speaker, fillPaint)
                canvas.drawArc(RectF(11f, 8f, 17f, 16f), -45f, 90f, false, strokePaint)
                canvas.drawArc(RectF(13f, 5f, 21f, 19f), -50f, 100f, false, strokePaint)
            }
            TvPlayerSymbol.SUBTITLES -> {
                canvas.drawRoundRect(RectF(3f, 6f, 21f, 18f), 2.5f, 2.5f, strokePaint)
                canvas.drawArc(RectF(6f, 9f, 11f, 15f), 45f, 270f, false, strokePaint)
                canvas.drawArc(RectF(13f, 9f, 18f, 15f), 45f, 270f, false, strokePaint)
            }
            TvPlayerSymbol.MINIMIZE -> {
                canvas.drawRect(3f, 5f, 21f, 19f, strokePaint)
                canvas.drawRect(12f, 11f, 19f, 17f, fillPaint)
            }
            TvPlayerSymbol.EXTERNAL -> {
                val box =
                    Path().apply {
                        moveTo(12f, 5f)
                        lineTo(5f, 5f)
                        lineTo(5f, 19f)
                        lineTo(19f, 19f)
                        lineTo(19f, 12f)
                    }
                canvas.drawPath(box, strokePaint)
                canvas.drawLine(12f, 12f, 19.5f, 4.5f, strokePaint)
                canvas.drawLine(14.5f, 4.5f, 19.5f, 4.5f, strokePaint)
                canvas.drawLine(19.5f, 4.5f, 19.5f, 9.5f, strokePaint)
            }
            TvPlayerSymbol.STOP -> {
                canvas.drawRect(6f, 6f, 18f, 18f, fillPaint)
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

    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
