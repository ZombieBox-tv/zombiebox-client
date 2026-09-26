package io.github.diegog0477.zombiebox.client.features.playback.data

import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan
import io.github.diegog0477.zombiebox.shared.GatewayApi
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal enum class PlaybackReadinessDecision {
    RETRY,
    READY,
    FAILED,
    CANCELLED,
}

internal data class PlaybackReadinessResult(
    val decision: PlaybackReadinessDecision,
    val retryAfterMs: Long = PlaybackReadinessPolicy.POLL_INTERVAL_MS,
)

internal object PlaybackReadinessPolicy {
    const val MAX_WAIT_MS = 120_000L
    const val REQUEST_TIMEOUT_MS = 5_000
    const val POLL_INTERVAL_MS = 1_000L

    fun decide(status: Int, contentLength: Long): PlaybackReadinessDecision =
        when {
            status == HttpURLConnection.HTTP_ACCEPTED || status == 429 ->
                PlaybackReadinessDecision.RETRY
            status == HttpURLConnection.HTTP_OK && contentLength > 0 ->
                PlaybackReadinessDecision.READY
            else -> PlaybackReadinessDecision.FAILED
        }

    fun retryDelayMs(status: Int, retryAfter: String?, nowEpochMs: Long): Long {
        if (status != HttpURLConnection.HTTP_ACCEPTED && status != 429) return POLL_INTERVAL_MS
        val seconds = retryAfter?.trim()?.toLongOrNull()
        if (seconds != null && seconds >= 0) return seconds.coerceAtMost(MAX_WAIT_MS / 1000) * 1000L
        val date =
            retryAfter?.let { value ->
                try {
                    SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                        .apply {
                            isLenient = false
                            timeZone = TimeZone.getTimeZone("GMT")
                        }
                        .parse(value.trim())
                        ?.time
                } catch (_: Exception) {
                    null
                }
            }
        return if (date == null) POLL_INTERVAL_MS else (date - nowEpochMs).coerceIn(0, MAX_WAIT_MS)
    }
}

/** Owns one cancellable authenticated readiness probe for a playback plan. */
internal class PlaybackReadinessCancellation {
    @Volatile
    var cancelled: Boolean = false
        private set

    private val lock = Any()
    private var connection: HttpURLConnection? = null

    fun attach(value: HttpURLConnection): Boolean =
        synchronized(lock) {
            if (cancelled) {
                value.disconnect()
                false
            } else {
                connection = value
                true
            }
        }

    fun detach(value: HttpURLConnection) {
        synchronized(lock) { if (connection === value) connection = null }
    }

    fun cancel() {
        val active =
            synchronized(lock) {
                cancelled = true
                connection.also { connection = null }
            }
        active?.disconnect()
    }
}

internal class GatewayPlaybackReadinessRepository(
    private val api: GatewayApi,
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) {
    @Throws(IOException::class)
    fun check(
        plan: PlaybackPlan,
        timeoutMs: Int,
        cancellation: PlaybackReadinessCancellation,
    ): PlaybackReadinessResult {
        if (cancellation.cancelled)
            return PlaybackReadinessResult(PlaybackReadinessDecision.CANCELLED)
        val endpoint = readinessUrl(plan.url)
        if (!sameOrigin(endpoint, URL(api.base)))
            return PlaybackReadinessResult(PlaybackReadinessDecision.FAILED)
        val http = openConnection(endpoint)
        if (!cancellation.attach(http))
            return PlaybackReadinessResult(PlaybackReadinessDecision.CANCELLED)
        try {
            if (cancellation.cancelled)
                return PlaybackReadinessResult(PlaybackReadinessDecision.CANCELLED)
            http.requestMethod = "HEAD"
            http.connectTimeout = timeoutMs.coerceIn(1, PlaybackReadinessPolicy.REQUEST_TIMEOUT_MS)
            http.readTimeout = timeoutMs.coerceIn(1, PlaybackReadinessPolicy.REQUEST_TIMEOUT_MS)
            http.instanceFollowRedirects = false
            http.useCaches = false
            http.setRequestProperty("Accept-Encoding", "identity")
            val token = api.token
            if (token.isNotEmpty()) {
                http.setRequestProperty("Authorization", "Bearer $token")
                http.setRequestProperty("X-Zombie-Device", api.device)
            }
            val status = http.responseCode
            if (cancellation.cancelled)
                return PlaybackReadinessResult(PlaybackReadinessDecision.CANCELLED)
            val contentLength = http.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            return PlaybackReadinessResult(
                PlaybackReadinessPolicy.decide(status, contentLength),
                PlaybackReadinessPolicy.retryDelayMs(
                    status,
                    http.getHeaderField("Retry-After"),
                    System.currentTimeMillis(),
                ),
            )
        } catch (error: SocketTimeoutException) {
            throw error
        } finally {
            cancellation.detach(http)
            http.disconnect()
        }
    }

    private fun readinessUrl(mediaUrl: String): URL {
        val separator = if (mediaUrl.contains('?')) '&' else '?'
        return URL("$mediaUrl${separator}prepare=1")
    }

    private fun sameOrigin(target: URL, gateway: URL): Boolean =
        target.protocol.equals(gateway.protocol, ignoreCase = true) &&
            target.host.equals(gateway.host, ignoreCase = true) &&
            effectivePort(target) == effectivePort(gateway) &&
            target.userInfo == null

    private fun effectivePort(url: URL): Int = if (url.port >= 0) url.port else url.defaultPort
}
