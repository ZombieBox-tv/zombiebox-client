package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.content.Context
import android.widget.FrameLayout

/**
 * Legacy Android (API 9+) compatible FrameLayout enforcing a fixed aspect ratio (width / height)
 * during layout measurement.
 */
class AspectRatioFrameLayout(context: Context, private val ratio: Float) : FrameLayout(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (width > 0 && ratio > 0f) {
            val height = (width / ratio + 0.5f).toInt()
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
