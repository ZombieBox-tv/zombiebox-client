package io.github.diegog0477.zombiebox.client.features.diagnostics.data

import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeAsset
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeResult
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.repository.ProbeRepository
import io.github.diegog0477.zombiebox.shared.GatewayApi
import io.github.diegog0477.zombiebox.shared.GatewayFailure
import org.json.JSONArray
import org.json.JSONObject

class GatewayProbeRepository(
    private val api: GatewayApi,
    private val localAssets: () -> List<ProbeAsset> = { emptyList() },
    private val recordEvidence: (List<ProbeResult>) -> Unit = {},
    private val clientVersion: () -> String = { "" },
    private val refreshInventory: () -> Unit = {},
) : ProbeRepository {
    private var cacheKey = ""
    private var suiteVersion = 1
    private var serverTimeUnixSeconds = 0L
    private var receivedAtNanos = 0L
    private var clientVersionRefreshSupported = true

    override fun assets(): List<ProbeAsset> {
        val version = clientVersion()
        clientVersionRefreshSupported = true
        if (version.isNotEmpty()) {
            try {
                api.request("PUT", "/v1/device/client", JSONObject().put("clientVersion", version))
            } catch (error: GatewayFailure) {
                if (error.status != 404) throw error
                // Older gateways have no authenticated version refresh. Replace their
                // evidence only after the newly measured suite has completed.
                clientVersionRefreshSupported = false
            }
        }
        refreshInventory()
        val manifest = api.request("GET", "/v1/probes?suite=2&extended=1")
        receivedAtNanos = System.nanoTime()
        val data = manifest.getJSONArray("probes")
        serverTimeUnixSeconds =
            ProbeServerClock.anchor(
                manifest.optLong("serverTimeUnixSeconds", 0L),
                (0 until data.length()).map { data.getJSONObject(it).getString("url") },
            )
        cacheKey = manifest.optString("cacheKey")
        suiteVersion = manifest.optInt("suiteVersion", 1)
        val assets =
            (0 until data.length().coerceAtMost(31)).map {
                val item = data.getJSONObject(it)
                val path = item.getString("url")
                require(path.startsWith("/v1/probes/") && !path.contains(".."))
                ProbeAsset(
                    item.getString("id"),
                    api.base + path,
                    item.getBoolean("video"),
                    item.optString("kind", "playback"),
                    item.optString("requires"),
                )
            }
        val texture =
            assets
                .firstOrNull { it.id == "h264-baseline-360" }
                ?.copy(id = "texture-output", kind = "texture-output")
        return assets + listOfNotNull(texture) + localAssets()
    }

    override fun save(results: List<ProbeResult>) {
        val testedAt =
            ProbeServerClock.testedAt(serverTimeUnixSeconds, receivedAtNanos, System.nanoTime())
        val capabilities = api.request("GET", "/v1/device").getJSONObject("capabilities")
        val previous =
            if (clientVersionRefreshSupported && capabilities.optString("cacheKey") == cacheKey)
                capabilities.optJSONArray("probes") ?: JSONArray()
            else JSONArray()
        val replaced = results.map { it.id }.toSet()
        val values = JSONArray()
        for (i in 0 until previous.length()) if (
            previous.getJSONObject(i).optString("id") !in replaced
        )
            values.put(previous.getJSONObject(i))
        results.forEach {
            values.put(
                JSONObject()
                    .put("id", it.id)
                    .put("status", it.status)
                    .put("prepareMs", it.prepareMs)
                    .put("firstFrameMs", it.firstFrameMs)
                    .put("positionMs", it.positionMs)
                    .put("completed", it.completed)
                    .put("droppedOrStalled", it.stalled)
                    .put("testedAt", testedAt)
            )
        }
        api.request(
            "PUT",
            "/v1/device/capabilities",
            JSONObject()
                .put("capabilitiesVersion", 1)
                .put("deviceId", api.device)
                .apply {
                    if (cacheKey.isNotEmpty()) {
                        put("cacheKey", cacheKey)
                        put("suiteVersion", suiteVersion)
                    }
                }
                .put("probes", values),
        )
        recordEvidence(results)
    }
}
