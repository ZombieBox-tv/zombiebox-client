package io.github.diegog0477.zombiebox.client.features.services.presentation.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvKeyboardOverlay
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.services.data.GatewayServicesRepository
import io.github.diegog0477.zombiebox.client.features.services.presentation.viewmodel.ServicesViewModel
import io.github.diegog0477.zombiebox.shared.GatewayApi
import java.util.concurrent.Executors

/** Views own focus and rendering; semantic data and work stay behind MVVM. */
class ServicesActivity : Activity() {
    companion object {
        const val EXTRA_SPOTIFY_FOCUSED = "spotify_focused"
    }

    private val ui by lazy { TvWidgets(this) }
    private val api = GatewayApi()
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler()
    private lateinit var model: ServicesViewModel
    private lateinit var code: EditText

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val prefs = getSharedPreferences("zombie", MODE_PRIVATE)
        api.configure(
            prefs.getString("gateway", "")!!,
            prefs.getString("device", "")!!,
            prefs.getString("token", "")!!,
        )
        model =
            ServicesViewModel(
                GatewayServicesRepository(api),
                { work -> executor.execute { work() } },
                { work -> handler.post { work() } },
            )
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
                setBackgroundColor(Color.rgb(10, 15, 16))
            }
        fun label(id: Int) =
            TextView(this).apply {
                setText(id)
                setTextColor(Color.WHITE)
                textSize = 18f
                typeface = TvTypography.regular(this@ServicesActivity)
                root.addView(this)
            }
        fun button(id: Int, click: () -> Unit) =
            Button(this).apply {
                setText(id)
                setOnClickListener { click() }
                typeface = TvTypography.semibold(this@ServicesActivity)
                isFocusable = true
                val focused =
                    GradientDrawable().apply {
                        setColor(Color.rgb(49, 210, 86))
                        setStroke(3, Color.WHITE)
                        cornerRadius = 10f
                    }
                val normal =
                    GradientDrawable().apply {
                        setColor(Color.rgb(31, 40, 42))
                        setStroke(1, Color.GRAY)
                        cornerRadius = 10f
                    }
                setBackgroundDrawable(
                    StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused), focused)
                        addState(intArrayOf(android.R.attr.state_pressed), focused)
                        addState(intArrayOf(), normal)
                    }
                )
                setTextColor(Color.WHITE)
                root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
            }
        label(R.string.gateway_services).textSize = 28f
        label(R.string.service_check_detail)
        val status = label(R.string.loading)
        val inventory = label(R.string.loading)
        val refresh = button(R.string.refresh_services) { model.refresh() }
        label(R.string.spotify).textSize = 24f
        val track = label(R.string.unavailable)
        val isSpotifyFocused = intent?.getBooleanExtra(EXTRA_SPOTIFY_FOCUSED, false) == true
        val pairingGuide = label(R.string.spotify_pairing_checking)
        pairingGuide.visibility = if (isSpotifyFocused) View.VISIBLE else View.GONE
        code =
            EditText(this).apply {
                setHint(R.string.operator_code)
                setHintTextColor(Color.LTGRAY)
                setTextColor(Color.WHITE)
                typeface = TvTypography.regular(this@ServicesActivity)
                setBackgroundDrawable(
                    ui.box(
                        ui.panel,
                        if (isSpotifyFocused) ui.providerAccent("spotify") else ui.muted,
                    )
                )
                setPadding(ui.dp(12), 0, ui.dp(12), 0)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true)
                filters = arrayOf(android.text.InputFilter.LengthFilter(6))
                isSaveEnabled = false
                setOnClickListener {
                    TvKeyboardOverlay.show(
                        this@ServicesActivity,
                        this,
                        getString(R.string.operator_code),
                    )
                }
            }
        root.addView(code)
        val buttons = arrayListOf<Button>()
        val authorizeButton = button(R.string.spotify_authorize) { useCode { model.authorize(it) } }
        if (isSpotifyFocused) {
            code.visibility = View.GONE
            authorizeButton.visibility = View.GONE
        }
        if (isSpotifyFocused) {
            val spotifyAccent = ui.providerAccent("spotify")
            val focused =
                GradientDrawable().apply {
                    setColor(spotifyAccent)
                    setStroke(ui.dp(3), Color.WHITE)
                    cornerRadius = ui.dp(10).toFloat()
                }
            val normal =
                GradientDrawable().apply {
                    setColor(Color.rgb(18, 56, 28))
                    setStroke(ui.dp(2), spotifyAccent)
                    cornerRadius = ui.dp(10).toFloat()
                }
            authorizeButton.setBackgroundDrawable(
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), focused)
                    addState(intArrayOf(android.R.attr.state_pressed), focused)
                    addState(intArrayOf(), normal)
                }
            )
            authorizeButton.setTextColor(Color.WHITE)
        }
        buttons.add(authorizeButton)
        for ((label, command) in
            listOf(
                R.string.music_resume to "resume",
                R.string.music_pause to "pause",
                R.string.music_previous to "previous",
                R.string.music_next to "next",
                R.string.stop to "stop",
            )) {
            val commandButton = button(label) { useCode { model.command(command, it) } }
            if (isSpotifyFocused) commandButton.visibility = View.GONE
            buttons.add(commandButton)
        }
        val prompt = label(R.string.no_authorization_pending).apply { isSaveEnabled = false }
        button(R.string.close) { finish() }
        setContentView(ScrollView(this).apply { addView(root) })
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        model.observer = { value ->
            status.setText(
                if (value.loading) R.string.loading
                else if (value.failed) R.string.unavailable else R.string.ready
            )
            refresh.isEnabled = !value.loading
            buttons.forEach { it.isEnabled = !value.loading }
            inventory.text =
                value.snapshot.integrations.joinToString("\n") {
                    getString(R.string.integration_status, serviceName(it.id), stateName(it.state))
                }
            track.text =
                value.snapshot.music?.let {
                    getString(R.string.music_status, it.title, it.artist, stateName(it.state))
                } ?: getString(R.string.unavailable)
            if (isSpotifyFocused) {
                val spotify = value.snapshot.integrations.firstOrNull { it.id == "spotify" }
                val codeAvailable =
                    spotify != null &&
                        spotify.state == "AUTH_REQUIRED" &&
                        (spotify.authMode == "device_auth" || spotify.authMode.isEmpty())
                pairingGuide.setText(
                    when {
                        value.loading -> R.string.spotify_pairing_checking
                        value.failed || spotify == null -> R.string.spotify_pairing_unavailable
                        spotify.authMode == "zeroconf" -> R.string.spotify_pairing_zeroconf
                        spotify.state == "READY" -> R.string.spotify_pairing_ready
                        codeAvailable -> R.string.spotify_pairing_device_auth
                        else -> R.string.spotify_pairing_unavailable
                    }
                )
                code.visibility = if (codeAvailable) View.VISIBLE else View.GONE
                authorizeButton.visibility = if (codeAvailable) View.VISIBLE else View.GONE
            }
            prompt.text =
                value.prompt?.let {
                    if (it.waiting) getString(R.string.authorization_code, it.url, it.code)
                    else getString(R.string.no_authorization_pending)
                } ?: ""
        }
        refresh.requestFocus()
        model.refresh()
    }

    private fun useCode(action: (String) -> Unit) {
        val value = code.text.toString()
        code.setText("")
        if (value.isEmpty()) {
            code.error = getString(R.string.operator_code)
            code.requestFocus()
            return
        }
        action(value)
    }

    private fun serviceName(id: String): String =
        when (id) {
            "local" -> getString(R.string.local_library)
            "android_mirror",
            "mediamtx" -> getString(R.string.android_mirror)
            "youtube_receiver" -> getString(R.string.youtube_receiver)
            "rebrowser" -> getString(R.string.browser)
            "ffmpeg" -> "FFmpeg"
            "threadfin" -> "Threadfin"
            "spotify" -> "Spotify"
            "airplay" -> "AirPlay"
            "iptv" -> "IPTV"
            "youtube" -> "YouTube"
            "plex" -> "Plex"
            "jellyfin" -> "Jellyfin"
            "stremio" -> "Stremio"
            else -> getString(R.string.apps_content)
        }

    private fun stateName(state: String): String =
        getString(
            when (state) {
                "READY" -> R.string.ready
                "DISABLED" -> R.string.disabled
                "AUTH_REQUIRED" -> R.string.authorization_required
                "CONFIGURED" -> R.string.configured
                "NOT_IMPLEMENTED" -> R.string.implementation_pending
                "PLAYING" -> R.string.playing
                "PAUSED" -> R.string.paused
                "STOPPED" -> R.string.stopped
                "BUFFERING" -> R.string.loading
                else -> R.string.unavailable
            }
        )

    override fun onPause() {
        code.setText("")
        super.onPause()
    }

    override fun onDestroy() {
        model.close()
        api.close()
        executor.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
