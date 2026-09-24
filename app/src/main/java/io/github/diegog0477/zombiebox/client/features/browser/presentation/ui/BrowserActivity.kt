package io.github.diegog0477.zombiebox.client.features.browser.presentation.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.platform.WindowInsetsPolicy
import io.github.diegog0477.zombiebox.client.core.ui.RemoteFocus
import io.github.diegog0477.zombiebox.client.core.ui.TvDpadKeyboard
import io.github.diegog0477.zombiebox.client.core.ui.TvTheme
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.browser.data.GatewayBrowserRepository
import io.github.diegog0477.zombiebox.client.features.browser.presentation.viewmodel.BrowserViewModel
import io.github.diegog0477.zombiebox.shared.GatewayApi
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
class BrowserActivity : Activity() {
    private val ui by lazy { TvWidgets(this) }
    private val api = GatewayApi()
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler()
    private lateinit var model: BrowserViewModel
    private lateinit var addressField: EditText
    private lateinit var openButton: Button
    private lateinit var statusText: TextView
    private lateinit var pageView: ImageView
    private lateinit var pageContainer: FrameLayout
    private lateinit var pageModeBadge: TextView
    private lateinit var pointerView: BrowserPointerView
    private lateinit var pointerButton: Button
    private lateinit var placeholderPanel: LinearLayout
    private lateinit var placeholderTitle: TextView
    private lateinit var placeholderDetail: TextView
    private lateinit var pageControl: View
    private lateinit var textField: EditText
    private lateinit var typeButton: Button
    private lateinit var keyboard: TvDpadKeyboard
    private var activeTarget: EditText? = null
    private var userInteracted = false
    private var consumedBackDown = false
    private var restoringInputFocus = false
    private var lastPageKeyAt = 0L
    private var pointerMode = false
    private var pointerX = 480
    private var pointerY = 270
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
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
        pointerMode = saved?.getBoolean("pointerMode") ?: false
        pointerX = (saved?.getInt("pointerX") ?: 480).coerceIn(0, 959)
        pointerY = (saved?.getInt("pointerY") ?: 270).coerceIn(0, 539)
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(TvTheme.shellBackground)
                setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
            }
        val toolbar =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        fun navigationButton(symbol: Int, label: Int, command: String): Button =
            Button(this).apply {
                setText(symbol)
                contentDescription = getString(label)
                TvTheme.styleDarkButton(this, ui, this@BrowserActivity, accentStroke = true)
                textSize = 22f
                setPadding(0, 0, 0, 0)
                setOnClickListener { model.input(command) }
                toolbar.addView(
                    this,
                    LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)).apply { rightMargin = ui.dp(6) },
                )
            }
        val navigationButtons =
            listOf(
                navigationButton(R.string.browser_back_symbol, R.string.browser_back, "back"),
                navigationButton(
                    R.string.browser_forward_symbol,
                    R.string.browser_forward,
                    "forward",
                ),
                navigationButton(R.string.browser_reload_symbol, R.string.browser_reload, "reload"),
            )
        navigationButtons.forEach { it.isEnabled = false }
        val address =
            TvTheme.createDarkInputField(this, ui).apply {
                tag = "address"
                setHint(R.string.browser_address)
            }
        addressField = address
        address.setText(saved?.getString("address") ?: "")
        suppressSystemKeyboard(address)
        address.setOnClickListener {
            userInteracted = true
            showKeyboard(addressField, focusKey = true)
        }

        val browserAccent = ui.providerAccent("rebrowser")
        val radius = ui.dp(8).toFloat()
        val open =
            Button(this).apply {
                tag = "open"
                setText(R.string.browser_open)
                typeface = TvTypography.semibold(this@BrowserActivity)
                textSize = 15f
                setTextColor(Color.WHITE)
                isFocusable = true
                setPadding(ui.dp(22), ui.dp(8), ui.dp(22), ui.dp(8))
                val openNormal =
                    TvTheme.roundedBox(
                        radius,
                        Color.rgb(26, 44, 68),
                        ui.dp(1),
                        Color.rgb(55, 90, 130),
                    )
                val openFocused =
                    TvTheme.roundedBox(radius, Color.rgb(36, 62, 96), ui.dp(2), browserAccent)
                setBackgroundDrawable(
                    StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused), openFocused)
                        addState(intArrayOf(android.R.attr.state_pressed), openFocused)
                        addState(intArrayOf(), openNormal)
                    }
                )
                setOnClickListener { performOpen() }
            }
        openButton = open
        toolbar.addView(
            address,
            LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply { setMargins(0, 0, ui.dp(8), 0) },
        )
        toolbar.addView(open, LinearLayout.LayoutParams(-2, ui.dp(48)))
        root.addView(toolbar)

        val status =
            TextView(this).apply {
                setText(R.string.browser_idle_detail)
                textSize = 13f
                typeface = TvTypography.regular(this@BrowserActivity)
                setTextColor(ui.muted)
                setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(8))
            }
        statusText = status
        root.addView(status)

        val image =
            object : ImageView(this) {
                    override fun onFocusChanged(
                        gainFocus: Boolean,
                        direction: Int,
                        previouslyFocusedRect: android.graphics.Rect?,
                    ) {
                        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
                        pageModeBadge.visibility = if (gainFocus) View.VISIBLE else View.GONE
                        pointerView.visibility =
                            if (gainFocus && pointerMode && isEnabled) View.VISIBLE else View.GONE
                    }
                }
                .apply {
                    tag = "page"
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isEnabled = false
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
            if (
                key !in
                    listOf(
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                    )
            )
                return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_DOWN) {
                val now = SystemClock.uptimeMillis()
                if (event.repeatCount > 0 && now - lastPageKeyAt < 180) return@setOnKeyListener true
                lastPageKeyAt = now
                if (pointerMode) {
                    when (key) {
                        KeyEvent.KEYCODE_DPAD_UP -> movePointer(0, -48)
                        KeyEvent.KEYCODE_DPAD_DOWN -> movePointer(0, 48)
                        KeyEvent.KEYCODE_DPAD_LEFT -> movePointer(-48, 0)
                        KeyEvent.KEYCODE_DPAD_RIGHT -> movePointer(48, 0)
                        else -> model.click(pointerX, pointerY)
                    }
                } else {
                    when (key) {
                        KeyEvent.KEYCODE_DPAD_UP -> model.scroll(-300)
                        KeyEvent.KEYCODE_DPAD_DOWN -> model.scroll(300)
                        KeyEvent.KEYCODE_DPAD_LEFT -> model.input("key", "Shift+Tab")
                        KeyEvent.KEYCODE_DPAD_RIGHT -> model.input("key", "Tab")
                        else -> model.input("key", "Enter")
                    }
                }
            }
            true
        }

        val placeholder =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24))
            }
        val pTitle =
            TextView(this).apply {
                setText(R.string.browser)
                textSize = 20f
                typeface = TvTypography.semibold(this@BrowserActivity)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, ui.dp(8))
            }
        val pDetail =
            TextView(this).apply {
                setText(R.string.browser_idle_detail)
                textSize = 14f
                typeface = TvTypography.regular(this@BrowserActivity)
                setTextColor(ui.muted)
                gravity = Gravity.CENTER
                setLineSpacing(ui.dp(2).toFloat(), 1.15f)
            }
        placeholder.addView(pTitle)
        placeholder.addView(pDetail)
        placeholderTitle = pTitle
        placeholderDetail = pDetail
        placeholderPanel = placeholder

        val pageFrame =
            FrameLayout(this).apply {
                setAddStatesFromChildren(true)
                setBackgroundDrawable(
                    StateListDrawable().apply {
                        val radius = ui.dp(8).toFloat()
                        addState(
                            intArrayOf(android.R.attr.state_focused),
                            TvTheme.roundedBox(radius, Color.rgb(12, 17, 19), ui.dp(2), ui.green),
                        )
                        addState(
                            intArrayOf(),
                            TvTheme.roundedBox(
                                radius,
                                Color.rgb(12, 17, 19),
                                ui.dp(1),
                                Color.rgb(36, 46, 50),
                            ),
                        )
                    }
                )
                addView(image, FrameLayout.LayoutParams(-1, -1))
                addView(placeholder, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            }
        pageContainer = pageFrame
        pageModeBadge =
            TextView(this).apply {
                setText(
                    if (pointerMode) R.string.browser_pointer_mode else R.string.browser_page_mode
                )
                textSize = 13f
                typeface = TvTypography.semibold(this@BrowserActivity)
                setTextColor(Color.WHITE)
                setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6))
                setBackgroundDrawable(TvTheme.roundedBox(ui.dp(6).toFloat(), Color.rgb(22, 32, 35)))
                visibility = View.GONE
            }
        pageFrame.addView(
            pageModeBadge,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.RIGHT).apply {
                setMargins(0, ui.dp(8), ui.dp(8), 0)
            },
        )
        pointerView = BrowserPointerView(this).apply { visibility = View.GONE }
        pageFrame.addView(
            pointerView,
            FrameLayout.LayoutParams(ui.dp(30), ui.dp(30), Gravity.TOP or Gravity.LEFT),
        )
        pageFrame.viewTreeObserver.addOnGlobalLayoutListener { updatePointerPosition() }
        root.addView(pageFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(
            TextView(this).apply {
                setText(R.string.browser_controls_hint)
                textSize = 12f
                typeface = TvTypography.regular(this@BrowserActivity)
                setTextColor(Color.rgb(130, 142, 148))
                setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(4))
            }
        )

        val controls =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        fun control(key: String, id: Int, action: () -> Unit): Button {
            val btn =
                Button(this).apply {
                    tag = key
                    setText(id)
                    TvTheme.styleDarkButton(this, ui, this@BrowserActivity, accentStroke = true)
                    setOnClickListener { action() }
                    layoutParams =
                        LinearLayout.LayoutParams(-2, ui.dp(44)).apply {
                            setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                        }
                    if (key == "page-control") pageControl = this
                }
            controls.addView(btn)
            return btn
        }
        control("page-control", R.string.browser_page) {
            if (pageView.isEnabled) image.requestFocus()
        }
        control("previous", R.string.browser_previous_link) { model.input("key", "Shift+Tab") }
        control("next", R.string.browser_next_link) { model.input("key", "Tab") }
        control("activate", R.string.browser_activate) { model.input("key", "Enter") }
        control("scroll-up", R.string.browser_scroll_up) { model.scroll(-300) }
        control("scroll-down", R.string.browser_scroll_down) { model.scroll(300) }
        pointerButton =
            control("pointer-mode", R.string.browser_pointer_control) {
                pointerMode = !pointerMode
                pointerButton.setText(
                    if (pointerMode) R.string.browser_links_mode
                    else R.string.browser_pointer_control
                )
                pageModeBadge.setText(
                    if (pointerMode) R.string.browser_pointer_mode else R.string.browser_page_mode
                )
                pointerView.visibility = if (pointerMode) View.VISIBLE else View.GONE
                updatePointerPosition()
                image.requestFocus()
            }
        pointerButton.setText(
            if (pointerMode) R.string.browser_links_mode else R.string.browser_pointer_control
        )
        pointerButton.isEnabled = false
        control("exit", R.string.close) { finish() }
        pageControl.isEnabled = false
        root.addView(HorizontalScrollView(this).apply { addView(controls) })

        val entry =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, ui.dp(4), 0, 0)
            }
        val text =
            TvTheme.createDarkInputField(this, ui).apply {
                tag = "text"
                setHint(R.string.browser_text)
            }
        textField = text
        suppressSystemKeyboard(text)
        text.setOnClickListener {
            userInteracted = true
            showKeyboard(textField, focusKey = true)
        }
        val type =
            Button(this).apply {
                tag = "type"
                setText(R.string.browser_type)
                TvTheme.styleDarkButton(this, ui, this@BrowserActivity, accentStroke = true)
                setOnClickListener { performType() }
                layoutParams =
                    LinearLayout.LayoutParams(-2, ui.dp(44)).apply { setMargins(ui.dp(4), 0, 0, 0) }
            }
        typeButton = type
        entry.addView(text, LinearLayout.LayoutParams(0, ui.dp(44), 1f))
        entry.addView(type)
        root.addView(entry)

        keyboard = TvDpadKeyboard(this).apply { visibility = View.GONE }
        root.addView(
            keyboard,
            LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                .apply { topMargin = ui.dp(6) },
        )

        WindowInsetsPolicy.apply(root)
        setContentView(root)
        focus.remember(saved?.getString("browserFocus"))
        val focusRows =
            listOf(
                "toolbar" to toolbar,
                "page" to pageFrame,
                "controls" to controls,
                "entry" to entry,
            )
        fun bindInputFocus() {
            addressField.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    if (userInteracted && !restoringInputFocus) {
                        showKeyboard(addressField, focusKey = false)
                    }
                } else {
                    checkFocusLeftInput()
                }
            }
            textField.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    if (userInteracted && !restoringInputFocus) {
                        showKeyboard(textField, focusKey = false)
                    }
                } else {
                    checkFocusLeftInput()
                }
            }
        }
        focus.rebuild(focusRows, true)
        bindInputFocus()

        val savedActiveInput = saved?.getString("activeInput")
        if (savedActiveInput == "text") {
            userInteracted = true
            showKeyboard(textField, focusKey = false)
        } else if (savedActiveInput == "address") {
            userInteracted = true
            showKeyboard(addressField, focusKey = false)
        }

        var lastFrame: ByteArray? = null
        model.observer = { value ->
            val hadPage = image.isEnabled
            val isIdle =
                value.session.isEmpty() && value.frame == null && !value.loading && !value.failed
            when {
                value.failed -> {
                    status.setText(R.string.browser_unavailable_detail)
                    placeholderTitle.setText(R.string.browser_unavailable_title)
                    placeholderDetail.setText(R.string.browser_unavailable_detail)
                    placeholder.visibility = if (value.frame == null) View.VISIBLE else View.GONE
                }
                value.loading -> {
                    status.setText(
                        if (value.frame == null) R.string.browser_loading_detail
                        else R.string.browser_detail
                    )
                    placeholderTitle.setText(R.string.loading)
                    placeholderDetail.setText(R.string.browser_loading_detail)
                    if (value.frame == null) {
                        placeholder.visibility = View.VISIBLE
                    }
                }
                isIdle -> {
                    status.setText(R.string.browser_idle_detail)
                    placeholderTitle.setText(R.string.browser)
                    placeholderDetail.setText(R.string.browser_idle_detail)
                    placeholder.visibility = View.VISIBLE
                }
                else -> {
                    status.setText(R.string.browser_detail)
                    if (value.frame != null) {
                        placeholder.visibility = View.GONE
                    }
                }
            }
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
                        placeholder.visibility = View.GONE
                    }
                }
            }
            val hasPage = image.drawable != null && value.session.isNotEmpty()
            navigationButtons.forEach { it.isEnabled = value.session.isNotEmpty() }
            image.isEnabled = hasPage
            pageControl.isEnabled = hasPage
            pointerButton.isEnabled = hasPage
            pointerView.visibility =
                if (hasPage && image.hasFocus() && pointerMode) View.VISIBLE else View.GONE
            if (hasPage) updatePointerPosition()
            if (hadPage != hasPage) {
                if (!hasPage && image.hasFocus()) openButton.requestFocus()
                focus.rebuild(focusRows, false)
                bindInputFocus()
            }
        }
        model.restore(saved?.getString("browserSession") ?: "")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        userInteracted = true
        if (event.action == KeyEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (event.keyCode == KeyEvent.KEYCODE_BACK && keyboard.visibility == View.VISIBLE) {
                consumedBackDown = true
                hideKeyboard(restoreFocusTo = activeTarget ?: openButton)
                return true
            }
            if (focused === pageView) {
                return super.dispatchKeyEvent(event)
            }
            if (isDescendantOf(focused, keyboard)) {
                return super.dispatchKeyEvent(event)
            }
            if (focused is EditText) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (keyboard.visibility == View.VISIBLE) {
                            keyboard.focusDefaultKey()
                            return true
                        } else {
                            showKeyboard(focused, focusKey = true)
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (focused === addressField) {
                            if (keyboard.visibility == View.VISIBLE) {
                                hideKeyboard(restoreFocusTo = addressField)
                            }
                            return true
                        } else if (focused === textField) {
                            if (keyboard.visibility == View.VISIBLE) {
                                hideKeyboard()
                            }
                            if (pageControl.isEnabled) pageControl.requestFocus()
                            else openButton.requestFocus()
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (
                            focused === addressField &&
                                focused.selectionStart == focused.text.length
                        ) {
                            if (keyboard.visibility == View.VISIBLE) hideKeyboard()
                            openButton.requestFocus()
                            return true
                        }
                        if (
                            focused === textField && focused.selectionStart == focused.text.length
                        ) {
                            if (keyboard.visibility == View.VISIBLE) hideKeyboard()
                            typeButton.requestFocus()
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_DPAD_CENTER -> {
                        showKeyboard(focused, focusKey = true)
                        return true
                    }
                }
                return super.dispatchKeyEvent(event)
            }
            if (focus.move(event.keyCode, focused)) {
                return true
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && consumedBackDown) {
                consumedBackDown = false
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        if (keyboard.visibility == View.VISIBLE) {
            hideKeyboard(restoreFocusTo = activeTarget ?: openButton)
            return
        }
        if (currentFocus === pageView) pageControl.requestFocus() else super.onBackPressed()
    }

    override fun onSaveInstanceState(state: Bundle) {
        val currentFocusKey =
            when (currentFocus) {
                addressField -> "address"
                textField -> "text"
                else -> focus.selectedKey
            }
        state.putString("browserFocus", currentFocusKey)
        state.putString("browserSession", model.state.session)
        state.putString("address", addressField.text.toString().take(2048))
        state.putBoolean("pointerMode", pointerMode)
        state.putInt("pointerX", pointerX)
        state.putInt("pointerY", pointerY)
        if (keyboard.visibility == View.VISIBLE) {
            state.putString("activeInput", if (activeTarget === textField) "text" else "address")
        }
        super.onSaveInstanceState(state)
    }

    private fun showKeyboard(target: EditText, focusKey: Boolean = false) {
        activeTarget = target
        keyboard.attachTarget(target)
        keyboard.visibility = View.VISIBLE
        if (target === addressField) {
            keyboard.maxTextLength = 2048
            keyboard.setOnDone { performOpen() }
            keyboard.nextUpFocusView = addressField
            keyboard.nextDownFocusView = textField
            keyboard.nextRightFocusView = openButton
        } else {
            keyboard.maxTextLength = 100
            keyboard.setOnDone { performType() }
            keyboard.nextUpFocusView = textField
            keyboard.nextDownFocusView = addressField
            keyboard.nextRightFocusView = typeButton
        }
        suppressSystemKeyboard(target)
        if (focusKey) {
            keyboard.post { keyboard.focusDefaultKey() }
        }
    }

    private fun hideKeyboard(restoreFocusTo: View? = null) {
        activeTarget = null
        keyboard.attachTarget(null)
        keyboard.visibility = View.GONE
        restoringInputFocus = true
        restoreFocusTo?.requestFocus()
        restoringInputFocus = false
    }

    private fun performOpen() {
        val url = addressField.text.toString().trim()
        hideKeyboard(restoreFocusTo = if (pageView.isEnabled) pageView else openButton)
        model.open(url)
    }

    private fun movePointer(dx: Int, dy: Int) {
        pointerX = (pointerX + dx).coerceIn(0, 959)
        pointerY = (pointerY + dy).coerceIn(0, 539)
        updatePointerPosition()
        model.move(pointerX, pointerY)
    }

    private fun updatePointerPosition() {
        if (!::pointerView.isInitialized || pointerView.visibility != View.VISIBLE) return
        val drawable = pageView.drawable ?: return
        if (pageView.width == 0 || pageView.height == 0) return
        val point =
            floatArrayOf(
                pointerX * drawable.intrinsicWidth / 960f,
                pointerY * drawable.intrinsicHeight / 540f,
            )
        pageView.imageMatrix.mapPoints(point)
        val params = pointerView.layoutParams as FrameLayout.LayoutParams
        val left =
            (pageView.left + pageView.paddingLeft + point[0] - pointerView.width / 2f).toInt()
        val top = (pageView.top + pageView.paddingTop + point[1] - pointerView.height / 2f).toInt()
        if (params.leftMargin != left || params.topMargin != top) {
            params.leftMargin = left
            params.topMargin = top
            pointerView.layoutParams = params
        }
    }

    private fun performType() {
        val value = textField.text.toString()
        if (value.isEmpty()) return
        textField.setText("")
        hideKeyboard(restoreFocusTo = if (pageView.isEnabled) pageView else openButton)
        model.input("text", value)
    }

    private fun suppressSystemKeyboard(field: EditText) {
        try {
            val method =
                EditText::class
                    .java
                    .getMethod("setShowSoftInputOnFocus", Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(field, false)
        } catch (_: Throwable) {}
        field.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(field.windowToken, 0)
        }
    }

    private fun checkFocusLeftInput() {
        handler.post {
            val f = currentFocus
            val isInputOrKeyboard =
                f === addressField || f === textField || isDescendantOf(f, keyboard)
            if (!isInputOrKeyboard && keyboard.visibility == View.VISIBLE) {
                hideKeyboard()
            }
        }
    }

    private fun isDescendantOf(child: View?, parent: ViewGroup): Boolean {
        var p = child?.parent
        while (p != null) {
            if (p === parent) return true
            p = p.parent
        }
        return false
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
