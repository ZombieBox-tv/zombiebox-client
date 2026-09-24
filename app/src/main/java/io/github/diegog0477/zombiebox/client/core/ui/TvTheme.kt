package io.github.diegog0477.zombiebox.client.core.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.diegog0477.zombiebox.client.R

@Suppress("DEPRECATION")
object TvTheme {
    val shellBackground = Color.rgb(10, 15, 16)
    val cardBackground = Color.rgb(14, 19, 21)
    val cardBorder = Color.rgb(42, 53, 56)
    val inputBackground = Color.rgb(18, 24, 26)
    val inputBorder = Color.rgb(48, 58, 62)
    val buttonBackground = Color.rgb(22, 28, 30)
    val buttonBorder = Color.rgb(48, 58, 62)
    val buttonFocused = Color.rgb(34, 44, 48)

    fun roundedBox(
        radius: Float,
        fillColor: Int,
        strokeWidth: Int = 0,
        strokeColor: Int = Color.TRANSPARENT,
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radius
            if (strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }

    /** Let short forms wrap content while keeping long forms within the TV viewport. */
    fun boundedScroll(context: Context): ScrollView =
        object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val cap = (resources.displayMetrics.heightPixels * 0.58f).toInt()
                val parentLimit =
                    if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED)
                        cap
                    else View.MeasureSpec.getSize(heightMeasureSpec)
                super.onMeasure(
                    widthMeasureSpec,
                    View.MeasureSpec.makeMeasureSpec(
                        minOf(cap, parentLimit),
                        View.MeasureSpec.AT_MOST,
                    ),
                )
            }
        }

    fun createDarkInputField(
        context: Context,
        ui: TvWidgets,
        secret: Boolean = false,
        multiLine: Boolean = false,
    ): EditText {
        val radius = ui.dp(8).toFloat()
        val normalBg = roundedBox(radius, inputBackground, ui.dp(1), inputBorder)
        val focusedBg = roundedBox(radius, Color.rgb(26, 34, 37), ui.dp(2), ui.green)
        return EditText(context).apply {
            if (!multiLine) {
                setSingleLine(true)
            }
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(140, 150, 155))
            setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10))
            setBackgroundDrawable(
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), focusedBg)
                    addState(intArrayOf(), normalBg)
                }
            )
            inputType =
                when {
                    multiLine -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                }
            typeface = TvTypography.regular(context)
            isSaveEnabled = false
        }
    }

    fun styleDarkButton(
        button: Button,
        ui: TvWidgets,
        context: Context,
        accentStroke: Boolean = false,
        primary: Boolean = false,
    ) {
        button.typeface = TvTypography.semibold(context)
        button.textSize = 14f
        button.isFocusable = true
        button.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8))
        val strokeColor = if (accentStroke) ui.green else Color.WHITE
        val radius = ui.dp(8).toFloat()
        if (primary) {
            button.setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(ui.muted, shellBackground),
                )
            )
            button.setBackgroundDrawable(
                StateListDrawable().apply {
                    addState(
                        intArrayOf(-android.R.attr.state_enabled),
                        roundedBox(radius, ui.panel, ui.dp(1), Color.rgb(35, 42, 45)),
                    )
                    addState(
                        intArrayOf(android.R.attr.state_focused),
                        roundedBox(radius, ui.green, ui.dp(2), Color.WHITE),
                    )
                    addState(
                        intArrayOf(android.R.attr.state_pressed),
                        roundedBox(radius, ui.green, ui.dp(2), Color.WHITE),
                    )
                    addState(intArrayOf(), roundedBox(radius, ui.green))
                }
            )
        } else {
            button.setTextColor(Color.WHITE)
            button.setBackgroundDrawable(
                StateListDrawable().apply {
                    addState(
                        intArrayOf(-android.R.attr.state_enabled),
                        roundedBox(radius, Color.rgb(16, 20, 22), ui.dp(1), Color.rgb(30, 36, 38)),
                    )
                    addState(
                        intArrayOf(android.R.attr.state_focused),
                        roundedBox(radius, buttonFocused, ui.dp(2), strokeColor),
                    )
                    addState(
                        intArrayOf(android.R.attr.state_pressed),
                        roundedBox(radius, buttonFocused, ui.dp(2), strokeColor),
                    )
                    addState(
                        intArrayOf(),
                        roundedBox(radius, buttonBackground, ui.dp(1), buttonBorder),
                    )
                }
            )
        }
    }

    fun createDarkButton(
        context: Context,
        ui: TvWidgets,
        label: String,
        accentStroke: Boolean = false,
        click: () -> Unit,
    ): Button =
        Button(context).apply {
            text = label
            styleDarkButton(this, ui, context, accentStroke = accentStroke)
            setOnClickListener { click() }
            layoutParams =
                LinearLayout.LayoutParams(-2, ui.dp(44)).apply {
                    setMargins(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(3))
                }
        }

    fun createPrimaryButton(
        context: Context,
        ui: TvWidgets,
        label: String,
        click: () -> Unit,
    ): Button =
        Button(context).apply {
            text = label
            styleDarkButton(this, ui, context, primary = true)
            setOnClickListener { click() }
            layoutParams =
                LinearLayout.LayoutParams(-2, ui.dp(44)).apply {
                    setMargins(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(3))
                }
        }

    fun styleDiscoveryPanel(panel: ViewGroup, ui: TvWidgets, context: Context) {
        if (panel.childCount >= 1 && panel.getChildAt(0) is Button) {
            val refresh = panel.getChildAt(0) as Button
            styleDarkButton(refresh, ui, context, accentStroke = true)
            (refresh.layoutParams as? LinearLayout.LayoutParams)?.apply {
                width = LinearLayout.LayoutParams.MATCH_PARENT
                height = ui.dp(44)
                setMargins(0, ui.dp(4), 0, ui.dp(4))
            }
        }
        if (panel.childCount >= 2 && panel.getChildAt(1) is TextView) {
            val status = panel.getChildAt(1) as TextView
            status.typeface = TvTypography.regular(context)
            status.setTextColor(ui.muted)
            status.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
        }
        if (panel.childCount >= 3 && panel.getChildAt(2) is ViewGroup) {
            val results = panel.getChildAt(2) as ViewGroup
            results.setOnHierarchyChangeListener(
                object : ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent: View?, child: View?) {
                        if (child is Button) {
                            styleDarkButton(child, ui, context, accentStroke = true)
                            (child.layoutParams as? LinearLayout.LayoutParams)?.apply {
                                width = LinearLayout.LayoutParams.MATCH_PARENT
                                height = ui.dp(44)
                                setMargins(0, ui.dp(3), 0, ui.dp(3))
                            }
                        }
                    }

                    override fun onChildViewRemoved(parent: View?, child: View?) {}
                }
            )
        }
    }

    fun showDarkCardDialog(activity: Activity, ui: TvWidgets, content: View): AlertDialog {
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width =
            (activity.resources.displayMetrics.widthPixels * 0.78f).toInt().coerceAtMost(ui.dp(580))
        dialog.window?.setLayout(width, -2)
        return dialog
    }

    fun showSingleChoiceDialog(
        activity: Activity,
        titleRes: Int,
        options: Array<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit,
    ): AlertDialog {
        val ui = TvWidgets(activity)
        var dialogRef: AlertDialog? = null
        val container =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(14))
                setBackgroundDrawable(
                    roundedBox(ui.dp(12).toFloat(), cardBackground, ui.dp(1), cardBorder)
                )
            }
        val titleView =
            TextView(activity).apply {
                text = activity.getString(titleRes)
                textSize = 21f
                typeface = TvTypography.semibold(activity)
                setTextColor(Color.WHITE)
                setPadding(ui.dp(6), ui.dp(2), ui.dp(6), ui.dp(12))
            }
        container.addView(titleView)

        val optionsLayout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        var initialFocusView: View? = null
        val radius = ui.dp(8).toFloat()
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val row =
                Button(activity).apply {
                    text = if (isSelected) "●  $label" else "○  $label"
                    textSize = 15f
                    typeface =
                        if (isSelected) TvTypography.semibold(activity)
                        else TvTypography.regular(activity)
                    gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                    setPadding(ui.dp(18), ui.dp(10), ui.dp(18), ui.dp(10))
                    isFocusable = true
                    val strokeColor = if (isSelected) ui.green else Color.WHITE
                    val normalFill =
                        if (isSelected) Color.rgb(24, 34, 37) else Color.rgb(18, 23, 25)
                    val normalStroke = if (isSelected) ui.green else Color.rgb(42, 52, 55)
                    val normalBox = roundedBox(radius, normalFill, ui.dp(1), normalStroke)
                    val focusedBox = roundedBox(radius, buttonFocused, ui.dp(2), strokeColor)
                    setBackgroundDrawable(
                        StateListDrawable().apply {
                            addState(intArrayOf(android.R.attr.state_focused), focusedBox)
                            addState(intArrayOf(android.R.attr.state_pressed), focusedBox)
                            addState(intArrayOf(), normalBox)
                        }
                    )
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        dialogRef?.dismiss()
                        onSelected(index)
                    }
                    layoutParams =
                        LinearLayout.LayoutParams(-1, ui.dp(48)).apply {
                            setMargins(ui.dp(2), ui.dp(3), ui.dp(2), ui.dp(3))
                        }
                }
            if (isSelected) initialFocusView = row
            optionsLayout.addView(row)
        }
        val scroll = ScrollView(activity).apply { addView(optionsLayout) }
        container.addView(
            scroll,
            LinearLayout.LayoutParams(-1, ui.dp((options.size * 52).coerceAtMost(360))),
        )

        val closeBtn =
            createDarkButton(activity, ui, activity.getString(R.string.close)) {
                dialogRef?.dismiss()
            }
        val footer =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                setPadding(ui.dp(4), ui.dp(10), ui.dp(4), 0)
                addView(closeBtn)
            }
        container.addView(footer)

        val dialog = AlertDialog.Builder(activity).setView(container).create()
        dialogRef = dialog
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width =
            (activity.resources.displayMetrics.widthPixels * 0.76f).toInt().coerceAtMost(ui.dp(540))
        dialog.window?.setLayout(width, -2)
        (initialFocusView ?: optionsLayout.getChildAt(0))?.requestFocus()
        return dialog
    }
}
