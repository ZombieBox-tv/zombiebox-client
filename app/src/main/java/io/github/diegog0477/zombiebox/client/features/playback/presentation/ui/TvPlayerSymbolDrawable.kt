package io.github.diegog0477.zombiebox.client.features.playback.presentation.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    QUALITY,
    DESCRIPTION,
}

internal object TvPlayerSymbolPixelGrid {
    fun coordinate(origin: Float, coordinate: Float, scale: Float): Float =
        (origin + coordinate * scale).roundToInt().toFloat()
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

        val left = bounds.left + (bounds.width() - size) / 2f
        val top = bounds.top + (bounds.height() - size) / 2f
        val scale = size / 24f
        val save = canvas.save()
        if (drawPixelAlignedSymbol(canvas, left, top, scale)) {
            canvas.restoreToCount(save)
            return
        }
        canvas.translate(left, top)
        canvas.scale(scale, scale)

        when (symbol) {
            TvPlayerSymbol.DESCRIPTION -> {
                canvas.drawRoundRect(RectF(4f, 4f, 20f, 20f), 2f, 2f, strokePaint)
                canvas.drawLine(7f, 8f, 17f, 8f, strokePaint)
                canvas.drawLine(7f, 12f, 17f, 12f, strokePaint)
                canvas.drawLine(7f, 16f, 13f, 16f, strokePaint)
            }
            else -> Unit
        }
        canvas.restoreToCount(save)
    }

    /**
     * Draws transport shapes directly in drawable pixels so their straight edges land on pixel
     * boundaries. Fractional scaling of the 24-unit paths softened these prominent controls on
     * low-density TV panels.
     */
    private fun drawPixelAlignedSymbol(
        canvas: Canvas,
        left: Float,
        top: Float,
        scale: Float,
    ): Boolean {
        when (symbol) {
            TvPlayerSymbol.AUDIO_TRACKS -> drawPixelAlignedAudioTracks(canvas, left, top, scale)
            TvPlayerSymbol.SUBTITLES -> drawPixelAlignedSubtitles(canvas, left, top, scale)
            TvPlayerSymbol.MINIMIZE -> drawPixelAlignedMinimize(canvas, left, top, scale)
            TvPlayerSymbol.EXTERNAL -> drawPixelAlignedExternal(canvas, left, top, scale)
            TvPlayerSymbol.PLAY -> drawPolygon(canvas, left, top, scale, 7f, 5f, 19f, 12f, 7f, 19f)
            TvPlayerSymbol.PAUSE -> {
                drawPixelRect(canvas, left, top, scale, 6f, 5f, 10f, 19f)
                drawPixelRect(canvas, left, top, scale, 14f, 5f, 18f, 19f)
            }
            TvPlayerSymbol.PREVIOUS -> {
                drawPixelRect(canvas, left, top, scale, 5f, 5f, 8f, 19f)
                drawPolygon(canvas, left, top, scale, 19f, 5f, 8f, 12f, 19f, 19f)
            }
            TvPlayerSymbol.NEXT -> {
                drawPolygon(canvas, left, top, scale, 5f, 5f, 16f, 12f, 5f, 19f)
                drawPixelRect(canvas, left, top, scale, 16f, 5f, 19f, 19f)
            }
            TvPlayerSymbol.SEEK_BACK -> {
                drawPolygon(canvas, left, top, scale, 13f, 6f, 4f, 12f, 13f, 18f)
                drawPolygon(canvas, left, top, scale, 21f, 6f, 12f, 12f, 21f, 18f)
            }
            TvPlayerSymbol.SEEK_FORWARD -> {
                drawPolygon(canvas, left, top, scale, 3f, 6f, 12f, 12f, 3f, 18f)
                drawPolygon(canvas, left, top, scale, 11f, 6f, 20f, 12f, 11f, 18f)
            }
            TvPlayerSymbol.QUALITY -> drawPixelAlignedGear(canvas, left, top, scale)
            TvPlayerSymbol.STOP -> drawPixelRect(canvas, left, top, scale, 6f, 6f, 18f, 18f)
            else -> return false
        }
        return true
    }

    private fun drawPixelAlignedAudioTracks(canvas: Canvas, left: Float, top: Float, scale: Float) {
        drawPolygon(canvas, left, top, scale, 3f, 9f, 7f, 9f, 13f, 5f, 13f, 19f, 7f, 15f, 3f, 15f)
        val oldWidth = strokePaint.strokeWidth
        val oldCap = strokePaint.strokeCap
        strokePaint.strokeWidth = pixelStrokeWidth(scale, 2.2f)
        strokePaint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(
            pixelRect(left, top, scale, 11f, 8f, 17f, 16f),
            -45f,
            90f,
            false,
            strokePaint,
        )
        canvas.drawArc(
            pixelRect(left, top, scale, 13f, 5f, 21f, 19f),
            -50f,
            100f,
            false,
            strokePaint,
        )
        strokePaint.strokeWidth = oldWidth
        strokePaint.strokeCap = oldCap
    }

    private fun drawPixelAlignedSubtitles(canvas: Canvas, left: Float, top: Float, scale: Float) {
        val frameWidth = pixelStrokeWidth(scale, 1.5f).roundToInt().toFloat()
        drawPixelOutline(canvas, left, top, scale, 3f, 6f, 21f, 18f, frameWidth)

        val oldWidth = strokePaint.strokeWidth
        val oldCap = strokePaint.strokeCap
        strokePaint.strokeWidth = pixelStrokeWidth(scale, 1.3f)
        strokePaint.strokeCap = Paint.Cap.SQUARE
        drawPixelLine(canvas, left, top, scale, 10f, 9.5f, 8f, 9.5f)
        drawPixelLine(canvas, left, top, scale, 8f, 9.5f, 7f, 10.5f)
        drawPixelLine(canvas, left, top, scale, 7f, 10.5f, 7f, 13.5f)
        drawPixelLine(canvas, left, top, scale, 7f, 13.5f, 8f, 14.5f)
        drawPixelLine(canvas, left, top, scale, 8f, 14.5f, 10f, 14.5f)
        drawPixelLine(canvas, left, top, scale, 17f, 9.5f, 15f, 9.5f)
        drawPixelLine(canvas, left, top, scale, 15f, 9.5f, 14f, 10.5f)
        drawPixelLine(canvas, left, top, scale, 14f, 10.5f, 14f, 13.5f)
        drawPixelLine(canvas, left, top, scale, 14f, 13.5f, 15f, 14.5f)
        drawPixelLine(canvas, left, top, scale, 15f, 14.5f, 17f, 14.5f)
        strokePaint.strokeWidth = oldWidth
        strokePaint.strokeCap = oldCap
    }

    private fun drawPixelAlignedMinimize(canvas: Canvas, left: Float, top: Float, scale: Float) {
        val frameWidth = pixelStrokeWidth(scale, 1.5f).roundToInt().toFloat()
        drawPixelOutline(canvas, left, top, scale, 3f, 5f, 21f, 19f, frameWidth)
        drawPixelRect(canvas, left, top, scale, 12f, 11f, 19f, 17f)
    }

    private fun drawPixelAlignedExternal(canvas: Canvas, left: Float, top: Float, scale: Float) {
        val oldWidth = strokePaint.strokeWidth
        val oldCap = strokePaint.strokeCap
        strokePaint.strokeWidth = pixelStrokeWidth(scale, 1.5f)
        strokePaint.strokeCap = Paint.Cap.SQUARE
        drawPixelLine(canvas, left, top, scale, 5f, 8f, 5f, 19f)
        drawPixelLine(canvas, left, top, scale, 5f, 19f, 17f, 19f)
        drawPixelLine(canvas, left, top, scale, 19f, 14f, 19f, 18f)
        drawPixelLine(canvas, left, top, scale, 8f, 5f, 12f, 5f)
        drawPixelLine(canvas, left, top, scale, 10f, 14f, 19f, 5f)
        drawPixelLine(canvas, left, top, scale, 14f, 5f, 19f, 5f)
        drawPixelLine(canvas, left, top, scale, 19f, 5f, 19f, 10f)
        strokePaint.strokeWidth = oldWidth
        strokePaint.strokeCap = oldCap
    }

    private fun drawPixelOutline(
        canvas: Canvas,
        left: Float,
        top: Float,
        scale: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        thickness: Float,
    ) {
        val leftPixel = pixelCoordinate(left, x1, scale)
        val topPixel = pixelCoordinate(top, y1, scale)
        val rightPixel = pixelCoordinate(left, x2, scale)
        val bottomPixel = pixelCoordinate(top, y2, scale)
        canvas.drawRect(leftPixel, topPixel, rightPixel, topPixel + thickness, fillPaint)
        canvas.drawRect(leftPixel, bottomPixel - thickness, rightPixel, bottomPixel, fillPaint)
        canvas.drawRect(leftPixel, topPixel, leftPixel + thickness, bottomPixel, fillPaint)
        canvas.drawRect(rightPixel - thickness, topPixel, rightPixel, bottomPixel, fillPaint)
    }

    private fun drawPixelLine(
        canvas: Canvas,
        left: Float,
        top: Float,
        scale: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ) {
        canvas.drawLine(
            pixelStrokeCoordinate(left, x1, scale),
            pixelStrokeCoordinate(top, y1, scale),
            pixelStrokeCoordinate(left, x2, scale),
            pixelStrokeCoordinate(top, y2, scale),
            strokePaint,
        )
    }

    private fun pixelRect(
        left: Float,
        top: Float,
        scale: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): RectF =
        RectF(
            pixelCoordinate(left, x1, scale),
            pixelCoordinate(top, y1, scale),
            pixelCoordinate(left, x2, scale),
            pixelCoordinate(top, y2, scale),
        )

    private fun pixelStrokeCoordinate(origin: Float, coordinate: Float, scale: Float): Float =
        pixelCoordinate(origin, coordinate, scale) + 0.5f

    private fun pixelStrokeWidth(scale: Float, designWidth: Float): Float =
        (designWidth * scale).roundToInt().coerceAtLeast(2).toFloat()

    private fun drawPolygon(
        canvas: Canvas,
        left: Float,
        top: Float,
        scale: Float,
        vararg points: Float,
    ) {
        if (points.size < 6 || points.size % 2 != 0) return
        val path = Path()
        path.moveTo(pixelCoordinate(left, points[0], scale), pixelCoordinate(top, points[1], scale))
        var index = 2
        while (index < points.size) {
            path.lineTo(
                pixelCoordinate(left, points[index], scale),
                pixelCoordinate(top, points[index + 1], scale),
            )
            index += 2
        }
        path.close()
        canvas.drawPath(path, fillPaint)
    }

    private fun drawPixelRect(
        canvas: Canvas,
        left: Float,
        top: Float,
        scale: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ) {
        canvas.drawRect(
            pixelCoordinate(left, x1, scale),
            pixelCoordinate(top, y1, scale),
            pixelCoordinate(left, x2, scale),
            pixelCoordinate(top, y2, scale),
            fillPaint,
        )
    }

    private fun drawPixelAlignedGear(canvas: Canvas, left: Float, top: Float, scale: Float) {
        // Separate strokes avoid the old API 13 even-odd Path fill rendering
        // only the center circle and leaving the gear invisible.
        val centerX = pixelCoordinate(left, 12f, scale)
        val centerY = pixelCoordinate(top, 12f, scale)
        val oldWidth = strokePaint.strokeWidth
        val oldCap = strokePaint.strokeCap
        strokePaint.strokeWidth = (2.8f * scale).roundToInt().coerceAtLeast(2).toFloat()
        strokePaint.strokeCap = Paint.Cap.SQUARE
        for (tooth in 0 until 8) {
            val angle = tooth * Math.PI / 4.0
            val fromX = (centerX + cos(angle) * 6.8f * scale).roundToInt().toFloat()
            val fromY = (centerY + sin(angle) * 6.8f * scale).roundToInt().toFloat()
            val toX = (centerX + cos(angle) * 9f * scale).roundToInt().toFloat()
            val toY = (centerY + sin(angle) * 9f * scale).roundToInt().toFloat()
            canvas.drawLine(fromX, fromY, toX, toY, strokePaint)
        }
        canvas.drawCircle(
            centerX,
            centerY,
            (6f * scale).roundToInt().coerceAtLeast(3).toFloat(),
            strokePaint,
        )
        strokePaint.strokeWidth = oldWidth
        strokePaint.strokeCap = oldCap
    }

    private fun pixelCoordinate(origin: Float, coordinate: Float, scale: Float): Float =
        TvPlayerSymbolPixelGrid.coordinate(origin, coordinate, scale)

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
