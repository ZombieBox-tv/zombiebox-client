package io.github.diegog0477.zombiebox.client.features.diagnostics.platform

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.wifi.WifiManager
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeAsset
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeResult
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.repository.ProbePlayback
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.util.UUID

/** Measures local multicast delivery only; it does not certify LAN discovery or DIAL. */
class PlatformProbes(
    context: Context,
    private val media: ProbePlayback,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) : ProbePlayback {
    private val context = context.applicationContext
    @Volatile private var generation = 0
    @Volatile private var socket: MulticastSocket? = null
    private val audioTrackLock = Any()
    @Volatile private var audioTrack: AudioTrack? = null

    override fun start(asset: ProbeAsset, result: (ProbeResult) -> Unit) {
        if (asset.id == AUDIO_TRACK_PROBE_ID) {
            val run = ++generation
            execute {
                val probeResult = runAudioTrackProbe(asset, run)
                if (probeResult != null) {
                    deliver { if (run == generation) result(probeResult) }
                }
            }
            return
        }
        if (asset.id != "multicast-loopback") {
            media.start(asset, result)
            return
        }
        val run = ++generation
        execute {
            var lock: WifiManager.MulticastLock? = null
            var detail = ""
            val status =
                try {
                    val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    lock =
                        wifi?.createMulticastLock("zombie-probe")?.apply {
                            setReferenceCounted(false)
                            acquire()
                        }
                    val channel = MulticastSocket(0)
                    try {
                        socket = channel
                        if (run != generation) throw IllegalStateException("Probe cancelled")
                        channel.soTimeout = 1500
                        channel.timeToLive = 1
                        channel.loopbackMode = false
                        val group = InetAddress.getByName("239.255.77.77")
                        channel.joinGroup(group)
                        try {
                            val nonce = UUID.randomUUID().toString().toByteArray(Charsets.US_ASCII)
                            channel.send(
                                DatagramPacket(nonce, nonce.size, group, channel.localPort)
                            )
                            val packet = DatagramPacket(ByteArray(128), 128)
                            channel.receive(packet)
                            if (
                                packet.length == nonce.size &&
                                    packet.data.take(packet.length) == nonce.toList()
                            )
                                "PASS"
                            else {
                                detail = "payload_mismatch"
                                "UNKNOWN"
                            }
                        } finally {
                            channel.leaveGroup(group)
                        }
                    } finally {
                        channel.close()
                    }
                } catch (_: SocketTimeoutException) {
                    detail = "multicast_timeout"
                    "UNKNOWN"
                } catch (e: Exception) {
                    detail = "multicast_error:" + e.javaClass.simpleName
                    "UNKNOWN"
                } finally {
                    socket = null
                    try {
                        lock?.release()
                    } catch (_: Exception) {}
                }
            deliver {
                if (run == generation) result(ProbeResult(asset.id, status, detail = detail))
            }
        }
    }

    override fun cancel() {
        generation++
        socket?.close()
        val trackToRelease =
            synchronized(audioTrackLock) {
                val current = audioTrack
                audioTrack = null
                current
            }
        releaseAudioTrack(trackToRelease)
        media.cancel()
    }

    /**
     * PASS proves that this device accepted silent PCM writes and its AudioTrack playback head
     * advanced. It does not prove that sound was audible or that a speaker/headphone route works.
     */
    private fun runAudioTrackProbe(asset: ProbeAsset, run: Int): ProbeResult? {
        var activeTrack: AudioTrack? = null
        var status = "UNKNOWN"
        var detail = ""
        try {
            val minimumBufferBytes =
                AudioTrack.getMinBufferSize(
                    AUDIO_TRACK_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            if (minimumBufferBytes <= 0) {
                return ProbeResult(
                    asset.id,
                    "UNKNOWN",
                    detail = "min_buffer_unavailable:$minimumBufferBytes",
                )
            }
            if (minimumBufferBytes > AUDIO_TRACK_MAX_BUFFER_BYTES) {
                return ProbeResult(asset.id, "UNKNOWN", detail = "min_buffer_exceeds_probe_limit")
            }

            val created =
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    AUDIO_TRACK_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minimumBufferBytes,
                    AudioTrack.MODE_STREAM,
                )
            activeTrack = created
            val (registered, previousTrack) =
                synchronized(audioTrackLock) {
                    if (run != generation) false to null
                    else {
                        val previous = audioTrack
                        audioTrack = created
                        true to previous
                    }
                }
            if (!registered) {
                releaseAudioTrack(created)
                return null
            }
            releaseAudioTrack(previousTrack)
            if (run != generation) return null
            if (created.state != AudioTrack.STATE_INITIALIZED) {
                status = "FAIL"
                detail = "track_not_initialized"
                return ProbeResult(asset.id, status, detail = detail)
            }

            // 512 stereo frames (about 12 ms) keeps each blocking legacy write bounded.
            val silence = ShortArray(AUDIO_TRACK_CHUNK_FRAMES * AUDIO_TRACK_CHANNELS)
            val initialHead = created.playbackHeadPosition
            created.play()
            val deadlineNanos = System.nanoTime() + AUDIO_TRACK_PROBE_BUDGET_NANOS
            var acceptedSamples = 0L
            while (run == generation && System.nanoTime() < deadlineNanos) {
                val writtenSamples = created.write(silence, 0, silence.size)
                if (writtenSamples < 0) {
                    status = "FAIL"
                    detail = "write_error:$writtenSamples"
                    return ProbeResult(asset.id, status, detail = detail)
                }
                if (writtenSamples == 0) {
                    Thread.sleep(AUDIO_TRACK_ZERO_WRITE_DELAY_MS)
                } else {
                    acceptedSamples += writtenSamples
                }
            }
            if (run != generation) return null

            val headFrames =
                AudioTrackProbePolicy.frameDelta(initialHead, created.playbackHeadPosition)
            return AudioTrackProbePolicy.result(asset.id, headFrames, acceptedSamples)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            if (run != generation) return null
            detail = "probe_interrupted"
        } catch (e: Exception) {
            if (run != generation) return null
            detail = "audiotrack_error:${e.javaClass.simpleName}"
        } finally {
            releaseOwnedAudioTrack(activeTrack)
        }
        return ProbeResult(asset.id, status, detail = detail.take(120))
    }

    private fun releaseOwnedAudioTrack(track: AudioTrack?) {
        if (track == null) return
        val ownsTrack =
            synchronized(audioTrackLock) {
                if (audioTrack === track) {
                    audioTrack = null
                    true
                } else {
                    false
                }
            }
        if (ownsTrack) releaseAudioTrack(track)
    }

    private fun releaseAudioTrack(track: AudioTrack?) {
        try {
            track?.release()
        } catch (_: Exception) {}
    }

    companion object {
        private const val AUDIO_TRACK_PROBE_ID = "audio-track-pcm-stream"
        private const val AUDIO_TRACK_SAMPLE_RATE = 44_100
        private const val AUDIO_TRACK_CHANNELS = 2
        private const val AUDIO_TRACK_CHUNK_FRAMES = 512
        private const val AUDIO_TRACK_MAX_BUFFER_BYTES = 1_048_576
        private const val AUDIO_TRACK_PROBE_BUDGET_NANOS = 2_300_000_000L
        private const val AUDIO_TRACK_ZERO_WRITE_DELAY_MS = 10L

        fun assets() =
            listOf(
                ProbeAsset("multicast-loopback", "", false, "local"),
                ProbeAsset(AUDIO_TRACK_PROBE_ID, "", false, "local"),
            )
    }
}
