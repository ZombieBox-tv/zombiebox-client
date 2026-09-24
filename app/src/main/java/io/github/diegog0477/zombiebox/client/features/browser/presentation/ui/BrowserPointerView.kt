package io.github.diegog0477.zombiebox.client.features.browser.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Local cursor over the remote browser frame, so D-pad pointer movement is visible immediately. */
class BrowserPointerView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val outline =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 5f * density
        }
    private val ring =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(97, 235, 125)
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
    private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = width / 2f
        val y = height / 2f
        val radius = minOf(width, height) * 0.28f
        canvas.drawCircle(x, y, radius, outline)
        canvas.drawCircle(x, y, radius, ring)
        canvas.drawCircle(x, y, 2f * density, center)
    }
}
