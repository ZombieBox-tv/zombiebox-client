package io.github.diegog0477.zombiebox.client.features.playback.platform

import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.catalog.data.GatewayCatalogRepository
import io.github.diegog0477.zombiebox.client.features.mirroring.data.GatewayReceiverRepository
import io.github.diegog0477.zombiebox.client.features.mirroring.presentation.viewmodel.BackgroundReceptionViewModel
import io.github.diegog0477.zombiebox.client.features.playback.data.GatewayPlaybackRepository
import io.github.diegog0477.zombiebox.client.features.playback.data.LocalPlaybackResumeRepository
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.*
import io.github.diegog0477.zombiebox.client.features.playback.domain.policy.SystemControlCoordinator
import io.github.diegog0477.zombiebox.client.features.playback.presentation.viewmodel.PlaybackSessionViewModel
import io.github.diegog0477.zombiebox.client.features.playback.presentation.viewmodel.PlaybackViewModel
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.data.GatewayYouTubeReceiverRepository
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.domain.model.YouTubeReception
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.domain.repository.YouTubePlaybackControl
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.presentation.viewmodel.YouTubeReceiverViewModel
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.presentation.viewmodel.YouTubeReceptionViewModel
import io.github.diegog0477.zombiebox.shared.GatewayApi
import java.util.concurrent.Executors

/** Keeps AirPlay controls tied to the receiver's last reported state. */
internal object IncomingAirPlayControlPolicy {
    private val controllableStates = setOf("PLAYING", "PAUSED")

    fun isIncomingAirPlay(session: PlaybackSession): Boolean =
        session.incoming && session.item?.provider == "airplay"

    fun actionFor(remoteState: String, command: String): String? {
        if (remoteState !in controllableStates) return null
        return when (command) {
            "toggle" -> "playpause"
            "pause" -> if (remoteState == "PLAYING") "playpause" else null
            "play" -> if (remoteState == "PAUSED") "playpause" else null
            else -> null
        }
    }

    fun systemPlayback(session: PlaybackSession, remoteState: String): SystemPlayback? {
        if (!isIncomingAirPlay(session) || remoteState !in controllableStates) return null
        val authoritative =
            session.copy(
                incoming = false,
                loading = false,
                progress = session.progress.copy(state = remoteState),
            )
        return SystemPlayback.from(authoritative)
    }
}

/** Started while visible, then foreground for the lifetime of a playback session. */
class PlaybackService : Service() {
    inner class Access : Binder() {
        val service
            get() = this@PlaybackService
    }

    private val handler = Handler()
    private val worker = Executors.newSingleThreadExecutor()
    private val api = GatewayApi()
    lateinit var player: EmbeddedPlayer
        private set

    lateinit var model: PlaybackSessionViewModel
        private set

    lateinit var focus: AudioFocusController
        private set

    private lateinit var wake: PowerManager.WakeLock
    private var systemToken: Any? = null
    private lateinit var systemControls: SystemControlCoordinator
    private lateinit var notifications: PlaybackNotification
    private var notificationKey = ""
    private lateinit var foregroundSession: ForegroundSession
    lateinit var youtube: YouTubeReceptionViewModel
        private set

    var youtubeListener: ((YouTubeReception) -> Unit)? = null
    private var profile = Triple("", "", "")

    private fun youtubeIntentKey(base: String, device: String) = "youtubeReceiver:$base:$device"

    fun enableYouTubeReceiver() {
        val (base, device, token) = profile
        if (base.isEmpty() || device.isEmpty() || token.isEmpty()) return
        getSharedPreferences("zombie", MODE_PRIVATE)
            .edit()
            .putBoolean(youtubeIntentKey(base, device), true)
            .apply()
        youtube.enable()
    }

    fun disableYouTubeReceiver() {
        val (base, device) = profile
        if (base.isNotEmpty() && device.isNotEmpty()) {
            getSharedPreferences("zombie", MODE_PRIVATE)
                .edit()
                .putBoolean(youtubeIntentKey(base, device), false)
                .apply()
        }
        youtube.disable()
    }

    private val youtubePoll =
        object : Runnable {
            override fun run() {
                if (closed) return
                youtube.tick()
                handler.postDelayed(this, 1000)
            }
        }
    private var closed = false
    private lateinit var backgroundReception: BackgroundReceptionViewModel
    private var receiverPlaybackState = ""
    var visible = false
        private set

    var listener: ((PlaybackSession) -> Unit)? = null
    var sizeListener: ((Int, Int) -> Unit)? = null
    private var width = 0
    private var height = 0
    private val receiverPoll =
        object : Runnable {
            override fun run() {
                if (closed) return
                model.recoveryTick()
                backgroundReception.tick()
                handler.postDelayed(this, 3000)
            }
        }

    override fun onCreate() {
        super.onCreate()
        notifications = PlaybackNotifications.create()
        foregroundSession = ForegroundSession.create()
        wake =
            (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zombiebox:playback")
                .apply { setReferenceCounted(false) }
        player =
            EmbeddedPlayer({ w, h ->
                width = w
                height = h
                sizeListener?.invoke(w, h)
            }) { status, position, duration ->
                model.mediaState(status, position, duration)
            }
        refreshFocus()
        model =
            PlaybackSessionViewModel(
                GatewayPlaybackRepository(api) {
                    getSharedPreferences("zombie", MODE_PRIVATE)
                        .getBoolean("networkAdaptation", true)
                },
                GatewayCatalogRepository(api),
                { id -> GatewayReceiverRepository(api).stop(id) },
                { work -> worker.execute { work() } },
                { work -> handler.post { work() } },
                resumeRepository = LocalPlaybackResumeRepository(applicationContext, api),
            )
        youtube =
            YouTubeReceptionViewModel(
                YouTubeReceiverViewModel(
                    GatewayYouTubeReceiverRepository(api),
                    { work -> worker.execute { work() } },
                    { work -> handler.post { work() } },
                ),
                PlaybackViewModel(
                    GatewayPlaybackRepository(api),
                    { work -> worker.execute { work() } },
                    { work -> handler.post { work() } },
                ),
                model,
                object : YouTubePlaybackControl {
                    override fun play(plan: PlaybackPlan, item: MediaItem) = playPlan(plan, item)

                    override fun pause() = player.pause()

                    override fun resume(): Boolean {
                        if (!focus.acquire()) return false
                        player.resume()
                        return true
                    }

                    override fun seek(positionMs: Int) = player.seekTo(positionMs)

                    override fun volume(value: Int, muted: Boolean, done: (Boolean) -> Unit) =
                        player.volume(value, muted, done)
                },
                getString(R.string.youtube),
                { android.os.SystemClock.elapsedRealtime() },
            )
        youtube.observer = {
            updateNotification(model.state)
            youtubeListener?.invoke(it)
        }
        backgroundReception =
            BackgroundReceptionViewModel(
                GatewayReceiverRepository(api),
                model,
                { work -> worker.execute { work() } },
                { work -> handler.post { work() } },
                { received ->
                    PlaybackPlan(
                        received.sessionId,
                        api.base + received.path,
                        received.mime,
                        received.mode,
                        0,
                        live = received.live,
                        seekable = received.seekable,
                    )
                },
                ::playPlan,
                ::receiverStatus,
                { youtube.standby() },
                getString(R.string.screen_mirroring),
                { android.os.SystemClock.elapsedRealtime() },
            )
        backgroundReception.observer = { updateNotification(model.state) }
        systemControls = SystemControlsFactory.create(this, ::systemCommand) { systemToken = it }
        model.play = ::playPlan
        model.stopPlayer = { player.stop() }
        model.observer = { state ->
            youtube.playbackState(
                state.progress.state,
                state.progress.positionMs,
                state.progress.durationMs,
            )
            updateNotification(state)
            listener?.invoke(state)
        }
        player.background(true)
        handler.post(receiverPoll)
        handler.post(youtubePoll)
    }

    fun configure(base: String, device: String, token: String) {
        val nextProfile = Triple(base, device, token)
        if (profile != nextProfile) {
            // Revoke old authority before changing the serialized transport profile.
            backgroundReception.reset()
            youtube.disable()
            if (profile.first.isNotEmpty()) model.stop()
            profile = nextProfile
        }
        val preferences = getSharedPreferences("zombie", MODE_PRIVATE)
        model.automaticRecovery =
            preferences.getBoolean("automaticRecovery", true) &&
                preferences.getString("playbackMode", "AUTO") == "AUTO"
        // Serialize profile changes after queued writes/revocation for the old gateway.
        worker.execute { api.configure(base, device, token) }
        if (base.isNotEmpty() && device.isNotEmpty() && token.isNotEmpty()) {
            val savedProvider = preferences.getString("mediaReceiver:$base:$device", "") ?: ""
            backgroundReception.configure(mediaProvider = savedProvider)
        }
        if (
            base.isNotEmpty() &&
                device.isNotEmpty() &&
                token.isNotEmpty() &&
                preferences.getBoolean(youtubeIntentKey(base, device), false)
        ) {
            youtube.enable()
        }
    }

    fun refreshFocus() {
        if (::focus.isInitialized) focus.release()
        val preferences = getSharedPreferences("strategy-health", MODE_PRIVATE)
        val identity =
            Build.FINGERPRINT +
                ":" +
                packageManager.getPackageInfo(packageName, 0).versionName +
                ":focus-1"
        if (preferences.getString("identity", "") != identity)
            preferences.edit().clear().putString("identity", identity).apply()
        focus =
            AudioFocusFactory.create(
                Build.VERSION.SDK_INT,
                getSystemService(AUDIO_SERVICE) as AudioManager,
                AudioManager.OnAudioFocusChangeListener {
                    if (it <= 0 && !model.setRecoveryPaused(true)) player.pause()
                },
                getSharedPreferences("zombie", MODE_PRIVATE)
                    .getBoolean("audioFocusCompatibility", false),
                {
                    StrategyHealth(
                        preferences.getInt("focusFailures", 0),
                        preferences.getLong("focusRetryAfter", 0),
                    )
                },
                { health ->
                    preferences
                        .edit()
                        .putInt("focusFailures", health.failures)
                        .putLong("focusRetryAfter", health.retryAfter)
                        .apply()
                },
            )
    }

    fun configureReceivers(mediaProvider: String? = null, castEnabled: Boolean? = null) {
        backgroundReception.configure(mediaProvider, castEnabled)
    }

    fun foreground(value: Boolean) {
        backgroundReception.foreground(value)
        visible = value
        player.foreground(value)
    }

    fun attach(observer: (PlaybackSession) -> Unit, size: (Int, Int) -> Unit) {
        listener = observer
        sizeListener = size
        size(width, height)
        observer(model.state)
    }

    fun detach(observer: (PlaybackSession) -> Unit) {
        if (listener === observer) {
            listener = null
            sizeListener = null
            youtubeListener = null
            player.surface(null)
            foreground(false)
        }
    }

    fun adopt(
        plan: PlaybackPlan,
        item: MediaItem,
        queue: List<MediaItem>?,
        cursor: QueueCursor?,
        incoming: Boolean,
        paused: Boolean,
    ) {
        model.adopt(plan, item, queue, cursor, incoming, paused = paused)
    }

    private fun playPlan(plan: PlaybackPlan, item: MediaItem) {
        if (plan.mode == "EXTERNAL_PLAYER") {
            model.mediaState("FAILED", plan.resumePositionMs, 0)
            return
        }
        if (focus.acquire())
            player.play(
                plan.url,
                plan.resumePositionMs,
                autoplay = model.state.progress.state != "PAUSED",
                video = item.kind != "audio",
                seekable = plan.seekable && !plan.live,
                mime = plan.mime,
            )
        else model.mediaState("FAILED", plan.resumePositionMs, 0)
    }

    fun discard(plan: PlaybackPlan, incoming: Boolean) {
        worker.execute {
            try {
                if (incoming) GatewayReceiverRepository(api).stop(plan.sessionId)
                else GatewayPlaybackRepository(api).stop(plan.sessionId)
            } catch (_: Exception) {}
        }
    }

    fun receiverStatus(item: MediaItem?, status: String) {
        receiverPlaybackState = status
        item?.let { model.metadata(it) }
        updateNotification(model.state)
    }

    fun toggle() {
        if (airplayCommand("toggle")) return
        if (model.setRecoveryPaused(model.state.progress.state != "PAUSED")) return
        if (model.state.incoming && model.state.item?.provider == "spotify") {
            val action = if (receiverPlaybackState == "PAUSED") "resume" else "pause"
            worker.execute {
                try {
                    GatewayReceiverRepository(api).command(action)
                } catch (_: Exception) {}
            }
        } else if (focus.acquire()) player.toggle()
    }

    private fun systemState(state: PlaybackSession) =
        IncomingAirPlayControlPolicy.systemPlayback(state, receiverPlaybackState)
            ?: SystemPlayback.from(
                state,
                state.incoming &&
                    state.item?.provider == "spotify" &&
                    receiverPlaybackState == "PAUSED",
            )

    /** Returns true whenever this is an AirPlay session, including when state is unknown. */
    private fun airplayCommand(command: String): Boolean {
        if (!IncomingAirPlayControlPolicy.isIncomingAirPlay(model.state)) return false
        val action = IncomingAirPlayControlPolicy.actionFor(receiverPlaybackState, command)
        if (action != null) {
            worker.execute {
                try {
                    GatewayReceiverRepository(api).airplayCommand(action)
                } catch (_: Exception) {}
            }
        }
        return true
    }

    private fun stopFromControls() {
        if (model.state.incoming)
            worker.execute {
                try {
                    GatewayReceiverRepository(api).cancelQueue()
                } catch (_: Exception) {}
            }
        // Stop the current item only. Receiver listening is a separate user choice.
        model.stop()
    }

    private fun systemCommand(command: String, position: Long) {
        if (closed) return
        val state = systemState(model.state)
        if (!state.allows(command)) return
        when (command) {
            "stop" -> stopFromControls()
            "next" -> model.next()
            "seek" -> SystemPlayback.localSeek(model.state, position)?.let { player.seekTo(it) }
            "play",
            "pause" -> {
                if (airplayCommand(command)) return
                val paused = command == "pause"
                if (model.state.incoming && model.state.item?.provider == "spotify") {
                    worker.execute {
                        try {
                            GatewayReceiverRepository(api)
                                .command(if (paused) "pause" else "resume")
                        } catch (_: Exception) {}
                    }
                } else if (!model.setRecoveryPaused(paused)) {
                    if (paused) player.pause() else if (focus.acquire()) player.resume()
                }
            }
        }
    }

    private fun updateNotification(state: PlaybackSession) {
        systemControls.update(
            systemState(state),
            Build.VERSION.SDK_INT >= 21 &&
                getSharedPreferences("zombie", MODE_PRIVATE).getBoolean("systemMediaControls", true),
        )
        if (state.plan == null) {
            if (youtube.state.enabled || backgroundReception.state.enabled) {
                if (wake.isHeld) wake.release()
                focus.release()
                val listening =
                    SystemPlayback(
                        active = true,
                        title =
                            getString(
                                if (backgroundReception.state.enabled)
                                    R.string.receiver_background_title
                                else R.string.youtube_receiver
                            ),
                        subtitle =
                            getString(
                                if (youtube.state.failed || backgroundReception.state.unavailable)
                                    R.string.unavailable
                                else if (backgroundReception.state.enabled)
                                    R.string.receiver_background_listening
                                else R.string.youtube_background_listening
                            ),
                    )
                val key =
                    "listening:${youtube.state.enabled}:${youtube.state.failed}:${backgroundReception.state}"
                if (notificationKey != key) {
                    notificationKey = key
                    foregroundSession.show(
                        this,
                        notifications.build(this, listening),
                        receiving = true,
                        playing = false,
                    )
                }
                return
            }
            if (state.loading) return
            notificationKey = ""
            if (wake.isHeld) wake.release()
            focus.release()
            stopForeground(true)
            stopSelf()
            return
        }
        val remotePaused =
            state.incoming && state.item?.provider == "spotify" && receiverPlaybackState == "PAUSED"
        val active =
            !remotePaused &&
                (state.progress.state == "PLAYING" || state.progress.state == "BUFFERING")
        if (active && !wake.isHeld) wake.acquire(6 * 60 * 60 * 1000L)
        if (!active && wake.isHeld) wake.release()
        val key =
            "${youtube.state.enabled}:${backgroundReception.state.enabled}:${state.item?.title}:${state.item?.subtitle}:$active:${systemToken != null}:${systemState(state).canPause}:${state.canNext}"
        if (key != notificationKey) {
            notificationKey = key
            foregroundSession.show(
                this,
                notifications.build(this, systemState(state), systemToken),
                receiving = youtube.state.enabled || backgroundReception.state.enabled,
                playing = true,
            )
        }
    }

    override fun onBind(intent: Intent): IBinder = Access()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "stop" -> stopFromControls()
            "next" -> model.next()
            "toggle" -> toggle()
        }
        if (
            model.state.plan == null &&
                !model.state.loading &&
                !youtube.state.enabled &&
                !backgroundReception.state.enabled
        )
            stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        closed = true
        handler.removeCallbacks(receiverPoll)
        handler.removeCallbacks(youtubePoll)
        youtubeListener = null
        youtube.close()
        backgroundReception.close()
        listener = null
        sizeListener = null
        systemControls.close()
        model.close()
        player.close()
        focus.release()
        if (wake.isHeld) wake.release()
        // Drain bounded progress/revocation work before closing the transport.
        worker.execute {
            handler.post {
                worker.execute { api.close() }
                worker.shutdown()
            }
        }
        super.onDestroy()
    }
}
