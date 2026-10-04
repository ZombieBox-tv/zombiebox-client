package io.github.diegog0477.zombiebox.client.features.home.domain.model

enum class PlaybackValidationStatus {
    CURRENT,
    REFRESH_REQUIRED,
    UNAVAILABLE,
}

enum class PlaybackValidationReason {
    EXPIRED,
    OTHER,
    NONE,
}

data class PlaybackValidation(
    val status: PlaybackValidationStatus = PlaybackValidationStatus.UNAVAILABLE,
    val reason: PlaybackValidationReason = PlaybackValidationReason.NONE,
) {
    val needsPlaybackCheck: Boolean
        get() = status == PlaybackValidationStatus.REFRESH_REQUIRED

    companion object {
        val UNAVAILABLE = PlaybackValidation()
    }
}
