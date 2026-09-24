package io.github.diegog0477.zombiebox.client.features.services.data

import io.github.diegog0477.zombiebox.client.features.services.domain.model.AuthorizationPrompt
import io.github.diegog0477.zombiebox.client.features.services.domain.model.Integration
import io.github.diegog0477.zombiebox.client.features.services.domain.model.MusicStatus
import io.github.diegog0477.zombiebox.client.features.services.domain.model.ServicesSnapshot
import io.github.diegog0477.zombiebox.client.features.services.domain.repository.ServicesRepository
import io.github.diegog0477.zombiebox.shared.GatewayApi
import org.json.JSONObject

class GatewayServicesRepository(private val api: GatewayApi) : ServicesRepository {
    override fun load(): ServicesSnapshot {
        val values = api.request("GET", "/v1/integrations").getJSONArray("integrations")
        val integrations =
            (0 until values.length()).map {
                val item = values.getJSONObject(it)
                Integration(
                    item.getString("id"),
                    item.getString("state"),
                    item.optString("authMode"),
                )
            }
        val music =
            try {
                val value = api.request("GET", "/v1/player/spotify")
                val item = value.optJSONObject("item")
                MusicStatus(
                    value.optString("state"),
                    item?.optString("title") ?: "",
                    item?.optString("subtitle") ?: "",
                )
            } catch (_: Exception) {
                null
            }
        return ServicesSnapshot(integrations, music)
    }

    override fun authorize(operatorCode: String): AuthorizationPrompt {
        val value = api.request("GET", "/v1/player/spotify/authorization", admin = operatorCode)
        return AuthorizationPrompt(
            value.optString("state") == "WAITING",
            value.optString("url"),
            value.optString("code"),
        )
    }

    override fun command(action: String, operatorCode: String) {
        api.request(
            "POST",
            "/v1/player/spotify",
            JSONObject().put("action", action),
            admin = operatorCode,
        )
    }
}
