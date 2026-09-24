package io.github.diegog0477.zombiebox.client.features.settings.presentation.viewmodel

/** Menus only: credentials, form drafts and consent never enter saved navigation. */
class SettingsNavigation {
    data class Menu(val route: String, val selected: Int = 0, val selectedKey: String = "")

    private val routes = setOf("settings", "devices", "advanced", "providers")
    private val roots = setOf("settings", "devices")
    var path: List<Menu> = emptyList()
        private set

    var generation = 0
        private set

    fun open(route: String) {
        require(route in routes)
        generation++
        if (route in roots) path = path.take(1).filter { it.route == route }
        else path = path.takeWhile { it.route in roots }
        if (path.none { it.route == route }) path = path + Menu(route)
    }

    fun activate(route: String, index: Int, key: String = "") {
        generation++
        select(route, index, key)
    }

    fun select(route: String, index: Int, key: String = "") {
        path =
            path.map {
                if (it.route == route)
                    it.copy(selected = index.coerceIn(0, 255), selectedKey = key.take(40))
                else it
            }
    }

    fun close(route: String) {
        val index = path.indexOfFirst { it.route == route }
        if (index >= 0) {
            generation++
            path = path.take(index)
        }
    }

    fun reset() {
        generation++
        path = emptyList()
    }

    fun sanitize(saved: List<Menu>): List<Menu> {
        val safe = saved.take(2).filter { it.route in routes }.distinctBy { it.route }
        val path =
            if (safe.size == 2 && (safe.first().route !in roots || safe.last().route in roots))
                safe.take(1)
            else safe
        return path.map {
            it.copy(selected = it.selected.coerceIn(0, 255), selectedKey = it.selectedKey.take(40))
        }
    }
}
