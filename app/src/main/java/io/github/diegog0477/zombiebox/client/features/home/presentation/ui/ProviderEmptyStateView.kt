package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets

/**
 * Truthful, polished empty state for provider tabs with no available content items. Never invents
 * posters, feeds, or account state.
 */
class ProviderEmptyStateView(
    context: Context,
    private val ui: TvWidgets,
    private val provider: String,
    private val actions: HomeActions,
    private val serviceState: String? = null,
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(28))

        val accentColor = ui.providerAccent(provider)
        setBackgroundDrawable(
            GradientDrawable().apply {
                setColor(Color.rgb(18, 24, 26))
                cornerRadius = ui.dp(10).toFloat()
                setStroke(ui.dp(1), Color.rgb(36, 44, 46))
            }
        )

        // Service Mark
        val mark = ServiceMarkView(context, provider, accentColor)
        val markWidth = if (provider == "plex") ui.dp(84) else ui.dp(48)
        addView(mark, LayoutParams(markWidth, ui.dp(48)).apply { bottomMargin = ui.dp(12) })

        // Title
        val titleView =
            ui.text(ui.serviceTitle(provider), 20f).apply {
                typeface = ui.bold
                gravity = Gravity.CENTER
            }
        addView(titleView)

        // Truthful description
        val messageText = truthfulMessage(provider)
        val messageView =
            ui.text(messageText, 13f, ui.muted).apply {
                gravity = Gravity.CENTER
                setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(16))
            }
        addView(messageView)
    }

    fun attachActions(focusRows: ArrayList<Pair<String, ViewGroup>>) {
        val actionsRow = ui.row().apply { gravity = Gravity.CENTER }

        if (serviceState == "DISABLED") {
            actionsRow.addView(
                ui.button(R.string.configure_services) { actions.providers() }
                    .apply { tag = "$provider:empty:settings" }
            )
        } else if (
            serviceState != "UNAVAILABLE" && provider in setOf("plex", "stremio", "jellyfin")
        ) {
            val catalogBtn =
                ui.button(R.string.browse_library) { actions.catalog(provider) }
                    .apply { tag = "$provider:empty:catalog" }
            actionsRow.addView(catalogBtn)
        }

        // Optional receiver/pairing actions
        if (provider == "youtube" && serviceState != "DISABLED") {
            val receiverBtn =
                ui.button(R.string.youtube_receiver) { actions.youtubeReceiver() }
                    .apply { tag = "$provider:empty:receiver" }
            actionsRow.addView(receiverBtn)
        } else if (provider == "airplay" && serviceState != "DISABLED") {
            val pinBtn =
                ui.primary(R.string.airplay_show_pin) { actions.airplayPairing() }
                    .apply { tag = "$provider:empty:airplay_pin" }
            actionsRow.addView(pinBtn)
        }

        if (actionsRow.childCount > 0) {
            addView(actionsRow)
            focusRows.add(Pair("$provider:empty", actionsRow))
        }
    }

    private fun truthfulMessage(provider: String): String =
        when (serviceState) {
            "DISABLED" -> context.getString(R.string.provider_service_disabled)
            "UNAVAILABLE" -> context.getString(R.string.provider_service_unavailable)
            else ->
                when (provider) {
                    "youtube" -> context.getString(R.string.youtube_empty_home)
                    "plex",
                    "stremio",
                    "jellyfin" -> context.getString(R.string.provider_empty_catalog)
                    "iptv" -> context.getString(R.string.catalog_empty)
                    "spotify" -> context.getString(R.string.nothing_playing)
                    "airplay" -> context.getString(R.string.airplay_pairing_detail).trim()
                    else -> context.getString(R.string.catalog_empty)
                }
        }
}
