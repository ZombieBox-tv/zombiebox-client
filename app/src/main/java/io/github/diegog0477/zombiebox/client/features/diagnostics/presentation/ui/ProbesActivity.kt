package io.github.diegog0477.zombiebox.client.features.diagnostics.presentation.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.features.diagnostics.data.GatewayDiagnosticsRepository
import io.github.diegog0477.zombiebox.client.features.diagnostics.data.GatewayProbeRepository
import io.github.diegog0477.zombiebox.client.features.diagnostics.platform.HardwareScanner
import io.github.diegog0477.zombiebox.client.features.diagnostics.platform.MediaProbePlayback
import io.github.diegog0477.zombiebox.client.features.diagnostics.presentation.viewmodel.ProbeViewModel
import io.github.diegog0477.zombiebox.client.features.playback.platform.SurfaceEvidence
import io.github.diegog0477.zombiebox.client.features.playback.presentation.ui.VideoOutputFactory
import io.github.diegog0477.zombiebox.client.features.playback.presentation.ui.VideoOutputView
import io.github.diegog0477.zombiebox.shared.GatewayApi
import java.util.concurrent.Executors

class ProbesActivity : Activity() {
    private val api = GatewayApi()
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler()
    private val playback = MediaProbePlayback()
    private lateinit var model: ProbeViewModel
    private var texture: VideoOutputView? = null
    private lateinit var probes:
        io.github.diegog0477.zombiebox.client.features.diagnostics.platform.PlatformProbes

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val prefs = getSharedPreferences("zombie", MODE_PRIVATE)
        api.configure(
            prefs.getString("gateway", "")!!,
            prefs.getString("device", "")!!,
            prefs.getString("token", "")!!,
        )
        val diagnostics = GatewayDiagnosticsRepository(api, HardwareScanner(applicationContext))
        probes =
            io.github.diegog0477.zombiebox.client.features.diagnostics.platform.PlatformProbes(
                applicationContext,
                playback,
                { work -> executor.execute { work() } },
                { work -> handler.post { work() } },
            )
        model =
            ProbeViewModel(
                GatewayProbeRepository(
                    api,
                    {
                        io.github.diegog0477.zombiebox.client.features.diagnostics.platform
                            .PlatformProbes
                            .assets()
                    },
                    { results ->
                        results.forEach {
                            SurfaceEvidence(applicationContext).record(it.id, it.status)
                        }
                    },
                    { packageManager.getPackageInfo(packageName, 0).versionName ?: "" },
                ) {
                    diagnostics.scanAndSave()
                },
                probes,
                { work -> executor.execute { work() } },
                { work -> handler.post { work() } },
            )
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(Color.rgb(10, 15, 16))
            }
        val description =
            TextView(this).apply {
                setText(R.string.probe_description)
                setTextColor(Color.WHITE)
                textSize = 18f
            }
        val status =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 16f
            }
        val preview = SurfaceView(this)
        val start =
            Button(this).apply {
                setText(R.string.run_probes)
                isEnabled = false
                setOnClickListener { model.start() }
            }
        val stop =
            Button(this).apply {
                setText(R.string.stop)
                setOnClickListener { model.cancel() }
            }
        root.addView(description)
        root.addView(
            preview,
            LinearLayout.LayoutParams(-1, (180 * resources.displayMetrics.density).toInt()),
        )
        texture = VideoOutputFactory.textureCandidate()
        texture?.let { output ->
            val view = output.create(this, { playback.textureSurface = it }, {})
            playback.textureFrames = { output.frames }
            root.addView(
                view,
                LinearLayout.LayoutParams(-1, (120 * resources.displayMetrics.density).toInt()),
            )
        }
        root.addView(start)
        root.addView(stop)
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
        preview.holder.addCallback(
            object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    playback.surface = holder
                    start.isEnabled = !model.state.running
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int,
                ) {}

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    playback.surface = null
                    model.cancel()
                    start.isEnabled = false
                }
            }
        )
        model.observer = { value ->
            start.isEnabled = !value.running && playback.surface != null
            val heading =
                getString(
                    if (value.clockUnavailable) R.string.probe_clock_unavailable
                    else if (value.failed) R.string.probes_unavailable
                    else if (value.saved) R.string.probes_saved
                    else if (value.running) R.string.loading else R.string.run_probes
                )
            status.text =
                heading +
                    "\n" +
                    value.current +
                    "\n" +
                    value.results.joinToString("\n") {
                        getString(
                            R.string.probe_result,
                            it.id,
                            getString(
                                when (it.status) {
                                    "PASS" -> R.string.probe_pass
                                    "FAIL" -> R.string.probe_fail
                                    else -> R.string.probe_unknown
                                }
                            ),
                            it.prepareMs,
                            it.positionMs,
                        )
                    }
        }
        start.requestFocus()
    }

    override fun onPause() {
        model.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        model.close()
        texture?.close()
        playback.close()
        api.close()
        executor.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
