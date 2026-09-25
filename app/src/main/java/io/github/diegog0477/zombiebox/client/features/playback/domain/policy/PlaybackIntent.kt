package io.github.diegog0477.zombiebox.client.features.playback.domain.policy

/** User intent survives temporary visibility/surface loss; an explicit pause always wins. */
class PlaybackIntent {
    companion object {
        /** Keep the user's latest play/pause choice through a stream replacement. */
        fun replacementAutoplay(reportedState: String, controlTarget: Boolean?): Boolean =
            controlTarget ?: (reportedState == "PLAYING" || reportedState == "BUFFERING")
    }

    var foreground = true
    var surfaceAvailable = false
    var backgroundPlayback = false
    private var video = true
    var wantsPlayback = false
        private set

    val canPlay: Boolean
        get() =
            wantsPlayback && if (foreground) (!video || surfaceAvailable) else backgroundPlayback

    fun begin(autoplay: Boolean, hasVideo: Boolean) {
        wantsPlayback = autoplay
        video = hasVideo
    }

    fun pause() {
        wantsPlayback = false
    }

    fun resume() {
        wantsPlayback = true
    }

    fun toggle() {
        wantsPlayback = !wantsPlayback
    }
}
