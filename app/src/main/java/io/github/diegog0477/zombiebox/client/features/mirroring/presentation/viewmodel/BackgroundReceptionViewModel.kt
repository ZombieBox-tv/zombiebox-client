package io.github.diegog0477.zombiebox.client.features.mirroring.presentation.viewmodel

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.BackgroundReception
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.ReceiverPlan
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.repository.ReceiverRepository
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan
import io.github.diegog0477.zombiebox.client.features.playback.presentation.viewmodel.PlaybackSessionViewModel

/** Background-only polling. The gateway remains the exclusive receiver ownership authority. */
class BackgroundReceptionViewModel(
    private val repository: ReceiverRepository,
    private val session: PlaybackSessionViewModel,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
    private val resolve: (ReceiverPlan) -> PlaybackPlan,
    private val play: (PlaybackPlan, MediaItem) -> Unit,
    private val metadata: (MediaItem?, String) -> Unit,
    private val beforeTakeover: () -> Unit,
    private val fallbackTitle: String,
    private val clock: () -> Long,
) {
    var state = BackgroundReception()
        private set

    var observer: ((BackgroundReception) -> Unit)? = null
    private var mediaListening = false
    private var desiredMediaProvider = ""
    private var castListening = false
    private var visible = true
    private var closed = false
    private var loading = false
    private var generation = 0
    private var trackedSession = ""
    private var attempts = 0
    private var retryAt = 0L
    private var healthySince: Long? = null
    private var rearmAttempts = 0
    private var rearmRetryAt = 0L

    fun configure(mediaProvider: String? = null, castEnabled: Boolean? = null) {
        if (closed) return
        mediaProvider?.let {
            mediaListening = it in listOf("spotify", "airplay", "auto", "universal")
            desiredMediaProvider = if (mediaListening) it else ""
            rearmAttempts = 0
            rearmRetryAt = 0L
        }
        castEnabled?.let { castListening = it }
        generation++
        publish(BackgroundReception(mediaListening || castListening))
    }

    fun foreground(value: Boolean) {
        if (visible == value) return
        visible = value
        healthySince = null
        generation++
    }

    private fun ownsPlayback() =
        session.state.incoming &&
            session.state.item?.provider in listOf("spotify", "airplay", "android_mirror", "cast")

    fun tick() {
        if (
            closed ||
                visible ||
                loading ||
                session.state.loading ||
                (!state.enabled && !ownsPlayback())
        )
            return
        loading = true
        val request = generation
        val previous = session.state.plan?.sessionId
        val desired = desiredMediaProvider
        val canRearm = desired.isNotEmpty() && clock() >= rearmRetryAt
        execute {
            val result = runCatching {
                val active = repository.active()
                if (active == null && canRearm && repository.mediaProvider().isEmpty()) {
                    repository.rearmMediaProvider(desired)
                }
                active
            }
            deliver {
                loading = false
                if (
                    closed ||
                        visible ||
                        request != generation ||
                        session.state.loading ||
                        session.state.plan?.sessionId != previous
                )
                    return@deliver
                if (result.isFailure && canRearm) {
                    rearmAttempts = (rearmAttempts + 1).coerceAtMost(5)
                    rearmRetryAt = clock() + (5000L shl rearmAttempts)
                } else if (result.isSuccess) {
                    rearmAttempts = 0
                    rearmRetryAt = 0L
                }
                result.fold(
                    { received ->
                        publish(state.copy(unavailable = received?.mode == "EXTERNAL_PLAYER"))
                        receive(received)
                    },
                    { publish(state.copy(unavailable = true)) },
                )
            }
        }
    }

    private fun receive(received: ReceiverPlan?) {
        if (received == null) {
            resetRecovery("")
            if (ownsPlayback() && !session.restoreInterrupted()) session.stop()
            return
        }
        // An external player needs a visible user action, never a background Activity launch.
        if (received.mode == "EXTERNAL_PLAYER") {
            return
        }
        val item = received.item ?: MediaItem(received.sessionId, "cast", fallbackTitle)
        val current = session.state.plan
        if (received.sessionId != current?.sessionId) {
            beforeTakeover()
            session.rememberInterruption()
            session.stop(preserveInterrupted = true)
            val plan = resolve(received)
            session.adopt(plan, item, emptyList(), incoming = true)
            resetRecovery(received.sessionId)
            metadata(item, received.state)
            play(plan, item)
            return
        }
        metadata(item, received.state)
        if (trackedSession != received.sessionId) resetRecovery(received.sessionId)
        val now = clock()
        if (session.state.progress.state == "PLAYING") {
            if (healthySince == null) healthySince = now
            if (now - healthySince!! >= 60_000) attempts = 0
        } else healthySince = null
        if (
            received.live &&
                received.state == "PLAYING" &&
                session.state.progress.state in listOf("FAILED", "ENDED") &&
                attempts < 3 &&
                now >= retryAt
        ) {
            attempts++
            retryAt = now + (2000L shl attempts)
            session.mediaState("BUFFERING", 0, 0)
            play(current!!, item)
        }
    }

    private fun resetRecovery(id: String) {
        trackedSession = id
        attempts = 0
        retryAt = 0
        healthySince = null
    }

    private fun publish(value: BackgroundReception) {
        if (state == value) return
        state = value
        observer?.invoke(state)
    }

    fun reset() {
        generation++
        mediaListening = false
        desiredMediaProvider = ""
        castListening = false
        rearmAttempts = 0
        rearmRetryAt = 0L
        resetRecovery("")
        publish(BackgroundReception())
    }

    fun close() {
        closed = true
        observer = null
        reset()
    }
}
