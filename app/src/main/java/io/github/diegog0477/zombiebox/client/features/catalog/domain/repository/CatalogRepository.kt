package io.github.diegog0477.zombiebox.client.features.catalog.domain.repository

import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogPage

interface CatalogRepository {
    fun page(provider: String, query: String, offset: Int, parent: String = ""): CatalogPage

    fun favoritePage(query: String, offset: Int): CatalogPage = page("iptv", query, offset)

    fun iptvPage(
        query: String,
        offset: Int,
        favoritesOnly: Boolean,
        category: String,
    ): CatalogPage = if (favoritesOnly) favoritePage(query, offset) else page("iptv", query, offset)

    fun setIptvFavorite(id: String, favorite: Boolean) {
        throw UnsupportedOperationException("IPTV favorites unavailable")
    }
}
