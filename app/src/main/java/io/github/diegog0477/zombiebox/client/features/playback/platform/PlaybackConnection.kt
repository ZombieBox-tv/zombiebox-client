package io.github.diegog0477.zombiebox.client.features.playback.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackSession
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.QueueCursor
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.domain.model.YouTubeReception

/** Activity-owned binding. Closing a screen detaches UI callbacks, never the session. */
class PlaybackConnection(
    context: Context,
    private val restored: (PlaybackSession) -> Unit,
    private val size: (Int, Int) -> Unit,
    private val changed: (String, Int, Int) -> Unit,
) : ServiceConnection, AudioFocusController {
    private val context = context.applicationContext
    private var service: PlaybackService? = null
    val ready: Boolean
        get() = service != null

    var youtubeState = YouTubeReception()
        private set

    var youtubeChanged: (() -> Unit)? = null
    private val receiverObserver: (YouTubeReception) -> Unit = {
        if (!closed) {
            youtubeState = it
            youtubeChanged?.invoke()
        }
    }

    private fun attachReceiver(target: PlaybackService) {
        target.youtubeListener = receiverObserver
        receiverObserver(target.youtube.state)
    }

    fun enableYouTube() = command {
        if (visible) {
            it.enableYouTubeReceiver()
            context.startService(Intent(context, PlaybackService::class.java))
        }
    }

    fun disableYouTube() = command { it.disableYouTubeReceiver() }

    fun standbyYouTube() = command { it.youtube.standby() }

    fun configureReceivers(mediaProvider: String? = null, castEnabled: Boolean? = null) = command {
        if (visible) {
            it.configureReceivers(mediaProvider, castEnabled)
            context.startService(Intent(context, PlaybackService::class.java))
        }
    }

    private var closed = false
    private var visible = false
    private var surface: PlayerSurface? = null
    private val pending = ArrayList<(PlaybackService) -> Unit>()
    private val observer: (PlaybackSession) -> Unit = { state ->
        if (!closed) {
            restored(state)
            val offset = state.plan?.timelineOffsetMs ?: 0
            changed(
                state.progress.state,
                (state.progress.positionMs - offset).coerceAtLeast(0),
                if (state.progress.durationMs > 0) state.progress.durationMs - offset else 0,
            )
        }
    }
    private val bound =
        this.context.bindService(
            Intent(this.context, PlaybackService::class.java),
            this,
            Context.BIND_AUTO_CREATE,
        )

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        if (closed) return
        val target = (binder as PlaybackService.Access).service
        service = target
        target.foreground(visible)
        target.player.surface(surface)
        val commands = pending.toList()
        pending.clear()
        commands.forEach { it(target) }
        target.attach(observer, size)
        attachReceiver(target)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service = null
    }

    private fun command(work: (PlaybackService) -> Unit) {
        if (closed) return
        val target = service
        if (target != null) work(target) else if (pending.size < 32) pending.add(work)
    }

    fun configure(base: String, device: String, token: String) = command {
        it.configure(base, device, token)
    }

    fun adopt(
        plan: PlaybackPlan,
        item: MediaItem,
        queue: List<MediaItem>? = null,
        cursor: QueueCursor? = null,
        incoming: Boolean = false,
        paused: Boolean = false,
    ) = command {
        if (!visible && it.model.state.plan == null) {
            it.discard(plan, incoming)
            return@command
        }
        it.adopt(plan, item, queue, cursor, incoming, paused)
        // Invoked from a visible Activity; promotion happens synchronously in adopt.
        context.startService(Intent(context, PlaybackService::class.java))
    }

    fun surface(value: PlayerSurface?) {
        surface = value
        service?.let { if (it.listener === observer) it.player.surface(value) }
    }

    fun foreground(value: Boolean) {
        visible = value
        service?.let {
            if (value) {
                it.foreground(true)
                it.attach(observer, size)
                attachReceiver(it)
                it.player.surface(surface)
            } else if (it.listener === observer) it.foreground(false)
        }
    }

    fun play(
        url: String,
        position: Int,
        autoplay: Boolean = true,
        video: Boolean = true,
        seekable: Boolean = true,
    ) = command {
        if (it.model.state.plan?.url != url) return@command
        if (it.focus.acquire()) it.player.play(url, position, autoplay, video, seekable)
        else it.model.mediaState("FAILED", position, 0)
    }

    fun resume() = command {
        if (!it.model.setRecoveryPaused(false) && it.focus.acquire()) it.player.resume()
    }

    fun pause() = command { if (!it.model.setRecoveryPaused(true)) it.player.pause() }

    fun toggle() = command { it.toggle() }

    fun receiverStatus(item: MediaItem?, status: String) = command {
        it.receiverStatus(item, status)
    }

    fun seek(delta: Int) = command { it.player.seek(delta) }

    fun seekTo(position: Int) = command { it.player.seekTo(position) }

    fun volume(value: Int, muted: Boolean, done: (Boolean) -> Unit) = command {
        it.player.volume(value, muted, done)
    }

    /** Suspend the decoder during a replacement without discarding queue/plan metadata. */
    fun stop() = command { it.model.suspendForReplacement() }

    fun failed() = command { it.model.replacementFailed() }

    fun end(preserveInterrupted: Boolean = false) = command { it.model.stop(preserveInterrupted) }

    fun rememberInterruption() = command { it.model.rememberInterruption() }

    fun restoreInterrupted(): Boolean = service?.model?.restoreInterrupted() ?: false

    fun subtitle(id: Int?) = command { it.model.subtitle(id) }

    fun next() = command { it.model.next() }

    fun resumeSaved(missing: () -> Unit) = command {
        if (visible && it.model.state.plan == null) {
            it.model.resumeSaved(missing)
            context.startService(Intent(context, PlaybackService::class.java))
        }
    }

    fun refreshFocus() = command { it.refreshFocus() }

    override fun acquire(): Boolean = service?.focus?.acquire() ?: !closed

    override fun release() {
        service?.focus?.release()
    }

    fun close() {
        closed = true
        youtubeChanged = null
        pending.clear()
        service?.detach(observer)
        service = null
        surface = null
        if (bound) context.unbindService(this)
    }
}
