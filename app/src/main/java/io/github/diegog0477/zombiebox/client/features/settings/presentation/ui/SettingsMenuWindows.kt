package io.github.diegog0477.zombiebox.client.features.settings.presentation.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import io.github.diegog0477.zombiebox.client.R
import io.github.diegog0477.zombiebox.client.core.ui.TvTheme
import io.github.diegog0477.zombiebox.client.core.ui.TvTypography
import io.github.diegog0477.zombiebox.client.core.ui.TvWidgets
import io.github.diegog0477.zombiebox.client.features.settings.presentation.viewmodel.SettingsNavigation

/**
 * TV-modern full-screen Settings PAGE with a near-black shell, consistent Barlow typography,
 * visible green D-pad focus outlines, grouped category tiles/left rail, and a right content pane.
 * API 9+ compatible using native Android Views with no AndroidX or Compose.
 */
@Suppress("DEPRECATION")
@SuppressLint("RtlHardcoded", "SetTextI18n")
class SettingsMenuWindows(private val activity: Activity, val navigation: SettingsNavigation) {
    private val roots = setOf("settings", "devices")
    private val ui = TvWidgets(activity)

    private val lists = HashMap<String, ListView>()
    private val itemKeys = HashMap<String, List<String>>()
    private val routeLabels = HashMap<String, Array<String>>()
    private val routeSubtitles = HashMap<String, List<String>>()
    private val routeDetails = HashMap<String, List<String>>()
    private val routeActionLabels = HashMap<String, List<String>>()
    private val routeActions = HashMap<String, (Int) -> Unit>()
    private val routeTitles = HashMap<String, Int>()

    private var activeRootRoute: String? = null
    private var activeSubRoute: String? = null
    private var pageDialog: AlertDialog? = null

    private var rootHeaderTitle: TextView? = null
    private var rootHeaderBreadcrumb: TextView? = null
    private var leftRailContainer: LinearLayout? = null
    private var leftRailTitle: TextView? = null
    private var rightPaneContainer: FrameLayout? = null
    private var bodyContainer: LinearLayout? = null
    private var previewAction: View? = null

    private fun isPortraitMode(): Boolean =
        activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT ||
            activity.resources.displayMetrics.widthPixels <
                activity.resources.displayMetrics.heightPixels

    fun show(
        route: String,
        title: Int,
        labels: Array<String>,
        keys: List<String> = emptyList(),
        subtitles: List<String> = emptyList(),
        details: List<String> = emptyList(),
        actionLabels: List<String> = emptyList(),
        selected: (Int) -> Unit,
    ) {
        if (activity.isFinishing) return

        routeTitles[route] = title
        routeLabels[route] = labels
        routeSubtitles[route] = subtitles
        routeDetails[route] = details
        routeActionLabels[route] = actionLabels
        routeActions[route] = selected
        itemKeys[route] = keys

        if (route in roots) {
            showRootMenu(route, title, labels, keys, subtitles, selected)
        } else {
            showSubMenu(route, title, labels, keys, subtitles, selected)
        }
    }

    private fun showRootMenu(
        route: String,
        title: Int,
        labels: Array<String>,
        keys: List<String>,
        subtitles: List<String>,
        selected: (Int) -> Unit,
    ) {
        navigation.open(route)

        if (pageDialog != null && pageDialog?.isShowing == true && activeRootRoute == route) {
            val list = lists[route]
            if (list != null) {
                val curPos = list.selectedItemPosition.let { if (it >= 0) it else 0 }
                list.adapter =
                    TvTileAdapter(activity, ui, labels, subtitles) {
                        list.selectedItemPosition.let { if (it >= 0) it else curPos }
                    }
                list.setSelection(curPos)
                if (activeSubRoute == null) {
                    updateCategoryPreview(route, curPos)
                }
            }
            return
        }

        ensurePageDialog(route, title)

        rootHeaderTitle?.text = activity.getString(title)
        rootHeaderBreadcrumb?.text = "  ·  " + activity.getString(R.string.brand)
        leftRailTitle?.text =
            activity.getString(
                if (route == "devices") R.string.settings_devices_remotes
                else R.string.settings_categories
            )

        var leftList = lists[route]
        if (leftList == null || leftRailContainer?.indexOfChild(leftList) == -1) {
            while ((leftRailContainer?.childCount ?: 0) > 1) {
                leftRailContainer?.removeViewAt(1)
            }
            leftList =
                ListView(activity).apply {
                    divider = ColorDrawable(Color.TRANSPARENT)
                    dividerHeight = ui.dp(6)
                    selector = ColorDrawable(Color.TRANSPARENT)
                    cacheColorHint = Color.TRANSPARENT
                    setDrawSelectorOnTop(false)
                    isFocusable = true
                }
            leftRailContainer?.addView(leftList, LinearLayout.LayoutParams(-1, -1))
            lists[route] = leftList
        }

        var selectedIndex = navigation.path.firstOrNull { it.route == route }?.selected ?: 0
        selectedIndex = selectedIndex.coerceIn(0, (labels.size - 1).coerceAtLeast(0))

        val adapter =
            TvTileAdapter(activity, ui, labels, subtitles) {
                leftList.selectedItemPosition.let { if (it >= 0) it else selectedIndex }
            }
        leftList.adapter = adapter
        leftList.setSelection(selectedIndex)

        leftList.setOnItemSelectedListener(
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    selectedIndex = position
                    navigation.select(route, position, keys.getOrNull(position) ?: "")
                    if (activeSubRoute == null) {
                        updateCategoryPreview(route, position)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        )

        leftList.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position
            navigation.activate(route, position, keys.getOrNull(position) ?: "")
            selected(position)
        }

        leftList.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                val target =
                    if (activeSubRoute != null) {
                        lists[activeSubRoute]
                    } else {
                        previewAction
                    }
                if (target != null) {
                    target.requestFocus()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }

        if (activeSubRoute == null) {
            updateCategoryPreview(route, selectedIndex)
            leftList.requestFocus()
        }
    }

    private fun showSubMenu(
        route: String,
        title: Int,
        labels: Array<String>,
        keys: List<String>,
        subtitles: List<String>,
        selected: (Int) -> Unit,
    ) {
        if (pageDialog == null || pageDialog?.isShowing != true || activeRootRoute == null) {
            showRootMenu(
                "settings",
                R.string.settings,
                routeLabels["settings"] ?: arrayOf(activity.getString(title)),
                itemKeys["settings"] ?: emptyList(),
                routeSubtitles["settings"] ?: emptyList(),
                routeActions["settings"] ?: {},
            )
        }

        activeSubRoute = route
        previewAction = null
        navigation.open(route)

        rootHeaderBreadcrumb?.text = "  ›  " + activity.getString(title)

        if (isPortraitMode()) {
            leftRailContainer?.visibility = View.GONE
        }

        val rightPane = rightPaneContainer ?: return
        rightPane.removeAllViews()

        val subMenuLayout =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(-1, -1)
            }

        val subHeader =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, ui.dp(12))
            }

        val titleCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val tagText =
            TextView(activity).apply {
                text =
                    activity.getString(
                        if (route == "providers") R.string.configure_services else R.string.advanced
                    )
                textSize = 12f
                typeface = TvTypography.semibold(activity)
                setTextColor(ui.green)
            }
        titleCol.addView(tagText)

        val titleText =
            TextView(activity).apply {
                text = activity.getString(title)
                textSize = 21f
                typeface = TvTypography.semibold(activity)
                setTextColor(Color.WHITE)
                setPadding(0, ui.dp(2), 0, 0)
            }
        titleCol.addView(titleText)
        subHeader.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))

        val backBtn =
            TvTheme.createDarkButton(activity, ui, "← " + activity.getString(R.string.close)) {
                closeSubRoute()
            }
        subHeader.addView(backBtn)
        subMenuLayout.addView(subHeader)

        val subList =
            ListView(activity).apply {
                divider = ColorDrawable(Color.TRANSPARENT)
                dividerHeight = ui.dp(6)
                selector = ColorDrawable(Color.TRANSPARENT)
                cacheColorHint = Color.TRANSPARENT
                setDrawSelectorOnTop(false)
                isFocusable = true
            }

        var subSelectedIndex = navigation.path.firstOrNull { it.route == route }?.selected ?: 0
        subSelectedIndex = subSelectedIndex.coerceIn(0, (labels.size - 1).coerceAtLeast(0))

        val subAdapter =
            TvTileAdapter(activity, ui, labels, subtitles) {
                subList.selectedItemPosition.let { if (it >= 0) it else subSelectedIndex }
            }
        subList.adapter = subAdapter
        subList.setSelection(subSelectedIndex)

        subList.setOnItemSelectedListener(
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    subSelectedIndex = position
                    navigation.select(route, position, keys.getOrNull(position) ?: "")
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        )

        subList.setOnItemClickListener { _, _, position, _ ->
            subSelectedIndex = position
            navigation.activate(route, position, keys.getOrNull(position) ?: "")
            selected(position)
        }

        subList.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                closeSubRoute()
                true
            } else {
                false
            }
        }

        subMenuLayout.addView(subList, LinearLayout.LayoutParams(-1, -1))
        rightPane.addView(subMenuLayout)
        lists[route] = subList

        subList.requestFocus()
    }

    private fun closeSubRoute() {
        val sub = activeSubRoute ?: return
        navigation.close(sub)
        lists.remove(sub)
        itemKeys.remove(sub)
        activeSubRoute = null

        val root = activeRootRoute
        rootHeaderBreadcrumb?.text = "  ·  " + activity.getString(R.string.brand)

        if (isPortraitMode()) {
            leftRailContainer?.visibility = View.VISIBLE
        }

        if (root != null) {
            val rootList = lists[root]
            val selected =
                rootList?.selectedItemPosition?.takeIf { it >= 0 }
                    ?: navigation.path.firstOrNull { it.route == root }?.selected
                    ?: 0
            updateCategoryPreview(root, selected)
            rootList?.setSelection(selected)
            rootList?.requestFocus()
        }
    }

    private fun ensurePageDialog(rootRoute: String, titleRes: Int) {
        if (pageDialog != null && pageDialog?.isShowing == true && activeRootRoute == rootRoute) {
            return
        }
        pageDialog?.dismiss()
        pageDialog = null

        activeRootRoute = rootRoute
        activeSubRoute = null

        val isPortrait = isPortraitMode()

        val root =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(TvTheme.shellBackground)
                setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16))
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }

        val header =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, ui.dp(12))
            }

        val headerTextCol =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

        val titleView =
            TextView(activity).apply {
                text = activity.getString(titleRes)
                textSize = 24f
                typeface = TvTypography.semibold(activity)
                setTextColor(Color.WHITE)
            }
        rootHeaderTitle = titleView
        headerTextCol.addView(titleView)

        val crumbView =
            TextView(activity).apply {
                text = "  ·  " + activity.getString(R.string.brand)
                textSize = 14f
                typeface = TvTypography.regular(activity)
                setTextColor(ui.muted)
            }
        rootHeaderBreadcrumb = crumbView
        headerTextCol.addView(crumbView)

        header.addView(headerTextCol, LinearLayout.LayoutParams(0, -2, 1f))

        val closeBtn =
            TvTheme.createDarkButton(activity, ui, activity.getString(R.string.close)) { close() }
        header.addView(closeBtn)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val body =
            LinearLayout(activity).apply {
                orientation = if (isPortrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            }
        bodyContainer = body

        val leftRail =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundDrawable(
                    TvTheme.roundedBox(
                        ui.dp(12).toFloat(),
                        TvTheme.cardBackground,
                        ui.dp(1),
                        TvTheme.cardBorder,
                    )
                )
                setPadding(ui.dp(10), ui.dp(12), ui.dp(10), ui.dp(10))
            }
        leftRailContainer = leftRail

        val railHeader =
            TextView(activity).apply {
                text = activity.getString(R.string.settings_categories)
                textSize = 12f
                typeface = TvTypography.semibold(activity)
                setTextColor(ui.green)
                setPadding(ui.dp(8), ui.dp(2), ui.dp(8), ui.dp(8))
            }
        leftRailTitle = railHeader
        leftRail.addView(railHeader)

        if (isPortrait) {
            body.addView(
                leftRail,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    setMargins(0, 0, 0, ui.dp(12))
                },
            )
        } else {
            body.addView(
                leftRail,
                LinearLayout.LayoutParams(ui.dp(310), LinearLayout.LayoutParams.MATCH_PARENT)
                    .apply { setMargins(0, 0, ui.dp(16), 0) },
            )
        }

        val rightPane =
            FrameLayout(activity).apply {
                setBackgroundDrawable(
                    TvTheme.roundedBox(
                        ui.dp(12).toFloat(),
                        Color.rgb(12, 17, 19),
                        ui.dp(1),
                        TvTheme.cardBorder,
                    )
                )
                setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18))
            }
        rightPaneContainer = rightPane

        if (isPortrait) {
            body.addView(
                rightPane,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.2f),
            )
        } else {
            body.addView(
                rightPane,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
            )
        }

        root.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val dialog = AlertDialog.Builder(activity).setView(root).create()
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (activeSubRoute != null) {
                    closeSubRoute()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        dialog.setOnDismissListener {
            if (pageDialog !== dialog) return@setOnDismissListener
            val rootToClose = activeRootRoute
            activeSubRoute = null
            activeRootRoute = null
            lists.clear()
            itemKeys.clear()
            pageDialog = null
            if (rootToClose != null) {
                navigation.close(rootToClose)
            }
        }
        pageDialog = dialog
        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(TvTheme.shellBackground))
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    private fun updateCategoryPreview(rootRoute: String, index: Int) {
        val rightPane = rightPaneContainer ?: return
        rightPane.removeAllViews()

        val labels = routeLabels[rootRoute] ?: return
        if (index !in labels.indices) return

        val label = labels[index]
        val subtitle = routeSubtitles[rootRoute]?.getOrNull(index) ?: ""
        val detail = routeDetails[rootRoute]?.getOrNull(index) ?: ""
        val actionLabel =
            routeActionLabels[rootRoute]?.getOrNull(index) ?: activity.getString(R.string.connect)

        val scroll = ScrollView(activity).apply { isFocusable = false }
        val content =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
            }

        val tagText =
            TextView(activity).apply {
                text =
                    activity
                        .getString(
                            if (rootRoute == "devices") R.string.settings_devices_remotes
                            else R.string.settings
                        )
                        .uppercase()
                textSize = 12f
                typeface = TvTypography.semibold(activity)
                setTextColor(ui.green)
                setPadding(0, 0, 0, ui.dp(4))
            }
        content.addView(tagText)

        val titleView =
            TextView(activity).apply {
                text = label
                textSize = 24f
                typeface = TvTypography.semibold(activity)
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, ui.dp(6))
            }
        content.addView(titleView)

        if (subtitle.isNotEmpty()) {
            val subView =
                TextView(activity).apply {
                    text = subtitle
                    textSize = 14f
                    typeface = TvTypography.semibold(activity)
                    setTextColor(ui.green)
                    setPadding(0, 0, 0, ui.dp(14))
                }
            content.addView(subView)
        }

        if (detail.isNotEmpty()) {
            val detailBox =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundDrawable(
                        TvTheme.roundedBox(
                            ui.dp(8).toFloat(),
                            Color.rgb(18, 24, 26),
                            ui.dp(1),
                            Color.rgb(38, 48, 52),
                        )
                    )
                    setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
                }
            val detailView =
                TextView(activity).apply {
                    text = detail
                    textSize = 14f
                    typeface = TvTypography.regular(activity)
                    setTextColor(ui.muted)
                    setLineSpacing(ui.dp(2).toFloat(), 1.15f)
                }
            detailBox.addView(detailView)
            content.addView(
                detailBox,
                LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, ui.dp(18)) },
            )
        }

        val actionBtn =
            TvTheme.createPrimaryButton(activity, ui, actionLabel) {
                routeActions[rootRoute]?.invoke(index)
            }
        previewAction = actionBtn
        actionBtn.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                lists[rootRoute]?.requestFocus()
                true
            } else {
                false
            }
        }
        content.addView(actionBtn)

        scroll.addView(content)
        rightPane.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    fun restoreSelection(route: String, index: Int, key: String = "") {
        val list = lists[route] ?: return
        val byKey = itemKeys[route]?.indexOf(key) ?: -1
        val selection =
            (if (byKey >= 0) byKey else index).coerceIn(0, (list.count - 1).coerceAtLeast(0))
        navigation.select(route, selection, itemKeys[route]?.getOrNull(selection) ?: "")
        list.setSelection(selection)
        list.requestFocus()
        if (route == activeRootRoute && activeSubRoute == null) {
            updateCategoryPreview(route, selection)
        }
    }

    fun snapshot(): List<SettingsNavigation.Menu> {
        val active = listOfNotNull(activeRootRoute, activeSubRoute)
        for (route in active) {
            val list = lists[route]
            val index =
                list?.selectedItemPosition?.takeIf { it >= 0 }
                    ?: navigation.path.firstOrNull { it.route == route }?.selected
                    ?: -1
            if (index >= 0) {
                navigation.select(route, index, itemKeys[route]?.getOrNull(index) ?: "")
            }
        }
        return navigation.path
    }

    fun close() {
        activeSubRoute = null
        activeRootRoute = null
        lists.clear()
        itemKeys.clear()
        navigation.reset()
        pageDialog?.dismiss()
        pageDialog = null
    }

    private class TvTileAdapter(
        private val context: Context,
        private val ui: TvWidgets,
        private val labels: Array<String>,
        private val subtitles: List<String>,
        private val getSelectedIndex: () -> Int,
    ) : BaseAdapter() {
        override fun getCount(): Int = labels.size

        override fun getItem(position: Int): String = labels[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val radius = ui.dp(8).toFloat()
            val normalBox =
                TvTheme.roundedBox(radius, Color.rgb(18, 24, 26), ui.dp(1), Color.rgb(38, 48, 52))
            val selectedBox = TvTheme.roundedBox(radius, Color.rgb(22, 32, 35), ui.dp(1), ui.green)
            val focusedBox = TvTheme.roundedBox(radius, Color.rgb(32, 44, 48), ui.dp(2), ui.green)

            val container =
                (convertView as? LinearLayout)
                    ?: LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10))
                        minimumHeight = ui.dp(54)

                        val titleView =
                            TextView(context).apply {
                                tag = "title"
                                textSize = 15f
                                typeface = TvTypography.semibold(context)
                                setTextColor(Color.WHITE)
                            }
                        val subtitleView =
                            TextView(context).apply {
                                tag = "subtitle"
                                textSize = 12f
                                typeface = TvTypography.regular(context)
                                setTextColor(ui.muted)
                                setPadding(0, ui.dp(2), 0, 0)
                            }
                        addView(titleView)
                        addView(subtitleView)
                    }

            val titleView = container.findViewWithTag<TextView>("title")
            val subtitleView = container.findViewWithTag<TextView>("subtitle")

            titleView?.text = labels[position]

            val sub = subtitles.getOrNull(position) ?: ""
            if (sub.isNotEmpty()) {
                subtitleView?.text = sub
                subtitleView?.visibility = View.VISIBLE
            } else {
                subtitleView?.visibility = View.GONE
            }

            val isSelected = (position == getSelectedIndex())
            container.setBackgroundDrawable(
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), focusedBox)
                    addState(intArrayOf(android.R.attr.state_pressed), focusedBox)
                    addState(intArrayOf(android.R.attr.state_selected), focusedBox)
                    addState(intArrayOf(), if (isSelected) selectedBox else normalBox)
                }
            )

            return container
        }
    }
}
