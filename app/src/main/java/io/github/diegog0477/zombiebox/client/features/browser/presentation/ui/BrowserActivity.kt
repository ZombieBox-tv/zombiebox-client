package io.github.diegog0477.zombiebox.client.features.browser.presentation.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.platform.WindowInsetsPolicy
import io.github.diegog0477.zombiebox.client.core.ui.RemoteFocus
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.browser.data.GatewayBrowserRepository
import io.github.diegog0477.zombiebox.client.features.browser.presentation.viewmodel.BrowserViewModel
import io.github.diegog0477.zombiebox.shared.GatewayApi
import java.util.concurrent.Executors

class BrowserActivity : Activity() {
    private val ui by lazy { TvWidgets(this) }
    private val api = GatewayApi()
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler()
    private lateinit var model: BrowserViewModel
    private lateinit var addressField: EditText
    private lateinit var pageView: ImageView
    private lateinit var pageControl: View
    private val focus = RemoteFocus()
    private val poll =
        object : Runnable {
            override fun run() {
                model.refresh()
                handler.postDelayed(this, 3000)
            }
        }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val prefs = getSharedPreferences("zombie", MODE_PRIVATE)
        api.configure(
            prefs.getString("gateway", "")!!,
            prefs.getString("device", "")!!,
            prefs.getString("token", "")!!,
        )
        model =
            BrowserViewModel(
                GatewayBrowserRepository(api),
                { work -> executor.execute { work() } },
                { work -> handler.post { work() } },
            )
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(10, 15, 16))
                setPadding(12, 12, 12, 12)
            }
        val toolbar = LinearLayout(this)
        val address =
            EditText(this).apply {
                tag = "address"
                setHint(R.string.browser_address)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.LTGRAY)
                setBackgroundDrawable(ui.box(ui.panel, ui.muted))
                setPadding(ui.dp(12), 0, ui.dp(12), 0)
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
        addressField = address
        address.setText(saved?.getString("address") ?: "")
        toolbar.addView(address, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(
            Button(this).apply {
                tag = "open"
                setText(R.string.browser_open)
                setOnClickListener { model.open(address.text.toString().trim()) }
            }
        )
        root.addView(toolbar)
        val status =
            TextView(this).apply {
                setText(R.string.browser_detail)
                setTextColor(Color.LTGRAY)
            }
        root.addView(status)
        val image =
            ImageView(this).apply {
                tag = "page"
                scaleType = ImageView.ScaleType.FIT_CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                contentDescription = getString(R.string.browser_page)
            }
        pageView = image
        image.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP && image.drawable != null) {
                val inverse = android.graphics.Matrix()
                if (image.imageMatrix.invert(inverse)) {
                    val point =
                        floatArrayOf(event.x - image.paddingLeft, event.y - image.paddingTop)
                    inverse.mapPoints(point)
                    val drawable = image.drawable
                    val width = drawable.intrinsicWidth.toFloat()
                    val height = drawable.intrinsicHeight.toFloat()
                    if (point[0] >= 0 && point[0] < width && point[1] >= 0 && point[1] < height)
                        model.click(
                            (point[0] * 960 / width).toInt(),
                            (point[1] * 540 / height).toInt(),
                        )
                    image.performClick()
                }
            }
            true
        }
        image.setOnKeyListener { _, key, event ->
            val value =
                when (key) {
                    KeyEvent.KEYCODE_DPAD_UP -> "ArrowUp"
                    KeyEvent.KEYCODE_DPAD_DOWN -> "ArrowDown"
                    KeyEvent.KEYCODE_DPAD_LEFT -> "ArrowLeft"
                    KeyEvent.KEYCODE_DPAD_RIGHT -> "ArrowRight"
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> "Enter"
                    else -> null
                }
            if (value != null) {
                if (event.action == KeyEvent.ACTION_UP) model.input("key", value)
                true
            } else false
        }
        val pageRow = FrameLayout(this).apply { addView(image, FrameLayout.LayoutParams(-1, -1)) }
        root.addView(pageRow, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(
            TextView(this).apply {
                setText(R.string.browser_controls_hint)
                setTextColor(Color.LTGRAY)
            }
        )
        val controls = LinearLayout(this)
        fun control(key: String, id: Int, action: () -> Unit) {
            controls.addView(
                Button(this).apply {
                    tag = key
                    setText(id)
                    setOnClickListener { action() }
                    if (key == "page-control") pageControl = this
                }
            )
        }
        control("back", R.string.browser_back) { model.input("back") }
        control("previous", R.string.browser_previous_link) { model.input("key", "Shift+Tab") }
        control("next", R.string.browser_next_link) { model.input("key", "Tab") }
        control("activate", R.string.browser_activate) { model.input("key", "Enter") }
        control("page-control", R.string.browser_page) { image.requestFocus() }
        control("exit", R.string.close) { finish() }
        root.addView(HorizontalScrollView(this).apply { addView(controls) })
        val entry = LinearLayout(this)
        val text =
            EditText(this).apply {
                tag = "text"
                setHint(R.string.browser_text)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.LTGRAY)
                setBackgroundDrawable(ui.box(ui.panel, ui.muted))
                setPadding(ui.dp(12), 0, ui.dp(12), 0)
                setSingleLine(true)
                isSaveEnabled = false
            }
        entry.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        entry.addView(
            Button(this).apply {
                tag = "type"
                setText(R.string.browser_type)
                setOnClickListener {
                    val value = text.text.toString()
                    text.setText("")
                    model.input("text", value)
                }
            }
        )
        root.addView(entry)
        WindowInsetsPolicy.apply(root)
        setContentView(root)
        focus.remember(saved?.getString("browserFocus"))
        focus.rebuild(
            listOf(
                "toolbar" to toolbar,
                "page" to pageRow,
                "controls" to controls,
                "entry" to entry,
            ),
            true,
        )
        var lastFrame: ByteArray? = null
        model.observer = { value ->
            status.setText(
                if (value.failed) R.string.unavailable
                else if (value.loading) R.string.loading else R.string.browser_detail
            )
            if (value.frame == null && lastFrame != null) {
                image.setImageDrawable(null)
                lastFrame = null
            }
            value.frame?.let { bytes ->
                if (bytes !== lastFrame) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth in 1..960 && bounds.outHeight in 1..540) {
                        image.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                        lastFrame = bytes
                    }
                }
            }
        }
        model.restore(saved?.getString("browserSession") ?: "")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (
            event.action == KeyEvent.ACTION_DOWN &&
                currentFocus !== pageView &&
                currentFocus !is EditText &&
                focus.move(event.keyCode, currentFocus)
        )
            return true
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        if (currentFocus === pageView) pageControl.requestFocus() else super.onBackPressed()
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putString("browserFocus", focus.selectedKey)
        state.putString("browserSession", model.state.session)
        state.putString("address", addressField.text.toString().take(2048))
        super.onSaveInstanceState(state)
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(poll, 3000)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    override fun onDestroy() {
        model.close(preserveSession = changingConfigurations != 0 && !isFinishing)
        executor.execute { api.close() }
        executor.shutdown()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
