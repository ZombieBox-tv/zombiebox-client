package io.github.diegog0477.zombiebox.client.features.home.data

import io.github.diegog0477.zombiebox.client.core.data.MediaItemDecoder
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeScope
import io.github.diegog0477.zombiebox.client.features.home.domain.model.HomeSnapshot
import io.github.diegog0477.zombiebox.client.features.home.domain.model.MediaSection
import io.github.diegog0477.zombiebox.client.features.home.domain.model.ServiceModule
import io.github.diegog0477.zombiebox.client.features.home.domain.repository.HomeRepository
import io.github.diegog0477.zombiebox.shared.GatewayApi
import java.net.URLEncoder
import org.json.JSONArray

/** Gateway JSON stops here; presentation observes semantic, immutable values. */
class GatewayHomeRepository(private val api: GatewayApi) : HomeRepository {
    override fun load(scope: HomeScope): HomeSnapshot {
        val home =
            api.request(
                "GET",
                "/v1/home?provider=" +
                    URLEncoder.encode(scope.provider, "UTF-8") +
                    "&q=" +
                    URLEncoder.encode(scope.query, "UTF-8"),
            )
        val modules = api.request("GET", "/v1/modules").optJSONArray("modules") ?: JSONArray()
        val sections = home.optJSONArray("sections") ?: JSONArray()
        val serviceModules =
            (0 until modules.length()).map {
                ServiceModule(
                    modules.getJSONObject(it).optString("id"),
                    modules.getJSONObject(it).optString("state"),
                )
            }
        // /v1/modules describes catalog-cache health. Spotify account readiness comes from
        // /v1/integrations and must not be presented as Ready merely because its worker is up.
        val scopedModules =
            if (scope.provider == "spotify") {
                serviceModules.filterNot { it.id == "spotify" } +
                    (spotifyIntegration() ?: ServiceModule("spotify", "CHECKING"))
            } else serviceModules
        return HomeSnapshot(
            home.optJSONObject("hero")?.optJSONObject("item")?.let {
                MediaItemDecoder.decodeItem(it)
            },
            (0 until sections.length()).map { i ->
                val section = sections.getJSONObject(i)
                val items = section.optJSONArray("items") ?: JSONArray()
                MediaSection(
                    section.optString("id"),
                    (0 until items.length()).map {
                        MediaItemDecoder.decodeItem(items.getJSONObject(it))
                    },
                )
            },
            scopedModules,
        )
    }

    private fun spotifyIntegration(): ServiceModule? =
        try {
            val integrations = api.request("GET", "/v1/integrations").optJSONArray("integrations")
            (0 until (integrations?.length() ?: 0))
                .asSequence()
                .mapNotNull { integrations?.optJSONObject(it) }
                .firstOrNull { it.optString("id") == "spotify" }
                ?.let { ServiceModule("spotify", it.optString("state"), it.optString("authMode")) }
        } catch (_: Exception) {
            null
        }
}
