package io.github.diegog0477.zombiebox.client.features.mirroring.domain.model

import io.github.diegog0477.zombiebox.client.core.model.MediaItem

data class ReceiverPlan(
    val sessionId: String,
    val path: String,
    val mime: String,
    val item: MediaItem? = null,
    val fullscreen: Boolean = true,
    val state: String = "PLAYING",
    val live: Boolean = true,
    val seekable: Boolean = false,
    val mode: String = "DIRECT_PLAY",
    val senderPositionKnown: Boolean = false,
    val senderPositionMs: Int = 0,
    val senderDurationMs: Int = 0,
    val senderPositionAgeMs: Int = 0,
)
