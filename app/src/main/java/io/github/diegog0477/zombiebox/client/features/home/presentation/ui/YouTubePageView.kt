package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.artwork.platform.ArtworkDecoder
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.ui.ArtworkImageView
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel.ArtworkViewModel
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui.CatalogUiPolicy
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeSnapshot

/**
 * Dedicated TV app-tab presentation for YouTube. Search is a compact icon in the section header;
 * returned videos use a 16:9 multi-row thumbnail grid with visible D-pad focus.
 */
class YouTubePageView(
    private val context: Context,
    private val ui: TvWidgets,
    private val artwork: ArtworkViewModel,
    private val decoder: ArtworkDecoder,
    private val actions: HomeActions,
) {
    private val itemIds = LinkedHashSet<String>()
    private var columns = 1
    private var nextOffset = -1
    private var activityFeed = false
    private var nextActivityCursor = ""
    private var loadingMore = false
    private var failedAutoOffset = -1
    private var failedAutoCursor = ""
    private val loadedActivityCursors = HashSet<String>()
    private var itemsParent: LinearLayout? = null
    private var pageFocusRows: ArrayList<Pair<String, ViewGroup>>? = null
    private var loadMoreButton: Button? = null

    fun render(
        parent: LinearLayout,
        snapshot: HomeSnapshot,
        isHomeFeed: Boolean,
        isDockedLandscape: Boolean,
        widthDp: Float,
        focusRows: ArrayList<Pair<String, ViewGroup>>,
    ) {
        val red = ui.providerAccent("youtube")
        val serviceState = snapshot.modules.firstOrNull { it.id == "youtube" }?.state
        val featured = snapshot.hero?.takeIf { it.provider == "youtube" || it.provider.isEmpty() }
        val items =
            snapshot.sections
                .flatMap { it.items }
                .filter { it.provider == "youtube" || it.provider.isEmpty() }
        itemIds.clear()
        items.forEach { item -> itemIds.add(item.id) }
        activityFeed = isHomeFeed && snapshot.youtubeActivityFeed
        nextOffset = if (isHomeFeed && !activityFeed) snapshot.youtubeNextOffset else -1
        nextActivityCursor = if (activityFeed) snapshot.youtubeNextCursor else ""
        loadingMore = false
        failedAutoOffset = -1
        failedAutoCursor = ""
        loadedActivityCursors.clear()
        itemsParent = parent
        pageFocusRows = focusRows
        loadMoreButton = null
        val hasContent = featured != null || items.isNotEmpty()

        val isReady = CatalogUiPolicy.isReady(serviceState)
        val canSearch = isReady && CatalogUiPolicy.supportsSearch("youtube")
        val canCatalog = CatalogUiPolicy.canBrowseLibrary("youtube", serviceState)

        val leadRow = ui.row().apply { setPadding(0, ui.dp(10), 0, ui.dp(8)) }
        leadRow.addView(
            ui.text(context.getString(R.string.youtube), 22f).apply { typeface = ui.bold },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        val searchAction =
            ImageButton(context).apply {
                contentDescription =
                    context.getString(R.string.search_provider, context.getString(R.string.youtube))
                tag = "youtube:action:search"
                setImageDrawable(HeaderIcon(false, Color.WHITE))
                setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
                setBackgroundDrawable(ui.focusBackground(red))
                isFocusable = true
                setOnClickListener { actions.searchProvider("youtube") }
                layoutParams = LinearLayout.LayoutParams(ui.dp(46), ui.dp(46))
            }
        if (canSearch) leadRow.addView(searchAction)

        val catalogAction =
            ui.button(
                    if (hasContent && !activityFeed) R.string.view_all else R.string.browse_library
                ) {
                    actions.catalog("youtube")
                }
                .apply {
                    tag = "youtube:action:catalog"
                    textSize = 12f
                }
        if ((hasContent || isHomeFeed) && canCatalog) leadRow.addView(catalogAction)

        val receiverAction =
            ui.button(R.string.youtube_receiver) { actions.youtubeReceiver() }
                .apply {
                    tag = "youtube:action:receiver"
                    textSize = 12f
                }
        if (hasContent && serviceState != "DISABLED") leadRow.addView(receiverAction)

        parent.addView(leadRow, LinearLayout.LayoutParams(-1, -2))
        focusRows.add(Pair("youtube:actions", leadRow))
        if (isHomeFeed) {
            parent.addView(
                ui.text(
                    context.getString(
                        if (activityFeed) R.string.youtube_activity_feed
                        else R.string.youtube_home_feed
                    ),
                    12f,
                    ui.muted,
                ),
                LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, ui.dp(6)) },
            )
        }

        // 2. Optional featured video hero banner
        if (featured != null) {
            renderFeaturedHero(parent, featured, focusRows)
        }

        // 3. YouTube video items from semantic sections
        if (items.isEmpty() && featured == null) {
            val emptyState = ProviderEmptyStateView(context, ui, "youtube", actions, serviceState)
            parent.addView(
                emptyState,
                LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, ui.dp(14), 0, ui.dp(14)) },
            )
            emptyState.attachActions(focusRows)
            return
        }

        if (items.isNotEmpty()) {
            // 16:9 Multi-row thumbnail grid adapting columns to screen form factor
            columns =
                when {
                    isDockedLandscape -> 3
                    widthDp >= 600f -> 2
                    else -> 1
                }
            appendRows(parent, items, focusRows, null)
            if (isHomeFeed && hasMore()) addLoadMore(parent, focusRows)
        }
    }

    fun onFocusChanged(focused: View?) {
        if (focused == null || !hasMore() || loadingMore) return
        val itemId = (focused.tag as? String)?.removePrefix("youtube:item:") ?: return
        val index = itemIds.indexOf(itemId)
        if (index < 0) return
        val prefetchRows = 2
        val firstPrefetchIndex = (itemIds.size - columns * prefetchRows).coerceAtLeast(0)
        if (index >= firstPrefetchIndex) requestMore(autoTriggered = true)
    }

    private fun addLoadMore(parent: LinearLayout, focusRows: ArrayList<Pair<String, ViewGroup>>) {
        val row = ui.row()
        val button =
            ui.button(R.string.youtube_more_videos) { requestMore() }
                .apply {
                    tag = "youtube:load-more"
                    textSize = 13f
                }
        row.addView(button)
        parent.addView(row)
        focusRows.add(Pair("youtube:load-more", row))
        loadMoreButton = button
    }

    private fun requestMore(autoTriggered: Boolean = false) {
        if (loadingMore || !hasMore() || itemIds.size >= MAX_FEED_ITEMS) return
        val requestedOffset = if (activityFeed) -1 else nextOffset
        val requestedCursor = if (activityFeed) nextActivityCursor else ""
        val failedAutoRequest =
            if (activityFeed) requestedCursor.isNotBlank() && requestedCursor == failedAutoCursor
            else requestedOffset == failedAutoOffset
        if (autoTriggered && failedAutoRequest) return
        if (activityFeed && requestedCursor in loadedActivityCursors) {
            nextActivityCursor = ""
            removeLoadMore()
            actions.refreshHomeFocus(true)
            return
        }
        if (!autoTriggered) {
            failedAutoOffset = -1
            failedAutoCursor = ""
        }
        val parent = itemsParent ?: return
        val focusRows = pageFocusRows ?: return
        val button = loadMoreButton ?: return
        val rootAtStart = parent.rootView
        loadingMore = true
        button.setText(R.string.youtube_loading_more)
        if (activityFeed) {
            actions.loadYouTubeActivityPage(requestedCursor) { page, failure ->
                completePage(
                    page?.items,
                    requestedOffset,
                    requestedCursor,
                    page?.nextCursor.orEmpty(),
                    failure
                        ?: if (page == null) IllegalStateException("Missing YouTube activity page")
                        else null,
                    parent,
                    rootAtStart,
                    focusRows,
                    button,
                )
            }
        } else {
            actions.loadYouTubePage(requestedOffset) { page, failure ->
                completePage(
                    page?.items,
                    requestedOffset,
                    requestedCursor,
                    "",
                    failure
                        ?: if (page == null) IllegalStateException("Missing YouTube page")
                        else null,
                    parent,
                    rootAtStart,
                    focusRows,
                    button,
                    page?.nextOffset ?: -1,
                )
            }
        }
    }

    private fun completePage(
        pageItems: List<MediaItem>?,
        requestedOffset: Int,
        requestedCursor: String,
        returnedCursor: String,
        failure: Exception?,
        parent: LinearLayout,
        rootAtStart: View,
        focusRows: ArrayList<Pair<String, ViewGroup>>,
        button: Button,
        returnedOffset: Int = -1,
    ) {
        if (parent.rootView !== rootAtStart || loadMoreButton !== button) return
        loadingMore = false
        if (failure != null || pageItems == null) {
            if (activityFeed) failedAutoCursor = requestedCursor
            else failedAutoOffset = requestedOffset
            button.setText(R.string.youtube_retry_feed)
            return
        }

        val additions = ArrayList<MediaItem>()
        val remaining = MAX_FEED_ITEMS - itemIds.size
        for (item in pageItems) {
            if (additions.size >= remaining) break
            if (
                item.provider == "youtube" &&
                    item.kind == "video" &&
                    item.playable &&
                    itemIds.add(item.id)
            ) {
                additions.add(item)
            }
        }
        if (additions.isNotEmpty()) appendRows(parent, additions, focusRows, button.parent as? View)
        if (activityFeed) {
            loadedActivityCursors.add(requestedCursor)
            nextActivityCursor =
                if (
                    returnedCursor.isBlank() ||
                        returnedCursor == requestedCursor ||
                        returnedCursor in loadedActivityCursors ||
                        itemIds.size >= MAX_FEED_ITEMS
                ) {
                    ""
                } else {
                    returnedCursor
                }
        } else {
            nextOffset =
                if (returnedOffset <= requestedOffset || itemIds.size >= MAX_FEED_ITEMS) {
                    -1
                } else {
                    returnedOffset
                }
        }
        if (!hasMore()) {
            removeLoadMore(parent)
        } else {
            button.setText(R.string.youtube_more_videos)
        }
        actions.refreshHomeFocus(!hasMore())
    }

    private fun hasMore(): Boolean =
        if (activityFeed) nextActivityCursor.isNotBlank() else nextOffset >= 0

    private fun removeLoadMore(parent: LinearLayout? = itemsParent) {
        val button = loadMoreButton ?: return
        val row = button.parent as? ViewGroup
        if (row != null) parent?.removeView(row)
        pageFocusRows?.removeAll { it.first == "youtube:load-more" }
        loadMoreButton = null
    }

    private fun appendRows(
        parent: LinearLayout,
        items: List<MediaItem>,
        focusRows: ArrayList<Pair<String, ViewGroup>>,
        insertBefore: View?,
    ) {
        val rowBase = focusRows.count { it.first.startsWith("youtube:row:") }
        var parentIndex = insertBefore?.let(parent::indexOfChild) ?: parent.childCount
        var focusIndex = focusRows.indexOfFirst { it.first == "youtube:load-more" }
        if (focusIndex < 0) focusIndex = focusRows.size

        for ((pageRow, rowItems) in items.chunked(columns).withIndex()) {
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams =
                        LinearLayout.LayoutParams(-1, -2).apply {
                            setMargins(0, ui.dp(3), 0, ui.dp(4))
                        }
                }
            rowItems.forEach { row.addView(createVideoCard(it)) }
            repeat((columns - rowItems.size).coerceAtLeast(0)) {
                row.addView(
                    View(context).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(0, 0, 1f).apply {
                                setMargins(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(5))
                            }
                    }
                )
            }
            parent.addView(row, parentIndex++)
            focusRows.add(focusIndex++, Pair("youtube:row:${rowBase + pageRow}", row))
        }
    }

    private fun renderFeaturedHero(
        parent: LinearLayout,
        featured: MediaItem,
        focusRows: ArrayList<Pair<String, ViewGroup>>,
    ) {
        val heroFrame = FrameLayout(context).apply { minimumHeight = ui.dp(210) }
        val heroLayout =
            ui.column().apply {
                setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16))
                setBackgroundDrawable(
                    GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(
                                Color.argb(245, 18, 12, 14),
                                Color.argb(120, 18, 12, 14),
                                ui.background,
                            ),
                        )
                        .apply { cornerRadius = ui.dp(10).toFloat() }
                )
            }

        if (featured.imageUrl.isNotEmpty()) {
            val bgArt =
                ArtworkImageView(context, decoder).apply { bind(artwork, featured.imageUrl, true) }
            heroFrame.addView(bgArt, FrameLayout.LayoutParams(-1, -1))
        }

        heroFrame.addView(heroLayout, FrameLayout.LayoutParams(-1, -2))

        heroLayout.addView(
            ui.text(context.getString(R.string.youtube), 12f, ui.providerAccent("youtube"))
        )
        heroLayout.addView(
            ui.text(featured.title, 26f).apply {
                typeface = ui.bold
                maxLines = 2
            }
        )
        if (featured.subtitle.isNotEmpty()) {
            heroLayout.addView(ui.text(featured.subtitle, 13f, ui.muted).apply { maxLines = 1 })
        }
        if (featured.description.isNotEmpty()) {
            heroLayout.addView(ui.text(featured.description, 14f, ui.muted).apply { maxLines = 2 })
        }

        val heroActions = ui.row().apply { setPadding(0, ui.dp(8), 0, 0) }
        if (featured.playable) {
            heroActions.addView(
                ui.primary(
                    if (featured.positionMs > 0) R.string.resume_content else R.string.play
                ) {
                    actions.play(featured)
                }
            )
        }
        heroActions.addView(ui.button(R.string.more_info) { actions.details(featured) })
        heroLayout.addView(heroActions)

        parent.addView(
            heroFrame,
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, ui.dp(6), 0, ui.dp(8)) },
        )
        focusRows.add(Pair("youtube:hero", heroActions))
    }

    private fun createVideoCard(item: MediaItem): View {
        val card =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isClickable = true
                tag = "youtube:item:" + item.id
                setBackgroundDrawable(redWhiteFocusDrawable())
                setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(6))
                layoutParams =
                    LinearLayout.LayoutParams(0, -2, 1f).apply {
                        setMargins(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(5))
                    }
            }

        // 16:9 Thumbnail container
        val thumbFrame =
            AspectRatioFrameLayout(context, 16f / 9f).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
        val artView =
            ArtworkImageView(context, decoder).apply { bind(artwork, item.imageUrl, false) }
        thumbFrame.addView(artView, FrameLayout.LayoutParams(-1, -1))

        if (item.positionMs > 0 && item.durationMs > 0) {
            val progressBar = ui.progress(item.positionMs, item.durationMs)
            thumbFrame.addView(progressBar, FrameLayout.LayoutParams(-1, ui.dp(3), Gravity.BOTTOM))
        } else if (item.durationMs > 0) {
            val timeBadge =
                ui.text(ui.formatTime(item.durationMs), 10f, Color.WHITE).apply {
                    setBackgroundDrawable(ui.box(Color.argb(190, 10, 15, 16)))
                    setPadding(ui.dp(4), ui.dp(1), ui.dp(4), ui.dp(1))
                }
            thumbFrame.addView(
                timeBadge,
                FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.RIGHT).apply {
                    setMargins(0, 0, ui.dp(4), ui.dp(4))
                },
            )
        }
        card.addView(thumbFrame)

        // Metadata: title and channel
        val metaColumn = ui.column().apply { setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(2)) }

        val titleView =
            ui.text(item.title, 14f).apply {
                maxLines = 2
                typeface = ui.bold
                ellipsize = TextUtils.TruncateAt.END
            }
        metaColumn.addView(titleView)

        val channelName = item.subtitle.ifEmpty { context.getString(R.string.youtube) }
        val channelView =
            ui.text(channelName, 12f, ui.muted).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        metaColumn.addView(channelView)

        card.addView(metaColumn)
        card.setOnClickListener { actions.details(item) }
        return card
    }

    /**
     * Visible red+white D-pad focus: Radiant YouTube red outer border (2dp) + crisp white inner
     * stroke (2dp) over dark red surface.
     */
    private fun redWhiteFocusDrawable(): StateListDrawable {
        val red = ui.providerAccent("youtube")
        val normalBg = ui.box(ui.panel)

        val focusedOuter =
            GradientDrawable().apply {
                setColor(red)
                cornerRadius = ui.dp(8).toFloat()
            }
        val focusedInner =
            GradientDrawable().apply {
                setColor(Color.rgb(28, 14, 16))
                setStroke(ui.dp(2), Color.WHITE)
                cornerRadius = ui.dp(6).toFloat()
            }
        val focusedLayer =
            LayerDrawable(arrayOf(focusedOuter, focusedInner)).apply {
                setLayerInset(1, ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
            }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focusedLayer)
            addState(intArrayOf(android.R.attr.state_pressed), focusedLayer)
            addState(intArrayOf(), normalBg)
        }
    }

    private companion object {
        const val MAX_FEED_ITEMS = 400
    }
}
