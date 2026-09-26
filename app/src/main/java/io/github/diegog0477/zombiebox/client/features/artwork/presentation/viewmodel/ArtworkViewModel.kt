package io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel

import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRepository
import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRequestRole
import java.util.concurrent.RejectedExecutionException

/** Bounded in-flight work and a four MiB encoded-image LRU; views own decoded bitmaps. */
class ArtworkViewModel(
    private val repository: ArtworkRepository,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
    private val maxCacheBytes: Int = 4 * 1024 * 1024,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var generation = 0L
    private var inFlight = 0
    private var nextSequence = 0L

    /** Changes only when reset scopes callbacks and cache to a different gateway. */
    val scopeRevision: Long
        get() = generation

    private data class Cached(val bytes: ByteArray, val expires: Long)

    private data class RequestKey(val generation: Long, val key: String)

    private data class QueuedRequest(
        val requestKey: RequestKey,
        val path: String,
        val role: ArtworkRequestRole,
        val sequence: Long,
    )

    private val cache = LinkedHashMap<String, Cached>()
    private val pending = LinkedHashMap<RequestKey, MutableList<(ByteArray?) -> Unit>>()
    private val queued = ArrayList<QueuedRequest>()
    private var cacheBytes = 0
    private var closed = false

    fun reset(clearCache: Boolean = true) {
        generation++
        val stale = queued.filter { it.requestKey.generation != generation }
        queued.removeAll(stale.toSet())
        stale.forEach { request ->
            pending.remove(request.requestKey)?.forEach { callback -> callback(null) }
        }
        if (clearCache) {
            cache.clear()
            cacheBytes = 0
        }
    }

    fun load(path: String, role: ArtworkRequestRole, display: (ByteArray?) -> Unit) {
        if (closed) return
        if (path.isEmpty()) {
            display(null)
            return
        }
        val key = "$role:$path"
        cache.remove(key)?.let { cached ->
            if (now() < cached.expires) {
                cache[key] = cached
                display(cached.bytes)
                return
            }
            cacheBytes -= cached.bytes.size
        }
        val screen = generation
        val requestKey = RequestKey(screen, key)
        pending[requestKey]?.let { callbacks ->
            if (callbacks.size >= MAX_WAITERS_PER_REQUEST) {
                callbacks.removeAt(0)(null)
            }
            callbacks.add(display)
            return
        }
        pending[requestKey] = arrayListOf(display)
        val request = QueuedRequest(requestKey, path, role, nextSequence++)
        if (inFlight < MAX_IN_FLIGHT && queued.isEmpty()) {
            submit(request)
            return
        }
        if (queued.size >= MAX_QUEUED) {
            if (role != ArtworkRequestRole.AUDIO || !evictQueuedForAudio()) {
                pending.remove(requestKey)?.forEach { callback -> callback(null) }
                return
            }
        }
        queued.add(request)
        pump()
    }

    private fun evictQueuedForAudio(): Boolean {
        val candidate =
            queued.indices
                .filter { queued[it].role != ArtworkRequestRole.AUDIO }
                .maxWithOrNull(
                    compareBy<Int> { priority(queued[it].role) }
                        .thenByDescending { queued[it].sequence }
                )
                ?: queued.indices
                    .filter { queued[it].role == ArtworkRequestRole.AUDIO }
                    .minByOrNull { queued[it].sequence }
                ?: return false
        val evicted = queued.removeAt(candidate)
        pending.remove(evicted.requestKey)?.forEach { callback -> callback(null) }
        return true
    }

    private fun pump() {
        while (!closed && inFlight < MAX_IN_FLIGHT && queued.isNotEmpty()) {
            val nextIndex =
                queued.indices.minWithOrNull(
                    compareBy<Int> { priority(queued[it].role) }.thenBy { queued[it].sequence }
                )!!
            submit(queued.removeAt(nextIndex))
        }
    }

    private fun priority(role: ArtworkRequestRole): Int =
        when (role) {
            ArtworkRequestRole.AUDIO -> 0
            ArtworkRequestRole.HERO -> 1
            ArtworkRequestRole.DEFAULT -> 2
        }

    private fun submit(request: QueuedRequest) {
        inFlight++
        try {
            execute {
                val bytes =
                    try {
                        repository.image(request.path, request.role)
                    } catch (_: Exception) {
                        null
                    }
                deliver {
                    val callbacks = pending.remove(request.requestKey)
                    inFlight = (inFlight - 1).coerceAtLeast(0)
                    if (closed || callbacks == null) return@deliver
                    val isCurrentScope = request.requestKey.generation == generation
                    if (isCurrentScope && bytes != null) cache(request.requestKey.key, bytes)
                    val result = if (isCurrentScope) bytes else null
                    pump()
                    callbacks.forEach { callback -> callback(result) }
                }
            }
        } catch (_: RejectedExecutionException) {
            val callbacks = pending.remove(request.requestKey)
            inFlight = (inFlight - 1).coerceAtLeast(0)
            callbacks?.forEach { callback -> callback(null) }
        }
    }

    private fun cache(key: String, bytes: ByteArray) {
        if (bytes.size > minOf(256 * 1024, maxCacheBytes)) return
        cache.remove(key)?.let { cacheBytes -= it.bytes.size }
        while (cacheBytes + bytes.size > maxCacheBytes && cache.isNotEmpty()) {
            val oldest = cache.keys.first()
            cacheBytes -= cache.remove(oldest)!!.bytes.size
        }
        cache[key] = Cached(bytes, now() + 5 * 60 * 1000L)
        cacheBytes += bytes.size
    }

    fun load(path: String, hero: Boolean, display: (ByteArray?) -> Unit) {
        load(path, if (hero) ArtworkRequestRole.HERO else ArtworkRequestRole.DEFAULT, display)
    }

    fun close() {
        closed = true
        cache.clear()
        cacheBytes = 0
        generation++
        queued.clear()
        pending.clear()
    }

    private companion object {
        const val MAX_IN_FLIGHT = 2
        const val MAX_QUEUED = 22
        const val MAX_WAITERS_PER_REQUEST = 12
    }
}
