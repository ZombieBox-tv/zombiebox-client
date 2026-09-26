package io.github.diegog0477.zombiebox.client.features.playback.domain.model

data class PlaybackPlan(
    val sessionId: String,
    val url: String,
    val mime: String,
    val mode: String,
    val resumePositionMs: Int,
    val timelineOffsetMs: Int = 0,
    val live: Boolean = false,
    val seekable: Boolean = true,
    val subtitleId: Int? = null,
    val prepareBeforePlayback: Boolean = false,
)

data class PlaybackProgress(val state: String, val positionMs: Int, val durationMs: Int)
