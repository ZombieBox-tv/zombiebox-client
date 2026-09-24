package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.view.ViewGroup
import android.view.Window
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogOverlay
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.viewmodel.GuideViewModel

/**
 * TV-native electronic programme grid dialog per resources/ui-rules/references.md. Manages
 * full-screen dialog lifecycle, D-pad scrolling/paging, and state restoration.
 */
class GuideDialog(
    private val activity: Activity,
    private var channels: List<MediaItem>,
    private val play: (MediaItem, CatalogOverlay) -> Unit,
    private val reload: (((List<MediaItem>) -> Unit, () -> Unit) -> Unit)? = null,
    private val previousPage: (() -> Unit)? = null,
    private val nextPage: (() -> Unit)? = null,
    private val dismissed: () -> Unit = {},
) {
    private val handler = Handler()
    private var refreshing = false
    private var paging = false
    private var gridView: GuideGridView? = null
    private var model: GuideViewModel? = null
    private var tick: Runnable? = null
    private var dialog: AlertDialog? = null

    val activeDialog: AlertDialog?
        get() = dialog?.takeIf { it.isShowing }

    val visible: Boolean
        get() = dialog?.isShowing == true

    fun pageFailed() {
        if (!visible) return
        paging = false
        gridView?.enablePagingButtons(true)
        gridView?.setPageStatus(activity.getString(R.string.guide_page_failed))
    }

    private fun changePage(action: () -> Unit) {
        if (refreshing || paging) return
        paging = true
        gridView?.enablePagingButtons(false)
        gridView?.setPageStatus(activity.getString(R.string.loading))
        action()
    }

    fun snapshot(): CatalogOverlay {
        if (!visible) return CatalogOverlay()
        val currentModel = model
        val currentGrid = gridView
        return CatalogOverlay(
            kind = "guide",
            guideTime = currentModel?.time ?: 0L,
            selectedChannel =
                currentGrid?.selectedChannelId?.ifEmpty {
                    channels.getOrNull(currentGrid.focusedRow)?.id
                        ?: channels.firstOrNull()?.id
                        ?: ""
                } ?: "",
        )
    }

    fun close() {
        tick?.let { handler.removeCallbacks(it) }
        tick = null
        dialog?.dismiss()
        dialog = null
        gridView = null
        model = null
    }

    fun show(saved: CatalogOverlay = CatalogOverlay()) {
        val currentModel = GuideViewModel(channels, System.currentTimeMillis() / 1000)
        if (saved.guideTime > 0) {
            currentModel.restore(saved.guideTime)
        }
        model = currentModel

        var currentDialog: AlertDialog? = null

        val grid =
            GuideGridView(
                activity = activity,
                channels = channels,
                model = currentModel,
                onPlay = { channel ->
                    val returnPoint = snapshot().copy(selectedChannel = channel.id)
                    currentDialog?.dismiss()
                    play(channel, returnPoint)
                },
                onShiftWindow = { _ -> },
                onPreviousPage = previousPage?.let { action -> { changePage(action) } },
                onNextPage = nextPage?.let { action -> { changePage(action) } },
                onClose = { currentDialog?.dismiss() },
            )
        gridView = grid
        grid.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )

        val builder = AlertDialog.Builder(activity).setView(grid)
        val created = builder.create()
        try {
            created.requestWindowFeature(Window.FEATURE_NO_TITLE)
        } catch (_: Exception) {}
        currentDialog = created
        dialog = created

        created.setOnDismissListener {
            tick?.let { handler.removeCallbacks(it) }
            dismissed()
        }

        created.show()

        created.window?.let { win ->
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            win.setBackgroundDrawable(ColorDrawable(GuideTheme.bgDark))
        }

        grid.restoreState(saved.selectedChannel, saved.guideTime)

        tick =
            object : Runnable {
                override fun run() {
                    if (dialog !== created || !created.isShowing) return
                    grid.updateClockAndProgress()
                    if (!refreshing && !paging && reload != null) {
                        refreshing = true
                        reload.invoke(
                            { fresh ->
                                refreshing = false
                                if (dialog === created && created.isShowing) {
                                    val selectedID = grid.selectedChannelId
                                    val time = currentModel.time
                                    channels = fresh
                                    val updatedModel =
                                        GuideViewModel(channels, System.currentTimeMillis() / 1000)
                                    updatedModel.restore(time)
                                    model = updatedModel
                                    grid.updateChannels(channels, updatedModel, selectedID)
                                    grid.setPageStatus("")
                                }
                            },
                            {
                                refreshing = false
                                if (dialog === created && created.isShowing) {
                                    grid.setPageStatus(activity.getString(R.string.guide_stale))
                                }
                            },
                        )
                    }
                    handler.postDelayed(this, 60000)
                }
            }
        handler.postDelayed(tick!!, 60000)
    }
}
