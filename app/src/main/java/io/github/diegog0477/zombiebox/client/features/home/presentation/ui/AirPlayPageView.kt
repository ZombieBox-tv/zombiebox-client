package io.github.diegog0477.zombiebox.client.features.home.presentation.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeSnapshot

/** AirPlay is a receiver, so its tab shows connection and live-session actions, not a catalog. */
class AirPlayPageView(
    private val context: Context,
    private val ui: TvWidgets,
    private val actions: HomeActions,
) {
    fun render(
        parent: LinearLayout,
        snapshot: HomeSnapshot,
        focusRows: ArrayList<Pair<String, ViewGroup>>,
    ) {
        val moduleState = snapshot.modules.firstOrNull { it.id == "airplay" }?.state
        val sessions = snapshot.sections.flatMap { it.items }.filter { it.provider == "airplay" }
        val audio = sessions.firstOrNull { it.id == "airplay-audio" }
        val mirror = sessions.firstOrNull { it.id == "airplay-live" }

        val hero =
            ui.column().apply {
                setPadding(ui.dp(22), ui.dp(20), ui.dp(22), ui.dp(20))
                setBackgroundDrawable(ui.box(ui.panel, ui.providerAccent("airplay")))
            }
        val heading = ui.row()
        heading.addView(
            ServiceMarkView(context, "airplay", ui.providerAccent("airplay")),
            LinearLayout.LayoutParams(ui.dp(52), ui.dp(52)).apply { rightMargin = ui.dp(14) },
        )
        heading.addView(
            ui.text(context.getString(R.string.airplay), 30f).apply { typeface = ui.bold },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        heading.addView(
            ui.text(statusLabel(moduleState), 13f, statusColor(moduleState)).apply {
                gravity = Gravity.CENTER
                setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6))
                setBackgroundDrawable(ui.box(ui.background, statusColor(moduleState)))
            }
        )
        hero.addView(heading)
        hero.addView(ui.text(context.getString(R.string.airplay_intro), 17f, ui.muted))
        hero.addView(ui.text(statusDetail(moduleState), 14f, ui.muted))

        val controls = ui.row().apply { setPadding(0, ui.dp(12), 0, 0) }
        controls.addView(
            ui.primary(R.string.airplay_show_pin) { actions.airplayPairing() }
                .apply { tag = "airplay:show_pin" }
        )
        controls.addView(
            ui.button(R.string.airplay_audio_options) { actions.airplayAudioOptions() }
                .apply { tag = "airplay:audio_options" }
        )
        if (moduleState != "HEALTHY" && moduleState != "READY") {
            controls.addView(
                ui.button(R.string.airplay_configure) { actions.airplayConfigure() }
                    .apply { tag = "airplay:configure" }
            )
        }
        hero.addView(controls)
        parent.addView(
            hero,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = ui.dp(12)
                bottomMargin = ui.dp(12)
            },
        )
        focusRows.add(Pair("airplay:controls", controls))

        parent.addView(
            ui.text(context.getString(R.string.airplay_receive_title), 18f).apply {
                typeface = ui.bold
                setPadding(ui.dp(4), ui.dp(8), 0, ui.dp(8))
            }
        )
        sessionCard(
            parent,
            focusRows,
            "audio",
            R.string.airplay_audio_title,
            audio,
            R.string.airplay_audio_idle,
            R.string.airplay_audio_active,
            R.string.airplay_open_player,
        )
        sessionCard(
            parent,
            focusRows,
            "mirror",
            R.string.airplay_mirror_title,
            mirror,
            R.string.airplay_mirror_idle,
            R.string.airplay_mirror_active,
            R.string.airplay_open_screen,
        )
    }

    private fun sessionCard(
        parent: LinearLayout,
        focusRows: ArrayList<Pair<String, ViewGroup>>,
        key: String,
        title: Int,
        item: MediaItem?,
        idleDetail: Int,
        activeDetail: Int,
        openLabel: Int,
    ) {
        val card =
            ui.column().apply {
                setPadding(ui.dp(18), ui.dp(13), ui.dp(18), ui.dp(13))
                setBackgroundDrawable(ui.box(ui.panel, Color.rgb(46, 55, 59)))
            }
        val heading = ui.row()
        heading.addView(
            ui.text(context.getString(title), 20f).apply { typeface = ui.bold },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        heading.addView(
            ui.text(
                context.getString(
                    if (item?.playable == true) R.string.playing else R.string.airplay_waiting
                ),
                13f,
                if (item?.playable == true) ui.green else ui.muted,
            )
        )
        card.addView(heading)
        val description =
            when {
                item?.playable != true -> context.getString(idleDetail)
                key == "mirror" || item.title == "AirPlay audio" -> context.getString(activeDetail)
                else ->
                    listOf(item.title, item.subtitle).filter { it.isNotBlank() }.joinToString(" · ")
            }
        card.addView(ui.text(description, 15f, ui.muted).apply { maxLines = 2 })
        if (item?.playable == true) {
            val controls = ui.row()
            controls.addView(
                ui.button(openLabel) { actions.play(item) }.apply { tag = "airplay:$key:open" }
            )
            card.addView(controls)
            focusRows.add(Pair("airplay:$key", controls))
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(10) })
    }

    private fun statusLabel(state: String?): String =
        context.getString(
            when (state) {
                "HEALTHY",
                "READY" -> R.string.ready
                "DISABLED" -> R.string.disabled
                "DEGRADED",
                "UNAVAILABLE",
                "AUTH_REQUIRED" -> R.string.unavailable
                else -> R.string.loading
            }
        )

    private fun statusDetail(state: String?): String =
        context.getString(
            when (state) {
                "HEALTHY",
                "READY" -> R.string.airplay_state_ready
                "DISABLED" -> R.string.airplay_state_disabled
                "DEGRADED",
                "UNAVAILABLE",
                "AUTH_REQUIRED" -> R.string.airplay_state_unavailable
                else -> R.string.airplay_state_loading
            }
        )

    private fun statusColor(state: String?): Int =
        if (state == "HEALTHY" || state == "READY") ui.green else ui.muted
}
