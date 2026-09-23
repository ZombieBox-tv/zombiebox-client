package io.github.diegog0477.zombiebox.client.core.data

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.core.model.Programme
import org.json.JSONArray
import org.json.JSONObject

object MediaItemDecoder {
    fun decodeItem(item: JSONObject): MediaItem {
        val programmes = item.optJSONArray("programmes") ?: JSONArray()
        return MediaItem(
            item.getString("id"),
            item.optString("provider"),
            item.optString("title"),
            item.optString("subtitle"),
            item.optString("description"),
            item.optInt("positionMs"),
            (0 until programmes.length()).map {
                val programme = programmes.getJSONObject(it)
                Programme(
                    programme.optString("title"),
                    programme.optLong("start"),
                    programme.optLong("end"),
                )
            },
            item.optString("imageUrl"),
            item.optInt("durationMs"),
            item.optString("browseId"),
            item.optBoolean("playable", true),
            item.optString("kind"),
            item.optString("guideState"),
            item.optBoolean("favorite", false),
        )
    }
}
