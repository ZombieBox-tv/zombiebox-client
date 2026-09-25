package io.github.diegog0477.zombiebox.client.features.settings.presentation.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvKeyboardOverlay
import io.github.diegog0477.zombiebox.client.core.ui.TvTheme
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.discovery.presentation.ui.DiscoveryPanel
import io.github.diegog0477.zombiebox.client.features.discovery.presentation.viewmodel.DiscoveryViewModel
import io.github.diegog0477.zombiebox.client.features.services.presentation.ui.ServicesActivity
import io.github.diegog0477.zombiebox.client.features.settings.domain.model.GatewayProfile
import io.github.diegog0477.zombiebox.client.features.settings.domain.model.ProviderPatch
import io.github.diegog0477.zombiebox.client.features.settings.domain.model.ProviderSettings
import io.github.diegog0477.zombiebox.client.features.settings.presentation.viewmodel.SettingsNavigation
import io.github.diegog0477.zombiebox.client.features.settings.presentation.viewmodel.SettingsViewModel

data class SettingsActions(
    val paired: (GatewayProfile) -> Unit,
    val refresh: () -> Unit,
    val render: () -> Unit,
    val diagnostics: () -> Unit,
    val audioSettings: () -> Unit,
    val receiverSettings: () -> Unit,
    val youtubeReceiverSettings: () -> Unit,
    val mediaReceiverSettings: () -> Unit,
    val resumePlayback: () -> Unit,
    val companionSettings: () -> Unit = {},
)

/**
 * Settings and Devices TV navigation. Dialog inputs and forms stay behind focused dark overlays.
 * Credentials and drafts never enter saved navigation.
 */
@Suppress("DEPRECATION")
@SuppressLint("RtlHardcoded")
class SettingsDialogs(
    private val activity: Activity,
    private val model: SettingsViewModel,
    private val paired: () -> Boolean,
    private val address: () -> String,
    private val error: (Exception) -> Unit,
    private val discovery: () -> DiscoveryViewModel,
    private val actions: SettingsActions,
) {
    private val ui = TvWidgets(activity)
    private var hdmiDialog: AlertDialog? = null
    private val menus = SettingsMenuWindows(activity, model.navigation)
    private val owned = ArrayList<AlertDialog>()
    private var closed = false

    fun snapshot() = menus.snapshot()

    fun restore(saved: List<SettingsNavigation.Menu>) {
        val path = model.navigation.sanitize(saved)
        path.forEach { entry ->
            when (entry.route) {
                "settings" -> show()
                "devices" -> devices()
                "advanced" -> advanced()
                "providers" -> providers(entry.selected, entry.selectedKey)
            }
            menus.restoreSelection(entry.route, entry.selected, entry.selectedKey)
        }
    }

    private fun own(dialog: AlertDialog): AlertDialog {
        owned.removeAll { !it.isShowing }
        owned.add(dialog)
        return dialog
    }

    fun close() {
        closed = true
        menus.close()
        owned.forEach { it.dismiss() }
        owned.clear()
        hdmiDialog?.dismiss()
        hdmiDialog = null
    }

    private fun field(
        parent: LinearLayout,
        label: Int,
        secret: Boolean = false,
        multiLine: Boolean = false,
    ): EditText {
        parent.addView(
            TextView(activity).apply {
                text = activity.getString(label)
                textSize = 14f
                typeface = TvTypography.semibold(activity)
                setTextColor(ui.muted)
                setPadding(ui.dp(2), ui.dp(6), ui.dp(2), ui.dp(4))
            }
        )
        val input =
            TvTheme.createDarkInputField(activity, ui, secret = secret, multiLine = multiLine)
        input.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                    TvKeyboardOverlay.show(activity, input, activity.getString(label))
                true
            } else false
        }
        parent.addView(
            input,
            LinearLayout.LayoutParams(-1, if (multiLine) ui.dp(72) else ui.dp(48)).apply {
                setMargins(0, 0, 0, ui.dp(8))
            },
        )
        return input
    }

    private fun dialogCard(
        title: String,
        subtitle: String? = null,
    ): Triple<LinearLayout, ScrollView, LinearLayout> {
        val card =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(14))
                setBackgroundDrawable(
                    TvTheme.roundedBox(
                        ui.dp(12).toFloat(),
                        TvTheme.cardBackground,
                        ui.dp(1),
                        TvTheme.cardBorder,
                    )
                )
            }
        card.addView(
            TextView(activity).apply {
                text = title
                textSize = 21f
                typeface = TvTypography.semibold(activity)
                setTextColor(Color.WHITE)
                setPadding(
                    ui.dp(4),
                    ui.dp(2),
                    ui.dp(4),
                    if (subtitle != null) ui.dp(4) else ui.dp(10),
                )
            }
        )
        if (subtitle != null) {
            card.addView(
                TextView(activity).apply {
                    text = subtitle
                    textSize = 13f
                    typeface = TvTypography.regular(activity)
                    setTextColor(ui.muted)
                    setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(12))
                }
            )
        }
        val form = ui.column().apply { setPadding(0, 0, 0, ui.dp(8)) }
        val scroll = TvTheme.boundedScroll(activity).apply { addView(form) }
        card.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        return Triple(card, scroll, form)
    }

    fun pairing() {
        val (card, _, form) =
            dialogCard(
                activity.getString(R.string.connect_gateway),
                activity.getString(R.string.lan_notice),
            )
        val addressField = field(form, R.string.gateway_address)
        addressField.setText(address())
        addressField.hint = activity.getString(R.string.gateway_hint)
        val discoveryModel = discovery()
        val discoveryPanel =
            DiscoveryPanel(activity, discoveryModel) { candidate ->
                addressField.setText(candidate)
            }
        TvTheme.styleDiscoveryPanel(discoveryPanel, ui, activity)
        form.addView(discoveryPanel)
        val codeField = field(form, R.string.operator_code, secret = true)

        var dialogRef: AlertDialog? = null
        val cancelBtn =
            TvTheme.createDarkButton(activity, ui, activity.getString(R.string.cancel)) {
                dialogRef?.dismiss()
            }
        lateinit var connectBtn: Button
        connectBtn =
            TvTheme.createPrimaryButton(activity, ui, activity.getString(R.string.connect)) {
                val candidate = addressField.text.toString().trim().trimEnd('/')
                if (!model.validAddress(candidate)) {
                    addressField.error = activity.getString(R.string.invalid_address)
                    return@createPrimaryButton
                }
                val pairingCode = codeField.text.toString()
                codeField.setText("")
                connectBtn.isEnabled = false
                model.pair(
                    candidate,
                    pairingCode,
                    pairedResult@{ profile ->
                        if (closed || dialogRef?.isShowing != true) return@pairedResult
                        menus.close()
                        actions.paired(profile)
                        dialogRef?.dismiss()
                    },
                    { failure ->
                        if (!closed && dialogRef?.isShowing == true) {
                            connectBtn.isEnabled = true
                            error(failure)
                        }
                    },
                )
            }
        val footer =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                setPadding(0, ui.dp(8), 0, 0)
                addView(cancelBtn)
                addView(connectBtn)
            }
        card.addView(footer, LinearLayout.LayoutParams(-1, -2))

        val dialog = AlertDialog.Builder(activity).setView(card).create()
        dialogRef = dialog
        dialog.setOnDismissListener {
            codeField.setText("")
            discoveryModel.close()
        }
        own(dialog)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width =
            (activity.resources.displayMetrics.widthPixels * 0.78f).toInt().coerceAtMost(ui.dp(580))
        dialog.window?.setLayout(width, -2)
        discoveryModel.refresh()
    }

    /** Exposes actual preferences and configurations on the TV full-screen settings page. */
    fun show() {
        val labels =
            arrayOf(
                activity.getString(R.string.connect_gateway),
                activity.getString(R.string.configure_services),
                activity.getString(R.string.diagnostics),
                activity.getString(R.string.language),
                activity.getString(R.string.presentation_mode),
                activity.getString(R.string.advanced),
                activity.getString(R.string.media_languages),
                activity.getString(R.string.gateway_services),
                activity.getString(R.string.resume_previous),
            )
        val keys =
            listOf(
                "gateway",
                "providers",
                "diagnostics",
                "language",
                "mode",
                "advanced",
                "media_languages",
                "services",
                "resume",
            )
        val subtitles =
            listOf(
                if (paired()) activity.getString(R.string.settings_connected_address, address())
                else activity.getString(R.string.settings_not_paired),
                "YouTube, Plex, Jellyfin, Stremio, IPTV",
                activity.getString(R.string.settings_diagnostics_summary),
                if (model.preferences.language == "es") "Español" else "English",
                model.preferences.mode,
                activity.getString(R.string.settings_advanced_summary),
                activity.getString(R.string.settings_languages_summary),
                activity.getString(R.string.settings_services_summary),
                activity.getString(R.string.settings_resume_summary),
            )
        val details =
            listOf(
                activity.getString(R.string.settings_gateway_detail),
                activity.getString(R.string.settings_providers_detail),
                activity.getString(R.string.settings_diagnostics_detail),
                activity.getString(R.string.settings_language_detail),
                activity.getString(R.string.settings_mode_detail),
                activity.getString(R.string.settings_advanced_detail),
                activity.getString(R.string.settings_languages_detail),
                activity.getString(R.string.settings_services_detail),
                activity.getString(R.string.settings_resume_detail),
            )
        val actionLabels =
            listOf(
                activity.getString(R.string.connect),
                activity.getString(R.string.configure_services),
                activity.getString(R.string.diagnostics),
                activity.getString(R.string.language),
                activity.getString(R.string.presentation_mode),
                activity.getString(R.string.advanced),
                activity.getString(R.string.media_languages),
                activity.getString(R.string.gateway_services),
                activity.getString(R.string.resume_previous),
            )
        menus.show(
            "settings",
            R.string.settings,
            labels,
            keys = keys,
            subtitles = subtitles,
            details = details,
            actionLabels = actionLabels,
        ) { index ->
            when (index) {
                0 -> pairing()
                1 -> providers()
                2 -> actions.diagnostics()
                3 -> language()
                4 -> mode()
                5 -> advanced()
                6 -> mediaLanguages()
                7 -> activity.startActivity(Intent(activity, ServicesActivity::class.java))
                8 -> {
                    menus.close()
                    actions.resumePlayback()
                }
            }
        }
    }

    /** Devices surface retaining pairing, remote companion, and receiver controls. */
    fun devices() {
        val labels =
            arrayOf(
                activity.getString(R.string.connect_gateway),
                activity.getString(R.string.phone_pair_title),
                activity.getString(R.string.youtube_receiver),
                activity.getString(R.string.media_receiver),
                activity.getString(R.string.receive_cast),
            )
        val keys =
            listOf("gateway", "companion", "youtube_receiver", "media_receiver", "receive_cast")
        val subtitles =
            listOf(
                if (paired()) activity.getString(R.string.settings_connected_address, address())
                else activity.getString(R.string.settings_not_paired),
                activity.getString(R.string.devices_companion_summary),
                activity.getString(R.string.devices_youtube_summary),
                activity.getString(R.string.devices_media_summary),
                activity.getString(R.string.devices_mirror_summary),
            )
        val details =
            listOf(
                activity.getString(R.string.devices_gateway_detail),
                activity.getString(R.string.devices_companion_detail),
                activity.getString(R.string.devices_youtube_detail),
                activity.getString(R.string.devices_media_detail),
                activity.getString(R.string.devices_mirror_detail),
            )
        val actionLabels =
            listOf(
                activity.getString(R.string.connect),
                activity.getString(R.string.phone_pair_title),
                activity.getString(R.string.youtube_receiver),
                activity.getString(R.string.media_receiver),
                activity.getString(R.string.receive_cast),
            )
        menus.show(
            "devices",
            R.string.devices,
            labels,
            keys = keys,
            subtitles = subtitles,
            details = details,
            actionLabels = actionLabels,
        ) { index ->
            when (index) {
                0 -> pairing()
                1 -> actions.companionSettings()
                2 -> actions.youtubeReceiverSettings()
                3 -> actions.mediaReceiverSettings()
                4 -> actions.receiverSettings()
            }
        }
    }

    private fun mediaLanguages() {
        if (!paired()) {
            pairing()
            return
        }
        val generation = model.navigation.generation
        model.mediaPreferences(
            { value ->
                if (!closed && generation == model.navigation.generation)
                    own(MediaLanguageDialog(activity, model, error).show(value))
            },
            error,
        )
    }

    fun providers(selected: Int = 0, selectedKey: String = "") {
        if (!paired()) {
            pairing()
            return
        }
        val generation = model.navigation.generation
        model.providers(
            loaded@{ providers ->
                if (closed || generation != model.navigation.generation) return@loaded
                val labels =
                    providers
                        .map {
                            ui.serviceTitle(it.id) +
                                " · " +
                                activity.getString(
                                    if (it.enabled) R.string.enabled else R.string.disabled
                                )
                        }
                        .toTypedArray()
                val subtitles =
                    providers.map { provider ->
                        if (provider.managed) activity.getString(R.string.server_managed)
                        else if (provider.configured) activity.getString(R.string.configured)
                        else if (provider.implemented)
                            activity.getString(R.string.settings_not_configured)
                        else activity.getString(R.string.adapter_pending)
                    }
                menus.show(
                    "providers",
                    R.string.configure_services,
                    labels,
                    keys = providers.map { it.id },
                    subtitles = subtitles,
                ) { index ->
                    providerForm(providers[index])
                }
                menus.restoreSelection("providers", selected, selectedKey)
            },
            error,
        )
    }

    private fun providerForm(provider: ProviderSettings) {
        val id = provider.id
        if (provider.managed) {
            val content =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16))
                    setBackgroundDrawable(
                        TvTheme.roundedBox(
                            ui.dp(12).toFloat(),
                            TvTheme.cardBackground,
                            ui.dp(1),
                            TvTheme.cardBorder,
                        )
                    )
                    addView(
                        TextView(activity).apply {
                            text = ui.serviceTitle(id)
                            textSize = 21f
                            typeface = TvTypography.semibold(activity)
                            setTextColor(Color.WHITE)
                            setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(10))
                        }
                    )
                    addView(
                        TextView(activity).apply {
                            text = activity.getString(R.string.server_managed)
                            textSize = 14f
                            typeface = TvTypography.regular(activity)
                            setTextColor(ui.muted)
                            setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(16))
                        }
                    )
                }
            var managedDialog: AlertDialog? = null
            val closeBtn =
                TvTheme.createDarkButton(activity, ui, activity.getString(R.string.close)) {
                    managedDialog?.dismiss()
                }
            content.addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.RIGHT
                    addView(closeBtn)
                }
            )
            val dialog = AlertDialog.Builder(activity).setView(content).create()
            managedDialog = dialog
            dialog.show()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val width =
                (activity.resources.displayMetrics.widthPixels * 0.76f)
                    .toInt()
                    .coerceAtMost(ui.dp(520))
            dialog.window?.setLayout(width, -2)
            own(dialog)
            return
        }

        val (card, _, form) =
            dialogCard(
                ui.serviceTitle(id),
                activity.getString(
                    if (provider.implemented) R.string.secret_policy else R.string.adapter_pending
                ),
            )
        val enabled =
            CheckBox(activity).apply {
                setText(R.string.enabled)
                isChecked = provider.enabled
                setTextColor(Color.WHITE)
                typeface = TvTypography.regular(activity)
                setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(6))
            }
        form.addView(enabled)

        val addressField =
            field(
                form,
                if (id == "iptv") R.string.playlist_url else R.string.service_url,
                secret = id != "iptv",
            )
        addressField.hint =
            activity.getString(
                if (provider.configured) R.string.keep_existing else R.string.optional_url
            )
        val tokenField = field(form, R.string.service_token, secret = true)
        tokenField.hint =
            activity.getString(
                if (provider.hasToken) R.string.keep_existing else R.string.optional_token
            )
        val clearCheckbox =
            CheckBox(activity).apply {
                setText(R.string.remove_token)
                setTextColor(Color.WHITE)
                typeface = TvTypography.regular(activity)
                setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(6))
            }
        form.addView(clearCheckbox)

        val userField = if (id == "jellyfin") field(form, R.string.service_user) else null
        val epgField = if (id == "iptv") field(form, R.string.epg_url) else null
        val mappingField =
            if (id == "iptv") field(form, R.string.epg_mapping, multiLine = true) else null
        if (id == "iptv") {
            form.addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.epg_mapping_help)
                    textSize = 13f
                    typeface = TvTypography.regular(activity)
                    setTextColor(ui.muted)
                    setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(8))
                }
            )
        }
        val catalogField = if (id == "stremio") field(form, R.string.catalog_id) else null
        val mediaField = if (id == "stremio") field(form, R.string.media_type) else null
        val discoveryModel = discovery()
        val discoveryPanel =
            DiscoveryPanel(activity, discoveryModel) { candidate ->
                addressField.setText(candidate)
            }
        TvTheme.styleDiscoveryPanel(discoveryPanel, ui, activity)
        form.addView(discoveryPanel)
        val codeField = field(form, R.string.operator_code, secret = true)

        var dialogRef: AlertDialog? = null
        val cancelBtn =
            TvTheme.createDarkButton(activity, ui, activity.getString(R.string.cancel)) {
                dialogRef?.dismiss()
            }
        lateinit var saveBtn: Button
        saveBtn =
            TvTheme.createPrimaryButton(activity, ui, activity.getString(R.string.save)) {
                val patch = linkedMapOf<String, String>()
                for ((key, view) in
                    arrayOf(
                        "url" to addressField,
                        "token" to tokenField,
                        "userId" to userField,
                        "epgUrl" to epgField,
                        "epgMappings" to mappingField,
                        "catalogId" to catalogField,
                        "mediaType" to mediaField,
                    )) {
                    if (view != null && view.text.toString().isNotBlank())
                        patch[key] = view.text.toString().trim()
                }
                if (clearCheckbox.isChecked) patch["token"] = ""
                val admin = codeField.text.toString()
                codeField.setText("")
                tokenField.setText("")
                addressField.setText("")
                epgField?.setText("")
                saveBtn.isEnabled = false
                model.saveProvider(
                    id,
                    ProviderPatch(enabled.isChecked, patch),
                    admin,
                    savedResult@{
                        if (closed || dialogRef?.isShowing != true) return@savedResult
                        dialogRef?.dismiss()
                        Toast.makeText(activity, R.string.saved, Toast.LENGTH_SHORT).show()
                        actions.refresh()
                        providers()
                    },
                    failed = { failure ->
                        if (!closed && dialogRef?.isShowing == true) {
                            saveBtn.isEnabled = true
                            error(failure)
                        }
                    },
                )
            }
        val footer =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                setPadding(0, ui.dp(8), 0, 0)
                addView(cancelBtn)
                addView(saveBtn)
            }
        card.addView(footer, LinearLayout.LayoutParams(-1, -2))

        val dialog = AlertDialog.Builder(activity).setView(card).create()
        dialogRef = dialog
        dialog.setOnDismissListener {
            codeField.setText("")
            tokenField.setText("")
            addressField.setText("")
            epgField?.setText("")
            discoveryModel.close()
        }
        own(dialog)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width =
            (activity.resources.displayMetrics.widthPixels * 0.78f).toInt().coerceAtMost(ui.dp(580))
        dialog.window?.setLayout(width, -2)
    }

    private fun advanced() {
        val audioFocusSummary = activity.getString(R.string.settings_audio_focus_summary)
        val playbackModeSummary =
            activity.getString(R.string.settings_current_value, model.preferences.playbackMode)
        val recoverySummary =
            activity.getString(
                if (model.preferences.automaticRecovery) R.string.enabled else R.string.disabled
            )
        val networkSummary =
            activity.getString(
                if (model.preferences.networkAdaptation) R.string.enabled else R.string.disabled
            )
        val surfaceSummary =
            activity.getString(R.string.settings_current_value, model.preferences.surfaceBackend)
        val systemMediaSummary =
            activity.getString(
                if (model.preferences.systemMediaControls) R.string.enabled else R.string.disabled
            )
        val hdmiSummary = activity.getString(R.string.settings_hdmi_summary)
        val dialSummary = activity.getString(R.string.settings_dial_summary)

        val labels =
            arrayOf(
                activity.getString(R.string.audio_focus_backend),
                activity.getString(R.string.playback_backend),
                activity.getString(R.string.automatic_recovery),
                activity.getString(R.string.network_adaptation),
                activity.getString(R.string.surface_backend),
                activity.getString(R.string.system_media_controls),
                activity.getString(R.string.hdmi_control),
                activity.getString(R.string.native_dial),
            )
        val keys =
            listOf(
                "audio_focus",
                "playback_backend",
                "automatic_recovery",
                "network_adaptation",
                "surface_backend",
                "system_media_controls",
                "hdmi_control",
                "native_dial",
            )
        val subtitles =
            listOf(
                audioFocusSummary,
                playbackModeSummary,
                recoverySummary,
                networkSummary,
                surfaceSummary,
                systemMediaSummary,
                hdmiSummary,
                dialSummary,
            )

        menus.show("advanced", R.string.advanced, labels, keys = keys, subtitles = subtitles) {
            index ->
            if (index == 0) actions.audioSettings()
            else if (index == 7) {
                val policy =
                    io.github.diegog0477.zombiebox.client.features.dial.platform.NativeDialPolicy
                val choices =
                    arrayOf(
                        activity.getString(R.string.disabled),
                        activity.getString(R.string.native_dial_enable),
                    )
                own(
                    TvTheme.showSingleChoiceDialog(
                        activity,
                        R.string.native_dial,
                        choices,
                        if (policy.enabled(activity)) 1 else 0,
                    ) { choice ->
                        try {
                            policy.enable(activity, choice == 1)
                        } catch (_: Exception) {
                            Toast.makeText(activity, R.string.native_dial_failed, Toast.LENGTH_LONG)
                                .show()
                        }
                        if (choice == 1)
                            Toast.makeText(activity, R.string.native_dial_scope, Toast.LENGTH_LONG)
                                .show()
                        advanced()
                    }
                )
            } else if (index == 6) {
                hdmiDialog?.dismiss()
                hdmiDialog =
                    io.github.diegog0477.zombiebox.client.features.hdmi.presentation.ui.HdmiDialog
                        .show(activity)
            } else if (index == 5) {
                val choices =
                    arrayOf(
                        activity.getString(R.string.disabled),
                        activity.getString(R.string.automatic),
                    )
                own(
                    TvTheme.showSingleChoiceDialog(
                        activity,
                        R.string.system_media_controls,
                        choices,
                        if (model.preferences.systemMediaControls) 1 else 0,
                    ) { choice ->
                        model.systemMediaControls(choice == 1)
                        advanced()
                    }
                )
            } else if (index == 4) {
                val modes = arrayOf("AUTO", "SURFACE", "TEXTURE")
                val choices =
                    arrayOf(
                        activity.getString(R.string.automatic),
                        "SurfaceView",
                        activity.getString(R.string.texture_verified),
                    )
                own(
                    TvTheme.showSingleChoiceDialog(
                        activity,
                        R.string.surface_backend,
                        choices,
                        modes.indexOf(model.preferences.surfaceBackend).coerceAtLeast(0),
                    ) { selected ->
                        model.surfaceBackend(modes[selected])
                        Toast.makeText(activity, R.string.surface_restart, Toast.LENGTH_LONG).show()
                        advanced()
                    }
                )
            } else if (index == 2 || index == 3) {
                val isRecovery = index == 2
                val titleRes =
                    if (isRecovery) R.string.automatic_recovery else R.string.network_adaptation
                val currentVal =
                    if (isRecovery) model.preferences.automaticRecovery
                    else model.preferences.networkAdaptation
                val choices =
                    arrayOf(
                        activity.getString(R.string.disabled),
                        activity.getString(R.string.enabled),
                    )
                own(
                    TvTheme.showSingleChoiceDialog(
                        activity,
                        titleRes,
                        choices,
                        if (currentVal) 1 else 0,
                    ) { choice ->
                        if (isRecovery) model.automaticRecovery(choice == 1)
                        else model.networkAdaptation(choice == 1)
                        advanced()
                    }
                )
            } else {
                val modes = arrayOf("AUTO", "DIRECT_PLAY", "REMUX", "TRANSCODE", "EXTERNAL_PLAYER")
                val choices =
                    arrayOf(
                            R.string.automatic,
                            R.string.direct_play,
                            R.string.remux,
                            R.string.transcode,
                            R.string.external_player,
                        )
                        .map { activity.getString(it) }
                        .toTypedArray()
                own(
                    TvTheme.showSingleChoiceDialog(
                        activity,
                        R.string.playback_backend,
                        choices,
                        modes.indexOf(model.preferences.playbackMode).coerceAtLeast(0),
                    ) { selection ->
                        model.playbackMode(modes[selection])
                        advanced()
                    }
                )
            }
        }
    }

    private fun language() {
        val currentLang = model.preferences.language
        val selectedIdx = if (currentLang == "es") 1 else 0
        own(
            TvTheme.showSingleChoiceDialog(
                activity,
                R.string.language,
                arrayOf("English", "Español"),
                selectedIdx,
            ) { which ->
                model.language(if (which == 0) "en" else "es")
                savePreferences()
                Toast.makeText(activity, R.string.restart_language, Toast.LENGTH_LONG).show()
                show()
            }
        )
    }

    private fun mode() {
        val modes = arrayOf("AUTO", "TV", "DOCKED", "HANDHELD")
        val labels =
            arrayOf(
                activity.getString(R.string.automatic),
                activity.getString(R.string.tv),
                activity.getString(R.string.docked),
                activity.getString(R.string.handheld),
            )
        val selectedIdx = modes.indexOf(model.preferences.mode).coerceAtLeast(0)
        own(
            TvTheme.showSingleChoiceDialog(
                activity,
                R.string.presentation_mode,
                labels,
                selectedIdx,
            ) { which ->
                model.mode(modes[which])
                savePreferences()
                actions.render()
                show()
            }
        )
    }

    private fun savePreferences() {
        if (paired())
            model.savePreferences(model.preferences.mode, model.preferences.language, error)
    }
}
