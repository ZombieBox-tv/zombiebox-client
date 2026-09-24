package io.github.diegog0477.zombiebox.client.features.settings.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.text.InputType
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
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

/** Dialog inputs/rendering only. Provider JSON and persistence stay behind the ViewModel. */
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

    private fun AlertDialog.Builder.showOwned(): AlertDialog = own(show())

    fun close() {
        closed = true
        menus.close()
        owned.forEach { it.dismiss() }
        owned.clear()
        hdmiDialog?.dismiss()
        hdmiDialog = null
    }

    private fun field(parent: LinearLayout, label: Int, secret: Boolean = false): EditText {
        parent.addView(ui.text(activity.getString(label), 14f, ui.muted))
        return EditText(activity).apply {
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(ui.muted)
            setBackgroundDrawable(ui.box(ui.panel, ui.muted))
            setPadding(ui.dp(12), 0, ui.dp(12), 0)
            inputType =
                if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSaveEnabled = false
            parent.addView(this, LinearLayout.LayoutParams(-1, ui.dp(48)))
        }
    }

    private fun dialogForm(): Pair<ScrollView, LinearLayout> {
        val form = ui.column()
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(12))
        val scroll = ScrollView(activity)
        scroll.addView(form)
        return Pair(scroll, form)
    }

    fun pairing() {
        val (scroll, form) = dialogForm()
        form.addView(ui.text(activity.getString(R.string.lan_notice), 14f, ui.muted))
        val address = field(form, R.string.gateway_address)
        address.setText(address())
        address.hint = activity.getString(R.string.gateway_hint)
        val discoveryModel = discovery()
        form.addView(
            DiscoveryPanel(activity, discoveryModel) { candidate -> address.setText(candidate) }
        )
        val code = field(form, R.string.operator_code, true)
        val dialog =
            AlertDialog.Builder(activity)
                .setTitle(R.string.connect_gateway)
                .setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.connect, null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val candidate = address.text.toString().trim().trimEnd('/')
                if (!model.validAddress(candidate)) {
                    address.error = activity.getString(R.string.invalid_address)
                    return@setOnClickListener
                }
                val pairingCode = code.text.toString()
                code.setText("")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                model.pair(
                    candidate,
                    pairingCode,
                    pairedResult@{ profile ->
                        if (closed || !dialog.isShowing) return@pairedResult
                        menus.close()
                        actions.paired(profile)
                        dialog.dismiss()
                    },
                    { failure ->
                        if (!closed && dialog.isShowing) {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            error(failure)
                        }
                    },
                )
            }
        }
        dialog.setOnDismissListener {
            code.setText("")
            discoveryModel.close()
        }
        own(dialog)
        dialog.show()
        discoveryModel.refresh()
    }

    fun show() {
        menus.show(
            "settings",
            R.string.settings,
            arrayOf(
                activity.getString(R.string.connect_gateway),
                activity.getString(R.string.configure_services),
                activity.getString(R.string.diagnostics),
                activity.getString(R.string.language),
                activity.getString(R.string.presentation_mode),
                activity.getString(R.string.advanced),
                activity.getString(R.string.receive_cast),
                activity.getString(R.string.gateway_services),
                activity.getString(R.string.youtube_receiver),
                activity.getString(R.string.media_receiver),
                activity.getString(R.string.media_languages),
                activity.getString(R.string.resume_previous),
                activity.getString(R.string.phone_pair_title),
            ),
        ) { index ->
            when (index) {
                0 -> pairing()
                1 -> providers()
                2 -> actions.diagnostics()
                3 -> language()
                4 -> mode()
                5 -> advanced()
                6 -> actions.receiverSettings()
                7 -> activity.startActivity(Intent(activity, ServicesActivity::class.java))
                8 -> actions.youtubeReceiverSettings()
                9 -> actions.mediaReceiverSettings()
                10 -> mediaLanguages()
                11 -> {
                    menus.close()
                    actions.resumePlayback()
                }
                12 -> actions.companionSettings()
            }
        }
    }

    fun devices() {
        menus.show(
            "devices",
            R.string.devices,
            arrayOf(
                activity.getString(R.string.connect_gateway),
                activity.getString(R.string.phone_pair_title),
                activity.getString(R.string.youtube_receiver),
                activity.getString(R.string.media_receiver),
                activity.getString(R.string.receive_cast),
            ),
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
                menus.show(
                    "providers",
                    R.string.configure_services,
                    labels,
                    providers.map { it.id },
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
            AlertDialog.Builder(activity)
                .setTitle(ui.serviceTitle(id))
                .setMessage(R.string.server_managed)
                .setPositiveButton(R.string.close, null)
                .showOwned()
            return
        }
        val (scroll, form) = dialogForm()
        form.addView(
            ui.text(
                activity.getString(
                    if (provider.implemented) R.string.secret_policy else R.string.adapter_pending
                ),
                14f,
                ui.muted,
            )
        )
        val enabled =
            CheckBox(activity).apply {
                setText(R.string.enabled)
                isChecked = provider.enabled
                setTextColor(Color.WHITE)
            }
        form.addView(enabled)
        val address =
            field(form, if (id == "iptv") R.string.playlist_url else R.string.service_url, true)
        address.hint =
            activity.getString(
                if (provider.configured) R.string.keep_existing else R.string.optional_url
            )
        val token = field(form, R.string.service_token, true)
        token.hint =
            activity.getString(
                if (provider.hasToken) R.string.keep_existing else R.string.optional_token
            )
        val clear =
            CheckBox(activity).apply {
                setText(R.string.remove_token)
                setTextColor(Color.WHITE)
            }
        form.addView(clear)
        val user = if (id == "jellyfin") field(form, R.string.service_user) else null
        val epg = if (id == "iptv") field(form, R.string.epg_url, true) else null
        val mapping =
            if (id == "iptv")
                field(form, R.string.epg_mapping).apply {
                    setSingleLine(false)
                    minLines = 2
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                }
            else null
        if (id == "iptv")
            form.addView(ui.text(activity.getString(R.string.epg_mapping_help), 13f, ui.muted))
        val catalog = if (id == "stremio") field(form, R.string.catalog_id) else null
        val media = if (id == "stremio") field(form, R.string.media_type) else null
        val discoveryModel = discovery()
        form.addView(
            DiscoveryPanel(activity, discoveryModel) { candidate -> address.setText(candidate) }
        )
        val code = field(form, R.string.operator_code, true)
        val dialog =
            AlertDialog.Builder(activity)
                .setTitle(ui.serviceTitle(id))
                .setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val patch = linkedMapOf<String, String>()
                for ((key, view) in
                    arrayOf(
                        "url" to address,
                        "token" to token,
                        "userId" to user,
                        "epgUrl" to epg,
                        "epgMappings" to mapping,
                        "catalogId" to catalog,
                        "mediaType" to media,
                    )) {
                    if (view != null && view.text.toString().isNotBlank())
                        patch[key] = view.text.toString().trim()
                }
                if (clear.isChecked) patch["token"] = ""
                val admin = code.text.toString()
                code.setText("")
                token.setText("")
                address.setText("")
                epg?.setText("")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                model.saveProvider(
                    id,
                    ProviderPatch(enabled.isChecked, patch),
                    admin,
                    savedResult@{
                        if (closed || !dialog.isShowing) return@savedResult
                        dialog.dismiss()
                        Toast.makeText(activity, R.string.saved, Toast.LENGTH_SHORT).show()
                        actions.refresh()
                    },
                    failed = { failure ->
                        if (!closed && dialog.isShowing) {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            error(failure)
                        }
                    },
                )
            }
        }
        dialog.setOnDismissListener {
            code.setText("")
            token.setText("")
            address.setText("")
            epg?.setText("")
            discoveryModel.close()
        }
        own(dialog)
        dialog.show()
    }

    private fun advanced() {
        menus.show(
            "advanced",
            R.string.advanced,
            arrayOf(
                activity.getString(R.string.audio_focus_backend),
                activity.getString(R.string.playback_backend),
                activity.getString(R.string.automatic_recovery),
                activity.getString(R.string.network_adaptation),
                activity.getString(R.string.surface_backend),
                activity.getString(R.string.system_media_controls),
                activity.getString(R.string.hdmi_control),
                activity.getString(R.string.native_dial),
            ),
        ) { index ->
            if (index == 0) actions.audioSettings()
            else if (index == 7) {
                val policy =
                    io.github.diegog0477.zombiebox.client.features.dial.platform.NativeDialPolicy
                AlertDialog.Builder(activity)
                    .setTitle(R.string.native_dial)
                    .setSingleChoiceItems(
                        arrayOf(
                            activity.getString(R.string.disabled),
                            activity.getString(R.string.native_dial_enable),
                        ),
                        if (policy.enabled(activity)) 1 else 0,
                    ) { dialog, choice ->
                        try {
                            policy.enable(activity, choice == 1)
                        } catch (_: Exception) {
                            Toast.makeText(activity, R.string.native_dial_failed, Toast.LENGTH_LONG)
                                .show()
                        }
                        dialog.dismiss()
                        if (choice == 1)
                            Toast.makeText(activity, R.string.native_dial_scope, Toast.LENGTH_LONG)
                                .show()
                    }
                    .setNegativeButton(R.string.close, null)
                    .showOwned()
            } else if (index == 6) {
                hdmiDialog?.dismiss()
                hdmiDialog =
                    io.github.diegog0477.zombiebox.client.features.hdmi.presentation.ui.HdmiDialog
                        .show(activity)
            } else if (index == 5) {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.system_media_controls)
                    .setSingleChoiceItems(
                        arrayOf(
                            activity.getString(R.string.disabled),
                            activity.getString(R.string.automatic),
                        ),
                        if (model.preferences.systemMediaControls) 1 else 0,
                    ) { dialog, choice ->
                        model.systemMediaControls(choice == 1)
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.close, null)
                    .showOwned()
            } else if (index == 4) {
                val modes = arrayOf("AUTO", "SURFACE", "TEXTURE")
                AlertDialog.Builder(activity)
                    .setTitle(R.string.surface_backend)
                    .setSingleChoiceItems(
                        arrayOf(
                            activity.getString(R.string.automatic),
                            "SurfaceView",
                            activity.getString(R.string.texture_verified),
                        ),
                        modes.indexOf(model.preferences.surfaceBackend),
                    ) { dialog, selected ->
                        model.surfaceBackend(modes[selected])
                        dialog.dismiss()
                        Toast.makeText(activity, R.string.surface_restart, Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton(R.string.close, null)
                    .showOwned()
            } else if (index == 2 || index == 3) {
                AlertDialog.Builder(activity)
                    .setTitle(
                        if (index == 2) R.string.automatic_recovery else R.string.network_adaptation
                    )
                    .setSingleChoiceItems(
                        arrayOf(
                            activity.getString(R.string.disabled),
                            activity.getString(R.string.enabled),
                        ),
                        if (
                            if (index == 2) model.preferences.automaticRecovery
                            else model.preferences.networkAdaptation
                        )
                            1
                        else 0,
                    ) { dialog, choice ->
                        if (index == 2) model.automaticRecovery(choice == 1)
                        else model.networkAdaptation(choice == 1)
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.close, null)
                    .showOwned()
            } else {
                val modes = arrayOf("AUTO", "DIRECT_PLAY", "REMUX", "TRANSCODE", "EXTERNAL_PLAYER")
                val labels =
                    arrayOf(
                            R.string.automatic,
                            R.string.direct_play,
                            R.string.remux,
                            R.string.transcode,
                            R.string.external_player,
                        )
                        .map { activity.getString(it) }
                        .toTypedArray()
                AlertDialog.Builder(activity)
                    .setTitle(R.string.playback_backend)
                    .setSingleChoiceItems(labels, modes.indexOf(model.preferences.playbackMode)) {
                        dialog,
                        selection ->
                        model.playbackMode(modes[selection])
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.close, null)
                    .showOwned()
            }
        }
    }

    private fun language() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.language)
            .setItems(arrayOf("English", "Español")) { _, which ->
                model.language(if (which == 0) "en" else "es")
                savePreferences()
                Toast.makeText(activity, R.string.restart_language, Toast.LENGTH_LONG).show()
            }
            .showOwned()
    }

    private fun mode() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.presentation_mode)
            .setItems(
                arrayOf(
                    activity.getString(R.string.automatic),
                    activity.getString(R.string.tv),
                    activity.getString(R.string.docked),
                    activity.getString(R.string.handheld),
                )
            ) { _, which ->
                model.mode(arrayOf("AUTO", "TV", "DOCKED", "HANDHELD")[which])
                savePreferences()
                actions.render()
            }
            .showOwned()
    }

    private fun savePreferences() {
        if (paired())
            model.savePreferences(model.preferences.mode, model.preferences.language, error)
    }
}
