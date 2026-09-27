package io.github.diegog0477.zombiebox.client.features.diagnostics.platform

/** An EVENT probe must prove playback past its initial two segments and later HLS fetches. */
internal object HlsEventProbePolicy {
    data class Evidence(
        val started: Boolean,
        val initialSegmentCount: Int,
        val publishedSegmentCount: Int,
        val laterPlaylistReloadObserved: Boolean,
        val laterSegmentDeliveryObserved: Boolean,
        val deliveredSegmentIndexes: List<Int>,
        val publicationComplete: Boolean,
    )

    data class Verdict(val status: String, val detail: String)

    fun evaluate(positionMs: Int, evidence: Evidence?): Verdict {
        if (positionMs < 20_000) return Verdict("UNKNOWN", "event_no_continuous_progress")
        if (evidence == null) return Verdict("UNKNOWN", "event_evidence_unavailable")
        if (!evidence.started || evidence.initialSegmentCount != 2)
            return Verdict("FAIL", "event_start_unverified")
        if (!evidence.laterPlaylistReloadObserved)
            return Verdict("FAIL", "event_no_playlist_reload")
        if (
            !evidence.laterSegmentDeliveryObserved ||
                evidence.deliveredSegmentIndexes.none { it >= 4 }
        )
            return Verdict("FAIL", "event_no_later_segment")
        if (!evidence.publicationComplete || evidence.publishedSegmentCount < 7)
            return Verdict("UNKNOWN", "event_publication_incomplete")
        return Verdict("PASS", "")
    }
}
