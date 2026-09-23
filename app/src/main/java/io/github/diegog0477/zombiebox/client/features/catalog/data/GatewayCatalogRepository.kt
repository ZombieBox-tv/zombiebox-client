package io.github.diegog0477.zombiebox.client.features.catalog.data

import io.github.diegog0477.zombiebox.client.core.data.MediaItemDecoder
import io.github.diegog0477.zombiebox.client.features.catalog.domain.model.CatalogPage
import io.github.diegog0477.zombiebox.client.features.catalog.domain.repository.CatalogRepository
import io.github.diegog0477.zombiebox.shared.GatewayApi
import java.net.URLEncoder

class GatewayCatalogRepository(private val api: GatewayApi) : CatalogRepository {
    override fun iptvPage(
        query: String,
        offset: Int,
        favoritesOnly: Boolean,
        category: String,
    ): CatalogPage {
        val result =
            api.request(
                "GET",
                "/v1/catalog?provider=iptv&offset=$offset&q=" +
                    URLEncoder.encode(query, "UTF-8") +
                    "&favorites=" +
                    (if (favoritesOnly) "1" else "0") +
                    "&category=" +
                    URLEncoder.encode(category, "UTF-8"),
            )
        val items = result.getJSONArray("items")
        val groups = result.optJSONArray("categories")
        return CatalogPage(
            (0 until items.length()).map { MediaItemDecoder.decodeItem(items.getJSONObject(it)) },
            result.optInt("nextOffset", -1),
            categories =
                if (groups == null) emptyList()
                else (0 until groups.length()).map { groups.getString(it) },
        )
    }

    override fun favoritePage(query: String, offset: Int): CatalogPage {
        return iptvPage(query, offset, true, "")
    }

    override fun setIptvFavorite(id: String, favorite: Boolean) {
        require(Regex("^iptv-[a-f0-9]{16}$").matches(id))
        api.request(if (favorite) "PUT" else "DELETE", "/v1/iptv/favorites/$id")
    }

    override fun page(provider: String, query: String, offset: Int, parent: String): CatalogPage {
        val endpoint =
            if (provider in listOf("plex", "jellyfin", "stremio", "youtube")) "/v1/browse"
            else "/v1/catalog"
        val result =
            api.request(
                "GET",
                endpoint +
                    "?provider=" +
                    URLEncoder.encode(provider, "UTF-8") +
                    "&offset=$offset&q=" +
                    URLEncoder.encode(query, "UTF-8") +
                    "&parent=" +
                    URLEncoder.encode(parent, "UTF-8"),
            )
        val items = result.getJSONArray("items")
        return CatalogPage(
            (0 until items.length()).map { MediaItemDecoder.decodeItem(items.getJSONObject(it)) },
            result.optInt("nextOffset", -1),
            result.optString("title"),
        )
    }
}
