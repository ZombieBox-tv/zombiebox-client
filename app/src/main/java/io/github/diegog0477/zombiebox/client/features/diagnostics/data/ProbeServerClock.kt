package io.github.diegog0477.zombiebox.client.features.diagnostics.data

import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeClockUnavailable

/** Dates only newly measured probes using gateway time and local monotonic elapsed time. */
internal object ProbeServerClock {
    private const val MAX_RUN_NANOS = 2L * 60L * 60L * 1_000_000_000L
    private const val PROBE_URL_LIFETIME_SECONDS = 600L
    private const val EARLIEST_SERVER_SECONDS = 1_577_836_800L
    private const val LATEST_SERVER_SECONDS = 4_102_444_800L
    private val expiresParameter = Regex("(?:[?&])expires=([0-9]{10})(?:&|$)")

    /** Old gateways omit serverTime but sign URLs with a shared, ten-minute expiry. */
    fun anchor(explicitServerSeconds: Long, probeUrls: List<String>): Long {
        if (explicitServerSeconds in EARLIEST_SERVER_SECONDS..LATEST_SERVER_SECONDS)
            return explicitServerSeconds
        if (explicitServerSeconds != 0L || probeUrls.isEmpty()) throw ProbeClockUnavailable()
        var expires: Long? = null
        for (url in probeUrls) {
            val value =
                expiresParameter.find(url)?.groupValues?.get(1)?.toLongOrNull()
                    ?: throw ProbeClockUnavailable()
            if (
                value !in
                    (EARLIEST_SERVER_SECONDS + PROBE_URL_LIFETIME_SECONDS)..LATEST_SERVER_SECONDS ||
                    (expires != null && expires != value)
            )
                throw ProbeClockUnavailable()
            expires = value
        }
        return expires!! - PROBE_URL_LIFETIME_SECONDS
    }

    fun testedAt(serverUnixSeconds: Long, receivedAtNanos: Long, nowNanos: Long): Long {
        if (serverUnixSeconds !in EARLIEST_SERVER_SECONDS..LATEST_SERVER_SECONDS)
            throw ProbeClockUnavailable()
        val elapsedNanos = nowNanos - receivedAtNanos
        if (elapsedNanos !in 0L..MAX_RUN_NANOS) throw ProbeClockUnavailable()
        return serverUnixSeconds + elapsedNanos / 1_000_000_000L
    }
}
