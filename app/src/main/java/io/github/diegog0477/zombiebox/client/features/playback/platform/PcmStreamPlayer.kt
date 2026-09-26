package io.github.diegog0477.zombiebox.client.features.playback.platform

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale

/** Wire and resource limits for the compatibility-only raw PCM stream. */
internal object PcmStreamPolicy {
    const val MIME_TYPE = "audio/x-zombiebox-pcm;format=s16le;rate=44100;channels=2"
    const val SAMPLE_RATE = 44_100
    const val CHANNELS = 2
    const val BYTES_PER_FRAME = 4
    const val READ_BUFFER_BYTES = 16 * 1024
    const val CONNECT_TIMEOUT_MS = 5_000
    const val READ_TIMEOUT_MS = 2_000
    // A connected AirPlay sender may stay paused for minutes while the live
    // HTTP stream remains open. Keep the bounded read poll alive long enough
    // to resume without rebuilding the decoder after each ordinary pause.
    const val MAX_CONSECUTIVE_READ_TIMEOUTS = 900
    const val MAX_EMPTY_READS = 20
    const val EMPTY_READ_DELAY_MS = 50L
    const val HEAD_POLL_MS = 100L
    const val DRAIN_TIMEOUT_MS = 5_000L
    const val HEAD_STALL_TIMEOUT_MS = 3_000L
    const val MAX_AUDIO_BUFFER_BYTES = 256 * 1024
    // The bridge's 0.6-second HLS segments arrive in bursts. Hold enough
    // PCM to cover a segment plus jitter without adding several seconds of lag.
    const val TARGET_AUDIO_BUFFER_BYTES = 160 * 1024

    private val parameters = mapOf("format" to "s16le", "rate" to "44100", "channels" to "2")

    fun supportsMime(value: String?): Boolean = value == MIME_TYPE

    fun acceptsResponse(statusCode: Int, contentType: String?): Boolean =
        statusCode == HttpURLConnection.HTTP_OK && hasExactParameters(contentType)

    fun isSafeUrl(value: String): Boolean =
        try {
            isHttpUrl(URL(value))
        } catch (_: Exception) {
            false
        }

    fun sameOrigin(first: URL, second: URL): Boolean =
        first.protocol.equals(second.protocol, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) &&
            effectivePort(first) == effectivePort(second)

    fun frameDelta(previous: Int, current: Int): Long =
        (current - previous).toLong() and 0xffff_ffffL

    fun audioBufferBytes(minimum: Int): Int =
        maxOf(minimum, TARGET_AUDIO_BUFFER_BYTES).coerceAtMost(MAX_AUDIO_BUFFER_BYTES)

    private fun hasExactParameters(value: String?): Boolean {
        if (value == null) return false
        val parts = value.split(';')
        if (!parts.firstOrNull().orEmpty().trim().equals("audio/x-zombiebox-pcm", true))
            return false
        if (parts.size != parameters.size + 1) return false
        val received = HashMap<String, String>(parameters.size)
        for (part in parts.drop(1)) {
            val assignment = part.split('=', limit = 2)
            if (assignment.size != 2) return false
            val key = assignment[0].trim().lowercase(Locale.US)
            val parameterValue = assignment[1].trim().lowercase(Locale.US)
            if (key !in parameters || received.put(key, parameterValue) != null) return false
        }
        return received == parameters
    }

    private fun isHttpUrl(url: URL): Boolean =
        (url.protocol.equals("http", true) || url.protocol.equals("https", true)) &&
            url.host.isNotEmpty() &&
            url.userInfo == null &&
            url.ref == null

    private fun effectivePort(url: URL): Int =
        if (url.port >= 0) url.port else if (url.protocol.equals("https", true)) 443 else 80
}

internal interface PcmStreamResponse {
    val statusCode: Int
    val contentType: String?

    fun connect() = Unit

    @Throws(IOException::class) fun read(buffer: ByteArray, offset: Int, length: Int): Int

    fun close()
}

internal interface PcmStreamTransport {
    @Throws(IOException::class) fun open(url: String): PcmStreamResponse
}

internal interface PcmAudioOutput {
    fun play()

    fun pause()

    fun write(buffer: ByteArray, offset: Int, length: Int): Int

    fun playbackHeadPosition(): Int

    fun volume(gain: Float): Boolean

    fun stop()

    fun release()
}

/** One serialized network/audio worker. Replacements cancel the old read before opening another. */
internal class PcmStreamPlayer(
    private val transport: PcmStreamTransport = HttpPcmStreamTransport(),
    private val outputFactory: () -> PcmAudioOutput = { AndroidPcmAudioOutput() },
) {
    private class Job(
        val generation: Int,
        val url: String,
        val startPositionMs: Int,
        val autoplay: Boolean,
        val changed: (String, Int, Int) -> Unit,
    ) {
        val monitor = Object()
        @Volatile var cancelled = false
        @Volatile var terminal = false
        var paused = !autoplay
        var response: PcmStreamResponse? = null
        var output: PcmAudioOutput? = null
        var outputStarted = false
        var previousHead = 0
        var headFrames = 0L
        var writtenFrames = 0L
        var lastHeadAdvanceNanos = 0L
        var awaitingHeadAdvance = autoplay
        var positionMs = startPositionMs.coerceAtLeast(0)
        var reportedState = if (autoplay) "BUFFERING" else "PAUSED"
    }

    private val lock = Object()
    private var generation = 0
    private var current: Job? = null
    private var pending: Job? = null
    private var closed = false
    @Volatile private var volumeGain = 1f
    private val worker =
        Thread({ runLoop() }, "zombie-pcm-stream").apply {
            isDaemon = true
            start()
        }

    fun play(
        url: String,
        startPositionMs: Int,
        autoplay: Boolean,
        changed: (String, Int, Int) -> Unit,
    ) {
        val old: Job?
        val oldPending: Job?
        val job: Job
        synchronized(lock) {
            if (closed) return
            old = current
            oldPending = pending
            generation += 1
            job = Job(generation, url, startPositionMs.coerceAtLeast(0), autoplay, changed)
            current = job
            pending = job
            lock.notifyAll()
        }
        old?.let(::cancel)
        if (oldPending !== old) oldPending?.let(::cancel)
        changed(if (autoplay) "BUFFERING" else "PAUSED", job.positionMs, 0)
    }

    fun pause() = setPaused(true)

    fun resume() = setPaused(false)

    private fun setPaused(value: Boolean) {
        val job = synchronized(lock) { current } ?: return
        val state: String
        synchronized(job.monitor) {
            if (job.cancelled || job.terminal || job.paused == value) return
            job.paused = value
            val output = job.output
            if (job.outputStarted && output != null) {
                if (value) output.pause() else output.play()
            }
            if (!value) job.awaitingHeadAdvance = true
            state = if (value) "PAUSED" else "BUFFERING"
            job.monitor.notifyAll()
        }
        emit(job, state, job.positionMs)
    }

    fun volume(value: Int, muted: Boolean): Boolean {
        if (synchronized(lock) { closed }) return false
        volumeGain = if (muted) 0f else value.coerceIn(0, 100) / 100f
        val job = synchronized(lock) { current } ?: return true
        synchronized(job.monitor) {
            if (job.cancelled) return true
            val output = job.output ?: return true
            return output.volume(volumeGain)
        }
    }

    fun stop() {
        val job: Job?
        synchronized(lock) {
            job = current
            current = null
            pending = null
            lock.notifyAll()
        }
        if (job != null) {
            cancel(job)
            val shouldReport =
                synchronized(lock) { !closed && current == null && generation == job.generation }
            if (shouldReport) job.changed("STOPPED", 0, 0)
        }
    }

    fun close() {
        val active: Job?
        val queued: Job?
        synchronized(lock) {
            if (closed) return
            closed = true
            active = current
            queued = pending
            current = null
            pending = null
            lock.notifyAll()
        }
        active?.let(::cancel)
        if (queued !== active) queued?.let(::cancel)
        worker.interrupt()
    }

    private fun runLoop() {
        while (true) {
            val job =
                synchronized(lock) {
                    while (!closed && pending == null) lock.wait()
                    if (closed) return
                    val next = pending
                    pending = null
                    next
                } ?: continue
            if (!job.cancelled && isCurrent(job)) runJob(job)
            synchronized(lock) { if (current === job && job.terminal) current = null }
        }
    }

    private fun runJob(job: Job) {
        try {
            if (!PcmStreamPolicy.isSafeUrl(job.url)) throw IOException("Invalid PCM stream URL")
            val response = transport.open(job.url)
            if (!installResponse(job, response)) {
                response.close()
                return
            }
            response.connect()
            if (!PcmStreamPolicy.acceptsResponse(response.statusCode, response.contentType))
                throw IOException("Unexpected PCM stream response")

            val output = outputFactory()
            if (!installOutput(job, output)) {
                output.stop()
                output.release()
                return
            }
            synchronized(job.monitor) {
                if (job.cancelled) return
                job.previousHead = output.playbackHeadPosition()
                job.lastHeadAdvanceNanos = System.nanoTime()
                if (!output.volume(volumeGain)) throw IOException("Audio output volume failed")
            }

            val buffer =
                ByteArray(PcmStreamPolicy.READ_BUFFER_BYTES + PcmStreamPolicy.BYTES_PER_FRAME)
            var carry = 0
            var emptyReads = 0
            var consecutiveTimeouts = 0
            while (true) {
                waitUntilResumed(job)
                if (job.cancelled) return
                val count =
                    try {
                        response.read(buffer, carry, PcmStreamPolicy.READ_BUFFER_BYTES)
                    } catch (_: SocketTimeoutException) {
                        consecutiveTimeouts += 1
                        reportHead(job, output)
                        if (consecutiveTimeouts > PcmStreamPolicy.MAX_CONSECUTIVE_READ_TIMEOUTS)
                            throw IOException("PCM stream read stalled")
                        continue
                    }
                if (count < 0) {
                    if (carry != 0) throw IOException("Incomplete PCM audio frame")
                    drain(job, output)
                    job.terminal = true
                    emit(job, "ENDED", job.positionMs)
                    return
                }
                if (count == 0) {
                    emptyReads += 1
                    if (emptyReads > PcmStreamPolicy.MAX_EMPTY_READS)
                        throw IOException("PCM stream returned empty reads")
                    Thread.sleep(PcmStreamPolicy.EMPTY_READ_DELAY_MS)
                    continue
                }
                emptyReads = 0
                consecutiveTimeouts = 0

                val available = carry + count
                val frameBytes = available - (available % PcmStreamPolicy.BYTES_PER_FRAME)
                if (frameBytes > 0) write(job, output, buffer, frameBytes)
                carry = available - frameBytes
                if (carry > 0) System.arraycopy(buffer, frameBytes, buffer, 0, carry)
            }
        } catch (_: InterruptedException) {
            if (!job.cancelled) fail(job)
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            if (!job.cancelled) fail(job)
        } finally {
            closeResources(job)
            if (!job.terminal && job.cancelled) job.terminal = true
        }
    }

    private fun write(job: Job, output: PcmAudioOutput, buffer: ByteArray, length: Int) {
        var offset = 0
        while (offset < length) {
            waitUntilResumed(job)
            if (job.cancelled) return
            synchronized(job.monitor) {
                if (job.cancelled) return
                if (!job.paused && !job.outputStarted) {
                    output.play()
                    job.outputStarted = true
                }
            }
            val written = output.write(buffer, offset, length - offset)
            if (written <= 0 || written % PcmStreamPolicy.BYTES_PER_FRAME != 0)
                throw IOException("PCM audio output write failed")
            offset += written
            job.writtenFrames += written / PcmStreamPolicy.BYTES_PER_FRAME
            reportHead(job, output)
        }
    }

    private fun reportHead(job: Job, output: PcmAudioOutput) {
        val delta: Long
        val state: String
        val position: Int
        synchronized(job.monitor) {
            val head = output.playbackHeadPosition()
            delta = PcmStreamPolicy.frameDelta(job.previousHead, head)
            if (delta > 0L) {
                job.previousHead = head
                job.headFrames += delta
                job.lastHeadAdvanceNanos = System.nanoTime()
                job.awaitingHeadAdvance = false
                job.positionMs =
                    (job.startPositionMs.toLong() +
                            job.headFrames * 1000L / PcmStreamPolicy.SAMPLE_RATE)
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt()
            }
            state =
                when {
                    job.paused -> "PAUSED"
                    job.awaitingHeadAdvance -> "BUFFERING"
                    System.nanoTime() - job.lastHeadAdvanceNanos >
                        PcmStreamPolicy.HEAD_STALL_TIMEOUT_MS * 1_000_000L -> "BUFFERING"
                    else -> "PLAYING"
                }
            position = job.positionMs
        }
        if (delta > 0L || state != job.reportedState) emit(job, state, position)
    }

    private fun drain(job: Job, output: PcmAudioOutput) {
        var deadline = System.nanoTime() + PcmStreamPolicy.DRAIN_TIMEOUT_MS * 1_000_000L
        while (!job.cancelled) {
            if (waitUntilResumed(job))
                deadline = System.nanoTime() + PcmStreamPolicy.DRAIN_TIMEOUT_MS * 1_000_000L
            reportHead(job, output)
            if (job.headFrames >= job.writtenFrames) return
            if (System.nanoTime() >= deadline) throw IOException("PCM audio drain timed out")
            synchronized(job.monitor) {
                if (!job.cancelled && !job.paused) job.monitor.wait(PcmStreamPolicy.HEAD_POLL_MS)
            }
        }
    }

    private fun waitUntilResumed(job: Job): Boolean {
        var waited = false
        synchronized(job.monitor) {
            while (!job.cancelled && job.paused && !isClosed()) {
                waited = true
                job.monitor.wait()
            }
        }
        return waited
    }

    private fun installResponse(job: Job, response: PcmStreamResponse): Boolean =
        synchronized(job.monitor) {
            if (job.cancelled) false
            else {
                job.response = response
                true
            }
        }

    private fun installOutput(job: Job, output: PcmAudioOutput): Boolean =
        synchronized(job.monitor) {
            if (job.cancelled) false
            else {
                job.output = output
                true
            }
        }

    private fun emit(job: Job, state: String, positionMs: Int) {
        if (!isCurrent(job) || job.cancelled) return
        job.reportedState = state
        job.changed(state, positionMs.coerceAtLeast(0), 0)
    }

    private fun fail(job: Job) {
        job.terminal = true
        emit(job, "FAILED", job.positionMs)
    }

    private fun isCurrent(job: Job): Boolean = synchronized(lock) { !closed && current === job }

    private fun isClosed(): Boolean = synchronized(lock) { closed }

    private fun cancel(job: Job) {
        synchronized(job.monitor) {
            job.cancelled = true
            job.monitor.notifyAll()
        }
        closeResources(job)
    }

    private fun closeResources(job: Job) {
        val response: PcmStreamResponse?
        val output: PcmAudioOutput?
        synchronized(job.monitor) {
            response = job.response
            output = job.output
            job.response = null
            job.output = null
        }
        try {
            response?.close()
        } catch (_: Exception) {}
        if (output != null) {
            try {
                output.stop()
            } catch (_: Exception) {}
            try {
                output.release()
            } catch (_: Exception) {}
        }
    }
}

private class HttpPcmStreamTransport : PcmStreamTransport {
    override fun open(url: String): PcmStreamResponse {
        val requested = URL(url)
        if (!PcmStreamPolicy.isSafeUrl(url)) throw IOException("Invalid PCM stream URL")
        val connection =
            requested.openConnection() as? HttpURLConnection
                ?: throw IOException("Unsupported PCM stream connection")
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = PcmStreamPolicy.CONNECT_TIMEOUT_MS
            connection.readTimeout = PcmStreamPolicy.READ_TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("Accept", PcmStreamPolicy.MIME_TYPE)
            connection.setRequestProperty("Connection", "close")
            return HttpPcmStreamResponse(connection, requested)
        } catch (error: Exception) {
            connection.disconnect()
            throw error
        }
    }
}

private class HttpPcmStreamResponse(
    private val connection: HttpURLConnection,
    private val requested: URL,
) : PcmStreamResponse {
    @Volatile private var input: InputStream? = null
    @Volatile private var closed = false
    @Volatile private var responseStatus = 0
    @Volatile private var responseContentType: String? = null

    override val statusCode: Int
        get() = responseStatus

    override val contentType: String?
        get() = responseContentType

    override fun connect() {
        if (closed) throw IOException("PCM stream was cancelled")
        connection.connect()
        if (!PcmStreamPolicy.sameOrigin(requested, connection.url))
            throw IOException("PCM stream origin changed")
        responseStatus = connection.responseCode
        responseContentType = connection.getHeaderField("Content-Type")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed) return -1
        var stream = input
        if (stream == null) {
            stream = connection.inputStream
            if (closed) {
                stream.close()
                return -1
            }
            input = stream
        }
        return stream.read(buffer, offset, length)
    }

    override fun close() {
        closed = true
        try {
            input?.close()
        } catch (_: Exception) {}
        connection.disconnect()
    }
}

private class AndroidPcmAudioOutput : PcmAudioOutput {
    private val track: AudioTrack

    init {
        val minimum =
            AudioTrack.getMinBufferSize(
                PcmStreamPolicy.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minimum <= 0 || minimum > PcmStreamPolicy.MAX_AUDIO_BUFFER_BYTES)
            throw IOException("AudioTrack buffer configuration failed")
        val size = PcmStreamPolicy.audioBufferBytes(minimum)
        val created =
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                PcmStreamPolicy.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                size,
                AudioTrack.MODE_STREAM,
            )
        if (created.state != AudioTrack.STATE_INITIALIZED) {
            created.release()
            throw IOException("AudioTrack initialization failed")
        }
        track = created
    }

    override fun play() = track.play()

    override fun pause() = track.pause()

    override fun write(buffer: ByteArray, offset: Int, length: Int): Int =
        track.write(buffer, offset, length)

    override fun playbackHeadPosition(): Int = track.playbackHeadPosition

    override fun volume(gain: Float): Boolean = track.setStereoVolume(gain, gain) >= 0

    override fun stop() {
        if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
    }

    override fun release() = track.release()
}
