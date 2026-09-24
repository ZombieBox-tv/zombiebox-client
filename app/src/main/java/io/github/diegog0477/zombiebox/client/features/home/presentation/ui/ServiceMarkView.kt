package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Small, code-drawn service marks; safe on Android 2.3 without vector drawable support. */
class ServiceMarkView(context: Context, private val service: String, private val accent: Int) :
    View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
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
            "airplay",
            "android_mirror" -> {
                paint.color = accent
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                canvas.drawRoundRect(RectF(3f, 5f, 37f, 30f), 2f, 2f, paint)
                paint.style = Paint.Style.FILL
                triangle(canvas, 20f, 23f, 31f, 37f, 9f, 37f)
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
}
