package io.github.diegog0477.zombiebox.client.features.playback.data

import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.QualityInventory
import io.github.diegog0477.zombiebox.client.features.playback.domain.model.QualityOption
import io.github.diegog0477.zombiebox.client.features.playback.domain.repository.QualityRepository
import io.github.diegog0477.zombiebox.shared.GatewayApi
import org.json.JSONObject

class GatewayQualityRepository(private val api: GatewayApi) : QualityRepository {
    override fun qualities(session: String): QualityInventory {
        return try {
            val value = api.request("GET", "/v1/playback/$session/qualities")
            val selectedId = value.optString("selectedId", "auto")
            val optionsArray = value.optJSONArray("options")
            val options =
                if (optionsArray != null && optionsArray.length() > 0) {
                    (0 until optionsArray.length()).map { i ->
                        val obj = optionsArray.getJSONObject(i)
                        QualityOption(
                            id = obj.getString("id"),
                            label = obj.optString("label", obj.getString("id")),
                            width = obj.optInt("width", 0),
                            height = obj.optInt("height", 0),
                        )
                    }
                } else {
                    listOf(QualityOption("auto", "Auto"))
                }
            QualityInventory(selectedId, options)
        } catch (_: Exception) {
            QualityInventory("auto", listOf(QualityOption("auto", "Auto")))
        }
    }

    override fun select(session: String, qualityId: String, positionMs: Int): PlaybackPlan {
        val payload =
            JSONObject().apply {
                put("qualityId", qualityId)
                put("positionMs", positionMs)
            }
        val response = api.request("POST", "/v1/playback/$session/quality", payload)
        return PlaybackPlanDecoder.decode(api.base, response)
    }
}
