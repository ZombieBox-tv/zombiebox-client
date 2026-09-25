package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.core.ui.FocusModel
import io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui.CatalogUiPolicy
import org.junit.Assert.*
import org.junit.Test

class CatalogUiPolicyTest {
    @Test
    fun browsableAndSearchableProvidersAreCorrect() {
        val supported = listOf("youtube", "plex", "stremio", "jellyfin", "iptv", "local")
        for (provider in supported) {
            assertTrue(
                "Expected $provider to support catalog",
                CatalogUiPolicy.supportsCatalog(provider),
            )
            assertTrue(
                "Expected $provider to support search",
                CatalogUiPolicy.supportsSearch(provider),
            )
            assertFalse(
                "Expected $provider to not be receiver-only",
                CatalogUiPolicy.isReceiverOnly(provider),
            )
        }
    }

    @Test
    fun receiverAndAuxiliaryServicesDoNotExposeCatalogOrSearch() {
        val receivers =
            listOf("airplay", "android_mirror", "rebrowser", "browser", "spotify", "cast")
        for (service in receivers) {
            assertFalse(
                "Expected $service to not support catalog",
                CatalogUiPolicy.supportsCatalog(service),
            )
            assertFalse(
                "Expected $service to not support search",
                CatalogUiPolicy.supportsSearch(service),
            )
            assertTrue(
                "Expected $service to be receiver-only",
                CatalogUiPolicy.isReceiverOnly(service),
            )
        }

        assertFalse(CatalogUiPolicy.supportsCatalog(null))
        assertFalse(CatalogUiPolicy.supportsCatalog(""))
        assertFalse(CatalogUiPolicy.supportsCatalog("unknown"))
        assertFalse(CatalogUiPolicy.supportsSearch(null))
        assertFalse(CatalogUiPolicy.supportsSearch(""))
        assertFalse(CatalogUiPolicy.supportsSearch("unknown"))
    }

    @Test
    fun searchQueryValidationRequiresAtLeastTwoNonWhitespaceCharacters() {
        assertFalse(CatalogUiPolicy.isValidSearchQuery(null))
        assertFalse(CatalogUiPolicy.isValidSearchQuery(""))
        assertFalse(CatalogUiPolicy.isValidSearchQuery("   "))
        assertFalse(CatalogUiPolicy.isValidSearchQuery("a"))
        assertFalse(CatalogUiPolicy.isValidSearchQuery(" a "))
        assertFalse(CatalogUiPolicy.isValidSearchQuery("  \t\n  "))

        assertTrue(CatalogUiPolicy.isValidSearchQuery("ab"))
        assertTrue(CatalogUiPolicy.isValidSearchQuery(" a b "))
        assertTrue(CatalogUiPolicy.isValidSearchQuery("zombie"))
    }

    @Test
    fun serviceReadinessRules() {
        assertTrue(CatalogUiPolicy.isReady("READY"))
        assertTrue(CatalogUiPolicy.isReady("HEALTHY"))
        assertFalse(CatalogUiPolicy.isReady("STARTING"))
        assertFalse(CatalogUiPolicy.isReady("INITIALIZING"))
        assertFalse(CatalogUiPolicy.isReady("DISABLED"))
        assertFalse(CatalogUiPolicy.isReady("UNKNOWN"))
        assertFalse(CatalogUiPolicy.isReady(null))
    }

    @Test
    fun browsingLibraryRequiresBothReadinessAndCatalogCapability() {
        assertTrue(CatalogUiPolicy.canBrowseLibrary("youtube", "READY"))
        assertTrue(CatalogUiPolicy.canBrowseLibrary("plex", "HEALTHY"))
        assertTrue(CatalogUiPolicy.canBrowseLibrary("iptv", "READY"))
        assertTrue(CatalogUiPolicy.canBrowseLibrary("local", "READY"))
        assertTrue(CatalogUiPolicy.canBrowseLibrary("local", "HEALTHY"))

        assertFalse(CatalogUiPolicy.canBrowseLibrary("youtube", "STARTING"))
        assertFalse(CatalogUiPolicy.canBrowseLibrary("youtube", "DISABLED"))
        assertFalse(CatalogUiPolicy.canBrowseLibrary("youtube", null))
        assertFalse(CatalogUiPolicy.canBrowseLibrary("local", "DISABLED"))

        assertFalse(CatalogUiPolicy.canBrowseLibrary("airplay", "READY"))
        assertFalse(CatalogUiPolicy.canBrowseLibrary("airplay", "HEALTHY"))
        assertFalse(CatalogUiPolicy.canBrowseLibrary("spotify", "READY"))
    }

    @Test
    fun emptyStateCtaSelectionResolvesContextually() {
        assertEquals(
            CatalogUiPolicy.EmptyCta.BROWSE_LIBRARY,
            CatalogUiPolicy.resolveEmptyCta(
                provider = "youtube",
                hasQuery = true,
                hasCategory = false,
                favoritesOnly = false,
                canBack = false,
            ),
        )

        assertEquals(
            CatalogUiPolicy.EmptyCta.IPTV_ALL_CATEGORIES,
            CatalogUiPolicy.resolveEmptyCta(
                provider = "iptv",
                hasQuery = false,
                hasCategory = true,
                favoritesOnly = false,
                canBack = false,
            ),
        )

        assertEquals(
            CatalogUiPolicy.EmptyCta.BROWSE_LIBRARY,
            CatalogUiPolicy.resolveEmptyCta(
                provider = "plex",
                hasQuery = false,
                hasCategory = true,
                favoritesOnly = false,
                canBack = false,
            ),
        )

        assertEquals(
            CatalogUiPolicy.EmptyCta.BROWSE_LIBRARY,
            CatalogUiPolicy.resolveEmptyCta(
                provider = "iptv",
                hasQuery = false,
                hasCategory = false,
                favoritesOnly = true,
                canBack = false,
            ),
        )

        assertEquals(
            CatalogUiPolicy.EmptyCta.BACK,
            CatalogUiPolicy.resolveEmptyCta(
                provider = "youtube",
                hasQuery = false,
                hasCategory = false,
                favoritesOnly = false,
                canBack = true,
            ),
        )

        assertEquals(
            CatalogUiPolicy.EmptyCta.NONE,
            CatalogUiPolicy.resolveEmptyCta(
                provider = "youtube",
                hasQuery = false,
                hasCategory = false,
                favoritesOnly = false,
                canBack = false,
            ),
        )
    }

    @Test
    fun needsSetupAndConfigurationAffordances() {
        assertTrue(CatalogUiPolicy.needsSetup("NEEDS_SETUP"))
        assertTrue(CatalogUiPolicy.needsSetup("UNCONFIGURED"))
        assertFalse(CatalogUiPolicy.needsSetup("READY"))
        assertFalse(CatalogUiPolicy.needsSetup("HEALTHY"))
        assertFalse(CatalogUiPolicy.needsSetup("DISABLED"))
        assertFalse(CatalogUiPolicy.needsSetup("DEGRADED"))
        assertFalse(CatalogUiPolicy.needsSetup(null))

        assertTrue(CatalogUiPolicy.canConfigure("DISABLED"))
        assertTrue(CatalogUiPolicy.canConfigure("AUTH_REQUIRED"))
        assertTrue(CatalogUiPolicy.canConfigure("NEEDS_SETUP"))
        assertTrue(CatalogUiPolicy.canConfigure("UNCONFIGURED"))
        assertFalse(CatalogUiPolicy.canConfigure("HEALTHY"))
        assertFalse(CatalogUiPolicy.canConfigure("READY"))
        assertFalse(CatalogUiPolicy.canConfigure("DEGRADED"))
        assertFalse(CatalogUiPolicy.canConfigure(null))

        // When unconfigured, IPTV cannot browse library
        assertFalse(CatalogUiPolicy.isReady("NEEDS_SETUP"))
        assertFalse(CatalogUiPolicy.isReady("UNCONFIGURED"))
        assertFalse(CatalogUiPolicy.canBrowseLibrary("iptv", "NEEDS_SETUP"))
        assertFalse(CatalogUiPolicy.canBrowseLibrary("iptv", "UNCONFIGURED"))
    }

    @Test
    fun iptvEmptyStateFocusRowIsNavigable() {
        val model = FocusModel()
        model.rebuild(
            listOf(
                FocusModel.Row("nav", listOf("home", "iptv")),
                FocusModel.Row("iptv:empty", listOf("iptv:empty:settings")),
            )
        )
        model.select("iptv")
        assertEquals("iptv", model.selected)
        assertEquals("iptv:empty:settings", model.move(0, 1))
    }
}
