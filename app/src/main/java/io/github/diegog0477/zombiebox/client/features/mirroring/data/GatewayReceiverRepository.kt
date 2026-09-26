package io.github.diegog0477.zombiebox.client.features.mirroring.data

import io.github.diegog0477.zombiebox.client.core.data.MediaItemDecoder
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.ReceiverPlan
import io.github.diegog0477.zombiebox.client.features.mirroring.domain.repository.ReceiverRepository
import io.github.diegog0477.zombiebox.shared.GatewayApi
import io.github.diegog0477.zombiebox.shared.GatewayFailure
import org.json.JSONObject

class GatewayReceiverRepository(private val api: GatewayApi) : ReceiverRepository {
    override fun mediaProvider(): String =
        try {
            api.request("GET", "/v1/media-receiver").optString("provider")
        } catch (error: GatewayFailure) {
            if (error.status == 404) "" else throw error
        }

    override fun rearmMediaProvider(provider: String) {
        require(provider in listOf("spotify", "airplay", "auto", "universal"))
        api.request(
            "PUT",
            "/v1/media-receiver",
            JSONObject().put("provider", provider).put("replaceExisting", false),
        )
    }

    override fun selectMediaProvider(provider: String) {
        if (provider == "universal") {
            val preferences =
                api.request("GET", "/v1/device/preferences")
                    .put("allowReceiverHandoff", true)
                    .put("allowCasting", true)
            api.request("PUT", "/v1/device/preferences", preferences)
        }
        if (provider.isEmpty()) api.request("DELETE", "/v1/media-receiver")
        else
            api.request(
                "PUT",
                "/v1/media-receiver",
                JSONObject().put("provider", provider).put("replaceExisting", true),
            )
    }

    override fun command(action: String) {
        api.request("POST", "/v1/player/spotify", JSONObject().put("action", action))
    }

    override fun airplayCommand(action: String) {
        require(action == "next" || action == "previous" || action == "playpause")
        api.request("POST", "/v1/player/airplay", JSONObject().put("action", action))
    }

    override fun active(): ReceiverPlan? {
        val reception = api.request("GET", "/v1/cast/active")
        val cast = reception.optJSONObject("plan")
        val receiver =
            try {
                api.request("GET", "/v1/media-receiver")
            } catch (error: GatewayFailure) {
                if (cast != null) return decode(cast, null)
                if (error.status == 404) return null
                throw error
            } catch (error: Exception) {
                if (cast != null) return decode(cast, null)
                throw error
            }
        val plan = receiver.optJSONObject("plan")
        if (plan == null) {
            if (cast != null) return decode(cast, null)
            if (reception.optBoolean("preparing"))
                throw IllegalStateException("Receiver is preparing the next queued item")
            return null
        }
        return decode(plan, receiver.optJSONObject("nowPlaying"))
    }

    private fun decode(plan: JSONObject, nowPlaying: JSONObject?): ReceiverPlan {
        val path = plan.getString("url")
        require(path.startsWith("/v1/streams/") && !path.contains("\\") && !path.contains("#"))
        val item = plan.optJSONObject("item")
        val positionKnown =
            item?.optString("provider") == "airplay" &&
                item.optString("kind") == "audio" &&
                nowPlaying?.optBoolean("positionKnown") == true &&
                nowPlaying.optLong("positionMs", -1L) in 0..Int.MAX_VALUE.toLong() &&
                nowPlaying.optLong("durationMs", -1L) in 1..Int.MAX_VALUE.toLong() &&
                nowPlaying.optLong("positionAgeMs", 0L) in 0..5000
        return ReceiverPlan(
            plan.getString("sessionId"),
            path,
            plan.getString("mimeType"),
            item?.let { MediaItemDecoder.decodeItem(it) },
            item?.optString("kind") != "audio",
            nowPlaying?.optString("state") ?: "PLAYING",
            plan.optBoolean("live", true),
            plan.optBoolean("seekable", false),
            plan.optString("mode", "DIRECT_PLAY"),
            positionKnown,
            if (positionKnown) nowPlaying!!.optInt("positionMs") else 0,
            if (positionKnown) nowPlaying!!.optInt("durationMs") else 0,
            if (positionKnown) nowPlaying!!.optInt("positionAgeMs") else 0,
        )
    }

    override fun cancelQueue() {
        api.request("DELETE", "/v1/cast/queue")
    }

    override fun stop(sessionId: String) {
        api.request("DELETE", "/v1/playback/$sessionId")
    }

    override fun handoffEnabled() =
        api.request("GET", "/v1/device/preferences").optBoolean("allowReceiverHandoff")

    override fun setHandoffEnabled(enabled: Boolean) {
        val preferences =
            api.request("GET", "/v1/device/preferences").put("allowReceiverHandoff", enabled)
        api.request("PUT", "/v1/device/preferences", preferences)
    }

    override fun enabled() = api.request("GET", "/v1/device/preferences").optBoolean("allowCasting")

    override fun setEnabled(enabled: Boolean) {
        val preferences = api.request("GET", "/v1/device/preferences").put("allowCasting", enabled)
        api.request("PUT", "/v1/device/preferences", preferences)
    }
}
