package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.YouTubeAccountPage
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.YouTubeAccountStatus
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.viewmodel.CatalogViewModel
import io.github.diegog0477.zombiebox.shared.GatewayFailure

/** Presents only semantic account data; OAuth tokens and Google DTOs stay on the Gateway. */
class YouTubeAccountDialogs(
    private val activity: Activity,
    private val model: CatalogViewModel,
    private val open: (MediaItem) -> Unit,
    private val failed: (Exception) -> Unit,
) {
    private var dialog: AlertDialog? = null
    private var generation = 0
    val visible: Boolean
        get() = dialog?.isShowing == true

    fun close() {
        generation++
        dialog?.dismiss()
        dialog = null
    }

    fun show() {
        val ticket = begin()
        model.youtubeAccount(
            { if (ticket == generation) showStatus(it) },
            { if (ticket == generation) failure(it) },
        )
    }

    private fun start() {
        val ticket = begin()
        model.startYouTubeAccount(
            { if (ticket == generation) showStatus(it) },
            { if (ticket == generation) failure(it) },
        )
    }

    private fun poll() {
        val ticket = begin()
        model.pollYouTubeAccount(
            { if (ticket == generation) showStatus(it) },
            {
                if (ticket == generation) {
                    if (it is GatewayFailure && (it.status == 429 || it.status == 410)) show()
                    else failure(it)
                }
            },
        )
    }

    private fun showStatus(status: YouTubeAccountStatus) {
        dialog?.dismiss()
        val builder = AlertDialog.Builder(activity).setTitle(R.string.youtube_account)
        when {
            !status.configured ->
                builder
                    .setMessage(R.string.youtube_account_unconfigured)
                    .setNegativeButton(R.string.close, null)
            status.connected -> {
                val choices =
                    arrayOf(
                        activity.getString(R.string.youtube_subscriptions),
                        activity.getString(R.string.youtube_playlists),
                        activity.getString(R.string.youtube_disconnect),
                    )
                builder
                    .setItems(choices) { _, index ->
                        when (index) {
                            0 -> page("subscriptions", "", emptyList())
                            1 -> page("playlists", "", emptyList())
                            else -> confirmDisconnect()
                        }
                    }
                    .setNegativeButton(R.string.close, null)
            }
            status.prompt != null -> {
                val prompt = status.prompt
                builder
                    .setMessage(
                        activity.getString(
                            R.string.youtube_account_prompt,
                            prompt.verificationUrl,
                            prompt.code,
                            prompt.intervalSeconds,
                        )
                    )
                    .setPositiveButton(R.string.youtube_check_authorization) { _, _ -> poll() }
                    .setNegativeButton(R.string.close, null)
            }
            else ->
                builder
                    .setMessage(R.string.youtube_account_disconnected)
                    .setPositiveButton(R.string.youtube_connect) { _, _ -> start() }
                    .setNegativeButton(R.string.close, null)
        }
        dialog = builder.show()
    }

    private fun page(kind: String, token: String, previous: List<String>) {
        val ticket = begin()
        model.youtubeAccountPage(
            kind,
            token,
            { if (ticket == generation) showPage(kind, token, previous, it) },
            { if (ticket == generation) failure(it) },
        )
    }

    private fun confirmDisconnect() {
        val code =
            EditText(activity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                hint = activity.getString(R.string.operator_code)
            }
        dialog =
            AlertDialog.Builder(activity)
                .setTitle(R.string.youtube_disconnect)
                .setView(code)
                .setPositiveButton(R.string.youtube_disconnect) { _, _ ->
                    val ticket = begin()
                    model.disconnectYouTubeAccount(
                        code.text.toString(),
                        { if (ticket == generation) showStatus(it) },
                        { if (ticket == generation) failure(it) },
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun showPage(
        kind: String,
        token: String,
        previous: List<String>,
        page: YouTubeAccountPage,
    ) {
        dialog?.dismiss()
        val label =
            if (kind == "subscriptions") R.string.youtube_subscriptions
            else R.string.youtube_playlists
        val names = page.items.map { it.title }.toTypedArray()
        val builder = AlertDialog.Builder(activity).setTitle(label)
        if (names.isEmpty()) builder.setMessage(R.string.catalog_empty)
        else
            builder.setItems(names) { _, index ->
                close()
                open(page.items[index])
            }
        if (page.nextPageToken.isNotEmpty() && previous.size < 20)
            builder.setPositiveButton(R.string.next_page) { _, _ ->
                page(kind, page.nextPageToken, previous + token)
            }
        if (previous.isNotEmpty())
            builder.setNeutralButton(R.string.back) { _, _ ->
                page(kind, previous.last(), previous.dropLast(1))
            }
        builder.setNegativeButton(R.string.close, null)
        dialog = builder.show()
    }

    private fun begin(): Int {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(activity).setMessage(R.string.loading).show()
        return ++generation
    }

    private fun failure(error: Exception) {
        close()
        failed(error)
    }
}
