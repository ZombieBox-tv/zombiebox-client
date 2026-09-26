package io.github.diegog0477.zombiebox.client.features.artwork.presentation.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.ImageView
import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRequestRole
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel.ArtworkViewModel

/** Gateway-sized derivatives only; a replaced binding never displays an old response. */
class ArtworkImageView(
    context: Context,
    private val decoder:
        io.github.diegog0477.zombiebox.client.features.artwork.platform.ArtworkDecoder,
) : ImageView(context) {
    private var request = 0
    private var bound = ""
    private var loading = false
    private var requestStartedAt = 0L
    private var retryAfter = 0L
    var onImageAvailabilityChanged: ((Boolean) -> Unit)? = null
    var onBitmapChanged: ((Bitmap?) -> Unit)? = null

    init {
        scaleType = ScaleType.CENTER_CROP
        isFocusable = false
    }

    fun bind(
        model: ArtworkViewModel,
        path: String,
        role: ArtworkRequestRole = ArtworkRequestRole.DEFAULT,
    ) {
        val key = "$role:$path"
        val now = SystemClock.elapsedRealtime()
        if (bound == key) {
            if (path.isEmpty()) return
            if (drawable != null) {
                onImageAvailabilityChanged?.invoke(true)
                return
            }
            if (loading && now - requestStartedAt < 10_000L) return
            if (!loading && now < retryAfter) return
        }
        bound = key
        val generation = ++request
        loading = path.isNotEmpty()
        requestStartedAt = now
        setImageDrawable(null)
        onImageAvailabilityChanged?.invoke(false)
        onBitmapChanged?.invoke(null)
        if (path.isEmpty()) return
        model.load(path, role) { bytes ->
            if (request != generation) return@load
            if (bytes == null) {
                loading = false
                retryAfter = SystemClock.elapsedRealtime() + 2_000L
                onImageAvailabilityChanged?.invoke(false)
                return@load
            }
            decoder.decode(bytes, role) { bitmap ->
                if (request == generation) {
                    loading = false
                    retryAfter = if (bitmap == null) SystemClock.elapsedRealtime() + 2_000L else 0L
                    setImageBitmap(bitmap)
                    onImageAvailabilityChanged?.invoke(bitmap != null)
                    onBitmapChanged?.invoke(bitmap)
                }
            }
        }
    }

    fun bind(model: ArtworkViewModel, path: String, hero: Boolean) {
        bind(model, path, if (hero) ArtworkRequestRole.HERO else ArtworkRequestRole.DEFAULT)
    }
}
