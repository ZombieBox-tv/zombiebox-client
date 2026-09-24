package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.artwork.platform.ArtworkDecoder
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.ui.ArtworkImageView
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel.ArtworkViewModel
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeSnapshot

/**
 * Dedicated Spotify page view for Zombie TV.
 *
 * Spotify is a receiver and playback target in V1, not a catalog clone. This view presents
 * authentic connection/worker state, contextual account pairing and explicit receiver activation.
 * For an actual active Spotify playback session, it renders artwork, metadata, progress and
 * accessible universal transport controls. When idle, no fake items or playback claims are made.
 */
@Suppress("DEPRECATION")
class SpotifyPageView(
    private val context: Context,
    private val ui: TvWidgets,
    private val actions: HomeActions,
    private val artwork: ArtworkViewModel,
    private val decoder: ArtworkDecoder,
) {
    private val spotifyGreen = ui.providerAccent("spotify")

    private var activeCard: LinearLayout? = null
    private var idleCard: LinearLayout? = null
    private var artView: ArtworkImageView? = null
    private var artFallback: ServiceMarkView? = null
    private var titleText: TextView? = null
    private var subtitleText: TextView? = null
    private var timingText: TextView? = null
    private var progressBar: PlaybackProgressBar? = null
    private var prevButton: ImageButton? = null
    private var playPauseButton: ImageButton? = null
    private var playPauseDrawable: PlaybackSymbolDrawable? = null
    private var nextButton: ImageButton? = null
    private var playbackStatusBadge: TextView? = null
    private var transportControls: ViewGroup? = null

    fun playbackControls(): ViewGroup? = transportControls

    fun render(
        parent: LinearLayout,
        snapshot: HomeSnapshot,
        playback: HomePlaybackSession,
        focusRows: ArrayList<Pair<String, ViewGroup>>,
    ) {
        val module = snapshot.modules.firstOrNull { it.id == "spotify" }
        val moduleState = module?.state
        val authMode = module?.authMode

        // Hero connection / worker status card (parallel to AirPlayPageView)
        val hero =
            ui.column().apply {
                setPadding(ui.dp(22), ui.dp(20), ui.dp(22), ui.dp(20))
                setBackgroundDrawable(ui.box(ui.panel, spotifyGreen))
            }

        val heading = ui.row()
        heading.addView(
            ServiceMarkView(context, "spotify", spotifyGreen),
            LinearLayout.LayoutParams(ui.dp(52), ui.dp(52)).apply { rightMargin = ui.dp(14) },
        )
        heading.addView(
            ui.text(context.getString(R.string.spotify), 30f).apply { typeface = ui.bold },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        val badge =
            ui.text(statusLabel(moduleState), 13f, statusColor(moduleState)).apply {
                gravity = Gravity.CENTER
                setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6))
                setBackgroundDrawable(ui.box(ui.background, statusColor(moduleState)))
            }
        heading.addView(badge)
        hero.addView(heading)

        hero.addView(ui.text(context.getString(R.string.spotify_intro), 17f, ui.muted))
        hero.addView(ui.text(statusDetail(moduleState, authMode), 14f, ui.muted))

        val controls = ui.row().apply { setPadding(0, ui.dp(12), 0, 0) }

        // Explicit receiver activation action
        val ready = moduleState == "READY" || moduleState == "HEALTHY"
        val localPairing = moduleState == "AUTH_REQUIRED" && authMode == "zeroconf"
        if (ready || localPairing) {
            controls.addView(
                ui.primary(R.string.spotify_activate_receiver) { actions.spotifyReceive() }
                    .apply { tag = "spotify:activate_receiver" }
            )
        }

        // Local discovery gives the phone a first path; device-auth keeps its code flow available.
        if (ready || moduleState == "AUTH_REQUIRED") {
            controls.addView(
                (if (moduleState == "AUTH_REQUIRED" && !localPairing)
                        ui.primary(R.string.spotify_connect_account) { actions.spotifyAuthorize() }
                    else ui.button(R.string.spotify_connect_account) { actions.spotifyAuthorize() })
                    .apply { tag = "spotify:connect_account" }
            )
        }
        if (
            moduleState == "DISABLED" || moduleState == "UNAVAILABLE" || moduleState == "DEGRADED"
        ) {
            controls.addView(
                ui.button(R.string.configure_services) { actions.providers() }
                    .apply { tag = "spotify:configure" }
            )
        }

        hero.addView(controls)
        parent.addView(
            hero,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = ui.dp(12)
                bottomMargin = ui.dp(12)
            },
        )
        if (controls.childCount > 0) focusRows.add(Pair("spotify:controls", controls))

        // Session / Now Playing section title
        parent.addView(
            ui.text(context.getString(R.string.spotify_receive_title), 18f).apply {
                typeface = ui.bold
                setPadding(ui.dp(4), ui.dp(8), 0, ui.dp(8))
            }
        )

        // Idle session card
        val idle =
            ui.column().apply {
                setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16))
                setBackgroundDrawable(ui.box(ui.panel, Color.rgb(46, 55, 59)))
            }
        val idleHeading = ui.row()
        idleHeading.addView(
            ui.text(context.getString(R.string.spotify_idle_title), 20f).apply {
                typeface = ui.bold
            },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        idleHeading.addView(
            ui.text(context.getString(R.string.idle), 13f, ui.muted).apply {
                setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4))
            }
        )
        idle.addView(idleHeading)
        idle.addView(
            ui.text(context.getString(R.string.spotify_idle_detail), 15f, ui.muted).apply {
                maxLines = 3
                setPadding(0, ui.dp(6), 0, 0)
            }
        )
        idleCard = idle
        parent.addView(idle, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(10) })

        // Active playback card (artwork, title/artist, progress, transport controls)
        val active =
            ui.column().apply {
                setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16))
                setBackgroundDrawable(ui.box(ui.panel, spotifyGreen))
            }
        val activeHeading = ui.row()
        activeHeading.addView(
            ui.text(context.getString(R.string.spotify_now_playing), 20f).apply {
                typeface = ui.bold
            },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        val activeBadge =
            ui.text(context.getString(R.string.playing), 13f, spotifyGreen).apply {
                setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4))
            }
        playbackStatusBadge = activeBadge
        activeHeading.addView(activeBadge)
        active.addView(activeHeading)

        val contentRow =
            ui.row().apply {
                setPadding(0, ui.dp(12), 0, 0)
                gravity = Gravity.CENTER_VERTICAL
            }

        // Square album artwork container
        val artContainer =
            FrameLayout(context).apply {
                setBackgroundDrawable(ui.box(Color.rgb(16, 22, 24), ui.panel))
            }
        val art =
            ArtworkImageView(context, decoder).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        artView = art
        artContainer.addView(art, FrameLayout.LayoutParams(-1, -1))
        val fallback =
            ServiceMarkView(context, "spotify", spotifyGreen).apply { visibility = View.GONE }
        artFallback = fallback
        artContainer.addView(
            fallback,
            FrameLayout.LayoutParams(ui.dp(64), ui.dp(64), Gravity.CENTER),
        )
        contentRow.addView(
            artContainer,
            LinearLayout.LayoutParams(ui.dp(130), ui.dp(130)).apply { rightMargin = ui.dp(18) },
        )

        val metaCol = ui.column()

        val trackTitle =
            ui.text("", 22f, Color.WHITE).apply {
                typeface = ui.bold
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 0, 0, ui.dp(2))
            }
        titleText = trackTitle
        metaCol.addView(trackTitle)

        val trackSubtitle =
            ui.text("", 15f, ui.muted).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 0, 0, ui.dp(4))
            }
        subtitleText = trackSubtitle
        metaCol.addView(trackSubtitle)

        val timing = ui.text("", 13f, ui.muted).apply { setPadding(0, ui.dp(2), 0, 0) }
        timingText = timing
        metaCol.addView(timing)

        val progBar = PlaybackProgressBar(context, ui).apply { accentColor = spotifyGreen }
        progressBar = progBar
        metaCol.addView(progBar)

        // Transport controls
        val transportRow = ui.row().apply { setPadding(0, ui.dp(4), 0, 0) }
        transportControls = transportRow

        val prevBtn =
            createControlButton(
                PlaybackSymbol.PREVIOUS,
                R.string.music_previous,
                "spotify:transport:prev",
            ) {
                actions.previous()
            }
        prevButton = prevBtn
        transportRow.addView(prevBtn)

        val playDrawable = PlaybackSymbolDrawable(PlaybackSymbol.PAUSE, Color.WHITE)
        playPauseDrawable = playDrawable
        val playPauseBtn =
            ImageButton(context).apply {
                tag = "spotify:transport:play"
                contentDescription = context.getString(R.string.play_pause)
                setImageDrawable(playDrawable)
                isFocusable = true
                isClickable = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
                setBackgroundDrawable(controlFocusBackground(spotifyGreen))
                setOnClickListener { actions.playPause() }
                layoutParams =
                    LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)).apply {
                        setMargins(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3))
                    }
            }
        playPauseButton = playPauseBtn
        transportRow.addView(playPauseBtn)

        val nextBtn =
            createControlButton(
                PlaybackSymbol.NEXT,
                R.string.music_next,
                "spotify:transport:next",
            ) {
                actions.next()
            }
        nextButton = nextBtn
        transportRow.addView(nextBtn)

        val expandBtn =
            ui.button(R.string.open_player) { actions.expand() }
                .apply { tag = "spotify:transport:open" }
        transportRow.addView(expandBtn)

        metaCol.addView(transportRow)
        contentRow.addView(metaCol, LinearLayout.LayoutParams(0, -2, 1f))
        active.addView(contentRow)

        activeCard = active
        parent.addView(active, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(10) })
        if (isSpotifyPlaying(playback)) focusRows.add(Pair("spotify:playback", transportRow))

        // Update presentation based on current playback
        updatePlaybackSession(playback)
    }

    private fun isSpotifyPlaying(session: HomePlaybackSession): Boolean =
        session.active && session.item?.provider == "spotify"

    fun updatePlaybackSession(session: HomePlaybackSession) {
        val playing = isSpotifyPlaying(session)
        if (playing) {
            idleCard?.visibility = View.GONE
            activeCard?.visibility = View.VISIBLE
            bindPlayback(session)
        } else {
            activeCard?.visibility = View.GONE
            idleCard?.visibility = View.VISIBLE
        }
    }

    private fun bindPlayback(session: HomePlaybackSession) {
        val item = session.item
        val title = item?.title?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.spotify)
        titleText?.text = title
        subtitleText?.text = item?.subtitle ?: ""
        subtitleText?.visibility = if (item?.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE

        prevButton?.visibility = if (session.canPrevious) View.VISIBLE else View.GONE
        nextButton?.visibility = if (session.canNext) View.VISIBLE else View.GONE

        val imageUrl = item?.imageUrl
        if (!imageUrl.isNullOrEmpty()) {
            artView?.visibility = View.VISIBLE
            artFallback?.visibility = View.GONE
            artView?.bind(artwork, imageUrl)
        } else {
            artView?.visibility = View.GONE
            artFallback?.visibility = View.VISIBLE
        }

        updateProgress(session.state, session.positionMs, session.durationMs)
    }

    fun updateProgress(status: String, positionMs: Int, durationMs: Int) {
        val stateLabel = ui.localizedState(status)
        val timeLabel =
            if (durationMs > 0) "${ui.formatTime(positionMs)} / ${ui.formatTime(durationMs)}"
            else ui.formatTime(positionMs)
        timingText?.text = "$stateLabel · $timeLabel"

        progressBar?.setProgress(positionMs, durationMs)

        val isPlaying = status == "PLAYING"
        playPauseDrawable?.symbol = if (isPlaying) PlaybackSymbol.PAUSE else PlaybackSymbol.PLAY
        playPauseButton?.invalidate()

        playbackStatusBadge?.text = stateLabel
        playbackStatusBadge?.setTextColor(if (isPlaying) spotifyGreen else ui.muted)
    }

    private fun createControlButton(
        symbol: PlaybackSymbol,
        contentDescRes: Int,
        tagKey: String,
        click: () -> Unit,
    ): ImageButton {
        val drawable = PlaybackSymbolDrawable(symbol, Color.WHITE)
        return ImageButton(context).apply {
            tag = tagKey
            contentDescription = context.getString(contentDescRes)
            setImageDrawable(drawable)
            isFocusable = true
            isClickable = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
            setBackgroundDrawable(controlFocusBackground(spotifyGreen))
            setOnClickListener { click() }
            layoutParams =
                LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)).apply {
                    setMargins(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3))
                }
        }
    }

    private fun controlFocusBackground(accent: Int): StateListDrawable {
        fun box(bgColor: Int, strokeColor: Int, strokeWidthDp: Int) =
            GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = ui.dp(8).toFloat()
                setStroke(ui.dp(strokeWidthDp), strokeColor)
            }
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                box(Color.rgb(32, 42, 45), accent, 2),
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                box(Color.rgb(32, 42, 45), accent, 2),
            )
            addState(intArrayOf(), box(Color.rgb(20, 27, 29), Color.rgb(38, 48, 51), 1))
        }
    }

    private fun statusLabel(state: String?): String =
        context.getString(
            when (state) {
                "HEALTHY",
                "READY" -> R.string.ready
                "DISABLED" -> R.string.disabled
                "AUTH_REQUIRED" -> R.string.authorization_required
                "DEGRADED",
                "UNAVAILABLE" -> R.string.unavailable
                else -> R.string.loading
            }
        )

    private fun statusDetail(state: String?, authMode: String?): String =
        context.getString(
            when (state) {
                "HEALTHY",
                "READY" -> R.string.spotify_receiver_online
                "DISABLED" -> R.string.spotify_receiver_disabled
                "AUTH_REQUIRED" ->
                    if (authMode == "zeroconf") R.string.spotify_receiver_auth_zeroconf
                    else R.string.spotify_receiver_auth_required
                "DEGRADED",
                "UNAVAILABLE" -> R.string.spotify_receiver_unavailable
                else -> R.string.spotify_receiver_checking
            }
        )

    private fun statusColor(state: String?): Int =
        if (state == "HEALTHY" || state == "READY") spotifyGreen else ui.muted
}
