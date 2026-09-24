package io.github.diegog0477.zombiebox.client.core.ui

import android.content.Context
import android.graphics.Typeface

/** Static bundled faces render consistently on old Dalvik and current Android. */
object TvTypography {
    private var regularFace: Typeface? = null
    private var semiboldFace: Typeface? = null

    @Synchronized
    fun regular(context: Context): Typeface {
        if (regularFace == null)
            regularFace = load(context, "fonts/barlow_regular.ttf", Typeface.DEFAULT)
        return regularFace!!
    }

    @Synchronized
    fun semibold(context: Context): Typeface {
        if (semiboldFace == null)
            semiboldFace = load(context, "fonts/barlow_semibold.ttf", Typeface.DEFAULT_BOLD)
        return semiboldFace!!
    }

    private fun load(context: Context, path: String, fallback: Typeface): Typeface =
        try {
            Typeface.createFromAsset(context.assets, path)
        } catch (_: RuntimeException) {
            fallback
        }
}
