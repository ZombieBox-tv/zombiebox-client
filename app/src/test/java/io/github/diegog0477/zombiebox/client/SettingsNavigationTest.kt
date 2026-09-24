package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.settings.presentation.viewmodel.SettingsNavigation
import org.junit.Assert.*
import org.junit.Test

class SettingsNavigationTest {
    @Test
    fun devicesIsAnIndependentRootAndRestorable() {
        val model = SettingsNavigation()
        model.open("settings")
        model.open("devices")
        assertEquals(listOf(SettingsNavigation.Menu("devices")), model.path)
        assertEquals(model.path, model.sanitize(model.path))
    }

    @Test
    fun nestedBackPreservesTheParentSelection() {
        val model = SettingsNavigation()
        model.open("settings")
        model.activate("settings", 5)
        model.open("advanced")
        model.activate("advanced", 4)
        model.close("advanced")
        assertEquals(listOf(SettingsNavigation.Menu("settings", 5)), model.path)
        model.close("settings")
        assertTrue(model.path.isEmpty())
    }

    @Test
    fun closingOrAnotherActionInvalidatesPendingChildLoads() {
        val model = SettingsNavigation()
        model.open("settings")
        model.activate("settings", 1)
        val providerRequest = model.generation
        model.activate("settings", 3)
        assertNotEquals(providerRequest, model.generation)
        val request = model.generation
        model.close("settings")
        assertNotEquals(request, model.generation)
        model.open("providers")
        model.reset()
        assertTrue(model.path.isEmpty())
    }

    @Test
    fun recreationRestoresOnlyBoundedMenuBookmarksNeverForms() {
        val model = SettingsNavigation()
        val saved =
            model.sanitize(
                listOf(
                    SettingsNavigation.Menu("settings", 5),
                    SettingsNavigation.Menu("advanced", 999),
                )
            )
        saved.forEach {
            model.open(it.route)
            model.select(it.route, it.selected)
        }
        assertEquals(
            listOf(
                SettingsNavigation.Menu("settings", 5),
                SettingsNavigation.Menu("advanced", 255),
            ),
            model.path,
        )
        assertEquals(
            emptyList<SettingsNavigation.Menu>(),
            model.sanitize(listOf(SettingsNavigation.Menu("provider-secret-form"))),
        )
        assertEquals(
            listOf(SettingsNavigation.Menu("advanced")),
            model.sanitize(
                listOf(SettingsNavigation.Menu("advanced"), SettingsNavigation.Menu("providers"))
            ),
        )
    }
}
