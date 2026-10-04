package io.github.diegog0477.zombiebox.client.features.companion.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Handler
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.companion.presentation.viewmodel.CompanionViewModel
import io.github.diegog0477.zombiebox.shared.companion.PairingRequest

/** Owns local consent UI. Remote input is never dispatched into these dialogs. */
@Suppress("DEPRECATION")
class CompanionController(
    private val activity: Activity,
    private val model: CompanionViewModel,
    private val paired: () -> Boolean,
    private val inputReady: () -> Boolean,
    private val command: (String, String, String, String) -> String,
    private val error: (Exception) -> Unit,
    private val textInputId: () -> String = { "" },
) {
    private val handler = Handler()
    private val ui = TvWidgets(activity)
    private var running = false
    private var closed = false
    private var consent: AlertDialog? = null
    private var invitation: AlertDialog? = null
    private val tick =
        object : Runnable {
            override fun run() {
                if (!running || closed) return
                if (paired())
                    model.poll(
                        inputReady() && consent == null && invitation == null,
                        { commands ->
                            for (value in commands) {
                                val result =
                                    if (
                                        !running ||
                                            value.remainingMs <= 0 ||
                                            !inputReady() ||
                                            consent != null ||
                                            invitation != null
                                    )
                                        "BUSY"
                                    else
                                        command(
                                            value.action,
                                            value.provider,
                                            value.text,
                                            value.inputId,
                                        )
                                model.acknowledge(value.id, result)
                            }
                        },
                        { pending ->
                            if (running && consent == null && pending.isNotEmpty())
                                showConsent(pending.first())
                        },
                        if (consent == null && invitation == null) textInputId() else "",
                    )
                handler.postDelayed(this, 1000)
            }
        }

    fun wake() {
        if (!running || closed) return
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun resume() {
        running = true
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun pause() {
        running = false
        handler.removeCallbacks(tick)
        if (paired()) model.poll(false, {}, {})
    }

    fun show() {
        val form = ui.column()
        form.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12))
        val information = ui.text(activity.getString(R.string.phone_pair_loading), 18f)
        form.addView(information)
        val image = ImageView(activity)
        form.addView(image, LinearLayout.LayoutParams(ui.dp(256), ui.dp(256)))
        val trusted = ui.column()
        form.addView(trusted)
        invitation?.dismiss()
        val dialog =
            AlertDialog.Builder(activity)
                .setTitle(R.string.phone_pair_title)
                .setView(ScrollView(activity).apply { addView(form) })
                .setNegativeButton(R.string.close, null)
                .create()
        invitation = dialog
        dialog.setOnDismissListener {
            image.setImageDrawable(null)
            invitation = null
        }
        dialog.show()
        model.invite(
            { value ->
                if (!dialog.isShowing || closed) return@invite
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(value.png, 0, value.png.size, bounds)
                if (bounds.outWidth in 1..512 && bounds.outHeight in 1..512)
                    image.setImageBitmap(
                        BitmapFactory.decodeByteArray(value.png, 0, value.png.size)
                    )
                information.text = activity.getString(R.string.phone_pair_qr_consent)
                // The invitation expires server-side; remove it locally instead of displaying stale
                // codes.
                handler.postDelayed(
                    {
                        if (dialog.isShowing) {
                            image.setImageDrawable(null)
                            information.setText(R.string.phone_pair_expired)
                        }
                    },
                    value.remainingMs,
                )
            },
            error,
        )
        model.inventory(
            { inventory ->
                if (!dialog.isShowing || closed) return@inventory
                for (grant in inventory.grants) trusted.addView(
                    ui.action(activity.getString(R.string.phone_forget, grant.name)) {
                        model.revoke(
                            grant.id,
                            {
                                dialog.dismiss()
                                show()
                            },
                            error,
                        )
                    }
                )
            },
            error,
        )
    }

    private fun showConsent(request: PairingRequest) {
        if (!activity.hasWindowFocus() && invitation == null) return
        val form = ui.column().apply { setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12)) }
        form.addView(
            ui.text(
                activity.getString(R.string.phone_pair_accept, request.name, request.comparison),
                20f,
            )
        )
        val ignore =
            CheckBox(activity).apply {
                setText(R.string.phone_ignore_day)
                isChecked = false
            }
        form.addView(ignore)
        val dialog =
            AlertDialog.Builder(activity)
                .setTitle(R.string.phone_pair_accept_title)
                .setView(form)
                .setPositiveButton(R.string.phone_allow, null)
                .setNegativeButton(R.string.phone_deny, null)
                .create()
        consent = dialog
        dialog.setOnDismissListener { consent = null }
        dialog.setOnShowListener {
            val allow = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val deny = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            fun decide(accept: Boolean) {
                allow.isEnabled = false
                deny.isEnabled = false
                model.decide(
                    request.id,
                    accept,
                    { failure ->
                        if (dialog.isShowing) {
                            allow.isEnabled = true
                            deny.isEnabled = true
                        }
                        error(failure)
                    },
                    ignore24h = !accept && ignore.isChecked,
                    done = { if (dialog.isShowing) dialog.dismiss() },
                )
            }
            allow.setOnClickListener { decide(true) }
            deny.setOnClickListener { decide(false) }
        }
        dialog.show()
    }

    fun close() {
        closed = true
        running = false
        consent?.dismiss()
        invitation?.dismiss()
        handler.removeCallbacksAndMessages(null)
        model.close()
    }
}
