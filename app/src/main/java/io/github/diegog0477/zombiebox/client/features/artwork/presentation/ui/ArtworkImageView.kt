package io.github.diegog0477.zombiebox.client.features.artwork.presentation.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.ImageView
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRequestRole
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel.ArtworkViewModel

/** Identifies the displayed recording; receiver IDs can be shared by every song. */
internal fun MediaItem?.artworkIdentity(): String? =
    this?.takeIf { it.id.isNotBlank() && it.title.isNotBlank() }
        ?.let {
            listOf(
                    it.provider,
                    it.id,
                    it.title,
                    it.subtitle,
                    it.description,
                    it.durationMs.toString(),
                )
                .joinToString("\u001f")
        }

internal object ArtworkBindingPolicy {
    fun canRetainCurrentImage(
        hasCurrentImage: Boolean,
        currentIdentity: String?,
        nextIdentity: String?,
        currentScopeRevision: Long?,
        nextScopeRevision: Long,
    ): Boolean =
        hasCurrentImage &&
            !currentIdentity.isNullOrBlank() &&
            currentIdentity == nextIdentity &&
            currentScopeRevision == nextScopeRevision
}

/** Gateway-sized derivatives only; stale responses never cross binding identities. */
class ArtworkImageView(
    context: Context,
    private val decoder:
        io.github.diegog0477.zombiebox.client.features.artwork.platform.ArtworkDecoder,
) : ImageView(context) {
    private var request = 0
    private var bound: BindingKey? = null
    private var boundIdentity: String? = null
    private var boundScopeRevision: Long? = null
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
        mediaIdentity: String? = null,
    ) {
        val scopeRevision = model.scopeRevision
        val key = BindingKey(scopeRevision, role, path, mediaIdentity)
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

        val keepCurrentImage =
            ArtworkBindingPolicy.canRetainCurrentImage(
                hasCurrentImage = drawable != null,
                currentIdentity = boundIdentity,
                nextIdentity = mediaIdentity,
                currentScopeRevision = boundScopeRevision,
                nextScopeRevision = scopeRevision,
            )
        if (path.isEmpty() && keepCurrentImage) return

        bound = key
        boundIdentity = mediaIdentity
        boundScopeRevision = scopeRevision
        val generation = ++request
        loading = path.isNotEmpty()
        requestStartedAt = now
        if (!keepCurrentImage) {
            setImageDrawable(null)
            onImageAvailabilityChanged?.invoke(false)
            onBitmapChanged?.invoke(null)
        }
        if (path.isEmpty()) return
        model.load(path, role) { bytes ->
            if (request != generation) return@load
            if (bytes == null) {
                loading = false
                retryAfter = SystemClock.elapsedRealtime() + 2_000L
                onImageAvailabilityChanged?.invoke(drawable != null)
                return@load
            }
            decoder.decode(bytes, role) { bitmap ->
                if (request == generation) {
                    loading = false
                    retryAfter = if (bitmap == null) SystemClock.elapsedRealtime() + 2_000L else 0L
                    if (bitmap != null) {
                        setImageBitmap(bitmap)
                        onImageAvailabilityChanged?.invoke(true)
                        onBitmapChanged?.invoke(bitmap)
                    } else {
                        onImageAvailabilityChanged?.invoke(drawable != null)
                    }
                }
            }
        }
    }

    fun bind(model: ArtworkViewModel, path: String, hero: Boolean) {
        bind(model, path, if (hero) ArtworkRequestRole.HERO else ArtworkRequestRole.DEFAULT)
    }

    private data class BindingKey(
        val scopeRevision: Long,
        val role: ArtworkRequestRole,
        val path: String,
        val mediaIdentity: String?,
    )
}
