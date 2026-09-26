package io.github.diegog0477.zombiebox.client.features.playback.domain.policy

import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan

/** Each explicit retry moves forward. Non-seekable remux cannot accurately resume mid-item. */
class PlaybackRecovery {
    private var mode = "EXTERNAL_PLAYER"

    fun adopt(plan: PlaybackPlan) {
        mode = plan.mode
    }

    fun next(positionMs: Int): String? =
        when (mode) {
            "DIRECT_PLAY" -> if (positionMs > 0) "TRANSCODE" else "REMUX"
            "REMUX" -> "TRANSCODE"
            "HYBRID" -> "TRANSCODE"
            else -> null
        }

    fun attempted(value: String) {
        mode = value
    }
}
