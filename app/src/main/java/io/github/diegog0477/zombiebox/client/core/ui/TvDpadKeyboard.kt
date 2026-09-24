package io.github.diegog0477.zombiebox.client.core.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import io.github.diegog0477.zombiebox.client.R

/** Modes for onscreen TV D-pad keyboard. */
enum class TvKeyboardMode {
    LOWERCASE,
    UPPERCASE,
    SYMBOLS,
}

/**
 * Pure JVM-testable logic for TV keyboard character manipulation and grid navigation. Keeps
 * character handling completely decoupled from Android View hierarchies.
 */
object TvKeyboardActions {
    val NUMBERS_ROW = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    val LOWERCASE_ROWS =
        listOf(
            NUMBERS_ROW,
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "-"),
            listOf("z", "x", "c", "v", "b", "n", "m", ".", "_", "/"),
        )

    val UPPERCASE_ROWS =
        listOf(
            NUMBERS_ROW,
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "-"),
            listOf("Z", "X", "C", "V", "B", "N", "M", ".", "_", "/"),
        )

    val SYMBOL_ROWS =
        listOf(
            NUMBERS_ROW,
            listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
            listOf("~", "`", "+", "=", "[", "]", "{", "}", "\\", "|"),
            listOf("<", ">", ":", ";", "\"", "'", "?", "/", ",", "."),
        )

    fun rowsFor(mode: TvKeyboardMode): List<List<String>> =
        when (mode) {
            TvKeyboardMode.LOWERCASE -> LOWERCASE_ROWS
            TvKeyboardMode.UPPERCASE -> UPPERCASE_ROWS
            TvKeyboardMode.SYMBOLS -> SYMBOL_ROWS
        }

    /**
     * Inserts text at the selection position, respecting maximum query length. Returns the updated
     * string and the new cursor position.
     */
    fun insert(
        current: String,
        addition: String,
        selectionStart: Int,
        selectionEnd: Int,
        maxLength: Int = 100,
    ): Pair<String, Int> {
        val start = selectionStart.coerceIn(0, current.length)
        val end = selectionEnd.coerceIn(0, current.length)
        val selStart = minOf(start, end)
        val selEnd = maxOf(start, end)

        val lengthWithoutSelection = current.length - (selEnd - selStart)
        val remainingCapacity = (maxLength - lengthWithoutSelection).coerceAtLeast(0)
        if (remainingCapacity <= 0 || addition.isEmpty()) {
            return Pair(current, selStart)
        }

        val allowedAddition = addition.take(remainingCapacity)
        val newText = current.substring(0, selStart) + allowedAddition + current.substring(selEnd)
        val newCursor = selStart + allowedAddition.length
        return Pair(newText, newCursor)
    }

    /**
     * Deletes the selected text or the character preceding the cursor. Returns the updated string
     * and the new cursor position.
     */
    fun delete(current: String, selectionStart: Int, selectionEnd: Int): Pair<String, Int> {
        if (current.isEmpty()) return Pair("", 0)
        val start = selectionStart.coerceIn(0, current.length)
        val end = selectionEnd.coerceIn(0, current.length)
        val selStart = minOf(start, end)
        val selEnd = maxOf(start, end)

        if (selStart != selEnd) {
            val newText = current.substring(0, selStart) + current.substring(selEnd)
            return Pair(newText, selStart)
        }
        if (selStart > 0) {
            val newText = current.substring(0, selStart - 1) + current.substring(selStart)
            return Pair(newText, selStart - 1)
        }
        return Pair(current, 0)
    }

    /** Clears all text and resets cursor to 0. */
    fun clear(): Pair<String, Int> = Pair("", 0)

    /** Deterministic column mapping between key rows (10 keys) and action controls (4 keys). */
    fun keyColToActionIndex(col: Int): Int =
        when {
            col <= 2 -> 0 // Space
            col <= 4 -> 1 // Backspace
            col <= 6 -> 2 // Clear
            else -> 3 // Done
        }

    fun actionIndexToKeyCol(actionIndex: Int): Int =
        when (actionIndex) {
            0 -> 1 // Space -> 'x'
            1 -> 4 // Backspace -> 'b'
            2 -> 6 // Clear -> 'm'
            else -> 8 // Done -> '_'
        }

    fun keyColToTab(col: Int): Int =
        when {
            col <= 2 -> 0 // abc
            col <= 6 -> 1 // ABC
            else -> 2 // ?123
        }

    fun tabToKeyCol(tabIndex: Int): Int =
        when (tabIndex) {
            0 -> 1 // abc -> '2'
            1 -> 4 // ABC -> '5'
            else -> 8 // ?123 -> '9'
        }
}

/**
 * API9-compatible onscreen TV keyboard with predictable D-pad row/column navigation, compact
 * QWERTY/number/symbol layout, high-contrast focus outline, and automatic focus scrolling.
 *
 * Designed to be reusable for search dialogs, gateway URLs, and browser inputs.
 */
@Suppress("DEPRECATION")
class TvDpadKeyboard @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    private val ui = TvWidgets(context)
    private val regularFace = TvTypography.regular(context)
    private val semiboldFace = TvTypography.semibold(context)

    var target: EditText? = null
    var maxTextLength: Int = 100
    var onDoneListener: (() -> Unit)? = null
    var onKeyClickListener: ((String) -> Unit)? = null

    var nextUpFocusView: View? = null
    var nextDownFocusView: View? = null
    var nextRightFocusView: View? = null

    private var currentMode = TvKeyboardMode.LOWERCASE
    private val modeTabs = ArrayList<Button>()
    private val keyRows = ArrayList<ArrayList<Button>>()
    private val actionRow = ArrayList<Button>()
    private var lastFocusedKey: View? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        buildKeyboard()
    }

    fun attachTarget(editText: EditText?) {
        this.target = editText
    }

    fun setOnDone(action: () -> Unit) {
        this.onDoneListener = action
    }

    fun focusDefaultKey(): Boolean {
        val targetKey =
            lastFocusedKey?.takeIf { it.isShown }
                ?: keyRows.getOrNull(1)?.getOrNull(0)
                ?: keyRows.firstOrNull()?.firstOrNull()
        return targetKey?.requestFocus() ?: false
    }

    private fun buildKeyboard() {
        removeAllViews()
        modeTabs.clear()
        keyRows.clear()
        actionRow.clear()

        buildModeTabs()
        buildKeyGrid()
        buildActionRow()
        updateModeDisplay()
    }

    private fun buildModeTabs() {
        val tabsLayout =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, ui.dp(4))
            }

        val modeNames =
            listOf(
                Pair(TvKeyboardMode.LOWERCASE, getStringSafe(R.string.keyboard_mode_lower, "abc")),
                Pair(TvKeyboardMode.UPPERCASE, getStringSafe(R.string.keyboard_mode_upper, "ABC")),
                Pair(TvKeyboardMode.SYMBOLS, getStringSafe(R.string.keyboard_mode_symbols, "?123")),
            )

        modeNames.forEachIndexed { index, (mode, label) ->
            val tab =
                Button(context).apply {
                    text = label
                    textSize = 13f
                    typeface = semiboldFace
                    isFocusable = true
                    setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(4))
                    setOnClickListener { switchMode(mode) }
                    setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            lastFocusedKey = v
                            v.requestRectangleOnScreen(Rect(0, 0, v.width, v.height), false)
                        }
                    }
                    val params =
                        LayoutParams(-2, ui.dp(32)).apply { setMargins(ui.dp(3), 0, ui.dp(3), 0) }
                    layoutParams = params
                }
            modeTabs.add(tab)
            tabsLayout.addView(tab)
        }
        addView(tabsLayout)
    }

    private fun buildKeyGrid() {
        val rowsData = TvKeyboardActions.rowsFor(currentMode)
        rowsData.forEachIndexed { rowIndex, rowData ->
            val rowLayout =
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams =
                        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                }
            val rowButtons = ArrayList<Button>()
            rowData.forEachIndexed { colIndex, charStr ->
                val btn =
                    Button(context).apply {
                        text = charStr
                        textSize = 15f
                        typeface = regularFace
                        isFocusable = true
                        setPadding(0, 0, 0, 0)
                        setBackgroundDrawable(createKeyBackground(false))
                        setTextColor(createKeyTextColor(false))
                        setOnClickListener { handleCharacterClick(text.toString()) }
                        setOnFocusChangeListener { v, hasFocus ->
                            if (hasFocus) {
                                lastFocusedKey = v
                                v.requestRectangleOnScreen(Rect(0, 0, v.width, v.height), false)
                            }
                        }
                        layoutParams =
                            LayoutParams(0, ui.dp(36), 1.0f).apply {
                                setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                            }
                    }
                rowButtons.add(btn)
                rowLayout.addView(btn)
            }
            keyRows.add(rowButtons)
            addView(rowLayout)
        }
    }

    private fun buildActionRow() {
        val actionsLayout =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams =
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                        topMargin = ui.dp(2)
                    }
            }

        val spaceLabel = getStringSafe(R.string.keyboard_space, "Space")
        val backspaceLabel = getStringSafe(R.string.keyboard_backspace, "Delete")
        val clearLabel = getStringSafe(R.string.keyboard_clear, "Clear")
        val doneLabel = getStringSafe(R.string.keyboard_done, "Done")

        val actions =
            listOf(
                Triple(spaceLabel, 3.5f) { handleSpaceClick() },
                Triple(backspaceLabel, 2.0f) { handleBackspaceClick() },
                Triple(clearLabel, 2.0f) { handleClearClick() },
                Triple(doneLabel, 2.5f) { handleDoneClick() },
            )

        actions.forEachIndexed { index, (label, weight, action) ->
            val isDone = index == 3
            val btn =
                Button(context).apply {
                    text = label
                    textSize = 13f
                    typeface = semiboldFace
                    isFocusable = true
                    setPadding(0, 0, 0, 0)
                    setBackgroundDrawable(createKeyBackground(isDone = isDone))
                    setTextColor(createKeyTextColor(isDone = isDone))
                    setOnClickListener { action() }
                    setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            lastFocusedKey = v
                            v.requestRectangleOnScreen(Rect(0, 0, v.width, v.height), false)
                        }
                    }
                    layoutParams =
                        LayoutParams(0, ui.dp(36), weight).apply {
                            setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                        }
                }
            actionRow.add(btn)
            actionsLayout.addView(btn)
        }
        addView(actionsLayout)
    }

    fun switchMode(mode: TvKeyboardMode) {
        if (currentMode == mode) return
        currentMode = mode
        val rowsData = TvKeyboardActions.rowsFor(mode)
        for (r in rowsData.indices) {
            for (c in rowsData[r].indices) {
                keyRows.getOrNull(r)?.getOrNull(c)?.text = rowsData[r][c]
            }
        }
        updateModeDisplay()
    }

    private fun updateModeDisplay() {
        val radius = ui.dp(6).toFloat()
        modeTabs.forEachIndexed { index, tab ->
            val isCurrent =
                when (index) {
                    0 -> currentMode == TvKeyboardMode.LOWERCASE
                    1 -> currentMode == TvKeyboardMode.UPPERCASE
                    else -> currentMode == TvKeyboardMode.SYMBOLS
                }
            val strokeColor = if (isCurrent) ui.green else Color.rgb(44, 54, 58)
            val fillColor = if (isCurrent) Color.rgb(28, 44, 40) else Color.rgb(18, 24, 26)
            val normalBg = TvTheme.roundedBox(radius, fillColor, ui.dp(1), strokeColor)
            val focusedBg = TvTheme.roundedBox(radius, Color.WHITE, ui.dp(2), ui.green)
            tab.setBackgroundDrawable(
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), focusedBg)
                    addState(intArrayOf(android.R.attr.state_pressed), focusedBg)
                    addState(intArrayOf(), normalBg)
                }
            )
            val textColor = if (isCurrent) ui.green else ui.muted
            tab.setTextColor(
                ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_focused),
                        intArrayOf(android.R.attr.state_pressed),
                        intArrayOf(),
                    ),
                    intArrayOf(Color.rgb(10, 15, 16), Color.rgb(10, 15, 16), textColor),
                )
            )
        }
    }

    private fun createKeyBackground(isDone: Boolean): StateListDrawable {
        val radius = ui.dp(6).toFloat()
        val focusedBg = TvTheme.roundedBox(radius, Color.WHITE, ui.dp(2), ui.green)
        val normalFill = if (isDone) Color.rgb(26, 48, 36) else Color.rgb(22, 28, 30)
        val normalStroke = if (isDone) ui.green else Color.rgb(46, 56, 60)
        val normalBg = TvTheme.roundedBox(radius, normalFill, ui.dp(1), normalStroke)

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focusedBg)
            addState(intArrayOf(android.R.attr.state_pressed), focusedBg)
            addState(intArrayOf(), normalBg)
        }
    }

    private fun createKeyTextColor(isDone: Boolean): ColorStateList {
        val normalColor = if (isDone) ui.green else Color.rgb(235, 242, 245)
        val focusedColor = Color.rgb(10, 15, 16)
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_focused),
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf(),
            ),
            intArrayOf(focusedColor, focusedColor, normalColor),
        )
    }

    fun typeText(addition: String) {
        val et = target ?: return
        val text = et.text ?: return
        val (newText, newCursor) =
            TvKeyboardActions.insert(
                text.toString(),
                addition,
                et.selectionStart,
                et.selectionEnd,
                maxTextLength,
            )
        if (newText != text.toString()) {
            text.replace(0, text.length, newText)
            et.setSelection(newCursor.coerceIn(0, text.length))
            onKeyClickListener?.invoke(addition)
        }
    }

    fun backspace() {
        val et = target ?: return
        val text = et.text ?: return
        if (text.isEmpty()) return
        val (newText, newCursor) =
            TvKeyboardActions.delete(text.toString(), et.selectionStart, et.selectionEnd)
        text.replace(0, text.length, newText)
        et.setSelection(newCursor.coerceIn(0, text.length))
        onKeyClickListener?.invoke("BACKSPACE")
    }

    fun clearText() {
        val et = target ?: return
        val text = et.text ?: return
        text.clear()
        et.setSelection(0)
        onKeyClickListener?.invoke("CLEAR")
    }

    fun performDone() {
        onDoneListener?.invoke()
        onKeyClickListener?.invoke("DONE")
    }

    private fun handleCharacterClick(charStr: String) {
        typeText(charStr)
    }

    private fun handleSpaceClick() {
        typeText(" ")
    }

    private fun handleBackspaceClick() {
        backspace()
    }

    private fun handleClearClick() {
        clearText()
    }

    private fun handleDoneClick() {
        performDone()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Back key must never be trapped by the onscreen keyboard.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event)
        }
        return super.dispatchKeyEvent(event)
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val tabIndex = modeTabs.indexOf(focused)
        if (tabIndex >= 0) {
            return when (direction) {
                FOCUS_LEFT -> if (tabIndex > 0) modeTabs[tabIndex - 1] else focused
                FOCUS_RIGHT ->
                    if (tabIndex < modeTabs.size - 1) modeTabs[tabIndex + 1]
                    else nextRightFocusView ?: super.focusSearch(focused, direction)
                FOCUS_DOWN -> {
                    val col = TvKeyboardActions.tabToKeyCol(tabIndex)
                    keyRows.firstOrNull()?.getOrNull(col) ?: keyRows.firstOrNull()?.firstOrNull()
                }
                FOCUS_UP -> nextUpFocusView ?: super.focusSearch(focused, direction)
                else -> super.focusSearch(focused, direction)
            }
        }

        val rowIndex = keyRows.indexOfFirst { it.contains(focused) }
        if (rowIndex >= 0) {
            val colIndex = keyRows[rowIndex].indexOf(focused)
            return when (direction) {
                FOCUS_LEFT -> if (colIndex > 0) keyRows[rowIndex][colIndex - 1] else focused
                FOCUS_RIGHT ->
                    if (colIndex < keyRows[rowIndex].size - 1) keyRows[rowIndex][colIndex + 1]
                    else nextRightFocusView ?: super.focusSearch(focused, direction)
                FOCUS_UP -> {
                    if (rowIndex > 0) {
                        keyRows[rowIndex - 1][colIndex]
                    } else {
                        val tabIdx = TvKeyboardActions.keyColToTab(colIndex)
                        modeTabs.getOrNull(tabIdx) ?: modeTabs[0]
                    }
                }
                FOCUS_DOWN -> {
                    if (rowIndex < keyRows.size - 1) {
                        keyRows[rowIndex + 1][colIndex]
                    } else {
                        val actionIdx = TvKeyboardActions.keyColToActionIndex(colIndex)
                        actionRow.getOrNull(actionIdx) ?: actionRow.last()
                    }
                }
                else -> super.focusSearch(focused, direction)
            }
        }

        val actionIndex = actionRow.indexOf(focused)
        if (actionIndex >= 0) {
            return when (direction) {
                FOCUS_LEFT -> if (actionIndex > 0) actionRow[actionIndex - 1] else focused
                FOCUS_RIGHT ->
                    if (actionIndex < actionRow.size - 1) actionRow[actionIndex + 1]
                    else nextRightFocusView ?: super.focusSearch(focused, direction)
                FOCUS_UP -> {
                    val keyCol = TvKeyboardActions.actionIndexToKeyCol(actionIndex)
                    keyRows.lastOrNull()?.getOrNull(keyCol) ?: keyRows.lastOrNull()?.lastOrNull()
                }
                FOCUS_DOWN -> nextDownFocusView ?: super.focusSearch(focused, direction)
                else -> super.focusSearch(focused, direction)
            }
        }

        return super.focusSearch(focused, direction)
    }

    private fun getStringSafe(resId: Int, fallback: String): String =
        try {
            context.getString(resId)
        } catch (_: Exception) {
            fallback
        }
}
