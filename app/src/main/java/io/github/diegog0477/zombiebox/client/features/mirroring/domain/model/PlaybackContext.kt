package io.github.diegog0477.zombiebox.client.features.mirroring.domain.model

import io.github.diegog0477.zombiebox.client.core.model.MediaItem

data class PlaybackContext(
    val item: MediaItem?,
    val fullscreen: Boolean,
    val playing: Boolean,
    val incomingReceiverSessionId: String = "",
)
