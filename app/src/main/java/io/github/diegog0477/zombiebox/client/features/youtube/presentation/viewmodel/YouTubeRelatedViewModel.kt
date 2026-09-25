package io.github.diegog0477.zombiebox.client.features.youtube.presentation.viewmodel

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.youtube.domain.repository.YouTubeRelatedRepository
import io.github.diegog0477.zombiebox.shared.GatewayFailure
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Keeps the fullscreen rail tied to one video, independent of the browsed catalog. */
class YouTubeRelatedViewModel(
    private val repository: YouTubeRelatedRepository,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
    private val retryLater: (Long, () -> Unit) -> Unit = { delay, work ->
        RETRY_TIMER.schedule(work, delay, TimeUnit.MILLISECONDS)
    },
) {
    var videoId = ""
        private set

    var currentVideo: MediaItem? = null
        private set

    var items: List<MediaItem> = emptyList()
        private set

    var nextCursor = ""
        private set

    val hasMore: Boolean
        get() = nextCursor.isNotEmpty()

    private var generation = 0
    private var loading = false
    private var closed = false

    fun attach(itemId: String, changed: () -> Unit) {
        val id = itemId.removePrefix("youtube-")
        if (closed || id == videoId) return
        generation++
        videoId = if (VIDEO_ID.matches(id)) id else ""
        currentVideo = null
        items = emptyList()
        nextCursor = ""
        loading = false
        if (videoId.isNotEmpty()) fetch("", changed)
    }

    fun loadMore(changed: () -> Unit) {
        if (!closed && !loading && hasMore) fetch(nextCursor, changed)
    }

    private fun fetch(cursor: String, changed: () -> Unit, attempt: Int = 0) {
        val request = generation
        val id = videoId
        loading = true
        execute {
            val result = runCatching { repository.page(id, cursor) }
            deliver {
                if (closed || request != generation || videoId != id) return@deliver
                if (
                    result.exceptionOrNull()?.let(::isTemporaryFailure) == true &&
                        attempt < RETRY_DELAYS_MS.size
                ) {
                    // Resolving a video can occupy the YouTube worker while the
                    // related browse races it. Keep retries off the shared IO
                    // executor and re-check identity before each new request.
                    retryLater(RETRY_DELAYS_MS[attempt]) {
                        deliver {
                            if (!closed && request == generation && videoId == id) {
                                fetch(cursor, changed, attempt + 1)
                            }
                        }
                    }
                    return@deliver
                }
                loading = false
                result.onSuccess { page ->
                    if (page.videoId != id) return@onSuccess
                    if (cursor.isEmpty()) currentVideo = page.currentVideo
                    val seen = HashSet(items.map { it.id })
                    seen.add("youtube-$id")
                    val additions =
                        page.items.filter { it.provider == "youtube" && seen.add(it.id) }
                    items = (items + additions).take(360)
                    nextCursor =
                        if (items.size < 360 && additions.isNotEmpty()) page.nextCursor else ""
                }
                changed()
            }
        }
    }

    fun close() {
        closed = true
        generation++
    }

    companion object {
        private fun isTemporaryFailure(error: Throwable): Boolean =
            (error is GatewayFailure && (error.status == 503 || error.status == 504)) ||
                error is SocketTimeoutException

        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
        private val RETRY_DELAYS_MS = longArrayOf(2_000, 4_000, 8_000, 8_000)
        private val RETRY_TIMER =
            Executors.newSingleThreadScheduledExecutor { work ->
                Thread(work, "youtube-related-retry").apply { isDaemon = true }
            }
    }
}
