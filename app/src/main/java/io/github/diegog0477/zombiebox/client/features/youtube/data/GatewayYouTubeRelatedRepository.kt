package io.github.diegog0477.zombiebox.client.features.youtube.data

import io.github.diegog0477.zombiebox.client.core.data.MediaItemDecoder
import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.youtube.domain.model.RelatedPage
import io.github.diegog0477.zombiebox.client.features.youtube.domain.repository.YouTubeRelatedRepository
import io.github.diegog0477.zombiebox.shared.GatewayApi
import java.net.URLEncoder

class GatewayYouTubeRelatedRepository(private val api: GatewayApi) : YouTubeRelatedRepository {
    override fun page(videoId: String, cursor: String): RelatedPage {
        require(VIDEO_ID.matches(videoId))
        require(cursor.length <= 512)
        val path =
            "/v1/youtube/related?video=$videoId&limit=20" +
                if (cursor.isEmpty()) "" else "&cursor=" + URLEncoder.encode(cursor, "UTF-8")
        val response = api.request("GET", path)
        require(response.optString("videoId") == videoId)
        val current = response.optJSONObject("currentVideo")?.let(MediaItemDecoder::decodeItem)
        val raw = response.optJSONArray("items")
        val seen = HashSet<String>()
        seen.add("youtube-$videoId")
        val items = ArrayList<MediaItem>()
        if (raw != null) {
            for (index in 0 until minOf(raw.length(), 40)) {
                val item = MediaItemDecoder.decodeItem(raw.getJSONObject(index))
                if (
                    item.provider == "youtube" &&
                        item.kind == "video" &&
                        item.playable &&
                        seen.add(item.id)
                ) {
                    items.add(item)
                }
            }
        }
        val next = response.optString("nextCursor").take(512)
        return RelatedPage(
            videoId,
            current,
            items,
            if (response.optBoolean("hasMore")) next else "",
        )
    }

    companion object {
        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    }
}
