package io.github.diegog0477.zombiebox.client.features.playback.presentation.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.artwork.platform.ArtworkDecoder
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.ui.ArtworkImageView
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel.ArtworkViewModel
import io.github.diegog0477.zombiebox.client.features.home.presentation.ui.ServiceMarkView

/** Callbacks for all player actions. */
data class TvPlayerActions(
    val seekBack: () -> Unit,
    val previous: () -> Unit,
    val playPause: () -> Unit,
    val next: () -> Unit,
    val seekForward: () -> Unit,
    val description: () -> Unit = {},
    val quality: () -> Unit = {},
    val audioTracks: () -> Unit,
    val subtitles: () -> Unit,
    val minimize: () -> Unit,
    val external: () -> Unit,
    val stop: () -> Unit,
    val playItem: (MediaItem) -> Unit,
    val loadMoreRelated: (() -> Unit)? = null,
)

/**
 * Modern TV player chrome for Zombie Box TV.
 *
 * Provides:
 * - Video mode: unobtrusive gradient overlay over video surface with title, timeline, universal
 *   symbol transport controls, and YouTube related rail when available.
 * - Audio mode (Spotify/Music): dedicated fullscreen UI featuring large album artwork, metadata,
 *   and prominent transport controls without a black video rectangle.
 * - Transient chrome with D-pad auto-hide, description area, quality selection, and smooth related
 *   pagination.
 * - API 9+ compatibility using Barlow typography, provider accents, and explicit D-pad focus.
 */
@SuppressLint("ViewConstructor")
@Suppress("DEPRECATION")
class TvPlayerChromeView(
    context: Context,
    private val ui: TvWidgets,
    private val artwork: ArtworkViewModel,
    private val decoder: ArtworkDecoder,
    private val actions: TvPlayerActions,
) : FrameLayout(context) {

    private var accentColor = ui.green
    private var isAudioMode = false
    private var currentItem: MediaItem? = null
    private var isPlaying = false
    private var optimisticTargetPlaying: Boolean? = null
    private var optimisticTimestamp = 0L
    private val lowerPanel = PlayerChromeSections()
    private var returnControlKey = "player:play_pause"
    private var videoSeekable = false
    private var videoDurationMs = 0

    var isChromeVisible = true
        private set

    private val inactivityRunnable = Runnable {
        if (isPlaying && isChromeVisible && !isAudioMode) {
            hideChrome()
        }
    }

    // Video overlay views
    private val videoOverlay: FrameLayout
    private val topScrim: LinearLayout
    private val videoTitleText: TextView
    private val videoSubtitleText: TextView
    private val bottomScrim: LinearLayout
    private val videoTimeText: TextView
    private val videoDurationText: TextView
    private val videoTimelineRow: LinearLayout
    private val videoProgressBar: TvPlaybackProgressBar
    val videoControlsRow: LinearLayout
    val videoSeekBackButton: ImageButton
    val videoPrevButton: ImageButton
    val videoPlayPauseButton: ImageButton
    val videoNextButton: ImageButton
    val videoSeekForwardButton: ImageButton
    val videoDescriptionButton: ImageButton
    val videoQualityButton: ImageButton
    val videoAudioTracksButton: ImageButton
    val videoSubtitlesButton: ImageButton
    val videoMinimizeButton: ImageButton
    val videoExternalButton: ImageButton
    val videoStopButton: ImageButton
    private val relatedSection: LinearLayout
    private val relatedCardsRow: LinearLayout
    private val detailsSection: LinearLayout
    private val detailsTitleText: TextView
    private val detailsChannelText: TextView
    private val detailsScrollView: ScrollView
    private val detailsDescriptionText: TextView

    // Audio overlay views
    private val audioOverlay: FrameLayout
    private val audioArtContainer: FrameLayout
    private val audioArtView: ArtworkImageView
    private val audioArtFallback: ServiceMarkView
    private val audioProviderIcon: ServiceMarkView
    private val audioProviderTitle: TextView
    private val audioTitleText: TextView
    private val audioSubtitleText: TextView
    private val audioTimeText: TextView
    private val audioDurationText: TextView
    private val audioProgressBar: TvPlaybackProgressBar
    val audioPrimaryControlsRow: LinearLayout
    val audioSecondaryControlsRow: LinearLayout
    val audioPrevButton: ImageButton
    val audioSeekBackButton: ImageButton
    val audioPlayPauseButton: ImageButton
    val audioSeekForwardButton: ImageButton
    val audioNextButton: ImageButton
    val audioAudioTracksButton: ImageButton
    val audioSubtitlesButton: ImageButton
    val audioMinimizeButton: ImageButton
    val audioExternalButton: ImageButton
    val audioStopButton: ImageButton

    init {
        // --- 1. Video Overlay Layout ---
        videoOverlay =
            FrameLayout(context).apply {
                isClickable = true
                isFocusable = false
                setOnClickListener {
                    if (isAudioMode) return@setOnClickListener
                    if (isChromeVisible) {
                        hideChrome()
                    } else {
                        showChrome()
                        videoPlayPauseButton.requestFocus()
                    }
                }
            }
        addView(videoOverlay, LayoutParams(-1, -1))

        // Top scrim & info
        topScrim =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundDrawable(
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(Color.argb(200, 0, 0, 0), Color.TRANSPARENT),
                    )
                )
                setPadding(ui.dp(32), ui.dp(24), ui.dp(32), ui.dp(40))
                setOnClickListener { resetInactivityTimer() }
            }
        videoTitleText =
            TextView(context).apply {
                textSize = 22f
                typeface = TvTypography.semibold(context)
                setTextColor(Color.WHITE)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setShadowLayer(4f, 1f, 1f, Color.BLACK)
            }
        topScrim.addView(videoTitleText)

        videoSubtitleText =
            TextView(context).apply {
                textSize = 13f
                typeface = TvTypography.regular(context)
                setTextColor(ui.muted)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setShadowLayer(3f, 1f, 1f, Color.BLACK)
            }
        topScrim.addView(videoSubtitleText)
        videoOverlay.addView(topScrim, LayoutParams(-1, -2, Gravity.TOP))

        // Bottom scrim & controls
        bottomScrim =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundDrawable(
                    GradientDrawable(
                        GradientDrawable.Orientation.BOTTOM_TOP,
                        intArrayOf(
                            Color.argb(240, 10, 15, 16),
                            Color.argb(200, 10, 15, 16),
                            Color.TRANSPARENT,
                        ),
                    )
                )
                setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(16))
                setOnClickListener { resetInactivityTimer() }
            }

        // Timeline Row
        videoTimelineRow =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        videoTimeText =
            TextView(context).apply {
                textSize = 13f
                typeface = TvTypography.semibold(context)
                setTextColor(Color.WHITE)
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
            }
        videoTimelineRow.addView(videoTimeText, LinearLayout.LayoutParams(-2, -2))

        videoProgressBar =
            TvPlaybackProgressBar(context).apply {
                accentColor = this@TvPlayerChromeView.accentColor
                tag = "player:timeline"
                contentDescription = context.getString(R.string.player_timeline_unavailable)
                focusChanged = { focused ->
                    if (focused) removeCallbacks(inactivityRunnable) else resetInactivityTimer()
                }
            }
        videoTimelineRow.addView(
            videoProgressBar,
            LinearLayout.LayoutParams(0, ui.dp(32), 1f).apply {
                leftMargin = ui.dp(12)
                rightMargin = ui.dp(12)
            },
        )

        videoDurationText =
            TextView(context).apply {
                textSize = 13f
                typeface = TvTypography.semibold(context)
                setTextColor(ui.muted)
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
            }
        videoTimelineRow.addView(videoDurationText, LinearLayout.LayoutParams(-2, -2))
        bottomScrim.addView(videoTimelineRow)

        // Video Universal Controls Row
        val controlsScroll =
            HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        videoControlsRow =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, ui.dp(8), 0, ui.dp(8))
            }

        videoSeekBackButton =
            createButton(TvPlayerSymbol.SEEK_BACK, R.string.seek_back, "player:seek_back") {
                resetInactivityTimer()
                actions.seekBack()
            }
        videoControlsRow.addView(videoSeekBackButton)

        videoPrevButton =
            createButton(TvPlayerSymbol.PREVIOUS, R.string.music_previous, "player:prev") {
                resetInactivityTimer()
                actions.previous()
            }
        videoControlsRow.addView(videoPrevButton)

        videoPlayPauseButton =
            createButton(
                TvPlayerSymbol.PLAY,
                R.string.play_pause,
                "player:play_pause",
                sizeDp = 48,
            ) {
                resetInactivityTimer()
                onPlayPauseClicked()
            }
        videoControlsRow.addView(videoPlayPauseButton)

        videoNextButton =
            createButton(TvPlayerSymbol.NEXT, R.string.next_item, "player:next") {
                resetInactivityTimer()
                actions.next()
            }
        videoControlsRow.addView(videoNextButton)

        videoSeekForwardButton =
            createButton(
                TvPlayerSymbol.SEEK_FORWARD,
                R.string.seek_forward,
                "player:seek_forward",
            ) {
                resetInactivityTimer()
                actions.seekForward()
            }
        videoControlsRow.addView(videoSeekForwardButton)

        videoDescriptionButton =
            createButton(TvPlayerSymbol.DESCRIPTION, R.string.description, "player:description") {
                resetInactivityTimer()
                if (detailsSection.visibility == VISIBLE) {
                    detailsScrollView.requestFocus()
                }
                actions.description()
            }
        videoControlsRow.addView(videoDescriptionButton)

        videoQualityButton =
            createButton(TvPlayerSymbol.QUALITY, R.string.quality, "player:quality") {
                resetInactivityTimer()
                actions.quality()
            }
        videoControlsRow.addView(videoQualityButton)

        videoAudioTracksButton =
            createButton(
                TvPlayerSymbol.AUDIO_TRACKS,
                R.string.audio_tracks,
                "player:audio_tracks",
            ) {
                resetInactivityTimer()
                actions.audioTracks()
            }
        videoControlsRow.addView(videoAudioTracksButton)

        videoSubtitlesButton =
            createButton(TvPlayerSymbol.SUBTITLES, R.string.subtitles, "player:subtitles") {
                resetInactivityTimer()
                actions.subtitles()
            }
        videoControlsRow.addView(videoSubtitlesButton)

        videoMinimizeButton =
            createButton(TvPlayerSymbol.MINIMIZE, R.string.minimize, "player:minimize") {
                resetInactivityTimer()
                actions.minimize()
            }
        videoControlsRow.addView(videoMinimizeButton)

        videoExternalButton =
            createButton(TvPlayerSymbol.EXTERNAL, R.string.external_player, "player:external") {
                resetInactivityTimer()
                actions.external()
            }
        videoControlsRow.addView(videoExternalButton)

        videoStopButton =
            createButton(TvPlayerSymbol.STOP, R.string.stop, "player:stop") {
                resetInactivityTimer()
                actions.stop()
            }
        videoControlsRow.addView(videoStopButton)

        controlsScroll.addView(videoControlsRow)
        bottomScrim.addView(controlsScroll)

        // Related Videos Rail (for YouTube)
        relatedSection =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = GONE
            }
        val relatedScroll =
            HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        relatedCardsRow =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, ui.dp(6), 0, 0)
            }
        relatedScroll.addView(relatedCardsRow)
        relatedSection.addView(relatedScroll)
        bottomScrim.addView(relatedSection)

        // Details Section (below related rail)
        detailsSection =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = GONE
                setPadding(0, ui.dp(8), 0, 0)
            }
        detailsTitleText =
            TextView(context).apply {
                textSize = 16f
                typeface = TvTypography.semibold(context)
                setTextColor(Color.WHITE)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
        detailsSection.addView(detailsTitleText)

        detailsChannelText =
            TextView(context).apply {
                textSize = 13f
                typeface = TvTypography.regular(context)
                setTextColor(accentColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, ui.dp(2), 0, ui.dp(4))
            }
        detailsSection.addView(detailsChannelText)

        detailsScrollView =
            ScrollView(context).apply {
                isFocusable = true
                isClickable = true
                setBackgroundDrawable(detailsFocusBackground(accentColor))
                setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6))
                tag = "player:details"
                setOnFocusChangeListener { _, focused -> if (focused) resetInactivityTimer() }
            }
        detailsDescriptionText =
            TextView(context).apply {
                textSize = 14f
                typeface = TvTypography.regular(context)
                setTextColor(Color.rgb(220, 225, 230))
                setLineSpacing(ui.dp(3).toFloat(), 1f)
            }
        detailsScrollView.addView(detailsDescriptionText)
        detailsSection.addView(
            detailsScrollView,
            LinearLayout.LayoutParams(-1, ui.dp(84)).apply { topMargin = ui.dp(4) },
        )
        bottomScrim.addView(detailsSection)

        videoOverlay.addView(bottomScrim, LayoutParams(-1, -2, Gravity.BOTTOM))

        // --- 2. Audio Fullscreen Overlay Layout ---
        audioOverlay = FrameLayout(context).apply { visibility = GONE }
        addView(audioOverlay, LayoutParams(-1, -1))

        val audioCenterColumn =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(ui.dp(32), ui.dp(20), ui.dp(32), ui.dp(24))
            }

        // Audio Header
        val audioHeader =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        audioProviderIcon = ServiceMarkView(context, "", accentColor)
        audioHeader.addView(
            audioProviderIcon,
            LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)).apply { rightMargin = ui.dp(8) },
        )
        audioProviderTitle =
            TextView(context).apply {
                textSize = 13f
                typeface = TvTypography.semibold(context)
                setTextColor(accentColor)
            }
        audioHeader.addView(audioProviderTitle, LinearLayout.LayoutParams(-2, -2))
        audioCenterColumn.addView(audioHeader)

        // Album Art Box (large square)
        audioArtContainer =
            FrameLayout(context).apply {
                setBackgroundDrawable(ui.box(Color.rgb(18, 24, 26), Color.rgb(40, 52, 55)))
            }
        audioArtView =
            ArtworkImageView(context, decoder).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        audioArtContainer.addView(audioArtView, FrameLayout.LayoutParams(-1, -1))
        audioArtFallback = ServiceMarkView(context, "", accentColor).apply { visibility = GONE }
        audioArtContainer.addView(
            audioArtFallback,
            FrameLayout.LayoutParams(ui.dp(72), ui.dp(72), Gravity.CENTER),
        )
        audioCenterColumn.addView(
            audioArtContainer,
            LinearLayout.LayoutParams(ui.dp(210), ui.dp(210)).apply {
                topMargin = ui.dp(16)
                bottomMargin = ui.dp(16)
            },
        )

        // Metadata
        audioTitleText =
            TextView(context).apply {
                textSize = 22f
                typeface = TvTypography.semibold(context)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
        audioCenterColumn.addView(audioTitleText, LinearLayout.LayoutParams(-1, -2))

        audioSubtitleText =
            TextView(context).apply {
                textSize = 15f
                typeface = TvTypography.regular(context)
                setTextColor(ui.muted)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, ui.dp(4), 0, ui.dp(12))
            }
        audioCenterColumn.addView(audioSubtitleText, LinearLayout.LayoutParams(-1, -2))

        // Audio Timeline
        val audioTimeline =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        audioTimeText =
            TextView(context).apply {
                textSize = 12f
                typeface = TvTypography.semibold(context)
                setTextColor(Color.WHITE)
            }
        audioTimeline.addView(audioTimeText, LinearLayout.LayoutParams(-2, -2))

        audioProgressBar =
            TvPlaybackProgressBar(context).apply {
                accentColor = this@TvPlayerChromeView.accentColor
            }
        audioTimeline.addView(
            audioProgressBar,
            LinearLayout.LayoutParams(0, ui.dp(5), 1f).apply {
                leftMargin = ui.dp(10)
                rightMargin = ui.dp(10)
            },
        )

        audioDurationText =
            TextView(context).apply {
                textSize = 12f
                typeface = TvTypography.semibold(context)
                setTextColor(ui.muted)
            }
        audioTimeline.addView(audioDurationText, LinearLayout.LayoutParams(-2, -2))
        audioCenterColumn.addView(
            audioTimeline,
            LinearLayout.LayoutParams(ui.dp(440), -2).apply { bottomMargin = ui.dp(16) },
        )

        // Audio Large Primary Controls
        audioPrimaryControlsRow =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

        audioPrevButton =
            createButton(
                TvPlayerSymbol.PREVIOUS,
                R.string.music_previous,
                "player:audio_prev",
                sizeDp = 44,
            ) {
                actions.previous()
            }
        audioPrimaryControlsRow.addView(audioPrevButton)

        audioSeekBackButton =
            createButton(
                TvPlayerSymbol.SEEK_BACK,
                R.string.seek_back,
                "player:audio_seek_back",
                sizeDp = 44,
            ) {
                actions.seekBack()
            }
        audioPrimaryControlsRow.addView(audioSeekBackButton)

        audioPlayPauseButton =
            createButton(
                TvPlayerSymbol.PLAY,
                R.string.play_pause,
                "player:audio_play_pause",
                sizeDp = 58,
                circular = true,
            ) {
                onPlayPauseClicked()
            }
        audioPrimaryControlsRow.addView(audioPlayPauseButton)

        audioSeekForwardButton =
            createButton(
                TvPlayerSymbol.SEEK_FORWARD,
                R.string.seek_forward,
                "player:audio_seek_forward",
                sizeDp = 44,
            ) {
                actions.seekForward()
            }
        audioPrimaryControlsRow.addView(audioSeekForwardButton)

        audioNextButton =
            createButton(
                TvPlayerSymbol.NEXT,
                R.string.next_item,
                "player:audio_next",
                sizeDp = 44,
            ) {
                actions.next()
            }
        audioPrimaryControlsRow.addView(audioNextButton)

        audioCenterColumn.addView(audioPrimaryControlsRow)

        // Audio Secondary Controls
        audioSecondaryControlsRow =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(10), 0, 0)
            }

        audioAudioTracksButton =
            createButton(
                TvPlayerSymbol.AUDIO_TRACKS,
                R.string.audio_tracks,
                "player:audio_tracks",
                sizeDp = 38,
            ) {
                actions.audioTracks()
            }
        audioSecondaryControlsRow.addView(audioAudioTracksButton)

        audioSubtitlesButton =
            createButton(
                TvPlayerSymbol.SUBTITLES,
                R.string.subtitles,
                "player:audio_subtitles",
                sizeDp = 38,
            ) {
                actions.subtitles()
            }
        audioSecondaryControlsRow.addView(audioSubtitlesButton)

        audioMinimizeButton =
            createButton(
                TvPlayerSymbol.MINIMIZE,
                R.string.minimize,
                "player:audio_minimize",
                sizeDp = 38,
            ) {
                actions.minimize()
            }
        audioSecondaryControlsRow.addView(audioMinimizeButton)

        audioExternalButton =
            createButton(
                TvPlayerSymbol.EXTERNAL,
                R.string.external_player,
                "player:audio_external",
                sizeDp = 38,
            ) {
                actions.external()
            }
        audioSecondaryControlsRow.addView(audioExternalButton)

        audioStopButton =
            createButton(TvPlayerSymbol.STOP, R.string.stop, "player:audio_stop", sizeDp = 38) {
                actions.stop()
            }
        audioSecondaryControlsRow.addView(audioStopButton)

        audioCenterColumn.addView(audioSecondaryControlsRow)

        audioOverlay.addView(audioCenterColumn, LayoutParams(-1, -2, Gravity.CENTER))
    }

    private fun createButton(
        symbol: TvPlayerSymbol,
        contentDescRes: Int,
        tagKey: String,
        sizeDp: Int = 42,
        circular: Boolean = false,
        click: () -> Unit,
    ): ImageButton {
        val drawable = TvPlayerSymbolDrawable(symbol, Color.WHITE)
        return ImageButton(context).apply {
            tag = tagKey
            contentDescription = context.getString(contentDescRes)
            setImageDrawable(drawable)
            isFocusable = true
            isClickable = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = if (circular) ui.dp(14) else ui.dp(10)
            setPadding(pad, pad, pad, pad)
            setBackgroundDrawable(controlBackground(accentColor, circular))
            setOnClickListener { click() }
            layoutParams =
                LinearLayout.LayoutParams(ui.dp(sizeDp), ui.dp(sizeDp)).apply {
                    setMargins(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
                }
        }
    }

    private fun controlBackground(accent: Int, circular: Boolean = false): StateListDrawable {
        fun makeDrawable(bgColor: Int, strokeColor: Int, strokeWidthDp: Int) =
            GradientDrawable().apply {
                setColor(bgColor)
                if (circular) {
                    shape = GradientDrawable.OVAL
                } else {
                    cornerRadius = ui.dp(8).toFloat()
                }
                setStroke(ui.dp(strokeWidthDp), strokeColor)
            }

        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                if (circular) makeDrawable(accent, Color.WHITE, 3)
                else makeDrawable(Color.rgb(36, 46, 50), accent, 2),
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                if (circular) makeDrawable(accent, Color.WHITE, 3)
                else makeDrawable(Color.rgb(36, 46, 50), accent, 2),
            )
            addState(
                intArrayOf(),
                if (circular) makeDrawable(accent, Color.TRANSPARENT, 0)
                else makeDrawable(Color.argb(190, 20, 27, 29), Color.argb(120, 60, 72, 75), 1),
            )
        }
    }

    private fun cardFocusBackground(accent: Int): StateListDrawable {
        fun makeDrawable(bgColor: Int, strokeColor: Int, strokeWidthDp: Int) =
            GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = ui.dp(6).toFloat()
                setStroke(ui.dp(strokeWidthDp), strokeColor)
            }
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                makeDrawable(Color.rgb(32, 42, 45), accent, 2),
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                makeDrawable(Color.rgb(32, 42, 45), accent, 2),
            )
            addState(intArrayOf(), makeDrawable(Color.rgb(18, 24, 26), Color.TRANSPARENT, 0))
        }
    }

    private fun detailsFocusBackground(accent: Int): StateListDrawable {
        fun makeDrawable(bgColor: Int, strokeColor: Int, strokeWidthDp: Int) =
            GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = ui.dp(6).toFloat()
                setStroke(ui.dp(strokeWidthDp), strokeColor)
            }
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                makeDrawable(Color.argb(220, 20, 28, 32), accent, 2),
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                makeDrawable(Color.argb(220, 20, 28, 32), accent, 2),
            )
            addState(intArrayOf(), makeDrawable(Color.argb(140, 12, 16, 18), Color.TRANSPARENT, 0))
        }
    }

    private fun onPlayPauseClicked() {
        val currentEffective = optimisticTargetPlaying ?: isPlaying
        val next = !currentEffective
        optimisticTargetPlaying = next
        optimisticTimestamp = System.currentTimeMillis()
        applyPlayPauseVisuals(next)
        actions.playPause()
    }

    fun setOptimisticPlaying(playing: Boolean) {
        optimisticTargetPlaying = playing
        optimisticTimestamp = System.currentTimeMillis()
        applyPlayPauseVisuals(playing)
    }

    fun optimisticPlaying(): Boolean? = optimisticTargetPlaying

    private fun applyPlayPauseVisuals(playing: Boolean) {
        val symbol = if (playing) TvPlayerSymbol.PAUSE else TvPlayerSymbol.PLAY
        (videoPlayPauseButton.drawable as? TvPlayerSymbolDrawable)?.symbol = symbol
        (audioPlayPauseButton.drawable as? TvPlayerSymbolDrawable)?.symbol = symbol
        videoPlayPauseButton.invalidate()
        audioPlayPauseButton.invalidate()
        val descRes = if (playing) R.string.paused else R.string.play
        videoPlayPauseButton.contentDescription = context.getString(descRes)
        audioPlayPauseButton.contentDescription = context.getString(descRes)
    }

    fun resetInactivityTimer(delayMs: Long = 4500L) {
        removeCallbacks(inactivityRunnable)
        if (
            isPlaying &&
                isChromeVisible &&
                !isAudioMode &&
                !lowerPanel.expanded &&
                !videoProgressBar.isFocused
        ) {
            postDelayed(inactivityRunnable, delayMs)
        }
    }

    fun showChrome() {
        if (isAudioMode) return
        isChromeVisible = true
        topScrim.visibility = VISIBLE
        bottomScrim.visibility = VISIBLE
        resetInactivityTimer()
    }

    fun hideChrome() {
        if (isAudioMode) return
        removeCallbacks(inactivityRunnable)
        lowerPanel.collapse()
        applyLowerPanelVisibility()
        isChromeVisible = false
        topScrim.visibility = GONE
        bottomScrim.visibility = GONE
    }

    /** Binds active session information and switches between video and audio chrome. */
    fun bindSession(
        item: MediaItem?,
        isAudio: Boolean,
        accent: Int,
        canPrevious: Boolean,
        canNext: Boolean,
        seekable: Boolean,
        relatedItems: List<MediaItem>,
        hasMoreRelated: Boolean = false,
    ) {
        val sameVideo = !isAudio && !isAudioMode && currentItem?.id == item?.id
        currentItem = item
        isAudioMode = isAudio
        accentColor = accent
        videoSeekable = seekable
        updateTimelineAvailability()

        val provider = item?.provider ?: ""
        val title =
            item?.title?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.screen_mirroring)
        val subtitle = item?.subtitle ?: ""

        // Update progress bars
        videoProgressBar.accentColor = accent
        audioProgressBar.accentColor = accent

        // Refresh button drawables and backgrounds with new accent
        refreshButtonStyles()

        if (isAudio) {
            lowerPanel.bind(item?.id, related = false, details = false)
            removeCallbacks(inactivityRunnable)
            isChromeVisible = true
            videoOverlay.visibility = GONE
            audioOverlay.visibility = VISIBLE
            audioOverlay.setBackgroundDrawable(
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(mixColors(ui.panel, accent, 0.28f), Color.rgb(10, 15, 16)),
                )
            )

            audioProviderIcon.bind(provider, accent)
            audioProviderTitle.text = ui.serviceTitle(provider)
            audioProviderTitle.setTextColor(accent)
            audioArtFallback.bind(provider, accent)

            audioTitleText.text = title
            audioSubtitleText.text = subtitle
            audioSubtitleText.visibility = if (subtitle.isEmpty()) GONE else VISIBLE

            if (!item?.imageUrl.isNullOrEmpty()) {
                audioArtView.visibility = VISIBLE
                audioArtFallback.visibility = GONE
                audioArtView.bind(artwork, item.imageUrl)
            } else {
                audioArtView.visibility = GONE
                audioArtFallback.visibility = VISIBLE
            }

            audioPrevButton.visibility = if (canPrevious) VISIBLE else GONE
            audioNextButton.visibility = if (canNext) VISIBLE else GONE
            audioNextButton.isEnabled = canNext
            audioSeekBackButton.isEnabled = seekable
            audioSeekForwardButton.isEnabled = seekable
        } else {
            audioOverlay.visibility = GONE
            videoOverlay.visibility = VISIBLE
            if (!sameVideo) showChrome()

            videoTitleText.text = title
            videoSubtitleText.text = subtitle
            videoSubtitleText.visibility = if (subtitle.isEmpty()) GONE else VISIBLE

            videoPrevButton.visibility = if (canPrevious) VISIBLE else GONE
            videoNextButton.visibility = if (canNext) VISIBLE else GONE
            videoNextButton.isEnabled = canNext
            videoSeekBackButton.isEnabled = seekable
            videoSeekForwardButton.isEnabled = seekable

            // Details section (channel, title, description) when semantic metadata exists
            val hasDesc = !item?.description.isNullOrEmpty()
            val hasChannel = !item?.subtitle.isNullOrEmpty()
            if (hasDesc) {
                detailsTitleText.text = title
                detailsChannelText.text = subtitle
                detailsChannelText.setTextColor(accent)
                detailsChannelText.visibility = if (hasChannel) VISIBLE else GONE
                detailsDescriptionText.text = item.description
                detailsScrollView.visibility = if (hasDesc) VISIBLE else GONE
                videoDescriptionButton.visibility = VISIBLE
            } else {
                videoDescriptionButton.visibility = GONE
            }

            // Quality button is always visible in shared video player
            videoQualityButton.visibility = VISIBLE

            // Related videos rail (YouTube)
            if (provider == "youtube" && relatedItems.isNotEmpty()) {
                setRelatedItems(relatedItems, hasMoreRelated)
            } else {
                relatedCardsRow.removeAllViews()
            }
            lowerPanel.bind(
                item?.id,
                related = provider == "youtube" && relatedCardsRow.childCount > 0,
                details = hasDesc,
            )
            applyLowerPanelVisibility()
            resetInactivityTimer()
        }
    }

    private fun applyLowerPanelVisibility() {
        relatedSection.visibility =
            if (lowerPanel.expanded && lowerPanel.relatedAvailable) VISIBLE else GONE
        detailsSection.visibility =
            if (lowerPanel.expanded && lowerPanel.detailsAvailable) VISIBLE else GONE
    }

    fun isControlFocused(view: View?): Boolean = view?.parent === videoControlsRow

    fun isRelatedFocused(view: View?): Boolean = view?.parent === relatedCardsRow

    fun isDetailsFocused(view: View?): Boolean = view === detailsScrollView

    fun isTimelineFocused(view: View?): Boolean = view === videoProgressBar

    /** Returns the semantic focus key after expanding the lower panel, if content exists. */
    fun expandLowerPanelFrom(control: View?): String? {
        if (!lowerPanel.expand()) return null
        returnControlKey =
            if (isControlFocused(control)) control?.tag as? String ?: "player:play_pause"
            else "player:play_pause"
        applyLowerPanelVisibility()
        resetInactivityTimer()
        return if (lowerPanel.relatedAvailable) relatedCardsRow.getChildAt(0)?.tag as? String
        else detailsScrollView.tag as? String
    }

    /** Returns the control that opened the lower panel for deterministic focus restoration. */
    fun collapseLowerPanel(): String? {
        if (!lowerPanel.collapse()) return null
        applyLowerPanelVisibility()
        resetInactivityTimer()
        return returnControlKey
    }

    fun setRelatedItems(items: List<MediaItem>, hasMore: Boolean = false) {
        val existingCount = relatedCardsRow.childCount
        var canAppend = existingCount > 0 && items.size >= existingCount
        if (canAppend) {
            for (i in 0 until existingCount) {
                val card = relatedCardsRow.getChildAt(i)
                if (card.tag != "related:${items[i].id}") {
                    canAppend = false
                    break
                }
            }
        }

        if (canAppend) {
            val newItems = items.drop(existingCount)
            appendRelatedCards(newItems, hasMore)
        } else {
            relatedCardsRow.removeAllViews()
            appendRelatedCards(items, hasMore)
        }
    }

    fun appendRelatedCards(items: List<MediaItem>, hasMore: Boolean = false) {
        val cardWidth = ui.dp(150)
        val thumbHeight = ui.dp(84)

        for (item in items) {
            val card =
                object : LinearLayout(context) {
                        override fun onFocusChanged(
                            gainFocus: Boolean,
                            direction: Int,
                            previouslyFocusedRect: Rect?,
                        ) {
                            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
                            if (gainFocus) {
                                resetInactivityTimer()
                                val total = relatedCardsRow.childCount
                                val currentPos = relatedCardsRow.indexOfChild(this)
                                if (currentPos >= total - 2) actions.loadMoreRelated?.invoke()
                            }
                        }
                    }
                    .apply {
                        orientation = LinearLayout.VERTICAL
                        isFocusable = true
                        isClickable = true
                        setBackgroundDrawable(cardFocusBackground(accentColor))
                        setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(6))
                        tag = "related:${item.id}"
                        setOnClickListener {
                            resetInactivityTimer()
                            actions.playItem(item)
                        }
                        layoutParams =
                            LinearLayout.LayoutParams(cardWidth, -2).apply {
                                rightMargin = ui.dp(10)
                            }
                    }

            val thumbContainer =
                FrameLayout(context).apply { setBackgroundDrawable(ui.box(Color.rgb(18, 24, 26))) }
            val art =
                ArtworkImageView(context, decoder).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
            if (item.imageUrl.isNotEmpty()) {
                art.bind(artwork, item.imageUrl)
            }
            thumbContainer.addView(art, FrameLayout.LayoutParams(-1, -1))
            card.addView(
                thumbContainer,
                LinearLayout.LayoutParams(cardWidth - ui.dp(8), thumbHeight),
            )

            val titleText =
                TextView(context).apply {
                    text = item.title
                    textSize = 11f
                    typeface = TvTypography.regular(context)
                    setTextColor(Color.WHITE)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, ui.dp(4), 0, 0)
                }
            card.addView(titleText)
            relatedCardsRow.addView(card)
        }
    }

    private fun refreshButtonStyles() {
        videoSeekBackButton.setBackgroundDrawable(controlBackground(accentColor))
        videoPrevButton.setBackgroundDrawable(controlBackground(accentColor))
        videoPlayPauseButton.setBackgroundDrawable(controlBackground(accentColor))
        videoNextButton.setBackgroundDrawable(controlBackground(accentColor))
        videoSeekForwardButton.setBackgroundDrawable(controlBackground(accentColor))
        videoDescriptionButton.setBackgroundDrawable(controlBackground(accentColor))
        videoQualityButton.setBackgroundDrawable(controlBackground(accentColor))
        videoAudioTracksButton.setBackgroundDrawable(controlBackground(accentColor))
        videoSubtitlesButton.setBackgroundDrawable(controlBackground(accentColor))
        videoMinimizeButton.setBackgroundDrawable(controlBackground(accentColor))
        videoExternalButton.setBackgroundDrawable(controlBackground(accentColor))
        videoStopButton.setBackgroundDrawable(controlBackground(accentColor))
        detailsScrollView.setBackgroundDrawable(detailsFocusBackground(accentColor))
        detailsChannelText.setTextColor(accentColor)

        audioPrevButton.setBackgroundDrawable(controlBackground(accentColor))
        audioSeekBackButton.setBackgroundDrawable(controlBackground(accentColor))
        audioPlayPauseButton.setBackgroundDrawable(controlBackground(accentColor, circular = true))
        audioSeekForwardButton.setBackgroundDrawable(controlBackground(accentColor))
        audioNextButton.setBackgroundDrawable(controlBackground(accentColor))
        audioAudioTracksButton.setBackgroundDrawable(controlBackground(accentColor))
        audioSubtitlesButton.setBackgroundDrawable(controlBackground(accentColor))
        audioMinimizeButton.setBackgroundDrawable(controlBackground(accentColor))
        audioExternalButton.setBackgroundDrawable(controlBackground(accentColor))
        audioStopButton.setBackgroundDrawable(controlBackground(accentColor))
    }

    fun updateProgress(status: String, positionMs: Int, durationMs: Int): Boolean {
        val wasPlaying = isPlaying
        videoDurationMs = durationMs
        val timelineFocusChanged = updateTimelineAvailability()
        val timeLabel = ui.formatTime(positionMs)
        val durationLabel = if (durationMs > 0) ui.formatTime(durationMs) else ""

        videoTimeText.text = timeLabel
        videoDurationText.text = durationLabel
        videoProgressBar.setProgress(positionMs, durationMs)
        videoProgressBar.contentDescription =
            if (videoProgressBar.isFocusable)
                context.getString(R.string.player_timeline_position, timeLabel, durationLabel)
            else context.getString(R.string.player_timeline_unavailable)

        audioTimeText.text = timeLabel
        audioDurationText.text = durationLabel
        audioProgressBar.setProgress(positionMs, durationMs)

        val realPlaying = status == "PLAYING" || status == "BUFFERING"

        if (status in listOf("ENDED", "STOPPED", "FAILED")) {
            optimisticTargetPlaying = null
            isPlaying = false
            applyPlayPauseVisuals(false)
            removeCallbacks(inactivityRunnable)
            if (!isAudioMode && !isChromeVisible) {
                showChrome()
            }
        } else {
            val opt = optimisticTargetPlaying
            if (opt != null) {
                if (realPlaying == opt) {
                    optimisticTargetPlaying = null
                    isPlaying = realPlaying
                    applyPlayPauseVisuals(isPlaying)
                } else if (System.currentTimeMillis() - optimisticTimestamp > 2500L) {
                    optimisticTargetPlaying = null
                    isPlaying = realPlaying
                    applyPlayPauseVisuals(isPlaying)
                }
            } else {
                isPlaying = realPlaying
                applyPlayPauseVisuals(isPlaying)
            }

            if (status == "PLAYING" && !wasPlaying) {
                resetInactivityTimer()
            } else if (status == "BUFFERING" || status == "PAUSED") {
                removeCallbacks(inactivityRunnable)
                if (!isAudioMode && !isChromeVisible) {
                    showChrome()
                }
            }
        }
        return timelineFocusChanged
    }

    private fun updateTimelineAvailability(): Boolean {
        val enabled = videoSeekable && videoDurationMs > 0
        val changed = videoProgressBar.isFocusable != enabled
        videoProgressBar.isFocusable = enabled
        videoProgressBar.isEnabled = enabled
        return changed
    }

    fun setSeekable(value: Boolean) {
        videoSeekable = value
        updateTimelineAvailability()
        videoSeekBackButton.isEnabled = value
        videoSeekForwardButton.isEnabled = value
        audioSeekBackButton.isEnabled = value
        audioSeekForwardButton.isEnabled = value
    }

    fun setCanNext(value: Boolean) {
        videoNextButton.isEnabled = value
        videoNextButton.visibility = if (value) VISIBLE else GONE
        audioNextButton.isEnabled = value
        audioNextButton.visibility = if (value) VISIBLE else GONE
    }

    fun hasRelatedItems(): Boolean = lowerPanel.relatedAvailable

    fun focusRelated(): Boolean {
        if (!hasRelatedItems()) return false
        lowerPanel.expand()
        applyLowerPanelVisibility()
        relatedCardsRow.getChildAt(0)?.requestFocus()
        return true
    }

    fun hasDetails(): Boolean =
        lowerPanel.detailsAvailable && detailsScrollView.visibility == VISIBLE

    fun focusDetails(): Boolean {
        if (!hasDetails()) return false
        returnControlKey = "player:description"
        lowerPanel.expand()
        applyLowerPanelVisibility()
        detailsScrollView.requestFocus()
        return true
    }

    val relatedCards: ViewGroup
        get() = relatedCardsRow

    val detailsView: ScrollView
        get() = detailsScrollView

    val controlsRow: ViewGroup
        get() = videoControlsRow

    /**
     * Exposes focusable view groups to [io.github.diegog0477.zombiebox.client.core.ui.RemoteFocus].
     */
    fun focusRows(): List<Pair<String, ViewGroup>> =
        if (isAudioMode) {
            listOf(
                Pair("player_primary", audioPrimaryControlsRow),
                Pair("player_secondary", audioSecondaryControlsRow),
            )
        } else {
            val list = ArrayList<Pair<String, ViewGroup>>()
            if (videoProgressBar.isFocusable) list.add(Pair("player_timeline", videoTimelineRow))
            list.add(Pair("player_controls", videoControlsRow))
            if (relatedSection.visibility == VISIBLE && relatedCardsRow.childCount > 0) {
                list.add(Pair("player_related", relatedCardsRow))
            }
            if (detailsSection.visibility == VISIBLE && detailsScrollView.visibility == VISIBLE) {
                list.add(Pair("player_details", detailsSection))
            }
            list
        }

    fun primaryControl(): View = if (isAudioMode) audioPlayPauseButton else videoPlayPauseButton

    private fun mixColors(base: Int, overlay: Int, fraction: Float): Int =
        Color.rgb(
            (Color.red(base) * (1f - fraction) + Color.red(overlay) * fraction).toInt(),
            (Color.green(base) * (1f - fraction) + Color.green(overlay) * fraction).toInt(),
            (Color.blue(base) * (1f - fraction) + Color.blue(overlay) * fraction).toInt(),
        )
}
