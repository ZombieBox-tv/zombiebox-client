package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.home.data.PlaybackValidationDecoder
import io.github.diegog0477.zombiebox.client.features.home.domain.model.PlaybackValidationReason
import io.github.diegog0477.zombiebox.client.features.home.domain.model.PlaybackValidationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackValidationTest {
    @Test
    fun currentMeasurementsDoNotRequestAPlaybackCheck() {
        val validation = PlaybackValidationDecoder.decode("CURRENT", "unexpected")

        assertEquals(PlaybackValidationStatus.CURRENT, validation.status)
        assertFalse(validation.needsPlaybackCheck)
    }

    @Test
    fun expiredEvidenceRequestsTheRecoveryAction() {
        val validation = PlaybackValidationDecoder.decode("REFRESH_REQUIRED", "evidence_expired")

        assertEquals(PlaybackValidationStatus.REFRESH_REQUIRED, validation.status)
        assertEquals(PlaybackValidationReason.EXPIRED, validation.reason)
        assertTrue(validation.needsPlaybackCheck)
    }

    @Test
    fun otherRefreshReasonsUseGenericRecoveryCopy() {
        val validation = PlaybackValidationDecoder.decode("REFRESH_REQUIRED", "cache_mismatch")

        assertEquals(PlaybackValidationReason.OTHER, validation.reason)
        assertTrue(validation.needsPlaybackCheck)
    }

    @Test
    fun missingOrUnknownWireStatusIsUnavailable() {
        assertEquals(
            PlaybackValidationStatus.UNAVAILABLE,
            PlaybackValidationDecoder.decode(null, null).status,
        )
        assertEquals(
            PlaybackValidationStatus.UNAVAILABLE,
            PlaybackValidationDecoder.decode("FUTURE_STATUS", "evidence_expired").status,
        )
    }
}
