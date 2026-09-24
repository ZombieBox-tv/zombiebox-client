package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.YouTubeAccountPage
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.YouTubeAccountStatus
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.viewmodel.CatalogViewModel
import io.github.diegog0477.zombiebox.shared.GatewayFailure
import java.util.Locale

/** Renders semantic account state in the TV shell; credentials remain on the Gateway. */
class YouTubeAccountDialogs(
    private val activity: Activity,
    private val model: CatalogViewModel,
    private val open: (MediaItem) -> Unit,
    private val failed: (Exception) -> Unit,
) {
    private val ui = TvWidgets(activity)
    private val accent = ui.providerAccent("youtube")
    private var dialog: AlertDialog? = null
    private var generation = 0
    private var lastStatus: YouTubeAccountStatus? = null

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
        lastStatus = status
        render(activity.getString(R.string.youtube_account)) { body ->
            when {
                !status.configured -> {
                    message(body, activity.getString(R.string.youtube_account_unconfigured))
                    action(body, R.string.close) { close() }
                }
                status.connected -> {
                    action(body, R.string.youtube_subscriptions) {
                        page("subscriptions", "", emptyList())
                    }
                    action(body, R.string.youtube_playlists) { page("playlists", "", emptyList()) }
                    action(body, R.string.youtube_disconnect) { confirmDisconnect() }
                    action(body, R.string.close) { close() }
                }
                status.prompt != null -> {
                    val prompt = status.prompt
                    message(
                        body,
                        activity.getString(
                            R.string.youtube_account_prompt,
                            prompt.verificationUrl,
                            prompt.code,
                            prompt.intervalSeconds,
                        ),
                    )
                    action(body, R.string.youtube_check_authorization) { poll() }
                    action(body, R.string.close) { close() }
                }
                else -> {
                    message(body, activity.getString(R.string.youtube_account_disconnected))
                    action(body, R.string.youtube_connect) { start() }
                    action(body, R.string.close) { close() }
                }
            }
        }
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
        render(activity.getString(R.string.youtube_disconnect)) { body ->
            val code =
                EditText(activity).apply {
                    inputType =
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    hint = activity.getString(R.string.operator_code)
                    setTextColor(Color.WHITE)
                    setHintTextColor(ui.muted)
                    setBackgroundDrawable(ui.focusBackground(accent))
                    setPadding(ui.dp(16), 0, ui.dp(16), 0)
                    typeface = TvTypography.regular(activity)
                }
            body.addView(code, LinearLayout.LayoutParams(-1, ui.dp(56)))
            action(body, R.string.youtube_disconnect) {
                val ticket = begin()
                model.disconnectYouTubeAccount(
                    code.text.toString().trim(),
                    { if (ticket == generation) showStatus(it) },
                    { if (ticket == generation) failure(it) },
                )
            }
            action(body, R.string.cancel) { lastStatus?.let(::showStatus) ?: show() }
            code
        }
    }

    private fun showPage(
        kind: String,
        token: String,
        previous: List<String>,
        page: YouTubeAccountPage,
    ) {
        val label =
            if (kind == "subscriptions") R.string.youtube_subscriptions
            else R.string.youtube_playlists
        render(activity.getString(label)) { body ->
            if (page.items.isEmpty()) message(body, activity.getString(R.string.catalog_empty))
            for (item in page.items) {
                action(body, item.title) {
                    close()
                    open(item)
                }
            }
            if (page.nextPageToken.isNotEmpty() && previous.size < 20) {
                action(body, R.string.next_page) {
                    page(kind, page.nextPageToken, previous + token)
                }
            }
            action(body, R.string.back) {
                if (previous.isNotEmpty()) page(kind, previous.last(), previous.dropLast(1))
                else lastStatus?.let(::showStatus) ?: show()
            }
            action(body, R.string.close) { close() }
        }
    }

    private fun begin(): Int {
        val ticket = ++generation
        render(activity.getString(R.string.youtube_account)) { body ->
            message(body, activity.getString(R.string.loading))
            action(body, R.string.cancel) { close() }
        }
        return ticket
    }

    private fun failure(error: Exception) {
        close()
        failed(error)
    }

    private fun message(body: LinearLayout, value: String) {
        body.addView(ui.text(value, 18f, ui.muted).apply { setPadding(0, ui.dp(10), 0, ui.dp(20)) })
    }

    private fun action(body: LinearLayout, label: Int, click: () -> Unit): Button =
        action(body, activity.getString(label), click)

    private fun action(body: LinearLayout, label: String, click: () -> Unit): Button {
        val button = ui.action(label, accent, click).apply { maxLines = 2 }
        body.addView(
            button,
            LinearLayout.LayoutParams(-1, ui.dp(58)).apply { topMargin = ui.dp(8) },
        )
        return button
    }

    private fun render(title: String, populate: (LinearLayout) -> View?) {
        dialog?.dismiss()
        val body =
            ui.column().apply {
                setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(32))
                addView(
                    ui.text(activity.getString(R.string.youtube).uppercase(Locale.US), 13f, accent)
                        .apply { typeface = ui.bold }
                )
                addView(
                    ui.text(title, 30f).apply {
                        typeface = ui.bold
                        setPadding(0, ui.dp(4), 0, ui.dp(18))
                    }
                )
            }
        populate(body)
        val scroll =
            ScrollView(activity).apply {
                isFillViewport = true
                addView(body)
            }
        val frame =
            FrameLayout(activity).apply {
                setBackgroundColor(ui.background)
                val width = minOf(activity.resources.displayMetrics.widthPixels, ui.dp(820))
                addView(scroll, FrameLayout.LayoutParams(width, -1, Gravity.CENTER_HORIZONTAL))
            }
        val next = AlertDialog.Builder(activity).setView(frame).create()
        next.setOnCancelListener { close() }
        dialog = next
        next.show()
        next.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(ui.background))
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        frame.post { if (next.isShowing) findFirstFocusable(body)?.requestFocus() }
    }

    private fun findFirstFocusable(body: LinearLayout): View? {
        for (index in 0 until body.childCount) {
            val child = body.getChildAt(index)
            if (child.isFocusable) return child
        }
        return null
    }
}
