package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.playback.data.PlaybackReadinessDecision
import io.github.diegog0477.zombiebox.client.features.playback.data.PlaybackReadinessPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackReadinessPolicyTest {
    @Test
    fun acceptedPreparationRetriesUntilTheSpoolIsReady() {
        assertEquals(PlaybackReadinessDecision.RETRY, PlaybackReadinessPolicy.decide(202, -1))
        assertEquals(PlaybackReadinessDecision.RETRY, PlaybackReadinessPolicy.decide(429, -1))
        assertEquals(2_000L, PlaybackReadinessPolicy.retryDelayMs(202, "2", 0))
        assertEquals(4_000L, PlaybackReadinessPolicy.retryDelayMs(429, "4", 0))
        assertEquals(
            PlaybackReadinessPolicy.MAX_WAIT_MS,
            PlaybackReadinessPolicy.retryDelayMs(429, "999999999999999999", 0),
        )
        assertEquals(
            PlaybackReadinessPolicy.POLL_INTERVAL_MS,
            PlaybackReadinessPolicy.retryDelayMs(429, null, 0),
        )
    }

    @Test
    fun onlyAnOkResponseWithPositiveContentLengthIsReady() {
        assertEquals(PlaybackReadinessDecision.READY, PlaybackReadinessPolicy.decide(200, 1024))
        assertEquals(PlaybackReadinessDecision.FAILED, PlaybackReadinessPolicy.decide(200, 0))
        assertEquals(PlaybackReadinessDecision.FAILED, PlaybackReadinessPolicy.decide(200, -1))
    }

    @Test
    fun nonReadyStatusesDoNotRetryAndPollingStaysBounded() {
        for (status in listOf(206, 401, 403, 404, 409, 500)) {
            assertEquals(
                "unexpected readiness for HTTP $status",
                PlaybackReadinessDecision.FAILED,
                PlaybackReadinessPolicy.decide(status, 1024),
            )
        }
        assertEquals(120_000L, PlaybackReadinessPolicy.MAX_WAIT_MS)
        assertEquals(5_000, PlaybackReadinessPolicy.REQUEST_TIMEOUT_MS)
        assertEquals(1_000L, PlaybackReadinessPolicy.POLL_INTERVAL_MS)
    }
}
