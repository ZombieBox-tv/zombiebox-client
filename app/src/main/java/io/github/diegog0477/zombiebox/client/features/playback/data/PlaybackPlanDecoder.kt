package io.github.diegog0477.zombiebox.client.features.playback.data

import io.github.diegog0477.zombiebox.client.features.playback.domain.model.PlaybackPlan
import org.json.JSONObject

internal object PlaybackPlanDecoder {
    fun decode(base: String, plan: JSONObject) =
        PlaybackPlan(
            sessionId = plan.getString("sessionId"),
            url = base + plan.getString("url"),
            mime = plan.optString("mimeType", "video/mp4"),
            mode = plan.optString("mode"),
            resumePositionMs = plan.optInt("resumePositionMs"),
            timelineOffsetMs = plan.optInt("timelineOffsetMs"),
            live = plan.optBoolean("live"),
            seekable = plan.optBoolean("seekable", true),
            subtitleId = if (plan.has("subtitleId")) plan.getInt("subtitleId") else null,
            prepareBeforePlayback = plan.optBoolean("prepareBeforePlayback", false),
        )
}
