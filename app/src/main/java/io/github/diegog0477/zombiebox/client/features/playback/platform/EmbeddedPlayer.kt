package io.github.diegog0477.zombiebox.client.features.playback.platform

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import io.github.diegog0477.zombiebox.client.features.playback.domain.policy.PlaybackIntent

/** One media looper and one decoder. Callbacks belong to a specific playback generation. */
class EmbeddedPlayer(
    private val sizeChanged: (Int, Int) -> Unit,
    private val changed: (String, Int, Int) -> Unit,
) {
    private val thread = HandlerThread("zombie-player").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val intent = PlaybackIntent()
    private val pcm = PcmStreamPlayer()
    private var player: MediaPlayer? = null
    private var pcmActive = false
    private var output: PlayerSurface? = null
    private var prepared = false
    private var seekable = true
    private var seeking = false
    private var buffering = false
    private var position = 0
    private var duration = 0
    private var volumeGain = 1f
    private var prepareTimeout: Runnable? = null
    private var bufferingTimeout: Runnable? = null
    private var state = "STOPPED"
    @Volatile private var closed = false
    @Volatile private var epoch = 0
    private var activeEpoch = 0
    private val tick =
        object : Runnable {
            override fun run() {
                report()
                if (!closed && prepared) handler.postDelayed(this, 250)
            }
        }

    private fun report() {
        if (prepared && !seeking) {
            try {
                position = (player?.currentPosition ?: position).coerceAtLeast(0)
                duration = (player?.duration ?: duration).coerceAtLeast(0)
            } catch (_: IllegalStateException) {}
        }
        val current = state
        val currentPosition = position
        val currentDuration = duration
        val reportEpoch = activeEpoch
        main.post {
            if (!closed && reportEpoch == epoch) changed(current, currentPosition, currentDuration)
        }
    }

    private fun command(work: () -> Unit) {
        if (closed) return
        val request = epoch
        handler.post {
            if (!closed && request == activeEpoch) {
                try {
                    work()
                } catch (_: Exception) {
                    fail()
                }
            }
        }
    }

    fun surface(value: PlayerSurface?) {
        if (closed) return
        value?.retain()
        handler.post {
            if (closed) {
                value?.release()
                return@post
            }
            val previous = output
            output = value
            intent.surfaceAvailable = value != null
            try {
                if (value == null) applyIntent()
                if (previous !== value)
                    player?.let { media ->
                        previous?.detach(media)
                        if (value != null) value.attach(media) else media.setDisplay(null)
                    }
                applyIntent()
            } catch (_: Exception) {
                fail()
            } finally {
                previous?.release()
            }
        }
    }

    fun play(
        url: String,
        position: Int,
        autoplay: Boolean = true,
        video: Boolean = true,
        seekable: Boolean = true,
        mime: String = "",
    ) {
        if (closed) return
        val request = ++epoch
        val pcmStream = PcmStreamPolicy.supportsMime(mime)
        handler.post {
            if (closed || request != epoch) return@post
            dispose()
            activeEpoch = request
            this.position = position.coerceAtLeast(0)
            duration = 0
            this.seekable = seekable && !pcmStream
            pcmActive = pcmStream
            intent.begin(autoplay, video && !pcmStream)
            if (pcmStream) {
                state = if (intent.canPlay) "BUFFERING" else "PAUSED"
                report()
                pcm.play(url, this.position, intent.canPlay) { status, progress, length ->
                    handler.post {
                        if (!closed && request == epoch && request == activeEpoch && pcmActive) {
                            state = status
                            this.position = progress.coerceAtLeast(0)
                            duration = length.coerceAtLeast(0)
                            if (status == "FAILED" || status == "ENDED" || status == "STOPPED")
                                pcmActive = false
                            report()
                        }
                    }
                }
                return@post
            }
            state = "BUFFERING"
            report()
            try {
                prepare(url)
            } catch (error: Exception) {
                Log.w("ZombiePlayback", "MediaPlayer prepare failed: ${error.javaClass.simpleName}")
                fail()
            }
        }
    }

    private fun isCurrent(source: MediaPlayer): Boolean =
        !closed && player === source && activeEpoch == epoch

    private fun prepare(url: String) {
        val media = MediaPlayer()
        player = media
        media.setAudioStreamType(AudioManager.STREAM_MUSIC)
        media.setVolume(volumeGain, volumeGain)
        output?.attach(media)
        media.setOnVideoSizeChangedListener { source, width, height ->
            if (isCurrent(source)) {
                val request = activeEpoch
                main.post { if (!closed && request == epoch) sizeChanged(width, height) }
            }
        }
        media.setOnPreparedListener { source ->
            if (isCurrent(source)) {
                try {
                    prepared = true
                    prepareTimeout?.let { handler.removeCallbacks(it) }
                    prepareTimeout = null
                    duration = source.duration.coerceAtLeast(0)
                    if (seekable && position > 0 && duration > 0) {
                        seekPosition(position.coerceAtMost(duration))
                    }
                    applyIntent()
                    tick.run()
                } catch (_: Exception) {
                    fail()
                }
            }
        }
        media.setOnSeekCompleteListener { source ->
            if (isCurrent(source)) {
                seeking = false
                prepareTimeout?.let { handler.removeCallbacks(it) }
                prepareTimeout = null
                try {
                    applyIntent()
                } catch (_: Exception) {
                    fail()
                }
            }
        }
        media.setOnInfoListener { source, what, _ ->
            if (isCurrent(source)) {
                when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> buffering = true
                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> buffering = false
                }
                try {
                    applyIntent()
                } catch (_: Exception) {
                    fail()
                }
            }
            false
        }
        media.setOnCompletionListener { source ->
            if (isCurrent(source)) {
                intent.pause()
                state = "ENDED"
                report()
                handler.removeCallbacks(tick)
            }
        }
        media.setOnErrorListener { source, what, extra ->
            if (isCurrent(source)) {
                Log.w("ZombiePlayback", "MediaPlayer error what=$what extra=$extra")
                fail()
            }
            true
        }
        media.setDataSource(url)
        media.prepareAsync()
        prepareTimeout = Runnable { if (player === media && !prepared) fail() }
        handler.postDelayed(prepareTimeout!!, 20000)
    }

    private fun applyIntent() {
        if (pcmActive) {
            if (state == "ENDED" || state == "FAILED") return
            if (intent.canPlay) {
                if (state == "PAUSED") pcm.resume()
            } else if (state != "PAUSED") {
                pcm.pause()
            }
            return
        }
        if (!prepared || state == "ENDED") return
        val media = player ?: return
        if (intent.canPlay && !seeking) {
            if (!media.isPlaying) media.start()
            state = if (buffering) "BUFFERING" else "PLAYING"
            if (buffering && bufferingTimeout == null) {
                bufferingTimeout = Runnable {
                    if (isCurrent(media) && buffering && intent.canPlay) fail()
                }
                handler.postDelayed(bufferingTimeout!!, 20000)
            }
        } else {
            if (media.isPlaying) media.pause()
            state = if (seeking && intent.canPlay) "BUFFERING" else "PAUSED"
        }
        if (!buffering || !intent.canPlay) {
            bufferingTimeout?.let { handler.removeCallbacks(it) }
            bufferingTimeout = null
        }
        report()
    }

    fun background(value: Boolean) {
        command {
            intent.backgroundPlayback = value
            applyIntent()
        }
    }

    fun foreground(value: Boolean) {
        if (closed) return
        handler.post {
            intent.foreground = value
            try {
                applyIntent()
            } catch (_: Exception) {
                fail()
            }
        }
    }

    fun toggle() = command {
        intent.toggle()
        if (state == "ENDED" && intent.wantsPlayback) state = "PAUSED"
        applyIntent()
    }

    fun resume() = command {
        intent.resume()
        if (state == "ENDED") state = "PAUSED"
        applyIntent()
    }

    fun pause() = command {
        intent.pause()
        applyIntent()
    }

    fun seek(delta: Int) = command {
        if (prepared && seekable && duration > 0)
            seekPosition((position.toLong() + delta).coerceIn(0, duration.toLong()).toInt())
    }

    fun seekTo(position: Int) = command {
        if (prepared && seekable && duration > 0) seekPosition(position.coerceIn(0, duration))
    }

    private fun seekPosition(value: Int) {
        position = value
        seeking = true
        val media = player ?: return
        media.seekTo(value)
        prepareTimeout?.let { handler.removeCallbacks(it) }
        prepareTimeout = Runnable { if (isCurrent(media) && seeking) fail() }
        handler.postDelayed(prepareTimeout!!, 20000)
        applyIntent()
    }

    fun volume(level: Int, muted: Boolean, done: (Boolean) -> Unit) {
        if (closed) return
        handler.post {
            val success =
                try {
                    volumeGain = if (muted) 0f else level.coerceIn(0, 100) / 100f
                    if (pcmActive) pcm.volume(level, muted)
                    else {
                        player?.setVolume(volumeGain, volumeGain)
                        player != null
                    }
                } catch (_: Exception) {
                    false
                }
            main.post { if (!closed) done(success) }
        }
    }

    private fun fail() {
        // Keep the last known position instead of overwriting progress with zero after release.
        dispose()
        state = "FAILED"
        report()
    }

    fun stop() {
        if (closed) return
        val request = ++epoch
        handler.post {
            if (closed || request != epoch) return@post
            dispose()
            activeEpoch = request
            intent.pause()
            position = 0
            duration = 0
            state = "STOPPED"
            report()
        }
    }

    private fun dispose() {
        handler.removeCallbacks(tick)
        bufferingTimeout?.let { handler.removeCallbacks(it) }
        bufferingTimeout = null
        prepareTimeout?.let { handler.removeCallbacks(it) }
        prepareTimeout = null
        prepared = false
        seeking = false
        buffering = false
        pcmActive = false
        pcm.stop()
        val old = player
        player = null
        try {
            old?.release()
        } catch (_: Exception) {}
    }

    fun close() {
        if (closed) return
        closed = true
        epoch++
        handler.post {
            dispose()
            output?.release()
            output = null
            pcm.close()
            thread.quit()
        }
    }
}
