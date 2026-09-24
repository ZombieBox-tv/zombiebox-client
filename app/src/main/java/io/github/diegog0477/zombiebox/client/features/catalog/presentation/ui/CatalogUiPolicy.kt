package io.github.diegog0477.zombiebox.client.features.catalog.presentation.ui

/**
 * Shared UI capability policy for TV catalog browsing and search. Ensures only actual content
 * providers expose catalog or search affordances, while receiver-only and auxiliary services
 * (AirPlay, Android Mirror, Browser, Spotify) fail gracefully and never expose invalid routes or
 * crash paths.
 */
object CatalogUiPolicy {
    private val BROWSABLE_PROVIDERS =
        setOf("youtube", "plex", "stremio", "jellyfin", "iptv", "local")
    private val SEARCHABLE_PROVIDERS =
        setOf("youtube", "plex", "stremio", "jellyfin", "iptv", "local")
    private val RECEIVER_ONLY_SERVICES =
        setOf("airplay", "android_mirror", "rebrowser", "browser", "spotify", "cast")

    enum class EmptyCta {
        BROWSE_LIBRARY,
        IPTV_ALL_CATEGORIES,
        BACK,
        NONE,
    }

    fun supportsCatalog(provider: String?): Boolean =
        provider != null && provider in BROWSABLE_PROVIDERS

    fun supportsSearch(provider: String?): Boolean =
        provider != null && provider in SEARCHABLE_PROVIDERS

    fun isReceiverOnly(provider: String?): Boolean =
        provider != null &&
            (provider in RECEIVER_ONLY_SERVICES ||
                provider.startsWith("airplay") ||
                provider.startsWith("mirror"))

    fun isValidSearchQuery(phrase: String?): Boolean = phrase != null && phrase.trim().length >= 2

    fun isReady(serviceState: String?): Boolean =
        serviceState == "READY" || serviceState == "HEALTHY"

    fun canBrowseLibrary(provider: String?, serviceState: String?): Boolean =
        isReady(serviceState) && supportsCatalog(provider)

    fun resolveEmptyCta(
        provider: String,
        hasQuery: Boolean,
        hasCategory: Boolean,
        favoritesOnly: Boolean,
        canBack: Boolean,
    ): EmptyCta =
        when {
            hasQuery -> EmptyCta.BROWSE_LIBRARY
            hasCategory ->
                if (provider == "iptv") EmptyCta.IPTV_ALL_CATEGORIES else EmptyCta.BROWSE_LIBRARY
            favoritesOnly -> EmptyCta.BROWSE_LIBRARY
            canBack -> EmptyCta.BACK
            else -> EmptyCta.NONE
        }
}
