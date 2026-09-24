package io.github.diegog0477.zombiebox.client.features.playback.presentation.ui

import android.content.Context
import android.graphics.Color
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
    val audioTracks: () -> Unit,
    val subtitles: () -> Unit,
    val minimize: () -> Unit,
    val external: () -> Unit,
    val stop: () -> Unit,
    val playItem: (MediaItem) -> Unit,
)

/**
 * Modern TV player chrome for Zombie Box TV.
 *
 * Provides:
 * - Video mode: unobtrusive gradient overlay over video surface with title, timeline, universal
 *   symbol transport controls, and YouTube related rail when available.
 * - Audio mode (Spotify/Music): dedicated fullscreen UI featuring large album artwork, metadata,
 *   and prominent transport controls without a black video rectangle.
 * - API 9+ compatibility using Barlow typography, provider accents, and explicit D-pad focus.
 */
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

    // Video overlay views
    private val videoOverlay: FrameLayout
    private val videoTitleText: TextView
    private val videoSubtitleText: TextView
    private val videoTimeText: TextView
    private val videoDurationText: TextView
    private val videoProgressBar: TvPlaybackProgressBar
    val videoControlsRow: LinearLayout
    val videoSeekBackButton: ImageButton
    val videoPrevButton: ImageButton
    val videoPlayPauseButton: ImageButton
    val videoNextButton: ImageButton
    val videoSeekForwardButton: ImageButton
    val videoAudioTracksButton: ImageButton
    val videoSubtitlesButton: ImageButton
    val videoMinimizeButton: ImageButton
    val videoExternalButton: ImageButton
    val videoStopButton: ImageButton
    private val relatedSection: LinearLayout
    private val relatedCardsRow: LinearLayout

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
        videoOverlay = FrameLayout(context)
        addView(videoOverlay, LayoutParams(-1, -1))

        // Top scrim & info
        val topScrim =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundDrawable(
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(Color.argb(200, 0, 0, 0), Color.TRANSPARENT),
                    )
                )
                setPadding(ui.dp(32), ui.dp(24), ui.dp(32), ui.dp(40))
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
        val bottomScrim =
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
            }

        // Timeline Row
        val timelineRow =
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
        timelineRow.addView(videoTimeText, LinearLayout.LayoutParams(-2, -2))

        videoProgressBar =
            TvPlaybackProgressBar(context).apply {
                accentColor = this@TvPlayerChromeView.accentColor
            }
        timelineRow.addView(
            videoProgressBar,
            LinearLayout.LayoutParams(0, ui.dp(4), 1f).apply {
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
        timelineRow.addView(videoDurationText, LinearLayout.LayoutParams(-2, -2))
        bottomScrim.addView(timelineRow)

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
                actions.seekBack()
            }
        videoControlsRow.addView(videoSeekBackButton)

        videoPrevButton =
            createButton(TvPlayerSymbol.PREVIOUS, R.string.music_previous, "player:prev") {
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
                actions.playPause()
            }
        videoControlsRow.addView(videoPlayPauseButton)

        videoNextButton =
            createButton(TvPlayerSymbol.NEXT, R.string.next_item, "player:next") { actions.next() }
        videoControlsRow.addView(videoNextButton)

        videoSeekForwardButton =
            createButton(
                TvPlayerSymbol.SEEK_FORWARD,
                R.string.seek_forward,
                "player:seek_forward",
            ) {
                actions.seekForward()
            }
        videoControlsRow.addView(videoSeekForwardButton)

        videoAudioTracksButton =
            createButton(
                TvPlayerSymbol.AUDIO_TRACKS,
                R.string.audio_tracks,
                "player:audio_tracks",
            ) {
                actions.audioTracks()
            }
        videoControlsRow.addView(videoAudioTracksButton)

        videoSubtitlesButton =
            createButton(TvPlayerSymbol.SUBTITLES, R.string.subtitles, "player:subtitles") {
                actions.subtitles()
            }
        videoControlsRow.addView(videoSubtitlesButton)

        videoMinimizeButton =
            createButton(TvPlayerSymbol.MINIMIZE, R.string.minimize, "player:minimize") {
                actions.minimize()
            }
        videoControlsRow.addView(videoMinimizeButton)

        videoExternalButton =
            createButton(TvPlayerSymbol.EXTERNAL, R.string.external_player, "player:external") {
                actions.external()
            }
        videoControlsRow.addView(videoExternalButton)

        videoStopButton =
            createButton(TvPlayerSymbol.STOP, R.string.stop, "player:stop") { actions.stop() }
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
                actions.playPause()
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

    /** Binds active session information and switches between video and audio chrome. */
    fun bindSession(
        item: MediaItem?,
        isAudio: Boolean,
        accent: Int,
        canPrevious: Boolean,
        canNext: Boolean,
        seekable: Boolean,
        relatedItems: List<MediaItem>,
    ) {
        currentItem = item
        isAudioMode = isAudio
        accentColor = accent

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

            videoTitleText.text = title
            videoSubtitleText.text = subtitle
            videoSubtitleText.visibility = if (subtitle.isEmpty()) GONE else VISIBLE

            videoPrevButton.visibility = if (canPrevious) VISIBLE else GONE
            videoNextButton.visibility = if (canNext) VISIBLE else GONE
            videoNextButton.isEnabled = canNext
            videoSeekBackButton.isEnabled = seekable
            videoSeekForwardButton.isEnabled = seekable

            // Related videos rail (YouTube)
            if (provider == "youtube" && relatedItems.isNotEmpty()) {
                populateRelatedRail(relatedItems)
                relatedSection.visibility = VISIBLE
            } else {
                relatedSection.visibility = GONE
                relatedCardsRow.removeAllViews()
            }
        }
    }

    private fun populateRelatedRail(items: List<MediaItem>) {
        relatedCardsRow.removeAllViews()
        val cardWidth = ui.dp(150)
        val thumbHeight = ui.dp(84)

        for (item in items) {
            val card =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    isFocusable = true
                    isClickable = true
                    setBackgroundDrawable(cardFocusBackground(accentColor))
                    setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(6))
                    tag = "related:${item.id}"
                    setOnClickListener { actions.playItem(item) }
                    layoutParams =
                        LinearLayout.LayoutParams(cardWidth, -2).apply { rightMargin = ui.dp(10) }
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
        videoAudioTracksButton.setBackgroundDrawable(controlBackground(accentColor))
        videoSubtitlesButton.setBackgroundDrawable(controlBackground(accentColor))
        videoMinimizeButton.setBackgroundDrawable(controlBackground(accentColor))
        videoExternalButton.setBackgroundDrawable(controlBackground(accentColor))
        videoStopButton.setBackgroundDrawable(controlBackground(accentColor))

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

    fun updateProgress(status: String, positionMs: Int, durationMs: Int) {
        isPlaying = status == "PLAYING"
        val timeLabel = ui.formatTime(positionMs)
        val durationLabel = if (durationMs > 0) ui.formatTime(durationMs) else ""

        videoTimeText.text = timeLabel
        videoDurationText.text = durationLabel
        videoProgressBar.setProgress(positionMs, durationMs)

        audioTimeText.text = timeLabel
        audioDurationText.text = durationLabel
        audioProgressBar.setProgress(positionMs, durationMs)

        val symbol = if (isPlaying) TvPlayerSymbol.PAUSE else TvPlayerSymbol.PLAY
        (videoPlayPauseButton.drawable as? TvPlayerSymbolDrawable)?.symbol = symbol
        (audioPlayPauseButton.drawable as? TvPlayerSymbolDrawable)?.symbol = symbol

        val descRes = if (isPlaying) R.string.paused else R.string.play
        videoPlayPauseButton.contentDescription = context.getString(descRes)
        audioPlayPauseButton.contentDescription = context.getString(descRes)
    }

    fun setSeekable(value: Boolean) {
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
            list.add(Pair("player_controls", videoControlsRow))
            if (relatedSection.visibility == VISIBLE && relatedCardsRow.childCount > 0) {
                list.add(Pair("player_related", relatedCardsRow))
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
