package io.github.diegog0477.zombiebox.client.features.diagnostics.platform

import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.SurfaceHolder
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeAsset
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeResult
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.repository.ProbePlayback
import io.github.diegog0477.zombiebox.client.features.playback.platform.PlayerSurface
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** API9 calls only. Completion and advancement are evidence, prepare alone is not. */
class MediaProbePlayback : ProbePlayback {
    private val thread = HandlerThread("zombie-probes").apply { start() }
    private val handler = Handler(thread.looper)
    @Volatile var surface: SurfaceHolder? = null
    @Volatile var textureSurface: PlayerSurface? = null
    @Volatile var textureFrames: () -> Int = { 0 }
    private var boundOutput: PlayerSurface? = null
    private var player: MediaPlayer? = null
    private var generation = 0
    private var closed = false

    private enum class ProbeStage {
        PREPARING,
        PLAYING,
    }

    override fun start(asset: ProbeAsset, result: (ProbeResult) -> Unit) {
        handler.post {
            if (closed) return@post
            dispose()
            val run = ++generation
            var stage = ProbeStage.PREPARING
            val started = SystemClock.elapsedRealtime()
            var prepareMs = 0
            var firstFrameMs = 0
            var positionMs = 0
            val media = MediaPlayer()
            player = media
            var finished = false
            var operationStarted = false
            val initialFrames = textureFrames()
            var operationComplete = asset.kind in listOf("playback", "hls", "texture-output")
            var operationPosition = 0
            var advancedAfterOperation = false
            fun finish(
                status: String,
                completed: Boolean = false,
                stalled: Boolean = false,
                detail: String = "",
            ) {
                if (finished || run != generation) return
                finished = true
                dispose()
                result(
                    ProbeResult(
                        asset.id,
                        status,
                        prepareMs,
                        firstFrameMs,
                        positionMs,
                        completed,
                        stalled,
                        detail = detail.take(120),
                    )
                )
            }
            val tick =
                object : Runnable {
                    override fun run() {
                        if (finished || run != generation) return
                        try {
                            val current = media.currentPosition
                            positionMs = maxOf(positionMs, current)
                            if (operationComplete && current >= operationPosition + 500) {
                                advancedAfterOperation = true
                            }
                            if (
                                !operationStarted &&
                                    current >= (if (asset.kind == "seek") 800 else 300) &&
                                    !operationComplete
                            ) {
                                operationStarted = true
                                when (asset.kind) {
                                    "seek" -> media.seekTo(0)
                                    "pause-resume" -> {
                                        media.pause()
                                        val pausedAt = media.currentPosition
                                        handler.postDelayed(
                                            {
                                                if (finished || run != generation)
                                                    return@postDelayed
                                                try {
                                                    if (
                                                        media.isPlaying ||
                                                            kotlin.math.abs(
                                                                media.currentPosition - pausedAt
                                                            ) > 200
                                                    ) {
                                                        finish("FAIL", detail = "pause_failed")
                                                    } else {
                                                        operationPosition = media.currentPosition
                                                        operationComplete = true
                                                        media.start()
                                                    }
                                                } catch (e: Exception) {
                                                    finish(
                                                        "UNKNOWN",
                                                        detail =
                                                            "resume_error:${e.javaClass.simpleName}",
                                                    )
                                                }
                                            },
                                            300,
                                        )
                                    }
                                    "surface-reattach" -> {
                                        media.setDisplay(null)
                                        handler.postDelayed(
                                            {
                                                if (finished || run != generation)
                                                    return@postDelayed
                                                try {
                                                    val holder = surface
                                                    if (holder == null || !holder.surface.isValid) {
                                                        finish(
                                                            "UNKNOWN",
                                                            detail = "surface_invalid",
                                                        )
                                                    } else {
                                                        media.setDisplay(holder)
                                                        operationPosition = media.currentPosition
                                                        operationComplete = true
                                                    }
                                                } catch (e: Exception) {
                                                    finish(
                                                        "UNKNOWN",
                                                        detail =
                                                            "surface_error:${e.javaClass.simpleName}",
                                                    )
                                                }
                                            },
                                            200,
                                        )
                                    }
                                    else ->
                                        finish("UNKNOWN", detail = "unsupported_kind:${asset.kind}")
                                }
                            }
                        } catch (e: Exception) {
                            finish("UNKNOWN", detail = "tick_error:${e.javaClass.simpleName}")
                        }
                        handler.postDelayed(this, 100)
                    }
                }
            try {
                media.setVolume(0f, 0f)
                if (asset.video) {
                    if (asset.kind == "texture-output") {
                        val output = textureSurface
                        if (output == null) {
                            finish("UNKNOWN", detail = "missing_texture")
                            return@post
                        }
                        output.retain()
                        boundOutput = output
                        output.attach(media)
                    } else {
                        if (surface == null) {
                            finish("UNKNOWN", detail = "missing_surface")
                            return@post
                        }
                        media.setDisplay(surface)
                    }
                }
                media.setOnPreparedListener {
                    if (finished || run != generation) return@setOnPreparedListener
                    prepareMs = (SystemClock.elapsedRealtime() - started).toInt()
                    stage = ProbeStage.PLAYING
                    try {
                        media.start()
                        handler.post(tick)
                    } catch (e: Exception) {
                        finish("UNKNOWN", detail = "start_failed:${e.javaClass.simpleName}")
                    }
                }
                media.setOnInfoListener { _, what, _ ->
                    // Numeric rendering-start event is optional on old vendor players.
                    if (what == 3 && firstFrameMs == 0)
                        firstFrameMs = (SystemClock.elapsedRealtime() - started).toInt()
                    false
                }
                media.setOnSeekCompleteListener {
                    if (
                        !finished && run == generation && asset.kind == "seek" && operationStarted
                    ) {
                        try {
                            val current = media.currentPosition
                            // Completion alone is insufficient: validate the target and
                            // advancement.
                            if (current in 0..350) {
                                operationPosition = current
                                operationComplete = true
                            } else finish("FAIL", detail = "seek_pos:$current")
                        } catch (e: Exception) {
                            finish("UNKNOWN", detail = "seek_error:${e.javaClass.simpleName}")
                        }
                    }
                }
                media.setOnCompletionListener {
                    val passed =
                        operationComplete &&
                            advancedAfterOperation &&
                            (asset.kind != "texture-output" || textureFrames() - initialFrames >= 3)
                    val detail =
                        if (passed) ""
                        else
                            when {
                                !operationComplete -> "operation_incomplete"
                                !advancedAfterOperation -> "no_advance"
                                else -> "insufficient_frames"
                            }
                    finish(if (passed) "PASS" else "UNKNOWN", completed = true, detail = detail)
                }
                media.setOnErrorListener { _, what, extra ->
                    if (finished || run != generation) return@setOnErrorListener true
                    val stageStr =
                        when (stage) {
                            ProbeStage.PREPARING -> "prepare"
                            ProbeStage.PLAYING -> "playback"
                        }
                    val status = if (extra == -1010 || extra == -1007) "FAIL" else "UNKNOWN"
                    val baseDetail = "what=$what,extra=$extra@$stageStr"
                    if (asset.url.startsWith("http")) {
                        // Offload blocking network I/O so the HandlerThread is not held for up to
                        // 4 s (HEAD + GET fallback). Post finish() back to preserve thread-safety
                        // of the finished flag and honour generation cancellation.
                        Thread {
                                val evidence = probeHttpEvidence(asset.url)
                                val errDetail =
                                    if (evidence.isNotEmpty()) "$baseDetail $evidence"
                                    else baseDetail
                                handler.post { finish(status, detail = errDetail) }
                            }
                            .also { it.isDaemon = true }
                            .start()
                    } else {
                        finish(status, detail = baseDetail)
                    }
                    true
                }
                media.setDataSource(asset.url)
                media.prepareAsync()
                handler.postDelayed(
                    {
                        if (finished || run != generation) return@postDelayed
                        val capturedStage = stage
                        val stageStr =
                            when (capturedStage) {
                                ProbeStage.PREPARING -> "prepare"
                                ProbeStage.PLAYING -> "playback"
                            }
                        if (capturedStage == ProbeStage.PREPARING && asset.url.startsWith("http")) {
                            // Offload blocking network I/O; post finish() back to HandlerThread.
                            Thread {
                                    val evidence = probeHttpEvidence(asset.url)
                                    val timeoutDetail = buildString {
                                        append("timeout@").append(stageStr)
                                        if (evidence.isNotEmpty()) append(" ").append(evidence)
                                    }
                                    handler.post {
                                        // stage == PREPARING still means stalled = false (not yet
                                        // played)
                                        finish("UNKNOWN", stalled = false, detail = timeoutDetail)
                                    }
                                }
                                .also { it.isDaemon = true }
                                .start()
                        } else {
                            val timeoutDetail = "timeout@$stageStr"
                            // stage == PLAYING: prepare completed, playback timed out => stalled
                            finish(
                                "UNKNOWN",
                                stalled = capturedStage == ProbeStage.PLAYING,
                                detail = timeoutDetail,
                            )
                        }
                    },
                    12000,
                )
            } catch (e: Exception) {
                val exBase = "exception:${e.javaClass.simpleName}@prepare"
                if (asset.url.startsWith("http")) {
                    // Offload blocking network I/O; post finish() back to HandlerThread.
                    Thread {
                            val evidence = probeHttpEvidence(asset.url)
                            val exDetail =
                                if (evidence.isNotEmpty()) "$exBase $evidence" else exBase
                            handler.post { finish("UNKNOWN", detail = exDetail) }
                        }
                        .also { it.isDaemon = true }
                        .start()
                } else {
                    finish("UNKNOWN", detail = exBase)
                }
            }
        }
    }

    private fun dispose() {
        try {
            player?.release()
        } catch (_: Exception) {}
        player = null
        boundOutput?.release()
        boundOutput = null
    }

    override fun cancel() {
        handler.post {
            generation++
            dispose()
        }
    }

    fun close() {
        handler.post {
            closed = true
            generation++
            dispose()
            thread.quit()
        }
    }

    companion object {
        fun probeHttpEvidence(urlStr: String, timeoutMs: Int = 2000): String {
            if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) return ""
            return try {
                val url = URL(urlStr)
                fun probe(method: String): Pair<Int, String> {
                    val conn =
                        (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = method
                            connectTimeout = timeoutMs
                            readTimeout = timeoutMs
                            instanceFollowRedirects = false
                        }
                    try {
                        val code = conn.responseCode
                        val type =
                            if (code > 0)
                                conn.contentType?.substringBefore(";")?.trim()?.lowercase() ?: ""
                            else ""
                        return code to type
                    } finally {
                        conn.disconnect()
                    }
                }
                var (code, type) = probe("HEAD")
                if (code == 405 || code == -1) {
                    val fallback = probe("GET")
                    code = fallback.first
                    type = fallback.second
                }
                if (code in 200..399) {
                    if (type.isNotEmpty()) "http=$code,$type" else "http=$code"
                } else if (code > 0) {
                    "http=$code"
                } else {
                    "http=failed"
                }
            } catch (_: SocketTimeoutException) {
                "http=timeout"
            } catch (_: IOException) {
                "http=io_error"
            } catch (_: Exception) {
                "http=error"
            }
        }
    }
}
