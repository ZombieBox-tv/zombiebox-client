package io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel

import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRepository
import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRequestRole

/** Bounded in-flight work and a four MiB encoded-image LRU; views own decoded bitmaps. */
class ArtworkViewModel(
    private val repository: ArtworkRepository,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
    private val maxCacheBytes: Int = 4 * 1024 * 1024,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var generation = 0
    private var requested = 0

    private data class Cached(val bytes: ByteArray, val expires: Long)

    private val cache = LinkedHashMap<String, Cached>()
    private var cacheBytes = 0
    private var closed = false

    fun reset(clearCache: Boolean = true) {
        generation++
        requested = 0
        if (clearCache) {
            cache.clear()
            cacheBytes = 0
        }
    }

    fun load(path: String, role: ArtworkRequestRole, display: (ByteArray?) -> Unit) {
        if (closed || path.isEmpty()) return
        val key = "$role:$path"
        cache.remove(key)?.let { cached ->
            if (now() < cached.expires) {
                cache[key] = cached
                display(cached.bytes)
                return
            }
            cacheBytes -= cached.bytes.size
        }
        if (requested >= 12) return
        requested++
        val screen = generation
        execute {
            val bytes =
                try {
                    repository.image(path, role)
                } catch (_: Exception) {
                    null
                }
            deliver {
                if (!closed && screen == generation) {
                    requested--
                    if (bytes != null) {
                        if (bytes.size <= minOf(256 * 1024, maxCacheBytes)) {
                            cache.remove(key)?.let { cacheBytes -= it.bytes.size }
                            while (cacheBytes + bytes.size > maxCacheBytes && cache.isNotEmpty()) {
                                val oldest = cache.keys.first()
                                cacheBytes -= cache.remove(oldest)!!.bytes.size
                            }
                            cache[key] = Cached(bytes, now() + 5 * 60 * 1000L)
                            cacheBytes += bytes.size
                        }
                    }
                    display(bytes)
                }
            }
        }
    }

    fun load(path: String, hero: Boolean, display: (ByteArray?) -> Unit) {
        load(path, if (hero) ArtworkRequestRole.HERO else ArtworkRequestRole.DEFAULT, display)
    }

    fun close() {
        closed = true
        cache.clear()
        cacheBytes = 0
        reset()
    }
}
