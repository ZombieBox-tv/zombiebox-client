package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.widget.ImageView
import io.github.diegog0477.zombiebox.client.R
import kotlin.math.roundToInt

/** Provider artwork uses lossless official raster marks; local system symbols stay code-drawn. */
class ServiceMarkView(context: Context, service: String, accent: Int) : ImageView(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var service = service
    private var accent = accent

    init {
        scaleType = ScaleType.FIT_CENTER
        bind(service, accent)
    }

    fun bind(service: String, accent: Int) {
        this.service = service
        this.accent = accent
        val logo =
            when (service) {
                "youtube" -> R.drawable.service_youtube
                "plex" -> R.drawable.service_plex
                "jellyfin" -> R.drawable.service_jellyfin
                "stremio" -> R.drawable.service_stremio
                "spotify" -> R.drawable.service_spotify
                "rebrowser" -> R.drawable.service_chromium
                else -> 0
            }
        if (logo != 0) setImageResource(logo) else setImageDrawable(null)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawable != null) return
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        paint.style = Paint.Style.FILL
        canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        val detailedSystemMark = service == "airplay" || service == "android_mirror"
        if (detailedSystemMark) {
            if (service == "airplay") drawAirPlayMark(canvas, size)
            else drawAndroidMirrorMark(canvas, size)
            canvas.restore()
            return
        }
        canvas.scale(size / 40f, size / 40f)
        when (service) {
            "youtube" -> {
                paint.color = accent
                canvas.drawRoundRect(RectF(2f, 8f, 38f, 32f), 7f, 7f, paint)
                paint.color = Color.WHITE
                triangle(canvas, 16f, 13f, 16f, 27f, 29f, 20f)
            }
            "spotify" -> {
                paint.color = accent
                canvas.drawCircle(20f, 20f, 18f, paint)
                paint.color = Color.rgb(10, 15, 16)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.6f
                for (offset in 0..2) {
                    val y = 15f + offset * 6f
                    canvas.drawArc(RectF(8f, y - 4f, 33f, y + 8f), 205f, 125f, false, paint)
                }
                paint.style = Paint.Style.FILL
            }
            "stremio" -> {
                paint.color = accent
                triangle(canvas, 20f, 2f, 38f, 20f, 20f, 38f)
                triangle(canvas, 20f, 2f, 20f, 38f, 2f, 20f)
                paint.color = Color.WHITE
                triangle(canvas, 16f, 13f, 16f, 27f, 28f, 20f)
            }
            "iptv" -> {
                paint.color = accent
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawRoundRect(RectF(3f, 9f, 37f, 31f), 2f, 2f, paint)
                canvas.drawLine(20f, 31f, 20f, 36f, paint)
                canvas.drawLine(12f, 36f, 28f, 36f, paint)
                paint.style = Paint.Style.FILL
            }
            "plex" -> {
                paint.color = accent
                triangle(canvas, 12f, 3f, 29f, 20f, 12f, 37f)
                paint.color = Color.rgb(10, 15, 16)
                triangle(canvas, 14f, 11f, 23f, 20f, 14f, 29f)
            }
            else -> {
                paint.color = accent
                canvas.drawRoundRect(RectF(4f, 8f, 36f, 33f), 3f, 3f, paint)
                paint.color = Color.rgb(10, 15, 16)
                canvas.drawRect(10f, 14f, 30f, 27f, paint)
            }
        }
        canvas.restore()
    }

    private fun triangle(
        canvas: Canvas,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
    ) {
        val shape =
            Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
                lineTo(x3, y3)
                close()
            }
        canvas.drawPath(shape, paint)
    }

    private fun drawAirPlayMark(canvas: Canvas, size: Float) {
        val scale = size / 64f
        paint.color = accent
        paint.style = Paint.Style.FILL
        val edge = (3.5f * scale).roundToInt().coerceAtLeast(2).toFloat()
        drawPixelFrame(canvas, scale, 7f, 7f, 57f, 42f, edge)
        paint.style = Paint.Style.FILL
        triangle(
            canvas,
            pixelCoordinate(19f, scale),
            pixelCoordinate(57f, scale),
            pixelCoordinate(45f, scale),
            pixelCoordinate(57f, scale),
            pixelCoordinate(32f, scale),
            pixelCoordinate(38f, scale),
        )
    }

    private fun drawAndroidMirrorMark(canvas: Canvas, size: Float) {
        val scale = size / 64f
        paint.color = accent
        paint.style = Paint.Style.FILL
        val edge = (3.2f * scale).roundToInt().coerceAtLeast(2).toFloat()
        drawPixelFrame(canvas, scale, 23f, 6f, 59f, 38f, edge)
        drawPixelFrame(canvas, scale, 5f, 22f, 29f, 58f, edge)
        val left = pixelCoordinate(13f, scale)
        val right = pixelCoordinate(21f, scale)
        val bottom = pixelCoordinate(52f, scale)
        canvas.drawRect(left, bottom, right, bottom + edge, paint)

        val oldStyle = paint.style
        val oldWidth = paint.strokeWidth
        val oldCap = paint.strokeCap
        val oldJoin = paint.strokeJoin
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (2.5f * scale).roundToInt().coerceAtLeast(2).toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val startX = pixelStrokeCoordinate(23f, scale)
        val startY = pixelStrokeCoordinate(31f, scale)
        val cornerX = pixelStrokeCoordinate(38f, scale)
        val cornerY = pixelStrokeCoordinate(18f, scale)
        val arrow =
            Path().apply {
                moveTo(startX, startY)
                lineTo(cornerX, cornerY)
                lineTo(pixelStrokeCoordinate(32f, scale), cornerY)
                moveTo(cornerX, cornerY)
                lineTo(cornerX, pixelStrokeCoordinate(24f, scale))
            }
        canvas.drawPath(arrow, paint)
        paint.style = oldStyle
        paint.strokeWidth = oldWidth
        paint.strokeCap = oldCap
        paint.strokeJoin = oldJoin
    }

    private fun drawPixelFrame(
        canvas: Canvas,
        scale: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        thickness: Float,
    ) {
        val left = pixelCoordinate(x1, scale)
        val top = pixelCoordinate(y1, scale)
        val right = pixelCoordinate(x2, scale)
        val bottom = pixelCoordinate(y2, scale)
        canvas.drawRect(left, top, right, top + thickness, paint)
        canvas.drawRect(left, bottom - thickness, right, bottom, paint)
        canvas.drawRect(left, top, left + thickness, bottom, paint)
        canvas.drawRect(right - thickness, top, right, bottom, paint)
    }

    private fun pixelCoordinate(coordinate: Float, scale: Float): Float =
        (coordinate * scale).roundToInt().toFloat()

    private fun pixelStrokeCoordinate(coordinate: Float, scale: Float): Float =
        pixelCoordinate(coordinate, scale) + 0.5f
}
