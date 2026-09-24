package io.github.diegog0477.zombiebox.client.features.youtubereceiver.domain.model

enum class YouTubePlaybackIssue {
    UNAVAILABLE,
    EXTERNAL_PLAYER_REQUIRED,
}

data class YouTubeReception(
    val enabled: Boolean = false,
    val receiver: YouTubeReceiver? = null,
    val failed: Boolean = false,
    val playbackIssue: YouTubePlaybackIssue? = null,
    val playbackIssueRevision: Long = 0,
)
