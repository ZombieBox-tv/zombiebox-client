package io.github.diegog0477.zombiebox.client.features.diagnostics.presentation.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvTheme
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.HardwareReport
import io.github.diegog0477.zombiebox.client.features.diagnostics.presentation.viewmodel.DiagnosticsViewModel

/** A focused diagnostics page in the TV shell, without native dialog buttons. */
@Suppress("DEPRECATION")
class DiagnosticsDialog(private val activity: Activity, private val model: DiagnosticsViewModel) {
    private val ui = TvWidgets(activity)
    private var current: HardwareReport? = null
    private var busy = false
    private var detailOpen = false
    private lateinit var dialog: Dialog
    private lateinit var detailTitle: TextView
    private lateinit var detailBack: View
    private lateinit var detailBody: LinearLayout
    private lateinit var detailScroll: ScrollView
    private lateinit var inventoryTile: LinearLayout
    private lateinit var inventoryLabel: TextView
    private lateinit var firstAction: View
    private var returnAction: View? = null
    private var summary = ""

    fun show() {
        if (activity.isFinishing) return
        val displayAbi = Build.CPU_ABI?.trim()?.lowercase()
        val initialAbi =
            if (displayAbi != null && displayAbi !in listOf("unknown", "", "null", "none")) {
                displayAbi
            } else {
                activity.getString(R.string.probe_unknown)
            }
        summary =
            activity.getString(
                R.string.diagnostics_report,
                Build.VERSION.SDK_INT,
                Build.MANUFACTURER,
                Build.MODEL,
                initialAbi,
            )
        val portrait =
            activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT ||
                activity.resources.displayMetrics.widthPixels <
                    activity.resources.displayMetrics.heightPixels
        dialog = Dialog(activity).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val root =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(TvTheme.shellBackground)
                setPadding(ui.dp(28), ui.dp(18), ui.dp(28), ui.dp(20))
            }
        root.addView(header())
        val actions = actionColumn()
        val details = detailColumn()
        if (portrait) {
            val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            column.addView(actions)
            column.addView(details, LinearLayout.LayoutParams(-1, ui.dp(300)))
            root.addView(
                ScrollView(activity).apply {
                    isFillViewport = true
                    addView(column)
                },
                LinearLayout.LayoutParams(-1, 0, 1f),
            )
        } else {
            val row =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, ui.dp(12), 0, 0)
                }
            row.addView(
                ScrollView(activity).apply { addView(actions) },
                LinearLayout.LayoutParams(ui.dp(310), -1).apply { rightMargin = ui.dp(20) },
            )
            row.addView(details, LinearLayout.LayoutParams(0, -1, 1f))
            root.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(TvTheme.shellBackground))
        dialog.setOnDismissListener { model.close() }
        dialog.setOnKeyListener { _, code, event ->
            if (code == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && detailOpen) {
                showSummary()
                (returnAction ?: firstAction).requestFocus()
                true
            } else false
        }
        model.observer = { hardware, failed ->
            busy = false
            current = hardware
            inventoryTile.isEnabled = hardware != null && !failed
            inventoryTile.isFocusable = inventoryTile.isEnabled
            inventoryLabel.setTextColor(if (inventoryTile.isEnabled) Color.WHITE else ui.muted)
            summary =
                when {
                    failed -> activity.getString(R.string.error_request)
                    hardware == null -> summary
                    else ->
                        activity.getString(
                            R.string.hardware_report,
                            if (hardware.abis.isEmpty()) activity.getString(R.string.probe_unknown)
                            else hardware.abis.joinToString(", "),
                            hardware.cores,
                            hardware.memoryMb,
                            hardware.storageFreeMb,
                            hardware.network,
                            hardware.latencyMs,
                            hardware.decoders.size,
                            hardware.externalPlayers.size,
                        )
                }
            if (!detailOpen) showSummary()
        }
        dialog.show()
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        firstAction.requestFocus()
        scanHardware()
    }

    private fun header(): View {
        val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(activity.getString(R.string.settings), 13f, ui.green, true))
        labels.addView(text(activity.getString(R.string.diagnostics), 28f, Color.WHITE, true))
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(
            TvTheme.createDarkButton(activity, ui, activity.getString(R.string.close)) {
                dialog.dismiss()
            }
        )
        return row
    }

    private fun actionColumn(): LinearLayout {
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(text(activity.getString(R.string.diagnostics_actions), 14f, ui.muted, true))
        val inventory =
            tile(R.string.inventory_title, R.string.diagnostics_inventory_hint) {
                current?.let {
                    showDetail(
                        R.string.inventory_title,
                        HardwareInventoryDialog(activity).content(it),
                    )
                }
            }
        inventoryTile = inventory.first
        inventoryLabel = inventory.second
        inventoryTile.isEnabled = false
        inventoryTile.isFocusable = false
        inventoryLabel.setTextColor(ui.muted)
        column.addView(inventoryTile)
        column.addView(
            tile(R.string.export_diagnostics, R.string.diagnostics_export_hint) { exportReport() }
                .first
        )
        val probes =
            tile(R.string.run_probes, R.string.diagnostics_probes_hint) {
                    dialog.dismiss()
                    activity.startActivity(Intent(activity, ProbesActivity::class.java))
                }
                .first
        firstAction = probes
        column.addView(probes)
        column.addView(
            tile(R.string.rescan_hardware, R.string.diagnostics_rescan_hint) { scanHardware() }
                .first
        )
        return column
    }

    private fun detailColumn(): LinearLayout {
        val column =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(22), ui.dp(18), ui.dp(22), ui.dp(18))
                setBackgroundDrawable(
                    TvTheme.roundedBox(
                        ui.dp(12).toFloat(),
                        TvTheme.cardBackground,
                        ui.dp(1),
                        TvTheme.cardBorder,
                    )
                )
            }
        val detailHeader = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        detailTitle =
            text(activity.getString(R.string.diagnostics_overview), 21f, Color.WHITE, true)
        detailHeader.addView(detailTitle, LinearLayout.LayoutParams(0, -2, 1f))
        detailBack =
            TvTheme.createDarkButton(activity, ui, activity.getString(R.string.back)) {
                showSummary()
                (returnAction ?: firstAction).requestFocus()
            }
        detailHeader.addView(detailBack)
        column.addView(detailHeader)
        detailScroll =
            ScrollView(activity).apply {
                isFillViewport = true
                isFocusable = true
                setBackgroundColor(Color.TRANSPARENT)
            }
        detailBody = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        detailScroll.addView(detailBody)
        column.addView(detailScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        showSummary()
        return column
    }

    private fun showSummary() {
        detailOpen = false
        detailTitle.setText(R.string.diagnostics_overview)
        detailBack.visibility = View.GONE
        detailBody.removeAllViews()
        detailBody.addView(text(summary, 16f, Color.WHITE, false))
        detailScroll.scrollTo(0, 0)
    }

    private fun showDetail(title: Int, content: View) {
        detailOpen = true
        detailTitle.setText(title)
        detailBack.visibility = View.VISIBLE
        detailBody.removeAllViews()
        detailBody.addView(content)
        detailScroll.scrollTo(0, 0)
        detailScroll.requestFocus()
    }

    private fun scanHardware() {
        if (busy) return
        busy = true
        summary = activity.getString(R.string.loading)
        showSummary()
        model.scan()
    }

    private fun exportReport() {
        if (busy) return
        busy = true
        showDetail(
            R.string.export_diagnostics,
            text(activity.getString(R.string.loading), 16f, Color.WHITE, false),
        )
        model.export(
            { report ->
                busy = false
                val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                content.addView(text(report, 14f, Color.WHITE, false))
                content.addView(
                    TvTheme.createDarkButton(
                        activity,
                        ui,
                        activity.getString(R.string.share_report),
                    ) {
                        shareReport(report)
                    }
                )
                showDetail(R.string.export_diagnostics, content)
            },
            {
                busy = false
                showDetail(
                    R.string.export_diagnostics,
                    text(activity.getString(R.string.error_request), 16f, Color.WHITE, false),
                )
            },
        )
    }

    private fun shareReport(report: String) {
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, report)
                .putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.diagnostics))
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(
                Intent.createChooser(intent, activity.getString(R.string.share_report))
            )
        } else Toast.makeText(activity, R.string.share_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun tile(title: Int, hint: Int, click: () -> Unit): Pair<LinearLayout, TextView> {
        val titleView = text(activity.getString(title), 17f, Color.WHITE, true)
        val card =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isClickable = true
                setPadding(ui.dp(18), ui.dp(10), ui.dp(18), ui.dp(10))
                setBackgroundDrawable(
                    StateListDrawable().apply {
                        addState(
                            intArrayOf(android.R.attr.state_focused),
                            TvTheme.roundedBox(
                                ui.dp(9).toFloat(),
                                TvTheme.buttonFocused,
                                ui.dp(2),
                                ui.green,
                            ),
                        )
                        addState(
                            intArrayOf(),
                            TvTheme.roundedBox(
                                ui.dp(9).toFloat(),
                                TvTheme.buttonBackground,
                                ui.dp(1),
                                TvTheme.buttonBorder,
                            ),
                        )
                    }
                )
                setOnClickListener {
                    returnAction = this
                    click()
                }
                contentDescription = activity.getString(title) + ". " + activity.getString(hint)
            }
        card.addView(titleView)
        card.addView(text(activity.getString(hint), 13f, ui.muted, false))
        card.layoutParams =
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, ui.dp(4), 0, ui.dp(4)) }
        return card to titleView
    }

    private fun text(value: String, size: Float, color: Int, strong: Boolean): TextView =
        TextView(activity).apply {
            text = value
            textSize = size
            typeface =
                if (strong) TvTypography.semibold(activity) else TvTypography.regular(activity)
            setTextColor(color)
            setPadding(ui.dp(2), ui.dp(4), ui.dp(2), ui.dp(4))
        }
}
