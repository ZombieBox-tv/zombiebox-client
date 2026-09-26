package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Original low-cost line icons, rendered without API21 vector support or bitmap assets. */
class HeaderIcon(private val settings: Boolean, color: Int) : Drawable() {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    override fun draw(canvas: Canvas) {
        val size = minOf(bounds.width(), bounds.height()).toFloat()
        if (size <= 0f) return

        val left = bounds.left + (bounds.width() - size) / 2f
        val top = bounds.top + (bounds.height() - size) / 2f
        val scale = size / 24f
        paint.strokeWidth = (2.2f * scale).roundToInt().coerceAtLeast(2).toFloat()

        if (settings) {
            val centerX = pixelCoordinate(left, 12f, scale)
            val centerY = pixelCoordinate(top, 12f, scale)
            canvas.drawCircle(centerX, centerY, pixelLength(6.5f, scale), paint)
            canvas.drawCircle(centerX, centerY, pixelLength(2.5f, scale), paint)
            for (angle in 0 until 8) {
                val radians = angle * Math.PI / 4.0
                val innerRadius = 7f
                val outerRadius = 9.5f
                canvas.drawLine(
                    pixelCoordinate(left, 12f + cos(radians).toFloat() * innerRadius, scale),
                    pixelCoordinate(top, 12f + sin(radians).toFloat() * innerRadius, scale),
                    pixelCoordinate(left, 12f + cos(radians).toFloat() * outerRadius, scale),
                    pixelCoordinate(top, 12f + sin(radians).toFloat() * outerRadius, scale),
                    paint,
                )
            }
        } else {
            canvas.drawCircle(
                pixelCoordinate(left, 9.5f, scale),
                pixelCoordinate(top, 9.5f, scale),
                pixelLength(6.3f, scale),
                paint,
            )
            canvas.drawLine(
                pixelCoordinate(left, 14f, scale),
                pixelCoordinate(top, 14f, scale),
                pixelCoordinate(left, 21f, scale),
                pixelCoordinate(top, 21f, scale),
                paint,
            )
        }
    }

    private fun pixelCoordinate(origin: Float, coordinate: Float, scale: Float): Float =
        (origin + coordinate * scale).roundToInt().toFloat()

    private fun pixelLength(length: Float, scale: Float): Float =
        (length * scale).roundToInt().coerceAtLeast(1).toFloat()

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(filter: ColorFilter?) {
        paint.colorFilter = filter
        invalidateSelf()
    }

    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
