package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.artwork.platform.ArtworkDecoder
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.ui.ArtworkImageView
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel.ArtworkViewModel

/** Semantic model describing the active playback session for Home transport presentation. */
data class HomePlaybackSession(
    val active: Boolean = false,
    val item: MediaItem? = null,
    val state: String = "",
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val canNext: Boolean = false,
    val canPrevious: Boolean = false,
)

/** Callbacks for transport actions. */
data class TransportActions(
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val expand: () -> Unit,
)

/** Lightweight progress bar drawing current position fraction with contextual accent. */
class PlaybackProgressBar(context: Context, private val ui: TvWidgets) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progressFraction = 0f
    var accentColor: Int = ui.green

    init {
        layoutParams =
            LinearLayout.LayoutParams(-1, ui.dp(4)).apply {
                topMargin = ui.dp(4)
                bottomMargin = ui.dp(10)
            }
        isFocusable = false
    }

    fun setProgress(positionMs: Int, durationMs: Int) {
        progressFraction =
            if (durationMs > 0) {
                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            } else 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = Color.rgb(42, 53, 56)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        if (progressFraction > 0f) {
            paint.color = accentColor
            canvas.drawRect(0f, 0f, width * progressFraction, height.toFloat(), paint)
        }
    }
}

/**
 * Docked landscape Now Playing rail.
 *
 * Appears only for a real active playback session. Displays actual artwork, title, provider badge,
 * status/timing, progress, and accessible universal symbol controls with explicit focus outlines.
 */
class NowPlayingRailView(
    context: Context,
    private val ui: TvWidgets,
    private val artwork: ArtworkViewModel,
    private val decoder: ArtworkDecoder,
    private val actions: TransportActions,
) : LinearLayout(context) {
    private var accentColor = ui.green
    private val providerIcon: ServiceMarkView
    private val providerTitle: TextView
    private val artContainer: FrameLayout
    private val artView: ArtworkImageView
    private val artFallback: ServiceMarkView
    private val titleText: TextView
    private val subtitleText: TextView
    private val timingText: TextView
    private val progressBar: PlaybackProgressBar
    private val controlsRow: LinearLayout

    val prevButton: ImageButton
    val playPauseButton: ImageButton
    val nextButton: ImageButton
    val expandButton: ImageButton

    init {
        orientation = VERTICAL
        setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
        setBackgroundDrawable(ui.box(ui.background, ui.panel))

        // Header: Provider identity
        val header = ui.row()
        providerIcon = ServiceMarkView(context, "", accentColor)
        header.addView(
            providerIcon,
            LayoutParams(ui.dp(24), ui.dp(24)).apply { rightMargin = ui.dp(8) },
        )
        providerTitle =
            ui.text("", 14f, accentColor).apply {
                typeface = ui.bold
                setSingleLine(true)
            }
        header.addView(providerTitle, LayoutParams(0, -2, 1f))
        addView(header)

        // Artwork container (16:9 box with dark background)
        artContainer =
            FrameLayout(context).apply {
                setBackgroundDrawable(ui.box(Color.rgb(16, 22, 24), ui.panel))
            }
        artView =
            ArtworkImageView(context, decoder).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        artContainer.addView(artView, FrameLayout.LayoutParams(-1, -1))
        artFallback = ServiceMarkView(context, "", accentColor).apply { visibility = GONE }
        artContainer.addView(
            artFallback,
            FrameLayout.LayoutParams(ui.dp(54), ui.dp(54), Gravity.CENTER),
        )
        addView(
            artContainer,
            LayoutParams(-1, ui.dp(136)).apply {
                topMargin = ui.dp(10)
                bottomMargin = ui.dp(8)
            },
        )

        // Metadata
        titleText =
            ui.text("", 16f, Color.WHITE).apply {
                typeface = ui.bold
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, ui.dp(2), 0, ui.dp(2))
            }
        addView(titleText)

        subtitleText =
            ui.text("", 12f, ui.muted).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 0, 0, ui.dp(2))
            }
        addView(subtitleText)

        // Timing & Progress
        timingText = ui.text("", 12f, ui.muted).apply { setPadding(0, ui.dp(2), 0, 0) }
        addView(timingText)

        progressBar = PlaybackProgressBar(context, ui)
        addView(progressBar)

        // Universal symbol controls
        controlsRow = ui.row().apply { gravity = Gravity.CENTER }

        prevButton =
            createControlButton(
                PlaybackSymbol.PREVIOUS,
                R.string.music_previous,
                "transport:prev",
            ) {
                actions.previous()
            }
        controlsRow.addView(prevButton)

        playPauseButton =
            createControlButton(PlaybackSymbol.PLAY, R.string.play_pause, "transport:play") {
                actions.playPause()
            }
        controlsRow.addView(playPauseButton)

        nextButton =
            createControlButton(PlaybackSymbol.NEXT, R.string.next_item, "transport:next") {
                actions.next()
            }
        controlsRow.addView(nextButton)

        expandButton =
            createControlButton(PlaybackSymbol.EXPAND, R.string.expand, "transport:expand") {
                actions.expand()
            }
        controlsRow.addView(expandButton)

        addView(controlsRow, LayoutParams(-1, -2).apply { topMargin = ui.dp(4) })
        isFocusable = false
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
            setBackgroundDrawable(controlFocusBackground(accentColor))
            setOnClickListener { click() }
            layoutParams =
                LayoutParams(ui.dp(44), ui.dp(44)).apply {
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

    fun update(session: HomePlaybackSession) {
        val item = session.item
        val provider = item?.provider ?: ""
        accentColor = ui.providerAccent(provider)
        providerIcon.bind(provider, accentColor)
        artFallback.bind(provider, accentColor)

        providerTitle.text = ui.serviceTitle(provider)
        providerTitle.setTextColor(accentColor)

        val title =
            item?.title?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.screen_mirroring)
        titleText.text = title

        subtitleText.text = item?.subtitle ?: ""
        subtitleText.visibility = if (item?.subtitle.isNullOrEmpty()) GONE else VISIBLE

        updateProgress(session.state, session.positionMs, session.durationMs)

        prevButton.visibility = if (session.canPrevious) VISIBLE else GONE
        nextButton.visibility = if (session.canNext) VISIBLE else GONE

        prevButton.setBackgroundDrawable(controlFocusBackground(accentColor))
        playPauseButton.setBackgroundDrawable(controlFocusBackground(accentColor))
        nextButton.setBackgroundDrawable(controlFocusBackground(accentColor))
        expandButton.setBackgroundDrawable(controlFocusBackground(accentColor))

        if (!item?.imageUrl.isNullOrEmpty()) {
            artView.visibility = VISIBLE
            artFallback.visibility = GONE
            artView.bind(artwork, item?.imageUrl ?: "")
        } else {
            artView.visibility = GONE
            artFallback.visibility = VISIBLE
        }
    }

    fun updateProgress(status: String, positionMs: Int, durationMs: Int) {
        val stateLabel = ui.localizedState(status)
        val timeLabel =
            if (durationMs > 0) "${ui.formatTime(positionMs)} / ${ui.formatTime(durationMs)}"
            else ui.formatTime(positionMs)
        timingText.text = "$stateLabel · $timeLabel"

        progressBar.accentColor = accentColor
        progressBar.setProgress(positionMs, durationMs)

        val isPlaying = status == "PLAYING"
        (playPauseButton.drawable as? PlaybackSymbolDrawable)?.symbol =
            if (isPlaying) PlaybackSymbol.PAUSE else PlaybackSymbol.PLAY
        playPauseButton.contentDescription =
            context.getString(if (isPlaying) R.string.paused else R.string.play)
    }

    fun controls(): List<View> =
        listOf(prevButton, playPauseButton, nextButton, expandButton).filter {
            it.visibility == VISIBLE && it.isEnabled
        }

    fun primaryControl(): View = playPauseButton

    fun videoDockRect(relativeTo: View): Rect? {
        if (artContainer.width <= 0 || artContainer.height <= 0) return null
        val target = IntArray(2)
        val parent = IntArray(2)
        artContainer.getLocationOnScreen(target)
        relativeTo.getLocationOnScreen(parent)
        return Rect(
            target[0] - parent[0],
            target[1] - parent[1],
            target[0] - parent[0] + artContainer.width,
            target[1] - parent[1] + artContainer.height,
        )
    }

    fun hasFocusWithin(): Boolean = controls().any { it.isFocused || it.hasFocus() }

    fun moveFocus(keyCode: Int, current: View?): View? {
        val list = controls()
        if (list.isEmpty()) return null
        val index = list.indexOf(current)
        if (index < 0) return primaryControl()
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (index < list.lastIndex) list[index + 1] else null
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (index > 0) list[index - 1] else null
            }
            else -> null
        }
    }
}

/**
 * Compact transport bar for portable / handheld / portrait layout.
 *
 * Appears only when a real active playback session exists.
 */
class CompactTransportBar(
    context: Context,
    private val ui: TvWidgets,
    private val artwork: ArtworkViewModel,
    private val decoder: ArtworkDecoder,
    private val actions: TransportActions,
) : LinearLayout(context) {
    private var accentColor = ui.green
    private val artView: ArtworkImageView
    private val titleView: TextView
    private val statusView: TextView
    val playPauseButton: ImageButton
    val expandButton: ImageButton

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(6))
        setBackgroundColor(ui.panel)

        artView =
            ArtworkImageView(context, decoder).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        addView(artView, LayoutParams(ui.dp(40), ui.dp(40)).apply { rightMargin = ui.dp(10) })

        val meta = ui.column()
        titleView =
            ui.text("", 14f, Color.WHITE).apply {
                typeface = ui.bold
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 0, 0, 0)
            }
        meta.addView(titleView)

        statusView =
            ui.text("", 11f, ui.muted).apply {
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 0, 0, 0)
            }
        meta.addView(statusView)

        addView(meta, LayoutParams(0, -2, 1f))

        playPauseButton =
            ImageButton(context).apply {
                tag = "compact:play"
                contentDescription = context.getString(R.string.play_pause)
                setImageDrawable(PlaybackSymbolDrawable(PlaybackSymbol.PLAY, Color.WHITE))
                isFocusable = true
                isClickable = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
                setBackgroundDrawable(ui.focusBackground(accentColor))
                setOnClickListener { actions.playPause() }
                layoutParams =
                    LayoutParams(ui.dp(40), ui.dp(40)).apply {
                        setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                    }
            }
        addView(playPauseButton)

        expandButton =
            ImageButton(context).apply {
                tag = "compact:expand"
                contentDescription = context.getString(R.string.expand)
                setImageDrawable(PlaybackSymbolDrawable(PlaybackSymbol.EXPAND, Color.WHITE))
                isFocusable = true
                isClickable = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
                setBackgroundDrawable(ui.focusBackground(accentColor))
                setOnClickListener { actions.expand() }
                layoutParams =
                    LayoutParams(ui.dp(40), ui.dp(40)).apply {
                        setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                    }
            }
        addView(expandButton)
    }

    fun controlsContainer(): ViewGroup = this

    fun update(session: HomePlaybackSession) {
        val item = session.item
        val provider = item?.provider ?: ""
        accentColor = ui.providerAccent(provider)

        titleView.text =
            item?.title?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.screen_mirroring)

        updateProgress(session.state, session.positionMs, session.durationMs)

        playPauseButton.setBackgroundDrawable(ui.focusBackground(accentColor))
        expandButton.setBackgroundDrawable(ui.focusBackground(accentColor))

        if (!item?.imageUrl.isNullOrEmpty()) {
            artView.visibility = VISIBLE
            artView.bind(artwork, item?.imageUrl ?: "")
        } else {
            artView.visibility = GONE
        }
    }

    fun updateProgress(status: String, positionMs: Int, durationMs: Int) {
        val stateLabel = ui.localizedState(status)
        val timeLabel =
            if (durationMs > 0) "${ui.formatTime(positionMs)} / ${ui.formatTime(durationMs)}"
            else ui.formatTime(positionMs)
        statusView.text = "$stateLabel · $timeLabel"

        val isPlaying = status == "PLAYING"
        (playPauseButton.drawable as? PlaybackSymbolDrawable)?.symbol =
            if (isPlaying) PlaybackSymbol.PAUSE else PlaybackSymbol.PLAY
        playPauseButton.contentDescription =
            context.getString(if (isPlaying) R.string.paused else R.string.play)
    }
}
