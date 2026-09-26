package io.github.diegog0477.zombiebox.client.features.playback.presentation.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.QualityInventory
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.QualityOption
import kotlin.math.min

/** A TV-sized, D-pad navigable quality panel. The gateway supplies only verified choices. */
@Suppress("DEPRECATION")
class QualityDialog(private val context: Context) {
    private enum class LoadState {
        LOADING,
        READY,
        ERROR,
    }

    private lateinit var dialog: Dialog
    private lateinit var options: LinearLayout
    private lateinit var status: TextView
    private lateinit var retryButton: View
    private lateinit var closeButton: View
    private var inventory: QualityInventory? = null
    private var loadState = LoadState.LOADING
    private var select: (String) -> Unit = {}
    private var retry: () -> Unit = {}
    private var dismissed: () -> Unit = {}

    fun show(
        initial: QualityInventory?,
        select: (String) -> Unit,
        retry: () -> Unit,
        dismissed: () -> Unit = {},
    ): QualityDialog {
        this.inventory = initial
        this.select = select
        this.retry = retry
        this.dismissed = dismissed

        val ui = TvWidgets(context)
        dialog =
            Dialog(context).apply { requestWindowFeature(android.view.Window.FEATURE_NO_TITLE) }
        val panel =
            ui.column().apply {
                setPadding(ui.dp(22), ui.dp(18), ui.dp(22), ui.dp(18))
                setBackgroundDrawable(ui.box(ui.panel, ui.muted))
            }
        panel.addView(
            ui.text(context.getString(R.string.quality), 22f).apply {
                typeface = TvTypography.semibold(context)
                setPadding(ui.dp(8), 0, ui.dp(8), ui.dp(10))
            }
        )
        status =
            ui.text("", 15f, ui.muted).apply { setPadding(ui.dp(8), ui.dp(2), ui.dp(8), ui.dp(8)) }
        panel.addView(status, LinearLayout.LayoutParams(-1, -2))

        options = ui.column()
        panel.addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(options)
            },
            LinearLayout.LayoutParams(-1, -2),
        )

        retryButton = ui.button(R.string.quality_retry) { this.retry() }
        closeButton = ui.button(R.string.close) { dialog.dismiss() }
        panel.addView(
            ui.row().apply {
                gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                addView(retryButton)
                addView(closeButton)
            }
        )

        dialog.setContentView(panel)
        dialog.setOnDismissListener { this.dismissed() }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.72f }
        }
        loadState = LoadState.LOADING
        renderOptions()
        renderStatus()
        dialog.show()
        dialog.window?.setLayout(
            min(ui.dp(460), context.resources.displayMetrics.widthPixels - ui.dp(48)),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        if (!focusInitialOption()) closeButton.requestFocus()
        return this
    }

    fun update(value: QualityInventory) {
        val hadFocus = dialog.currentFocus != null
        val focusedId = focusedOptionId()
        inventory = value
        loadState = LoadState.READY
        renderOptions(focusedId)
        renderStatus()
        if (focusedId != null && value.options.none { it.id == focusedId }) {
            focusInitialOption()
        } else if (!hadFocus) {
            focusInitialOption()
        }
    }

    fun showLoading() {
        if (retryButton.hasFocus()) closeButton.requestFocus()
        loadState = LoadState.LOADING
        renderStatus()
    }

    fun showError() {
        loadState = LoadState.ERROR
        renderStatus()
        if (inventory == null) retryButton.requestFocus()
    }

    fun isShowing() = ::dialog.isInitialized && dialog.isShowing

    fun dismiss() {
        if (::dialog.isInitialized) dialog.dismiss()
    }

    private fun renderStatus() {
        val value = inventory
        when {
            loadState == LoadState.ERROR -> {
                status.setText(R.string.quality_load_failed)
                status.visibility = View.VISIBLE
                retryButton.visibility = View.VISIBLE
            }
            loadState == LoadState.LOADING && value == null -> {
                status.setText(R.string.quality_loading)
                status.visibility = View.VISIBLE
                retryButton.visibility = View.GONE
            }
            loadState == LoadState.LOADING -> {
                status.setText(R.string.quality_refreshing)
                status.visibility = View.VISIBLE
                retryButton.visibility = View.GONE
            }
            else -> {
                status.text = ""
                status.visibility = View.GONE
                retryButton.visibility = View.GONE
            }
        }
    }

    private fun renderOptions(preferredFocusId: String? = null) {
        options.removeAllViews()
        val value = inventory
        if (value == null || value.options.isEmpty()) {
            if (loadState == LoadState.READY) {
                val ui = TvWidgets(context)
                options.addView(
                    ui.text(context.getString(R.string.qualities_unavailable), 15f, ui.muted)
                )
            }
            return
        }

        val ui = TvWidgets(context)
        for (option in value.options) {
            options.addView(
                optionRow(ui, option, value.selectedId),
                LinearLayout.LayoutParams(-1, -2),
            )
        }
        if (value.options.size == 1 && value.options[0].id.equals("auto", ignoreCase = true)) {
            options.addView(
                ui.text(context.getString(R.string.quality_manual_unavailable), 15f, ui.muted)
                    .apply { setPadding(ui.dp(8), ui.dp(14), ui.dp(8), 0) }
            )
        }

        val focusId = preferredFocusId?.takeIf { id -> value.options.any { it.id == id } }
        if (focusId != null) options.findViewWithTag<View>(optionTag(focusId))?.requestFocus()
    }

    private fun optionRow(ui: TvWidgets, option: QualityOption, selectedId: String): View {
        val selected = option.id.equals(selectedId, ignoreCase = true)
        val label =
            if (option.id.equals("auto", ignoreCase = true)) {
                context.getString(R.string.quality_auto)
            } else {
                option.label
            }
        return TextView(context).apply {
            text = (if (selected) "✓  " else "    ") + label
            textSize = 18f
            typeface = TvTypography.regular(context)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            minHeight = ui.dp(50)
            setPadding(ui.dp(12), ui.dp(5), ui.dp(12), ui.dp(5))
            setBackgroundDrawable(ui.focusBackground())
            tag = optionTag(option.id)
            setOnClickListener {
                dialog.dismiss()
                select(option.id)
            }
        }
    }

    private fun focusInitialOption(): Boolean {
        val value = inventory ?: return false
        val target =
            value.options.firstOrNull { it.id.equals(value.selectedId, ignoreCase = true) }
                ?: value.options.firstOrNull()
                ?: return false
        return options.findViewWithTag<View>(optionTag(target.id))?.requestFocus() == true
    }

    private fun focusedOptionId(): String? {
        val focused = dialog.currentFocus ?: return null
        val tag = focused.tag as? String ?: return null
        return tag.takeIf { it.startsWith(OPTION_TAG_PREFIX) }?.removePrefix(OPTION_TAG_PREFIX)
    }

    private fun optionTag(id: String) = OPTION_TAG_PREFIX + id

    companion object {
        private const val OPTION_TAG_PREFIX = "quality-option:"
    }
}
