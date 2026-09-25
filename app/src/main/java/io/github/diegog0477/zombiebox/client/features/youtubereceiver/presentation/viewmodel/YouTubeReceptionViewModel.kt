package io.github.diegog0477.zombiebox.client.features.youtubereceiver.presentation.viewmodel

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.playback.presentation.viewmodel.PlaybackSessionViewModel
import io.github.diegog0477.zombiebox.client.features.playback.presentation.viewmodel.PlaybackViewModel
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.domain.model.YouTubeCommand
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.domain.model.YouTubePlaybackIssue
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.domain.model.YouTubeReception
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.domain.repository.YouTubePlaybackControl

/** Service-owned receiver intent and playback coordination; screens only observe this state. */
class YouTubeReceptionViewModel(
    private val lease: YouTubeReceiverViewModel,
    private val resolver: PlaybackViewModel,
    private val session: PlaybackSessionViewModel,
    private val playback: YouTubePlaybackControl,
    private val title: String,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    var state = YouTubeReception()
        private set

    var observer: ((YouTubeReception) -> Unit)? = null
    private var closed = false
    private var attempts = 0
    private var retryAt = 0L

    init {
        lease.command = ::receive
        lease.observer = {
            if (lease.receiver != null) {
                attempts = 0
                retryAt = 0
            }
            publish()
        }
        // A lost lease is transient; explicit receiver intent survives it. Open never
        // replaces another owner's lease, so a conflict remains visible as failed.
        lease.expired = {
            resolver.stop("", null)
            endIncoming()
            attempts = 0
            retryAt = clock() + 10_000L
            playbackFailed(YouTubePlaybackIssue.UNAVAILABLE)
            publish()
        }
    }

    private fun publish() {
        state = state.copy(receiver = lease.receiver, failed = lease.failed)
        observer?.invoke(state)
    }

    private fun playbackFailed(issue: YouTubePlaybackIssue) {
        if (!state.enabled || state.playbackIssue == issue) return
        state =
            state.copy(
                playbackIssue = issue,
                playbackIssueRevision = state.playbackIssueRevision + 1,
            )
        publish()
    }

    fun enable() {
        if (closed || (state.enabled && !state.failed)) return
        attempts = 0
        retryAt = 0
        state = state.copy(enabled = true, playbackIssue = null)
        publish()
        tick()
    }

    fun tick() {
        if (closed || !state.enabled) return
        if (lease.receiver != null) lease.tick()
        else if (clock() >= retryAt) {
            attempts = (attempts + 1).coerceAtMost(5)
            retryAt = clock() + (5000L shl attempts)
            lease.open()
        }
    }

    /**
     * A different source is taking over; invalidate pending resolution without closing the lease.
     */
    fun standby() {
        resolver.stop("", null)
        lease.standby()
        state = state.copy(playbackIssue = null)
        publish()
    }

    fun disable() {
        state = state.copy(enabled = false, playbackIssue = null)
        resolver.stop("", null)
        lease.disable()
        endIncoming()
    }

    private fun ownsPlayback(): Boolean =
        session.state.incoming && session.state.item?.provider == "youtube"

    private fun endIncoming() {
        if (ownsPlayback() && !session.restoreInterrupted()) session.stop()
    }

    fun playbackState(status: String, position: Int, duration: Int) {
        if (state.enabled && ownsPlayback()) {
            if (status == "FAILED") playbackFailed(YouTubePlaybackIssue.UNAVAILABLE)
            lease.playerState(status, position, duration)
        }
    }

    private fun receive(command: YouTubeCommand) {
        if (!state.enabled || (!ownsPlayback() && command.action !in listOf("play", "stop"))) {
            lease.complete(false, command.id)
            return
        }
        when (command.action) {
            "play" -> start(command)
            "pause" -> playback.pause()
            "resume" -> if (!playback.resume()) lease.complete(false, command.id)
            "stop" -> {
                resolver.stop("", null)
                lease.playerState("STOPPED", 0, 0)
                endIncoming()
            }
            "seek" -> {
                val plan = session.state.plan
                if (
                    plan != null &&
                        plan.seekable &&
                        !plan.live &&
                        command.positionMs >= plan.timelineOffsetMs
                )
                    playback.seek(command.positionMs - plan.timelineOffsetMs)
                else lease.complete(false, command.id)
            }
            "volume" ->
                playback.volume(command.volume, command.muted) { ok ->
                    lease.volumeApplied(command.id, command.volume, command.muted, ok)
                }
            else -> lease.complete(false, command.id)
        }
    }

    private fun start(command: YouTubeCommand) {
        val receiver = lease.receiver
        if (receiver == null) {
            playbackFailed(YouTubePlaybackIssue.UNAVAILABLE)
            lease.complete(false, command.id)
            return
        }
        state = state.copy(playbackIssue = null)
        publish()
        val item = MediaItem(command.itemId, "youtube", title)
        resolver.start(
            item.id,
            "AUTO",
            done = { plan ->
                if (
                    !state.enabled || !lease.accepts(command.id) || plan.mode == "EXTERNAL_PLAYER"
                ) {
                    resolver.stop(plan.sessionId, null)
                    if (
                        state.enabled && lease.accepts(command.id) && plan.mode == "EXTERNAL_PLAYER"
                    )
                        playbackFailed(YouTubePlaybackIssue.EXTERNAL_PLAYER_REQUIRED)
                    lease.complete(false, command.id)
                } else {
                    session.rememberInterruption()
                    session.stop(preserveInterrupted = true)
                    session.adopt(plan, item, emptyList(), incoming = true)
                    playback.play(plan, item)
                }
            },
            failed = {
                if (state.enabled && lease.accepts(command.id))
                    playbackFailed(YouTubePlaybackIssue.UNAVAILABLE)
                lease.complete(false, command.id)
            },
            positionMs = command.positionMs,
            receiverId = receiver.id,
        )
    }

    fun close() {
        closed = true
        state = state.copy(enabled = false, playbackIssue = null)
        observer = null
        resolver.close()
        lease.close()
    }
}
