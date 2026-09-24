package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.viewmodel.GuideViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * TV-native electronic programme grid per resources/ui-rules/references.md. Features:
 * - Upper band with large selected programme details, progress bar, metadata, and controls
 * - Compact sticky time ruler
 * - Aligned channel rows and programme blocks with prominent light selected block
 * - Left channel column with active row highlight
 * - Deterministic D-pad navigation and time window shifting
 * - Bounded view hierarchy for legacy RAM with in-place rebind on tick/reload
 * - Legible portrait fallback
 */
@Suppress("DEPRECATION")
class GuideGridView(
    private val activity: Activity,
    private var channels: List<MediaItem>,
    private var model: GuideViewModel,
    private val onPlay: (MediaItem) -> Unit,
    private val onShiftWindow: (Long) -> Unit,
    private val onPreviousPage: (() -> Unit)?,
    private val onNextPage: (() -> Unit)?,
    private val onClose: () -> Unit,
) : LinearLayout(activity) {

    private val isPortrait =
        activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    private val numSlots = if (isPortrait) 3 else 4
    private val channelColWidth = GuideTheme.dp(activity, if (isPortrait) 85 else 115)
    private val slotDuration = 1800L
    private val guideStart = model.time
    private val earliestSlot = (guideStart / slotDuration) * slotDuration
    private val latestSlot =
        ((guideStart + 48 * 3600 - numSlots * slotDuration) / slotDuration).coerceAtLeast(
            earliestSlot / slotDuration
        ) * slotDuration
    private val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

    var windowStartTime: Long = ((model.time / slotDuration) * slotDuration)
        private set

    var focusedRow: Int = 0
        private set

    var focusedCol: Int = 1 // 0 = channel badge, 1..numSlots = time slots
        private set

    var selectedChannelId: String = ""
        private set

    // Upper band views
    private val clockText: TextView
    private val channelTag: TextView
    private val staleBadge: TextView
    private val pageStatus: TextView
    private val programmeTitle: TextView
    private val progressContainer: LinearLayout
    private val progressBar: GuideProgressBar
    private val progressTimeText: TextView
    private val timeSpanText: TextView
    private val metaText: TextView
    private val descriptionText: TextView

    // Action buttons in upper band
    private val earlierBtn: Button
    private val laterBtn: Button
    private val prevPageBtn: Button?
    private val nextPageBtn: Button?
    private val closeBtn: Button
    val pagingButtons = ArrayList<View>()

    // Ruler
    private val rulerSlots = ArrayList<TextView>()

    // Channel rows
    private val gridScrollView: ScrollView
    private val rowsContainer: LinearLayout
    private val rowHolders = ArrayList<RowViewHolder>()

    // Empty state
    private val emptyView: TextView

    class GuideProgressBar(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var progress: Float = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            paint.color = Color.rgb(48, 58, 62)
            canvas.drawRect(0f, 0f, w, h, paint)
            if (progress > 0f) {
                paint.color = GuideTheme.accentProgress
                canvas.drawRect(0f, 0f, w * progress, h, paint)
            }
        }
    }

    private class RowViewHolder(
        val container: LinearLayout,
        val badge: TextView,
        val slots: Array<TextView>,
    )

    init {
        orientation = VERTICAL
        setBackgroundColor(GuideTheme.bgDark)
        val padH = GuideTheme.dp(activity, if (isPortrait) 12 else 20)
        val padV = GuideTheme.dp(activity, if (isPortrait) 10 else 14)
        setPadding(padH, padV, padH, padV)

        // ------------------ 1. Upper Band ------------------
        val upperBand =
            LinearLayout(activity).apply {
                orientation = if (isPortrait) VERTICAL else HORIZONTAL
                setPadding(0, 0, 0, GuideTheme.dp(activity, 8))
            }

        val detailsLayout =
            LinearLayout(activity).apply {
                orientation = VERTICAL
                if (!isPortrait) {
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                }
            }

        // Row 1: Clock, Channel Tag, Stale Badge, Status
        val statusRow =
            LinearLayout(activity).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

        clockText =
            TextView(activity).apply {
                textSize = 13f
                typeface = TvTypography.semibold(activity)
                setTextColor(GuideTheme.textMuted)
            }
        statusRow.addView(clockText)

        channelTag =
            TextView(activity).apply {
                textSize = 12f
                typeface = TvTypography.semibold(activity)
                setTextColor(Color.WHITE)
                setPadding(
                    GuideTheme.dp(activity, 8),
                    GuideTheme.dp(activity, 2),
                    GuideTheme.dp(activity, 8),
                    GuideTheme.dp(activity, 2),
                )
                setBackgroundDrawable(
                    GuideTheme.roundedBox(
                        GuideTheme.dp(activity, 4).toFloat(),
                        GuideTheme.badgeActiveRowBg,
                        GuideTheme.dp(activity, 1),
                        GuideTheme.accentIptv,
                    )
                )
                val lp =
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        setMargins(GuideTheme.dp(activity, 10), 0, 0, 0)
                    }
                layoutParams = lp
                visibility = GONE
            }
        statusRow.addView(channelTag)

        staleBadge =
            TextView(activity).apply {
                textSize = 12f
                typeface = TvTypography.semibold(activity)
                setTextColor(GuideTheme.accentStale)
                text = activity.getString(R.string.guide_stale)
                setPadding(
                    GuideTheme.dp(activity, 8),
                    GuideTheme.dp(activity, 2),
                    GuideTheme.dp(activity, 8),
                    GuideTheme.dp(activity, 2),
                )
                val lp =
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        setMargins(GuideTheme.dp(activity, 8), 0, 0, 0)
                    }
                layoutParams = lp
                visibility = GONE
            }
        statusRow.addView(staleBadge)

        pageStatus =
            TextView(activity).apply {
                textSize = 12f
                typeface = TvTypography.regular(activity)
                setTextColor(GuideTheme.textMuted)
                val lp =
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        setMargins(GuideTheme.dp(activity, 8), 0, 0, 0)
                    }
                layoutParams = lp
            }
        statusRow.addView(pageStatus)

        detailsLayout.addView(statusRow)

        // Row 2: Large Title
        programmeTitle =
            TextView(activity).apply {
                textSize = if (isPortrait) 20f else 26f
                typeface = TvTypography.semibold(activity)
                setTextColor(Color.WHITE)
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, GuideTheme.dp(activity, 3), 0, GuideTheme.dp(activity, 2))
            }
        detailsLayout.addView(programmeTitle)

        // Row 3: Progress Bar & Duration
        progressContainer =
            LinearLayout(activity).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, GuideTheme.dp(activity, 2), 0, GuideTheme.dp(activity, 2))
            }
        progressBar =
            GuideProgressBar(activity).apply {
                layoutParams =
                    LayoutParams(GuideTheme.dp(activity, 120), GuideTheme.dp(activity, 4)).apply {
                        setMargins(0, 0, GuideTheme.dp(activity, 8), 0)
                    }
            }
        progressContainer.addView(progressBar)

        progressTimeText =
            TextView(activity).apply {
                textSize = 13f
                typeface = TvTypography.semibold(activity)
                setTextColor(GuideTheme.accentProgress)
                setPadding(0, 0, GuideTheme.dp(activity, 8), 0)
            }
        progressContainer.addView(progressTimeText)

        timeSpanText =
            TextView(activity).apply {
                textSize = 13f
                typeface = TvTypography.regular(activity)
                setTextColor(GuideTheme.textMuted)
            }
        progressContainer.addView(timeSpanText)

        detailsLayout.addView(progressContainer)

        // Row 4: Category & Subtitle
        metaText =
            TextView(activity).apply {
                textSize = 13f
                typeface = TvTypography.regular(activity)
                setTextColor(GuideTheme.textSubtle)
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
            }
        detailsLayout.addView(metaText)

        // Row 5: Description
        descriptionText =
            TextView(activity).apply {
                textSize = 13f
                typeface = TvTypography.regular(activity)
                setTextColor(GuideTheme.textMuted)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, GuideTheme.dp(activity, 2), 0, 0)
            }
        detailsLayout.addView(descriptionText)

        upperBand.addView(detailsLayout)

        // Right side: Action controls
        val controlsLayout =
            LinearLayout(activity).apply {
                orientation = VERTICAL
                gravity = Gravity.RIGHT or Gravity.TOP
                if (isPortrait) {
                    setPadding(0, GuideTheme.dp(activity, 6), 0, 0)
                } else {
                    setPadding(GuideTheme.dp(activity, 16), 0, 0, 0)
                }
            }

        val buttonsRow =
            LinearLayout(activity).apply {
                orientation = HORIZONTAL
                gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            }

        earlierBtn = createButton(R.string.guide_earlier) { shiftWindow(-slotDuration) }
        buttonsRow.addView(earlierBtn)

        laterBtn = createButton(R.string.guide_later) { shiftWindow(slotDuration) }
        buttonsRow.addView(laterBtn)

        if (onPreviousPage != null) {
            val prev = createButton(R.string.guide_previous_channels) { onPreviousPage.invoke() }
            pagingButtons.add(prev)
            buttonsRow.addView(prev)
            prevPageBtn = prev
        } else {
            prevPageBtn = null
        }

        if (onNextPage != null) {
            val next = createButton(R.string.guide_next_channels) { onNextPage.invoke() }
            pagingButtons.add(next)
            buttonsRow.addView(next)
            nextPageBtn = next
        } else {
            nextPageBtn = null
        }

        closeBtn = createButton(R.string.close) { onClose.invoke() }
        buttonsRow.addView(closeBtn)

        controlsLayout.addView(buttonsRow)

        val dpadHint =
            TextView(activity).apply {
                text = activity.getString(R.string.guide_help)
                textSize = 11f
                typeface = TvTypography.regular(activity)
                setTextColor(GuideTheme.textSubtle)
                gravity = Gravity.RIGHT
                setPadding(0, GuideTheme.dp(activity, 4), 0, 0)
            }
        controlsLayout.addView(dpadHint)

        upperBand.addView(controlsLayout)
        addView(upperBand)

        // ------------------ 2. Time Ruler ------------------
        val rulerContainer =
            LinearLayout(activity).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val h = GuideTheme.dp(activity, 32)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, h)
                setBackgroundDrawable(
                    GuideTheme.roundedBox(
                        GuideTheme.dp(activity, 4).toFloat(),
                        GuideTheme.rulerBg,
                        GuideTheme.dp(activity, 1),
                        GuideTheme.rulerBorder,
                    )
                )
            }

        val rulerChannelHeader =
            TextView(activity).apply {
                layoutParams = LayoutParams(channelColWidth, LayoutParams.MATCH_PARENT)
                gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                setPadding(GuideTheme.dp(activity, 10), 0, 0, 0)
                text = activity.getString(R.string.guide).uppercase(Locale.getDefault())
                textSize = 11f
                typeface = TvTypography.semibold(activity)
                setTextColor(GuideTheme.textSubtle)
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
            }
        rulerContainer.addView(rulerChannelHeader)

        for (s in 0 until numSlots) {
            val slotHeader =
                TextView(activity).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                    gravity = Gravity.CENTER
                    textSize = 12f
                    typeface = TvTypography.semibold(activity)
                    setTextColor(Color.rgb(200, 212, 218))
                    setSingleLine(true)
                }
            rulerSlots.add(slotHeader)
            rulerContainer.addView(slotHeader)
        }
        addView(rulerContainer)

        // ------------------ 3. Channel Rows ------------------
        emptyView =
            TextView(activity).apply {
                text = activity.getString(R.string.catalog_empty)
                textSize = 14f
                typeface = TvTypography.regular(activity)
                setTextColor(GuideTheme.textMuted)
                gravity = Gravity.CENTER
                setPadding(0, GuideTheme.dp(activity, 30), 0, GuideTheme.dp(activity, 30))
                visibility = GONE
            }
        addView(emptyView)

        rowsContainer =
            LinearLayout(activity).apply {
                orientation = VERTICAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                setPadding(0, GuideTheme.dp(activity, 4), 0, 0)
            }

        gridScrollView =
            ScrollView(activity).apply {
                isVerticalScrollBarEnabled = false
                addView(rowsContainer)
            }
        addView(gridScrollView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // Wire down navigation from action buttons
        setupHeaderButtonsNavigation()

        // Initial setup
        updateTimeRuler()
        updateClockAndProgress()
        bindAllRows()
    }

    private fun createButton(labelRes: Int, click: () -> Unit): Button =
        Button(activity).apply {
            text = activity.getString(labelRes)
            textSize = 12f
            typeface = TvTypography.semibold(activity)
            setTextColor(Color.WHITE)
            isFocusable = true
            val padH = GuideTheme.dp(activity, 10)
            val padV = GuideTheme.dp(activity, 6)
            setPadding(padH, padV, padH, padV)
            setBackgroundDrawable(GuideTheme.buttonBackground(activity))
            val lp =
                LayoutParams(LayoutParams.WRAP_CONTENT, GuideTheme.dp(activity, 36)).apply {
                    setMargins(GuideTheme.dp(activity, 2), 0, GuideTheme.dp(activity, 2), 0)
                }
            layoutParams = lp
            setOnClickListener { click() }
        }

    private fun setupHeaderButtonsNavigation() {
        val buttons = listOfNotNull(earlierBtn, laterBtn, prevPageBtn, nextPageBtn, closeBtn)
        for (i in buttons.indices) {
            val btn = buttons[i]
            btn.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusCell(0, focusedCol.coerceIn(0, numSlots))
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (i > 0) {
                            buttons[i - 1].requestFocus()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (i < buttons.size - 1) {
                            buttons[i + 1].requestFocus()
                            true
                        } else false
                    }
                    else -> false
                }
            }
        }
    }

    fun updateClockAndProgress() {
        val now = System.currentTimeMillis()
        val timeStr = timeFormatter.format(Date(now))
        val tz = TimeZone.getDefault()
        val tzName = tz.getDisplayName(tz.inDaylightTime(Date(now)), TimeZone.SHORT)
        clockText.text = "$timeStr $tzName"

        if (channels.any { it.guideState == "STALE" }) {
            staleBadge.visibility = VISIBLE
        } else {
            staleBadge.visibility = GONE
        }

        updateUpperBand(focusedRow, focusedCol)
    }

    private fun updateTimeRuler() {
        for (s in 0 until numSlots) {
            val slotTime = windowStartTime + s * slotDuration
            val label = timeFormatter.format(Date(slotTime * 1000L))
            rulerSlots[s].text = label
        }
    }

    fun shiftWindow(delta: Long) {
        model.shift(delta)
        windowStartTime =
            ((model.time / slotDuration) * slotDuration).coerceIn(earliestSlot, latestSlot)
        onShiftWindow(delta)
        updateTimeRuler()
        bindAllRows()
        focusCell(focusedRow, focusedCol)
    }

    fun setPageStatus(status: String) {
        pageStatus.text = status
    }

    fun enablePagingButtons(enabled: Boolean) {
        pagingButtons.forEach { it.isEnabled = enabled }
    }

    fun updateChannels(
        fresh: List<MediaItem>,
        refreshedModel: GuideViewModel,
        targetSelectedId: String,
    ) {
        channels = fresh
        model = refreshedModel
        bindAllRows()
        val targetRow = channels.indexOfFirst { it.id == targetSelectedId }.takeIf { it >= 0 } ?: 0
        focusCell(targetRow, focusedCol)
    }

    fun restoreState(savedSelectedChannel: String, savedGuideTime: Long) {
        if (channels.isEmpty()) {
            earlierBtn.requestFocus()
            return
        }
        if (savedGuideTime > 0) {
            model.restore(savedGuideTime)
            windowStartTime =
                ((model.time / slotDuration) * slotDuration).coerceIn(earliestSlot, latestSlot)
            updateTimeRuler()
        }
        bindAllRows()

        val targetRow =
            if (savedSelectedChannel.isNotEmpty()) {
                channels.indexOfFirst { it.id == savedSelectedChannel }.takeIf { it >= 0 } ?: 0
            } else 0

        val targetCol =
            if (savedGuideTime > 0) {
                val offsetSlots = ((savedGuideTime - windowStartTime) / slotDuration).toInt()
                (offsetSlots + 1).coerceIn(1, numSlots)
            } else 1

        focusCell(targetRow, targetCol)
    }

    private fun bindAllRows() {
        if (channels.isEmpty()) {
            emptyView.visibility = VISIBLE
            rowsContainer.visibility = GONE
            updateUpperBand(0, 0)
            return
        }
        emptyView.visibility = GONE
        rowsContainer.visibility = VISIBLE

        // Ensure enough row holders
        while (rowHolders.size < channels.size) {
            val r = rowHolders.size
            val holder = createRowHolder(r)
            rowHolders.add(holder)
            rowsContainer.addView(holder.container)
        }

        // Hide extra holders
        for (i in channels.size until rowHolders.size) {
            rowHolders[i].container.visibility = GONE
        }

        // Bind active holders
        for (i in channels.indices) {
            rowHolders[i].container.visibility = VISIBLE
            bindRow(i)
        }
    }

    private fun createRowHolder(row: Int): RowViewHolder {
        val rowHeight = GuideTheme.dp(activity, if (isPortrait) 44 else 50)
        val rowContainer =
            LinearLayout(activity).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams =
                    LayoutParams(LayoutParams.MATCH_PARENT, rowHeight).apply {
                        setMargins(0, GuideTheme.dp(activity, 2), 0, GuideTheme.dp(activity, 2))
                    }
            }

        // Channel Badge (Col 0)
        val badge =
            TextView(activity).apply {
                layoutParams =
                    LayoutParams(channelColWidth, LayoutParams.MATCH_PARENT).apply {
                        setMargins(0, 0, GuideTheme.dp(activity, 2), 0)
                    }
                gravity = Gravity.CENTER
                textSize = 13f
                typeface = TvTypography.semibold(activity)
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                val pad = GuideTheme.dp(activity, 4)
                setPadding(pad, pad, pad, pad)
                isFocusable = true
            }
        rowContainer.addView(badge)

        val slots =
            Array(numSlots) { slotIndex ->
                TextView(activity)
                    .apply {
                        layoutParams =
                            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                                setMargins(
                                    GuideTheme.dp(activity, 2),
                                    0,
                                    GuideTheme.dp(activity, 2),
                                    0,
                                )
                            }
                        gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
                        textSize = 13f
                        typeface = TvTypography.regular(activity)
                        setSingleLine(true)
                        ellipsize = TextUtils.TruncateAt.END
                        val padH = GuideTheme.dp(activity, 8)
                        val padV = GuideTheme.dp(activity, 4)
                        setPadding(padH, padV, padH, padV)
                        isFocusable = true
                    }
                    .also { rowContainer.addView(it) }
            }

        return RowViewHolder(rowContainer, badge, slots)
    }

    private fun bindRow(r: Int) {
        val holder = rowHolders[r]
        val channel = channels[r]

        // Badge
        holder.badge.text = channel.title
        holder.badge.setOnClickListener { onPlay(channel) }
        holder.badge.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) focusCell(r, 0, requestViewFocus = false)
        }
        holder.badge.setOnKeyListener { _, keyCode, event -> handleCellKey(r, 0, keyCode, event) }

        // Slots
        for (s in 0 until numSlots) {
            val slotView = holder.slots[s]
            val slotStart = windowStartTime + s * slotDuration
            val slotEnd = slotStart + slotDuration
            val prog = channel.programmes.firstOrNull { it.start < slotEnd && it.end > slotStart }
            slotView.text = prog?.title ?: activity.getString(R.string.guide_no_programme)

            slotView.setOnClickListener { onPlay(channel) }
            slotView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) focusCell(r, s + 1, requestViewFocus = false)
            }
            slotView.setOnKeyListener { _, keyCode, event ->
                handleCellKey(r, s + 1, keyCode, event)
            }
        }

        updateRowStyles(r)
    }

    private fun handleCellKey(row: Int, col: Int, keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (row > 0) {
                    focusCell(row - 1, col)
                    true
                } else {
                    // Navigate to header buttons
                    earlierBtn.requestFocus()
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (row < channels.size - 1) {
                    focusCell(row + 1, col)
                    true
                } else true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (col > 0) {
                    focusCell(row, col - 1)
                    true
                } else {
                    // Col 0: shift time earlier if possible
                    if (windowStartTime - slotDuration >= earliestSlot) {
                        shiftWindow(-slotDuration)
                        focusCell(row, 0)
                    }
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (col < numSlots) {
                    focusCell(row, col + 1)
                    true
                } else {
                    // Last slot: shift time later if within 48h
                    if (windowStartTime + slotDuration <= guideStart + 48 * 3600) {
                        shiftWindow(slotDuration)
                        focusCell(row, numSlots)
                    }
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                if (row in channels.indices) {
                    onPlay(channels[row])
                }
                true
            }
            else -> false
        }
    }

    fun focusCell(row: Int, col: Int, requestViewFocus: Boolean = true) {
        if (channels.isEmpty()) return
        val clampedRow = row.coerceIn(0, channels.size - 1)
        val clampedCol = col.coerceIn(0, numSlots)

        val prevRow = focusedRow
        focusedRow = clampedRow
        focusedCol = clampedCol
        selectedChannelId = channels[clampedRow].id

        if (clampedCol > 0) {
            val cellTime = windowStartTime + (clampedCol - 1) * slotDuration
            model.restore(cellTime)
        }

        if (prevRow != clampedRow && prevRow in rowHolders.indices) {
            updateRowStyles(prevRow)
        }
        if (clampedRow in rowHolders.indices) {
            updateRowStyles(clampedRow)
        }

        updateUpperBand(clampedRow, clampedCol)

        if (requestViewFocus && clampedRow in rowHolders.indices) {
            val holder = rowHolders[clampedRow]
            val target = if (clampedCol == 0) holder.badge else holder.slots[clampedCol - 1]
            target.requestFocus()
            gridScrollView.requestChildFocus(holder.container, target)
        }
    }

    private fun updateRowStyles(r: Int) {
        if (r !in rowHolders.indices) return
        val holder = rowHolders[r]
        val isRowActive = r == focusedRow
        val radius = GuideTheme.dp(activity, 6).toFloat()

        // Badge styling
        if (isRowActive && focusedCol == 0) {
            // Channel badge itself is focused: prominent light card
            holder.badge.setBackgroundDrawable(
                GuideTheme.roundedBox(
                    radius,
                    GuideTheme.cardFocusedBg,
                    GuideTheme.dp(activity, 2),
                    GuideTheme.cardFocusedBorder,
                )
            )
            holder.badge.setTextColor(GuideTheme.cardFocusedText)
            holder.badge.typeface = TvTypography.semibold(activity)
        } else if (isRowActive) {
            // Row is active (a programme slot is focused): highlighted badge
            holder.badge.setBackgroundDrawable(
                GuideTheme.roundedBox(
                    radius,
                    GuideTheme.badgeActiveRowBg,
                    GuideTheme.dp(activity, 2),
                    GuideTheme.badgeActiveRowBorder,
                )
            )
            holder.badge.setTextColor(Color.WHITE)
            holder.badge.typeface = TvTypography.semibold(activity)
        } else {
            // Normal unselected row badge
            holder.badge.setBackgroundDrawable(
                GuideTheme.roundedBox(
                    radius,
                    GuideTheme.badgeNormalBg,
                    GuideTheme.dp(activity, 1),
                    GuideTheme.badgeNormalBorder,
                )
            )
            holder.badge.setTextColor(GuideTheme.textMuted)
            holder.badge.typeface = TvTypography.regular(activity)
        }

        // Slot styling
        for (s in 0 until numSlots) {
            val slotView = holder.slots[s]
            val isSlotFocused = isRowActive && focusedCol == s + 1

            if (isSlotFocused) {
                // Prominent light selected block per references.md
                slotView.setBackgroundDrawable(
                    GuideTheme.roundedBox(
                        radius,
                        GuideTheme.cardFocusedBg,
                        GuideTheme.dp(activity, 2),
                        GuideTheme.cardFocusedBorder,
                    )
                )
                slotView.setTextColor(GuideTheme.cardFocusedText)
                slotView.typeface = TvTypography.semibold(activity)
            } else {
                // Dark rounded card
                slotView.setBackgroundDrawable(
                    GuideTheme.roundedBox(
                        radius,
                        GuideTheme.cardNormalBg,
                        GuideTheme.dp(activity, 1),
                        GuideTheme.cardNormalBorder,
                    )
                )
                slotView.setTextColor(GuideTheme.textWhite)
                slotView.typeface = TvTypography.regular(activity)
            }
        }
    }

    private fun updateUpperBand(row: Int, col: Int) {
        if (channels.isEmpty() || row !in channels.indices) {
            programmeTitle.text = activity.getString(R.string.guide)
            channelTag.visibility = GONE
            progressContainer.visibility = GONE
            metaText.text = ""
            descriptionText.text = activity.getString(R.string.guide_help)
            return
        }

        val channel = channels[row]
        channelTag.visibility = VISIBLE
        channelTag.text = channel.title

        if (col == 0) {
            // Channel badge focused
            programmeTitle.text = channel.title
            progressContainer.visibility = GONE
            metaText.text =
                listOf(channel.category, channel.subtitle)
                    .filter { it.isNotEmpty() }
                    .joinToString(" • ")
            descriptionText.text =
                channel.description.ifEmpty { activity.getString(R.string.guide_help) }
            return
        }

        // Programme slot focused
        val slotStart = windowStartTime + (col - 1) * slotDuration
        val slotEnd = slotStart + slotDuration
        val prog = channel.programmes.firstOrNull { it.start < slotEnd && it.end > slotStart }

        if (prog != null) {
            programmeTitle.text = prog.title
            val now = System.currentTimeMillis() / 1000L
            progressContainer.visibility = VISIBLE
            val startStr = timeFormatter.format(Date(prog.start * 1000L))
            val endStr = timeFormatter.format(Date(prog.end * 1000L))
            val totalMin = ((prog.end - prog.start) / 60).toInt().coerceAtLeast(1)

            if (now in prog.start..prog.end) {
                val remainMin = ((prog.end - now) / 60).toInt().coerceAtLeast(0)
                progressBar.progress =
                    (now - prog.start).toFloat() / (prog.end - prog.start).toFloat()
                progressTimeText.text = activity.getString(R.string.guide_minutes_left, remainMin)
                progressTimeText.setTextColor(GuideTheme.accentProgress)
                timeSpanText.text = "$startStr – $endStr ($totalMin min)"
            } else if (now < prog.start) {
                val untilMin = ((prog.start - now) / 60).toInt().coerceAtLeast(0)
                progressBar.progress = 0f
                progressTimeText.text =
                    activity.getString(R.string.guide_starts_in_minutes, untilMin)
                progressTimeText.setTextColor(GuideTheme.textMuted)
                timeSpanText.text = "$startStr – $endStr ($totalMin min)"
            } else {
                progressBar.progress = 1f
                progressTimeText.setText(R.string.ended)
                progressTimeText.setTextColor(GuideTheme.textSubtle)
                timeSpanText.text = "$startStr – $endStr"
            }

            metaText.text =
                listOf(channel.category, channel.subtitle)
                    .filter { it.isNotEmpty() }
                    .joinToString(" • ")
            descriptionText.text =
                channel.description.ifEmpty { activity.getString(R.string.guide_help) }
        } else {
            programmeTitle.text = channel.title
            progressContainer.visibility = VISIBLE
            progressBar.progress = 0f
            progressTimeText.text = activity.getString(R.string.guide_no_programme)
            progressTimeText.setTextColor(GuideTheme.textMuted)
            val startStr = timeFormatter.format(Date(slotStart * 1000L))
            val endStr = timeFormatter.format(Date(slotEnd * 1000L))
            timeSpanText.text = "$startStr – $endStr"
            metaText.text =
                listOf(channel.category, channel.subtitle)
                    .filter { it.isNotEmpty() }
                    .joinToString(" • ")
            descriptionText.text =
                channel.description.ifEmpty { activity.getString(R.string.guide_help) }
        }
    }
}
