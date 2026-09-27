package io.github.diegog0477.zombiebox.client.features.diagnostics.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class HlsEventProbePolicyTest {
    private val complete =
        HlsEventProbePolicy.Evidence(
            started = true,
            initialSegmentCount = 2,
            publishedSegmentCount = 7,
            laterPlaylistReloadObserved = true,
            laterSegmentDeliveryObserved = true,
            deliveredSegmentIndexes = listOf(0, 1, 2, 3, 4, 5, 6),
            publicationComplete = true,
        )

    @Test
    fun laterSegmentsAndContinuousProgressPass() {
        assertEquals("PASS", HlsEventProbePolicy.evaluate(24_000, complete).status)
    }

    @Test
    fun firstSegmentOrLocalClockAloneCannotPass() {
        assertEquals("UNKNOWN", HlsEventProbePolicy.evaluate(5_000, complete).status)
        assertEquals("UNKNOWN", HlsEventProbePolicy.evaluate(24_000, null).status)
        assertEquals(
            "FAIL",
            HlsEventProbePolicy.evaluate(24_000, complete.copy(laterPlaylistReloadObserved = false))
                .status,
        )
        assertEquals(
            "FAIL",
            HlsEventProbePolicy.evaluate(
                    24_000,
                    complete.copy(deliveredSegmentIndexes = listOf(0, 1, 2)),
                )
                .status,
        )
    }
}
