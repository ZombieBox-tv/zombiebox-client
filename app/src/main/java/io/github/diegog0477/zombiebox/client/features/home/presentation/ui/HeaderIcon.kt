package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Original low-cost line icons, rendered without API21 vector support or bitmap assets. */
class HeaderIcon(private val settings: Boolean, color: Int) : Drawable() {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        if (settings) {
            canvas.drawCircle(12f, 12f, 6.5f, paint)
            canvas.drawCircle(12f, 12f, 2.5f, paint)
            for (angle in 0 until 8) {
                canvas.save()
                canvas.rotate(angle * 45f, 12f, 12f)
                canvas.drawLine(12f, 2.5f, 12f, 4.2f, paint)
                canvas.restore()
            }
        } else {
            canvas.drawCircle(9.5f, 9.5f, 6.3f, paint)
            canvas.drawLine(14f, 14f, 21f, 21f, paint)
        }
        canvas.restoreToCount(save)
    }

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
