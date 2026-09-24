package io.github.diegog0477.zombiebox.client.features.airplay.domain.repository

interface AirPlayPairingRepository {
    /** Transient receiver password; callers must not persist or log it. */
    fun pin(): String
}

class AirPlayPairingUnavailable(val reason: Reason) : Exception() {
    enum class Reason {
        UPDATE_GATEWAY,
        DISABLED,
        UNAVAILABLE,
    }
}
