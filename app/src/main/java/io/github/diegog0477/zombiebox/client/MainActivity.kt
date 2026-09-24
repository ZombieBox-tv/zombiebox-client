package io.github.diegog0477.zombiebox.client

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import io.github.diegog0477.zombiebox.client.core.data.GatewayEvents
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.presentation.ScreenTasks
import io.github.diegog0477.zombiebox.client.core.ui.RemoteFocus
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.airplay.data.GatewayAirPlayPairingRepository
import io.github.diegog0477.zombiebox.client.features.airplay.presentation.ui.AirPlayPairingDialog
import io.github.diegog0477.zombiebox.client.features.airplay.presentation.viewmodel.AirPlayPairingViewModel
import io.github.diegog0477.zombiebox.client.features.artwork.data.GatewayArtworkRepository
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.ui.ArtworkImageView
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel.ArtworkViewModel
import io.github.diegog0477.zombiebox.client.features.browser.presentation.ui.BrowserActivity
import io.github.diegog0477.zombiebox.client.features.catalog.data.GatewayCatalogRepository
import io.github.diegog0477.zombiebox.client.features.catalog.platform.CatalogSavedState
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui.CatalogDialogs
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.viewmodel.CatalogViewModel
import io.github.diegog0477.zombiebox.client.features.companion.data.GatewayCompanionRepository
import io.github.diegog0477.zombiebox.client.features.companion.presentation.ui.CompanionController
import io.github.diegog0477.zombiebox.client.features.companion.presentation.viewmodel.CompanionViewModel
import io.github.diegog0477.zombiebox.client.features.diagnostics.data.GatewayDiagnosticsRepository
import io.github.diegog0477.zombiebox.client.features.diagnostics.platform.HardwareScanner
import io.github.diegog0477.zombiebox.client.features.diagnostics.presentation.ui.DiagnosticsDialog
import io.github.diegog0477.zombiebox.client.features.diagnostics.presentation.viewmodel.DiagnosticsViewModel
import io.github.diegog0477.zombiebox.client.features.discovery.presentation.viewmodel.DiscoveryViewModel
import io.github.diegog0477.zombiebox.client.features.home.data.GatewayHomeRepository
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeScope
import io.github.diegog0477.zombiebox.client.features.home.presentation.ui.HomeActions
import io.github.diegog0477.zombiebox.client.features.home.presentation.ui.HomePlaybackSession
import io.github.diegog0477.zombiebox.client.features.home.presentation.ui.HomeView
import io.github.diegog0477.zombiebox.client.features.home.presentation.viewmodel.HomeViewModel
import io.github.diegog0477.zombiebox.client.features.mirroring.data.GatewayReceiverRepository
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.PlaybackContext
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.ReceiverChange
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.ReceiverPlan
import io.github.diegog0477.zombiebox.client.features.mirroring.presentation.ui.MediaReceiverDialog
import io.github.diegog0477.zombiebox.client.features.mirroring.presentation.viewmodel.ReceiverViewModel
import io.github.diegog0477.zombiebox.client.features.playback.data.GatewayPlaybackRepository
import io.github.diegog0477.zombiebox.client.features.playback.data.GatewayTracksRepository
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackProgress
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackSession
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.QueueCursor
import io.github.diegog0477.zombiebox.client.features.playback.platform.AudioFocusController
import io.github.diegog0477.zombiebox.client.features.playback.platform.PlaybackConnection
import io.github.diegog0477.zombiebox.client.features.playback.platform.PlaybackNotifications
import io.github.diegog0477.zombiebox.client.features.playback.platform.SurfaceEvidence
import io.github.diegog0477.zombiebox.client.features.playback.presentation.ui.PlaybackFailureDialog
import io.github.diegog0477.zombiebox.client.features.playback.presentation.ui.SurfaceOutputView
import io.github.diegog0477.zombiebox.client.features.playback.presentation.ui.TracksDialog
import io.github.diegog0477.zombiebox.client.features.playback.presentation.ui.TvPlayerActions
import io.github.diegog0477.zombiebox.client.features.playback.presentation.ui.TvPlayerChromeView
import io.github.diegog0477.zombiebox.client.features.playback.presentation.ui.VideoOutputFactory
import io.github.diegog0477.zombiebox.client.features.playback.presentation.ui.VideoOutputView
import io.github.diegog0477.zombiebox.client.features.playback.presentation.viewmodel.PlaybackViewModel
import io.github.diegog0477.zombiebox.client.features.playback.presentation.viewmodel.TracksViewModel
import io.github.diegog0477.zombiebox.client.features.services.presentation.ui.ServicesActivity
import io.github.diegog0477.zombiebox.client.features.settings.data.GatewaySettingsRepository
import io.github.diegog0477.zombiebox.client.features.settings.platform.SettingsSavedState
import io.github.diegog0477.zombiebox.client.features.settings.presentation.ui.SettingsActions
import io.github.diegog0477.zombiebox.client.features.settings.presentation.ui.SettingsDialogs
import io.github.diegog0477.zombiebox.client.features.settings.presentation.ui.TvChoicePanel
import io.github.diegog0477.zombiebox.client.features.settings.presentation.viewmodel.SettingsViewModel
import io.github.diegog0477.zombiebox.client.features.youtubereceiver.presentation.ui.YouTubeReceiverPanel
import io.github.diegog0477.zombiebox.shared.GatewayApi
import io.github.diegog0477.zombiebox.shared.GatewayDiscovery
import io.github.diegog0477.zombiebox.shared.GatewayFailure
import java.util.Locale
import java.util.concurrent.Executors

/** Native semantic Home. The gateway supplies content; all layout stays on-device. */
@Suppress("DEPRECATION")
class MainActivity : Activity() {
    private val ui by lazy { TvWidgets(this) { contextAccent() } }

    private var artworkScope = ""

    private fun currentPlaybackSession(): HomePlaybackSession =
        HomePlaybackSession(
            active = session.isNotEmpty(),
            item = currentItem,
            state = lastState,
            positionMs = lastPosition,
            durationMs = lastDuration,
            canNext = ::nextButton.isInitialized && nextButton.isEnabled,
            canPrevious = currentItem?.provider == "spotify",
        )

    private fun render() {
        val scope = api.base + "\n" + api.token
        artwork.reset(clearCache = artworkScope != scope)
        if (artworkScope != scope) artworkDecoder.clear()
        artworkScope = scope
        imageWorker.queue.clear()
        content.render(
            snapshot,
            homeViewModel.state.scope,
            isTV(),
            full && session.isNotEmpty(),
            currentPlaybackSession(),
        )
        content.status(homeViewModel.state.loading, homeViewModel.state.failure != null)
        if (!full && session.isNotEmpty()) content.post { positionDockedVideo() }
    }

    private val settingsModel by lazy {
        SettingsViewModel(GatewaySettingsRepository(applicationContext, api), screenTasks())
    }
    private val searchModel by lazy {
        io.github.diegog0477.zombiebox.client.features.catalog.presentation.viewmodel
            .SearchViewModel(
                io.github.diegog0477.zombiebox.client.features.catalog.data.GatewaySearchRepository(
                    api
                ),
                { work -> worker.execute { work() } },
                { work -> handler.post { work() } },
                { delay, work ->
                    val task = Runnable { work() }
                    handler.postDelayed(task, delay)
                    val cancel: () -> Unit = { handler.removeCallbacks(task) }
                    cancel
                },
            )
    }
    private val catalogModel by lazy {
        CatalogViewModel(GatewayCatalogRepository(api), screenTasks())
    }
    private val tracksModel by lazy {
        TracksViewModel(
            GatewayTracksRepository(api),
            { work -> worker.execute { work() } },
            { work -> handler.post { work() } },
        )
    }
    private lateinit var subtitleText: TextView
    private var timelineOffset = 0
    private var playbackSeekable = true
    private val seekButtons = ArrayList<View>()
    private lateinit var playerControls: LinearLayout

    private val playbackModel by lazy {
        PlaybackViewModel(
            GatewayPlaybackRepository(api) {
                getSharedPreferences("zombie", MODE_PRIVATE).getBoolean("networkAdaptation", true)
            },
            { work -> worker.execute { work() } },
            { work -> handler.post { work() } },
        )
    }
    private val events by lazy { GatewayEvents(api, poller) { work -> handler.post { work() } } }

    private fun screenTasks() =
        ScreenTasks({ work -> worker.execute { work() } }, { work -> handler.post { work() } })

    private val companionModel by lazy {
        CompanionViewModel(
            GatewayCompanionRepository(api),
            screenTasks(),
            { android.os.SystemClock.elapsedRealtime() },
            { api.base + "\n" + api.device },
        )
    }
    private val remoteText by lazy {
        io.github.diegog0477.zombiebox.client.features.companion.presentation.ui.RemoteTextEntry {
            if (!foreground) null
            else if (hasWindowFocus()) currentFocus as? EditText
            else catalogDialogs.remoteTextField()
        }
    }
    private val companionController: CompanionController by lazy {
        CompanionController(
            this,
            companionModel,
            { api.token.isNotEmpty() },
            { foreground && (hasWindowFocus() || catalogDialogs.remoteReady) },
            ::remoteCommand,
            ::error,
            { remoteText.inputId() },
        )
    }
    private val settingsDialogs: SettingsDialogs by lazy {
        SettingsDialogs(
            this,
            settingsModel,
            { api.token.isNotEmpty() },
            { api.base },
            ::error,
            {
                DiscoveryViewModel(
                    GatewayDiscovery()::scan,
                    { work -> worker.execute { work() } },
                    { work -> handler.post { work() } },
                )
            },
            SettingsActions(
                { profile ->
                    stopPlayback()
                    catalogDialogs.reset()
                    settingsModel.activate(profile)
                    player.configure(api.base, api.device, api.token)
                    receiverViewModel.reset()
                    refreshReceiverListening()
                    homeViewModel.reset()
                    refresh()
                    receiverViewModel.refresh()
                },
                {
                    catalogDialogs.reset()
                    refresh()
                },
                ::render,
                ::diagnostics,
                ::audioSettings,
                ::receiverSettings,
                ::youtubeReceiverSettings,
                ::mediaReceiverSettings,
                {
                    player.configure(api.base, api.device, api.token)
                    player.resumeSaved {
                        Toast.makeText(this, R.string.no_saved_playback, Toast.LENGTH_LONG).show()
                    }
                },
                { if (api.token.isNotEmpty()) companionController.show() else pairing() },
            ),
        )
    }

    private fun settings() = settingsDialogs.show()

    private fun pairing() = settingsDialogs.pairing()

    private fun providerList() = settingsDialogs.providers()

    private val catalogDialogs by lazy {
        CatalogDialogs(
            this,
            catalogModel,
            { homeViewModel.state.scope.query },
            searchModel,
            { startPlayback(it, catalogOrigin = true) },
            ::error,
            { item ->
                if (item.imageUrl.isEmpty()) null
                else ArtworkImageView(this, artworkDecoder).apply { bind(artwork, item.imageUrl) }
            },
            { item -> startPlayback(item, positionMs = 0, catalogOrigin = true) },
        )
    }

    private fun search() = catalogDialogs.search()

    private fun searchProvider(provider: String) = catalogDialogs.promptProviderSearch(provider)

    private fun catalogPage(provider: String) = catalogDialogs.page(provider)

    private fun details(item: MediaItem) = catalogDialogs.details(item)

    private val worker = Executors.newSingleThreadExecutor()
    private var youtubeIncoming = false
    private var diagnosticsModel: DiagnosticsViewModel? = null
    private var youtubeDialog: YouTubeReceiverPanel? = null
    private var shownYouTubePlaybackIssueRevision = 0L
    private var receiverOptionsPanel: TvChoicePanel? = null
    private var receiverOptionsLoading = false
    private var audioOptionsPanel: TvChoicePanel? = null
    private val receiverTick =
        object : Runnable {
            override fun run() {
                if (!closed) {
                    if (foreground && api.token.isNotEmpty() && ::receiverViewModel.isInitialized)
                        receiverViewModel.refresh()
                    handler.postDelayed(this, 3000)
                }
            }
        }
    private val poller = Executors.newSingleThreadExecutor()
    private val api = GatewayApi()
    private val handler = Handler()
    private val imageWorker =
        java.util.concurrent.ThreadPoolExecutor(
            2,
            2,
            0L,
            java.util.concurrent.TimeUnit.MILLISECONDS,
            java.util.concurrent.ArrayBlockingQueue<Runnable>(24),
        )
    private val artworkDecoder by lazy {
        io.github.diegog0477.zombiebox.client.features.artwork.platform.ArtworkDecoder(
            io.github.diegog0477.zombiebox.client.features.artwork.platform.ImageBudget.discover(
                this
            ),
            { work -> imageWorker.execute { work() } },
            { work -> handler.post { work() } },
        )
    }
    private val artwork by lazy {
        ArtworkViewModel(
            GatewayArtworkRepository(api),
            { work -> imageWorker.execute { work() } },
            { work -> handler.post { work() } },
            io.github.diegog0477.zombiebox.client.features.artwork.platform.ImageBudget.discover(
                    this
                )
                .encodedBytes,
        )
    }
    private val prefs by lazy { getSharedPreferences("zombie", MODE_PRIVATE) }
    private lateinit var root: FrameLayout
    private lateinit var content: HomeView
    private lateinit var now: TextView
    private lateinit var playerLayer: FrameLayout
    private lateinit var playerChrome: TvPlayerChromeView
    private lateinit var playerStatus: TextView
    private lateinit var videoSurface: VideoOutputView
    private lateinit var videoViewport: FrameLayout
    private lateinit var player: PlaybackConnection
    private lateinit var nextButton: View
    private var restoreFullscreen = true
    private var queueFailed = false
    private lateinit var homeViewModel: HomeViewModel
    private val snapshot
        get() = homeViewModel.state.snapshot

    private lateinit var receiverViewModel: ReceiverViewModel
    private var universalReception = false
    private var currentItem: MediaItem? = null
    private var session = ""
    private var stream = ""
    private var mime = "video/mp4"
    private var itemTitle = ""
    private val playbackFailure by lazy { PlaybackFailureDialog(this) }
    private var playbackPending = false
    private var playbackFeedbackGeneration = 0
    private var playbackLive = false
    private var receiverState = ""
    private lateinit var receiverInfo: TextView
    private lateinit var receiverArtwork: ArtworkImageView
    private var full = false
    private val homeFocus
        get() = content.focus

    private val playerFocus = RemoteFocus()
    private lateinit var bottom: LinearLayout
    private lateinit var audioController: AudioFocusController
    private var gamepadClick: View? = null
    private var lastReport = 0L
    private var lastState = ""
    private var lastPosition = 0
    private var lastDuration = 0
    @Volatile private var closed = false
    @Volatile private var foreground = false
    private val green
        get() = resources.getColor(R.color.accent_zombie)

    private val background = Color.rgb(10, 15, 16)
    private val panel = Color.rgb(24, 31, 33)
    private val muted = Color.rgb(167, 180, 186)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        restoreFullscreen =
            if (state?.containsKey("playbackFullscreen") == true)
                state.getBoolean("playbackFullscreen")
            else false
        val language = prefs.getString("language", "en") ?: "en"
        val config = Configuration(resources.configuration)
        config.locale = Locale(language)
        resources.updateConfiguration(config, resources.displayMetrics)
        api.base = prefs.getString("gateway", "") ?: ""
        api.device = prefs.getString("device", "") ?: ""
        api.token = prefs.getString("token", "") ?: ""
        root = FrameLayout(this)
        root.setBackgroundColor(background)
        val shell = ui.column()
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        content =
            HomeView(
                this,
                artwork,
                artworkDecoder,
                HomeActions(
                    navigate = { provider, query ->
                        catalogModel.rememberPlaybackReturn(null)
                        refresh(provider, query)
                    },
                    play = { item -> startPlayback(item) },
                    details = { item -> details(item) },
                    settings = { settings() },
                    search = { search() },
                    searchProvider = { provider -> searchProvider(provider) },
                    devices = { settingsDialogs.devices() },
                    youtubeReceiver = { youtubeReceiverSettings() },
                    airplayPairing = { airplayPairing() },
                    airplayReceive = { armMediaReceiver("airplay") },
                    airplayAudioOptions = { mediaReceiverSettings() },
                    airplayConfigure = { settingsDialogs.providers(selectedKey = "airplay") },
                    catalog = { provider -> catalogPage(provider) },
                    mirrorReceiver = { receiverSettings() },
                    providers = { providerList() },
                    pair = { pairing() },
                    playPause = { togglePlayback() },
                    next = { if (session.isNotEmpty()) player.next() },
                    previous = {
                        if (
                            currentItem?.provider == "spotify" &&
                                receiverViewModel.activeSession == session
                        ) {
                            receiverViewModel.command("previous", ::error)
                        }
                    },
                    expand = { if (session.isNotEmpty()) togglePlayerSize() },
                    spotifyAuthorize = { spotifyAuthorize() },
                    spotifyReceive = { armMediaReceiver("spotify") },
                ),
            )
        content.setPadding(ui.dp(22), ui.dp(16), ui.dp(22), ui.dp(18))
        shell.addView(content, LinearLayout.LayoutParams(-1, -1))
        bottom = ui.row()
        now = ui.text("", 16f)
        createPlayer()
        player =
            PlaybackConnection(
                this,
                ::restorePlayback,
                { width, height -> videoSurface.setVideoSize(width, height) },
            ) { status, position, duration ->
                if (playbackPending) return@PlaybackConnection
                if (::receiverViewModel.isInitialized && receiverViewModel.activeSession == session)
                    receiverViewModel.playbackState(status)
                lastPosition = position + timelineOffset
                lastDuration = if (duration > 0) duration + timelineOffset else 0
                subtitleText.text =
                    if (status == "PLAYING" || status == "PAUSED") tracksModel.textAt(lastPosition)
                    else ""
                subtitleText.visibility =
                    if (subtitleText.text.isEmpty()) View.GONE else View.VISIBLE
                playerStatus.text =
                    getString(
                        R.string.player_status,
                        ui.localizedState(status),
                        ui.formatTime(lastPosition),
                        ui.formatTime(lastDuration),
                    )
                if (::playerChrome.isInitialized) {
                    playerChrome.updateProgress(status, lastPosition, lastDuration)
                }
                content.updatePlaybackProgress(status, lastPosition, lastDuration)
                if (
                    session.isNotEmpty() &&
                        (!::receiverViewModel.isInitialized ||
                            receiverViewModel.activeSession.isEmpty())
                )
                    now.text = itemTitle
                if (status == "PLAYING")
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (
                    status == "FAILED" &&
                        lastState != "FAILED" &&
                        foreground &&
                        receiverViewModel.activeSession.isEmpty() &&
                        session.isNotEmpty() &&
                        !youtubeIncoming
                ) {
                    showPlaybackFailure()
                }
                lastState = status
            }
        audioController = player
        player.configure(api.base, api.device, api.token)
        player.youtubeChanged = {
            youtubeDialog?.update(player.youtubeState)
            showYouTubePlaybackIssue()
        }
        val backgroundExecutor = worker
        val uiHandler = handler
        homeViewModel =
            HomeViewModel(
                GatewayHomeRepository(api),
                { work -> backgroundExecutor.execute { work() } },
                { done -> uiHandler.post { done() } },
            )
        receiverViewModel =
            ReceiverViewModel(
                GatewayReceiverRepository(api),
                { work -> backgroundExecutor.execute { work() } },
                { done -> uiHandler.post { done() } },
            )
        receiverViewModel.observer = { plan -> receiveCast(plan) }
        handler.post(receiverTick)
        homeViewModel.observer = { state ->
            if (!closed) {
                content.selectScope(state.scope)
                content.status(state.loading, state.failure != null)
            }
            if (!closed && (!state.loading || state.snapshot.hero == null)) {
                render()
                state.failure?.let { error(it) }
            }
        }
        io.github.diegog0477.zombiebox.client.core.platform.WindowInsetsPolicy.apply(root)
        setContentView(root)
        render()
        content.focus.remember(state?.getString("homeFocus"))
        if (api.token.isNotEmpty()) {
            refresh(state?.getString("homeProvider") ?: "", state?.getString("homeQuery") ?: "")
            handler.post {
                catalogDialogs.restore(
                    CatalogSavedState.read(state),
                    state?.getString("catalogDetail") ?: "",
                    state?.getBoolean("catalogVisible") ?: true,
                    CatalogSavedState.readOverlay(state),
                    CatalogSavedState.readSearch(state),
                )
                catalogModel.rememberPlaybackReturn(CatalogSavedState.readPlaybackReturn(state))
                settingsDialogs.restore(SettingsSavedState.read(state))
            }
        } else handler.post { pairing() }
        events.start({ companionController.wake() }) { changed ->
            if (!closed && foreground) {
                receiverViewModel.refresh()
                if (changed) refresh()
            }
        }
    }

    private fun refresh(
        provider: String = homeViewModel.state.scope.provider,
        query: String = homeViewModel.state.scope.query,
    ) {
        if (provider == "rebrowser") {
            startActivity(Intent(this, BrowserActivity::class.java))
            return
        }
        if (api.token.isNotEmpty()) homeViewModel.refresh(HomeScope(provider, query))
    }

    private fun error(e: Exception) {
        val message =
            if (e is GatewayFailure)
                getString(
                    when (e.status) {
                        401 -> R.string.error_pairing
                        403 -> R.string.error_admin
                        409 -> R.string.error_conflict
                        429 -> R.string.error_busy
                        else -> R.string.error_request
                    }
                )
            else getString(R.string.error_network)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun audioSettings() {
        if (audioOptionsPanel?.visible == true) return
        val selected = prefs.getBoolean("audioFocusCompatibility", false)
        val panel = TvChoicePanel(this) { audioOptionsPanel = null }
        audioOptionsPanel = panel
        panel.show(
            section = R.string.advanced,
            title = R.string.audio_focus_backend,
            description = null,
            options =
                listOf(R.string.backend_auto, R.string.backend_compatibility).mapIndexed {
                    index,
                    label ->
                    TvChoicePanel.Option(label = label, selected = (index == 1) == selected) {
                        if ((index == 1) == selected) {
                            panel.dismiss()
                            return@Option
                        }
                        player.pause()
                        audioController.release()
                        prefs.edit().putBoolean("audioFocusCompatibility", index == 1).commit()
                        player.refreshFocus()
                        panel.dismiss()
                    }
                },
        )
    }

    private fun diagnostics() {
        val report =
            getString(
                R.string.diagnostics_report,
                Build.VERSION.SDK_INT,
                Build.MANUFACTURER,
                Build.MODEL,
                Build.CPU_ABI,
            )
        val model =
            io.github.diegog0477.zombiebox.client.features.diagnostics.presentation.viewmodel
                .DiagnosticsViewModel(
                    GatewayDiagnosticsRepository(api, HardwareScanner(applicationContext)),
                    { work -> worker.execute { work() } },
                    { work -> handler.post { work() } },
                )
        diagnosticsModel?.close()
        diagnosticsModel = model
        DiagnosticsDialog(this, model).show()
    }

    private fun youtubeReceiverSettings() {
        if (api.token.isEmpty()) {
            pairing()
            return
        }
        if (youtubeDialog?.visible == true) return
        youtubeDialog =
            YouTubeReceiverPanel(
                    this,
                    onEnable = { player.enableYouTube() },
                    onDisable = { player.disableYouTube() },
                    onClosed = { panel -> if (youtubeDialog === panel) youtubeDialog = null },
                )
                .also { it.show(player.youtubeState) }
    }

    private fun showYouTubePlaybackIssue() {
        if (!foreground || closed || isFinishing) return
        val state = player.youtubeState
        if (
            state.playbackIssue == null ||
                state.playbackIssueRevision <= shownYouTubePlaybackIssueRevision
        )
            return
        shownYouTubePlaybackIssueRevision = state.playbackIssueRevision
        if (youtubeDialog?.visible != true) youtubeReceiverSettings()
    }

    private fun receiverSettings() {
        if (api.token.isEmpty()) {
            pairing()
            return
        }
        if (receiverOptionsLoading || receiverOptionsPanel?.visible == true) return
        receiverOptionsLoading = true
        receiverViewModel.readEnabled(
            { enabled ->
                receiverOptionsLoading = false
                if (closed || isFinishing) return@readEnabled
                val panel = TvChoicePanel(this) { receiverOptionsPanel = null }
                receiverOptionsPanel = panel
                showReceiverOptions(panel, enabled)
            },
            { failure ->
                receiverOptionsLoading = false
                error(failure)
            },
        )
    }

    private fun showReceiverOptions(panel: TvChoicePanel, enabled: Boolean) {
        val choices =
            listOf(true, false).map { target ->
                TvChoicePanel.Option(
                    label = if (target) R.string.enabled else R.string.disabled,
                    selected = target == enabled,
                ) {
                    if (target == enabled) return@Option
                    panel.setBusy(true)
                    receiverViewModel.setEnabled(
                        target,
                        { failure ->
                            panel.setBusy(false)
                            error(failure)
                        },
                    ) {
                        player.configureReceivers(castEnabled = target)
                        if (panel.visible) showReceiverOptions(panel, target)
                    }
                }
            } +
                TvChoicePanel.Option(
                    label = R.string.receiver_handoff,
                    detail = R.string.receiver_handoff_detail,
                ) {
                    receiverHandoffSettings(panel)
                }
        panel.show(
            section = R.string.devices,
            title = R.string.receive_cast,
            description = R.string.receive_cast_detail,
            options = choices,
        )
    }

    private fun receiverHandoffSettings(panel: TvChoicePanel) {
        panel.setBusy(true)
        receiverViewModel.readHandoff(
            { enabled -> if (panel.visible) showReceiverHandoffOptions(panel, enabled) },
            { failure ->
                panel.setBusy(false)
                error(failure)
            },
        )
    }

    private fun showReceiverHandoffOptions(panel: TvChoicePanel, enabled: Boolean) {
        val choices =
            listOf(true, false).map { target ->
                TvChoicePanel.Option(
                    label = if (target) R.string.enabled else R.string.disabled,
                    selected = target == enabled,
                ) {
                    if (target == enabled) return@Option
                    panel.setBusy(true)
                    receiverViewModel.setHandoff(target) { failure ->
                        panel.setBusy(false)
                        error(failure)
                    }
                    receiverViewModel.readHandoff(
                        { confirmed ->
                            if (panel.visible) showReceiverHandoffOptions(panel, confirmed)
                        },
                        { failure ->
                            panel.setBusy(false)
                            error(failure)
                        },
                    )
                }
            }
        panel.show(
            section = R.string.devices,
            title = R.string.receiver_handoff,
            description = R.string.receiver_handoff_detail,
            options = choices,
            onBack = {
                panel.setBusy(true)
                receiverViewModel.readEnabled(
                    { current -> if (panel.visible) showReceiverOptions(panel, current) },
                    { failure ->
                        panel.setBusy(false)
                        error(failure)
                    },
                )
            },
        )
    }

    private fun mediaReceiverSettings() {
        if (api.token.isEmpty()) {
            pairing()
            return
        }
        MediaReceiverDialog(this, receiverViewModel, ::error, ::applyMediaReceiverSelection).show()
    }

    private fun applyMediaReceiverSelection(provider: String) {
        player.configureReceivers(mediaProvider = provider)
        universalReception = provider == "universal"
        if (universalReception) player.enableYouTube() else player.disableYouTube()
    }

    private fun armMediaReceiver(provider: String) {
        if (api.token.isEmpty()) {
            pairing()
            return
        }
        receiverViewModel.selectMediaProvider(provider, ::error) {
            applyMediaReceiverSelection(provider)
            Toast.makeText(
                    this,
                    getString(R.string.receiver_selected, ui.serviceTitle(provider)),
                    Toast.LENGTH_SHORT,
                )
                .show()
        }
    }

    private fun spotifyAuthorize() {
        if (api.token.isEmpty()) {
            pairing()
            return
        }
        startActivity(
            Intent(this, ServicesActivity::class.java).apply {
                putExtra(ServicesActivity.EXTRA_SPOTIFY_FOCUSED, true)
            }
        )
    }

    private fun airplayPairing() {
        if (api.token.isEmpty()) {
            pairing()
            return
        }
        val model =
            AirPlayPairingViewModel(
                GatewayAirPlayPairingRepository(api),
                { work -> worker.execute { work() } },
                { work -> handler.post { work() } },
            )
        AirPlayPairingDialog(this, model).show()
    }

    private fun updateReceiver(plan: ReceiverPlan) {
        currentItem = plan.item
        receiverState = plan.state
        player.receiverStatus(plan.item, plan.state)
        itemTitle = plan.item?.title ?: getString(R.string.screen_mirroring)
        now.text =
            listOf(itemTitle, plan.item?.subtitle ?: "")
                .filter { it.isNotEmpty() }
                .joinToString(" — ")
        content.setPlaybackSession(currentPlaybackSession())
        receiverInfo.text =
            listOf(itemTitle, plan.item?.subtitle ?: "", ui.localizedState(plan.state))
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        receiverInfo.visibility = if (plan.fullscreen) View.GONE else View.VISIBLE
        receiverArtwork.visibility =
            if (!plan.fullscreen && !plan.item?.imageUrl.isNullOrEmpty()) View.VISIBLE
            else View.GONE
        receiverArtwork.bind(artwork, if (plan.fullscreen) "" else plan.item?.imageUrl ?: "")
        if (::playerChrome.isInitialized && full) {
            updatePlayerChrome()
        }
    }

    private fun receiveCast(plan: ReceiverPlan?) {
        if (!foreground || !player.ready) return
        when (
            val change =
                receiverViewModel.transition(
                    plan,
                    PlaybackContext(
                        if (youtubeIncoming) null else currentItem,
                        full,
                        lastState == "PLAYING",
                    ),
                )
        ) {
            is ReceiverChange.Restore -> {
                stopPlayback(keepReceiver = true)
                restoreFullscreen = change.previous?.fullscreen ?: false
                if (!player.restoreInterrupted())
                    change.previous?.let { previous ->
                        previous.item?.let {
                            startPlayback(
                                it,
                                previous.fullscreen,
                                previous.playing,
                                catalogOrigin = true,
                            )
                        }
                    }
            }
            is ReceiverChange.Update -> {
                updateReceiver(change.plan)
            }
            is ReceiverChange.Reconnect -> {
                updateReceiver(change.plan)
                if (audioController.acquire())
                    player.play(
                        stream,
                        0,
                        video = change.plan.fullscreen,
                        seekable = change.plan.seekable,
                    )
            }
            is ReceiverChange.Begin -> {
                player.rememberInterruption()
                if (universalReception) player.standbyYouTube() else player.disableYouTube()
                stopPlayback(keepReceiver = true)
                session = change.plan.sessionId
                stream = api.base + change.plan.path
                mime = change.plan.mime
                updateReceiver(change.plan)
                player.configure(api.base, api.device, api.token)
                player.adopt(
                    PlaybackPlan(
                        session,
                        stream,
                        mime,
                        change.plan.mode,
                        0,
                        live = change.plan.live,
                        seekable = change.plan.seekable,
                    ),
                    currentItem ?: MediaItem(session, "cast", getString(R.string.screen_mirroring)),
                    emptyList(),
                    incoming = true,
                )
                lastReport = 0
                lastState = ""
                setSeekable(change.plan.seekable && !change.plan.live)
                setFullscreen(change.plan.fullscreen)
                if (audioController.acquire())
                    player.play(
                        stream,
                        0,
                        video = change.plan.fullscreen,
                        seekable = change.plan.seekable,
                    )
            }
            null -> Unit
        }
    }

    private fun isTV(): Boolean =
        when (prefs.getString("mode", "AUTO")) {
            "TV",
            "DOCKED" -> true
            "HANDHELD" -> false
            else -> !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        }

    private fun installVideoOutput(output: VideoOutputView) {
        if (::videoSurface.isInitialized) {
            videoSurface.close()
            videoViewport.removeView(videoSurface.view)
        }
        videoSurface = output
        val view =
            output.create(
                this,
                { player.surface(it) },
                {
                    handler.post {
                        if (!closed && !isFinishing && videoSurface === output) {
                            SurfaceEvidence(this).failed()
                            installVideoOutput(SurfaceOutputView())
                        }
                    }
                },
            )
        videoViewport.addView(view, 0, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
    }

    private fun createPlayer() {
        playerLayer = FrameLayout(this)
        playerLayer.setBackgroundColor(Color.BLACK)
        playerLayer.visibility = View.GONE
        val viewport = FrameLayout(this)
        videoViewport = viewport
        installVideoOutput(VideoOutputFactory.select(this, !isTV()))
        receiverInfo =
            ui.text("", 22f).apply {
                gravity = Gravity.CENTER
                visibility = View.GONE
                setBackgroundColor(panel)
                setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
            }
        viewport.addView(receiverInfo, FrameLayout.LayoutParams(-1, -1))
        receiverArtwork = ArtworkImageView(this, artworkDecoder).apply { visibility = View.GONE }
        viewport.addView(
            receiverArtwork,
            FrameLayout.LayoutParams(ui.dp(80), ui.dp(80), Gravity.LEFT or Gravity.TOP),
        )
        subtitleText =
            ui.text("", 22f).apply {
                gravity = Gravity.CENTER
                setShadowLayer(3f, 1f, 1f, Color.BLACK)
                setBackgroundColor(Color.argb(170, 0, 0, 0))
                visibility = View.GONE
            }
        viewport.addView(
            subtitleText,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                setMargins(ui.dp(24), 0, ui.dp(24), ui.dp(140))
            },
        )
        playerLayer.addView(viewport, FrameLayout.LayoutParams(-1, -1))
        playerStatus = ui.text("", 12f, muted).apply { visibility = View.GONE }

        playerChrome =
            TvPlayerChromeView(
                this,
                ui,
                artwork,
                artworkDecoder,
                TvPlayerActions(
                    seekBack = { seek(-10000) },
                    previous = {
                        if (
                            currentItem?.provider == "spotify" &&
                                receiverViewModel.activeSession == session
                        ) {
                            receiverViewModel.command("previous", ::error)
                        }
                    },
                    playPause = { togglePlayback() },
                    next = { player.next() },
                    seekForward = { seek(10000) },
                    audioTracks = { showTracks("audio") },
                    subtitles = { showTracks("subtitle") },
                    minimize = { togglePlayerSize() },
                    external = { external() },
                    stop = { stopPlayback() },
                    playItem = { item ->
                        startPlayback(item, fullscreen = true, catalogOrigin = true)
                    },
                ),
            )
        playerControls = playerChrome.videoControlsRow
        nextButton = playerChrome.videoNextButton
        nextButton.isEnabled = false
        seekButtons.clear()
        seekButtons.add(playerChrome.videoSeekBackButton)
        seekButtons.add(playerChrome.videoSeekForwardButton)
        seekButtons.add(playerChrome.audioSeekBackButton)
        seekButtons.add(playerChrome.audioSeekForwardButton)

        playerLayer.addView(playerChrome, FrameLayout.LayoutParams(-1, -1))
        playerFocus.rebuild(playerChrome.focusRows(), false)
        root.addView(playerLayer, FrameLayout.LayoutParams(-1, -1))
    }

    private fun setSeekable(value: Boolean) {
        playbackSeekable = value
        seekButtons.forEach { it.isEnabled = value }
        if (::playerChrome.isInitialized) {
            playerChrome.setSeekable(value)
            playerFocus.rebuild(playerChrome.focusRows(), false)
        }
    }

    private fun seek(delta: Int) {
        if (playbackSeekable) player.seek(delta)
    }

    private fun adoptPlan(plan: PlaybackPlan) {
        playbackModel.adopt(plan)
        playbackLive = plan.live
        session = plan.sessionId
        stream = plan.url
        mime = plan.mime
        timelineOffset = plan.timelineOffsetMs
        setSeekable(plan.seekable && !plan.live)
    }

    private fun retainPlan(plan: PlaybackPlan) {
        currentItem?.let { player.adopt(plan, it) }
    }

    private fun restorePlayback(state: PlaybackSession) {
        if (playbackPending) return
        youtubeIncoming = state.incoming && state.item?.provider == "youtube"
        nextButton.isEnabled = state.canNext
        if (::playerChrome.isInitialized) {
            playerChrome.setCanNext(state.canNext)
        }
        if (state.error && !queueFailed)
            Toast.makeText(this, R.string.next_failed, Toast.LENGTH_LONG).show()
        queueFailed = state.error
        val plan = state.plan
        if (plan == null) {
            if (session.isNotEmpty()) {
                session = ""
                stopPlayback(endSession = false)
            }
            return
        }
        if (session != plan.sessionId) {
            val wasPlaying = session.isNotEmpty()
            adoptPlan(plan)
            currentItem = state.item
            itemTitle = state.item?.title ?: getString(R.string.screen_mirroring)
            now.text = itemTitle
            content.setPlaybackSession(currentPlaybackSession())
            attachTracks(plan, state.subtitleId)
            if (youtubeIncoming) receiverViewModel.reset()
            if (state.incoming && !youtubeIncoming) {
                val receiver =
                    ReceiverPlan(
                        session,
                        stream.removePrefix(api.base),
                        mime,
                        state.item,
                        state.item?.kind != "audio",
                        live = plan.live,
                        seekable = plan.seekable,
                        mode = plan.mode,
                    )
                receiverViewModel.restore(receiver)
                updateReceiver(receiver)
            }
            lastState = ""
            lastPosition = state.progress.positionMs
            lastDuration = state.progress.durationMs
            setFullscreen(if (wasPlaying) full else restoreFullscreen)
        }
    }

    private fun attachTracks(plan: PlaybackPlan, subtitleId: Int? = plan.subtitleId) {
        tracksModel.attach(plan.sessionId)
        if (subtitleId != null) {
            tracksModel.subtitles(subtitleId, { player.subtitle(tracksModel.subtitleId) }, {})
        }
    }

    private fun showTracks(kind: String) {
        val requestedSession = session
        tracksModel.inventory(
            { inventory ->
                TracksDialog(this).show(inventory, kind, tracksModel.subtitleId) { id ->
                    if (requestedSession == session) {
                        if (kind == "subtitle")
                            tracksModel.subtitles(
                                id,
                                {
                                    player.subtitle(tracksModel.subtitleId)
                                    subtitleText.text = tracksModel.textAt(lastPosition)
                                    subtitleText.visibility =
                                        if (subtitleText.text.isEmpty()) View.GONE else View.VISIBLE
                                },
                                ::error,
                            )
                        else if (id != null) {
                            val paused = lastState != "PLAYING"
                            tracksModel.audio(
                                id,
                                lastPosition,
                                { plan ->
                                    adoptPlan(plan)
                                    retainPlan(plan)
                                    lastReport = 0
                                    player.play(
                                        stream,
                                        plan.resumePositionMs,
                                        !paused,
                                        currentItem?.kind != "audio",
                                        playbackSeekable,
                                    )
                                },
                                ::error,
                            )
                        }
                    }
                }
            },
            ::error,
        )
    }

    private fun setPlaying(playing: Boolean) {
        if (currentItem?.provider == "spotify" && receiverViewModel.activeSession == session) {
            receiverViewModel.command(if (playing) "resume" else "pause", ::error)
        } else if (playing) {
            if (audioController.acquire()) player.resume()
        } else player.pause()
    }

    private fun togglePlayback() {
        if (currentItem?.provider == "spotify" && receiverViewModel.activeSession == session) {
            receiverViewModel.command(if (receiverState == "PAUSED") "resume" else "pause", ::error)
            return
        }
        if (session.isNotEmpty() && foreground && audioController.acquire()) player.toggle()
    }

    private fun startPlayback(
        item: MediaItem,
        fullscreen: Boolean = true,
        autoplay: Boolean = true,
        positionMs: Int? = null,
        catalogOrigin: Boolean = false,
    ) {
        if (!catalogOrigin) catalogModel.rememberPlaybackReturn(null)
        if (universalReception) player.standbyYouTube() else player.disableYouTube()
        val catalog =
            catalogModel.screen?.takeIf { page -> page.page.items.any { it.id == item.id } }
        val queue =
            catalog?.page?.items
                ?: snapshot.sections
                    .firstOrNull { section -> section.items.any { it.id == item.id } }
                    ?.items
                ?: listOf(item)
        val cursor =
            catalog
                ?.takeIf { it.page.nextOffset > it.location.offset }
                ?.let {
                    QueueCursor(
                        it.location.provider,
                        it.location.parent,
                        it.location.query,
                        it.page.nextOffset,
                    )
                }
        stopPlayback()
        playbackPending = true
        showPlaybackFeedback(R.string.playback_starting)
        val requestFeedback = playbackFeedbackGeneration
        handler.postDelayed(
            {
                if (!closed && playbackPending && requestFeedback == playbackFeedbackGeneration) {
                    showPlaybackFeedback(R.string.playback_taking_longer, 10000)
                }
            },
            40000,
        )
        playbackModel.start(
            item.id,
            prefs.getString("playbackMode", "AUTO") ?: "AUTO",
            { plan ->
                playbackPending = false
                if (!foreground || !player.ready) {
                    playbackModel.stop(plan.sessionId, null)
                    showPlaybackFeedback(R.string.playback_start_failed, 10000)
                    return@start
                }
                showPlaybackFeedback(null)
                adoptPlan(plan)
                attachTracks(plan)
                currentItem = item
                player.configure(api.base, api.device, api.token)
                player.adopt(plan, item, queue, cursor)
                itemTitle = item.title
                now.text = itemTitle
                content.setPlaybackSession(currentPlaybackSession())
                lastState = ""
                lastReport = 0
                lastPosition = plan.resumePositionMs + plan.timelineOffsetMs
                lastDuration = 0
                setFullscreen(fullscreen)
                if (plan.mode == "EXTERNAL_PLAYER") {
                    external()
                    return@start
                }
                if (audioController.acquire()) {
                    player.play(
                        stream,
                        plan.resumePositionMs,
                        autoplay,
                        item.kind != "audio",
                        playbackSeekable,
                    )
                }
            },
            failed = { failure ->
                playbackPending = false
                showPlaybackFeedback(R.string.playback_start_failed, 10000)
                error(failure)
            },
            positionMs = positionMs,
        )
    }

    private fun showPlaybackFeedback(message: Int?, timeoutMs: Long = 0) {
        val generation = ++playbackFeedbackGeneration
        if (::content.isInitialized) content.playbackFeedback(message)
        if (message != null && timeoutMs > 0) {
            handler.postDelayed(
                {
                    if (!closed && generation == playbackFeedbackGeneration)
                        showPlaybackFeedback(null)
                },
                timeoutMs,
            )
        }
    }

    private fun showPlaybackFailure() {
        val expected = session
        playbackFailure.show(
            playbackModel.canRetry(if (playbackLive) 0 else lastPosition),
            { if (session == expected) retryPlayback() },
            { if (session == expected) replaceWithExternal() },
        )
    }

    private fun retryPlayback() {
        val item = currentItem ?: return
        val progress =
            PlaybackProgress("FAILED", if (playbackLive) 0 else lastPosition, lastDuration)
        playbackPending = true
        player.stop()
        playbackModel.retry(
            item.id,
            session,
            progress,
            { plan ->
                playbackPending = false
                adoptPlan(plan)
                retainPlan(plan)
                attachTracks(plan)
                lastPosition = plan.resumePositionMs + plan.timelineOffsetMs
                lastReport = 0
                lastState = ""
                if (audioController.acquire())
                    player.play(
                        stream,
                        plan.resumePositionMs,
                        video = item.kind != "audio",
                        seekable = playbackSeekable,
                    )
            },
            { failure ->
                playbackPending = false
                player.failed()
                error(failure)
                if (foreground) showPlaybackFailure()
            },
        )
    }

    private fun replaceWithExternal() {
        val item = currentItem ?: return
        val position = lastPosition
        playbackPending = true
        player.stop()
        playbackModel.stop(session, PlaybackProgress("FAILED", position, lastDuration))
        playbackModel.start(
            item.id,
            "EXTERNAL_PLAYER",
            { plan ->
                playbackPending = false
                adoptPlan(plan)
                retainPlan(plan)
                attachTracks(plan)
                if (foreground) external()
            },
            { failure ->
                playbackPending = false
                player.failed()
                error(failure)
            },
        )
    }

    private fun togglePlayerSize() {
        val returning = full
        setFullscreen(!full)
        if (returning) catalogDialogs.resumePlayback()
    }

    private fun getYouTubeRelated(): List<MediaItem> {
        if (currentItem?.provider != "youtube") return emptyList()
        val currentId = currentItem?.id ?: ""
        val catalogItems =
            catalogModel.screen?.page?.items?.filter {
                it.id != currentId && (it.provider.isEmpty() || it.provider == "youtube")
            }
        if (!catalogItems.isNullOrEmpty()) return catalogItems
        val snapshotItems =
            snapshot.sections
                .filter { section ->
                    section.id.contains("youtube", ignoreCase = true) ||
                        section.items.any { it.provider == "youtube" }
                }
                .flatMap { it.items }
                .filter { it.id != currentId }
                .distinctBy { it.id }
        if (snapshotItems.isNotEmpty()) return snapshotItems
        return snapshot.sections
            .flatMap { it.items }
            .filter { it.id != currentId && it.provider == "youtube" }
            .distinctBy { it.id }
    }

    private fun updatePlayerChrome() {
        if (!::playerChrome.isInitialized) return
        val isAudio = currentItem?.kind == "audio" || (currentItem?.provider == "spotify")
        val accent = contextAccent()
        val canPrev = currentItem?.provider == "spotify"
        val canNext = ::nextButton.isInitialized && nextButton.isEnabled
        val related = if (currentItem?.provider == "youtube") getYouTubeRelated() else emptyList()
        playerChrome.bindSession(
            item = currentItem,
            isAudio = isAudio,
            accent = accent,
            canPrevious = canPrev,
            canNext = canNext,
            seekable = playbackSeekable,
            relatedItems = related,
        )
        playerChrome.updateProgress(lastState, lastPosition, lastDuration)
        playerFocus.rebuild(playerChrome.focusRows(), false)
    }

    private fun setFullscreen(value: Boolean) {
        full = value
        if (full) {
            val isAudio = currentItem?.kind == "audio" || (currentItem?.provider == "spotify")
            playerLayer.visibility = View.VISIBLE
            playerLayer.layoutParams = FrameLayout.LayoutParams(-1, -1)
            playerStatus.visibility = View.GONE
            if (isAudio) {
                videoSurface.view.visibility = View.GONE
            } else {
                videoSurface.view.visibility = View.VISIBLE
            }
            if (::playerChrome.isInitialized) {
                playerChrome.visibility = View.VISIBLE
                updatePlayerChrome()
            }
            content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            playerLayer.bringToFront()
            playerFocus.restore()
        } else {
            content.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            if (::playerChrome.isInitialized) playerChrome.visibility = View.GONE
            val isAudio = currentItem?.kind == "audio" || (currentItem?.provider == "spotify")
            if (isAudio || session.isEmpty()) {
                playerLayer.visibility = View.GONE
            } else {
                playerStatus.visibility = View.GONE
                videoSurface.view.visibility = View.VISIBLE
                if (isTV()) {
                    playerLayer.visibility = View.VISIBLE
                    playerLayer.bringToFront()
                    content.post { positionDockedVideo() }
                } else {
                    playerLayer.visibility = View.GONE
                }
            }
            homeFocus.restore()
        }
    }

    private fun positionDockedVideo() {
        if (full || session.isEmpty() || currentItem?.kind == "audio") return
        val parent = playerLayer.parent as? View ?: return
        val dock =
            content.videoDockRect(parent)
                ?: run {
                    playerLayer.visibility = View.GONE
                    return
                }
        playerLayer.layoutParams =
            FrameLayout.LayoutParams(dock.width(), dock.height(), Gravity.TOP or Gravity.LEFT)
                .apply {
                    leftMargin = dock.left
                    topMargin = dock.top
                }
        playerLayer.visibility = View.VISIBLE
    }

    private fun stopPlayback(keepReceiver: Boolean = false, endSession: Boolean = true) {
        playbackFailure.dismiss()
        playbackPending = false
        showPlaybackFeedback(null)
        if (!keepReceiver && receiverViewModel.activeSession.isNotEmpty())
            receiverViewModel.dismiss(receiverViewModel.activeSession)
        currentItem = null
        receiverState = ""
        receiverInfo.visibility = View.GONE
        receiverArtwork.visibility = View.GONE
        receiverArtwork.bind(artwork, "")
        videoSurface.view.visibility = View.VISIBLE
        audioController.release()
        // Cancel pending Activity requests; the service owns session progress and revocation.
        playbackModel.stop("", PlaybackProgress("STOPPED", 0, 0))
        session = ""
        stream = ""
        tracksModel.attach("")
        subtitleText.text = ""
        subtitleText.visibility = View.GONE
        timelineOffset = 0
        full = false
        if (endSession) player.end(preserveInterrupted = keepReceiver)
        if (::playerChrome.isInitialized) playerChrome.visibility = View.GONE
        playerLayer.visibility = View.GONE
        now.setText(R.string.nothing_playing)
        content.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        content.setPlaybackSession(HomePlaybackSession(active = false))
        homeFocus.restore()
    }

    private fun external() {
        if (stream.isEmpty()) return
        player.pause()
        audioController.release()
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(stream), mime))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.no_external_player, Toast.LENGTH_LONG).show()
        }
    }

    private fun contextAccent(): Int =
        ui.providerAccent(
            currentItem?.provider?.takeIf { it.isNotEmpty() }
                ?: (if (::homeViewModel.isInitialized) homeViewModel.state.scope.provider else "")
        )

    private fun remoteCommand(
        action: String,
        provider: String,
        text: String,
        inputId: String,
    ): String {
        if (!foreground) return "BUSY"
        if (action == "TEXT") return remoteText.paste(text, inputId)
        if (!hasWindowFocus()) {
            if (!catalogDialogs.remoteReady) return "BUSY"
            val key =
                when (action) {
                    "UP" -> KeyEvent.KEYCODE_DPAD_UP
                    "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                    "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                    "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                    "OK" -> KeyEvent.KEYCODE_DPAD_CENTER
                    "BACK" -> KeyEvent.KEYCODE_BACK
                    else -> 0
                }
            if (key != 0) return if (catalogDialogs.remoteKey(key)) "EXECUTED" else "UNSUPPORTED"
            if (action == "HOME" || action == "PROVIDER") catalogDialogs.close()
            else return "UNSUPPORTED"
        }
        if (currentFocus is EditText) return "BUSY"
        when (action) {
            "HOME" -> {
                catalogModel.rememberPlaybackReturn(null)
                if (full) setFullscreen(false)
                refresh("", "")
                return "EXECUTED"
            }
            "PROVIDER" -> {
                catalogModel.rememberPlaybackReturn(null)
                if (full) setFullscreen(false)
                refresh(provider, "")
                return "EXECUTED"
            }
            "BACK" -> {
                if (full || session.isNotEmpty() || catalogModel.screen != null) onBackPressed()
                return "EXECUTED"
            }
            "VOLUME_UP",
            "VOLUME_DOWN",
            "MUTE" -> {
                val audio = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                if (action == "MUTE") return "UNSUPPORTED"
                audio.adjustStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    if (action == "VOLUME_UP") android.media.AudioManager.ADJUST_RAISE
                    else android.media.AudioManager.ADJUST_LOWER,
                    android.media.AudioManager.FLAG_SHOW_UI,
                )
                return "EXECUTED"
            }
        }
        val key =
            when (action) {
                "UP" -> KeyEvent.KEYCODE_DPAD_UP
                "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                "OK" -> KeyEvent.KEYCODE_DPAD_CENTER
                "PLAY_PAUSE" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "STOP" -> KeyEvent.KEYCODE_MEDIA_STOP
                "NEXT" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "SEEK_BACK" -> KeyEvent.KEYCODE_MEDIA_REWIND
                "SEEK_FORWARD" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                else -> return "UNSUPPORTED"
            }
        if (
            action in listOf("PLAY_PAUSE", "STOP", "NEXT", "SEEK_BACK", "SEEK_FORWARD") &&
                session.isEmpty()
        )
            return "UNSUPPORTED"
        val down = dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        val up = dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        return if (down || up) "EXECUTED" else "UNSUPPORTED"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (currentFocus !is EditText) {
            val isPlaying = session.isNotEmpty()
            if (full && isPlaying) {
                if (event.action == KeyEvent.ACTION_DOWN && playerFocus.move(key, currentFocus))
                    return true
            } else {
                if (content.isRightRailFocused()) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        when (key) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                val next = content.moveRightRailFocus(key, currentFocus)
                                if (next != null) {
                                    next.requestFocus()
                                    return true
                                }
                                content.restoreMainContentFocus()
                                return true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                val next = content.moveRightRailFocus(key, currentFocus)
                                if (next != null) {
                                    next.requestFocus()
                                    return true
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                content.restoreMainContentFocus()
                                return true
                            }
                        }
                    }
                    if (
                        key == KeyEvent.KEYCODE_BACK &&
                            event.action == KeyEvent.ACTION_UP &&
                            !event.isCanceled
                    ) {
                        content.restoreMainContentFocus()
                        return true
                    }
                } else {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (key == KeyEvent.KEYCODE_DPAD_RIGHT && content.isRightRailActive()) {
                            val beforeKey = homeFocus.selectedKey
                            if (homeFocus.move(key, currentFocus)) {
                                if (homeFocus.selectedKey == beforeKey) {
                                    if (content.focusRightRail()) return true
                                }
                                return true
                            } else {
                                if (content.focusRightRail()) return true
                            }
                        } else {
                            if (homeFocus.move(key, currentFocus)) return true
                        }
                    }
                }
            }
            // Preserve native CENTER/ENTER activation; gamepad A follows the same key-up contract.
            if (key == KeyEvent.KEYCODE_BUTTON_A) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                    gamepadClick = currentFocus
                if (event.action == KeyEvent.ACTION_UP) {
                    if (!event.isCanceled && gamepadClick === currentFocus)
                        gamepadClick?.performClick()
                    gamepadClick = null
                }
                return true
            }
            if (key == KeyEvent.KEYCODE_BUTTON_B) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBackPressed()
                return true
            }
            if (
                session.isNotEmpty() &&
                    key in
                        intArrayOf(
                            KeyEvent.KEYCODE_MEDIA_PLAY,
                            KeyEvent.KEYCODE_MEDIA_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_HEADSETHOOK,
                            KeyEvent.KEYCODE_MEDIA_STOP,
                            KeyEvent.KEYCODE_MEDIA_NEXT,
                            KeyEvent.KEYCODE_MEDIA_REWIND,
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        )
            ) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                    when (key) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> setPlaying(true)
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> setPlaying(false)
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> togglePlayback()
                        KeyEvent.KEYCODE_MEDIA_STOP -> stopPlayback()
                        KeyEvent.KEYCODE_MEDIA_NEXT -> player.next()
                        KeyEvent.KEYCODE_MEDIA_REWIND -> seek(-10000)
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(10000)
                    }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        if (full) togglePlayerSize()
        else if (content.isRightRailFocused()) {
            content.restoreMainContentFocus()
        } else if (session.isNotEmpty()) {
            stopPlayback()
            catalogDialogs.resume()
        } else if (!catalogDialogs.resume()) super.onBackPressed()
    }

    private fun refreshReceiverListening() {
        val profile = Triple(api.base, api.device, api.token)
        fun current() = !closed && foreground && profile == Triple(api.base, api.device, api.token)
        receiverViewModel.readMediaProvider(
            { provider ->
                if (current()) {
                    universalReception = provider == "universal"
                    player.configureReceivers(mediaProvider = provider)
                    if (universalReception) player.enableYouTube()
                }
            },
            {},
        )
        receiverViewModel.readEnabled(
            { enabled -> if (current()) player.configureReceivers(castEnabled = enabled) },
            {},
        )
    }

    override fun onResume() {
        super.onResume()
        PlaybackNotifications.requestPermission(this)
        foreground = true
        companionController.resume()
        if (::player.isInitialized) {
            if (session.isNotEmpty() && !audioController.acquire()) player.pause()
            player.foreground(true)
            showYouTubePlaybackIssue()
        }
        if (::receiverViewModel.isInitialized && api.token.isNotEmpty()) {
            receiverViewModel.resumeForeground()
            refreshReceiverListening()
        }
        if (
            !playbackPending &&
                lastState == "FAILED" &&
                session.isNotEmpty() &&
                receiverViewModel.activeSession.isEmpty() &&
                !youtubeIncoming
        )
            showPlaybackFailure()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("playbackFullscreen", full)
        outState.putString("homeProvider", homeViewModel.state.scope.provider)
        outState.putString("homeQuery", homeViewModel.state.scope.query)
        outState.putString("homeFocus", content.focus.selectedKey)
        CatalogSavedState.write(outState, catalogDialogs.snapshot())
        CatalogSavedState.writeOverlay(outState, catalogDialogs.overlaySnapshot())
        CatalogSavedState.writeSearch(outState, catalogDialogs.searchSnapshot())
        CatalogSavedState.writePlaybackReturn(outState, catalogModel.playbackReturn)
        outState.putString("catalogDetail", catalogDialogs.detailItemId)
        outState.putBoolean("catalogVisible", catalogDialogs.visible)
        SettingsSavedState.write(outState, settingsDialogs.snapshot())
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        companionController.pause()
        foreground = false
        playbackFailure.dismiss()
        player.foreground(false)
        super.onPause()
    }

    override fun onDestroy() {
        playbackFailure.dismiss()
        closed = true
        receiverOptionsPanel?.dismiss()
        audioOptionsPanel?.dismiss()
        events.close()
        companionController.close()
        settingsDialogs.close()
        settingsModel.close()
        videoSurface.close()
        catalogDialogs.close()
        catalogModel.close()
        searchModel.close()
        playbackModel.close()
        tracksModel.close()
        diagnosticsModel?.close()
        artwork.close()
        artworkDecoder.close()
        imageWorker.shutdownNow()
        receiverViewModel.close()
        homeViewModel.close()
        foreground = false
        handler.removeCallbacks(receiverTick)
        player.close()
        // Let closed ViewModels revoke plans already queued for UI delivery.
        worker.execute {
            handler.post {
                worker.execute { api.close() }
                worker.shutdown()
            }
        }
        poller.shutdownNow()
        super.onDestroy()
    }
}
