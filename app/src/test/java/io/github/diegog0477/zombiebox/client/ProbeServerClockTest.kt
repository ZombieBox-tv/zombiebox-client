package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.diagnostics.data.ProbeServerClock
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeClockUnavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProbeServerClockTest {
    @Test
    fun usesGatewayClockAndMonotonicElapsedInsteadOfDeviceWallClock() {
        val gatewayTime = 1_790_000_000L
        val received = 10_000_000_000L
        val later = received + 75_500_000_000L
        assertEquals(gatewayTime + 75L, ProbeServerClock.testedAt(gatewayTime, received, later))
    }

    @Test
    fun oldGatewaySignedUrlSuppliesClockEvenWhenDeviceWallClockIsIn2010() {
        val hostTime = 1_790_000_000L
        val expiry = hostTime + 600L
        val anchor =
            ProbeServerClock.anchor(
                0L,
                listOf(
                    "/v1/probes/aac?expires=$expiry&ticket=aaa",
                    "/v1/probes/h264?expires=$expiry&ticket=bbb",
                ),
            )
        assertEquals(hostTime, anchor)
        assertEquals(hostTime + 10L, ProbeServerClock.testedAt(anchor, 5L, 10_000_000_005L))
    }

    @Test
    fun rejectsMissingInconsistentOrExpiredAnchorRatherThanPublishingStaleEvidence() {
        assertThrows(ProbeClockUnavailable::class.java) { ProbeServerClock.anchor(0L, emptyList()) }
        assertThrows(ProbeClockUnavailable::class.java) {
            ProbeServerClock.anchor(
                0L,
                listOf(
                    "/v1/probes/aac?expires=1790000600&ticket=a",
                    "/v1/probes/h264?expires=1790000601&ticket=b",
                ),
            )
        }
        assertThrows(ProbeClockUnavailable::class.java) { ProbeServerClock.testedAt(0L, 1L, 2L) }
        assertThrows(ProbeClockUnavailable::class.java) {
            ProbeServerClock.testedAt(1_790_000_000L, 1L, 1L + 3L * 60L * 60L * 1_000_000_000L)
        }
        assertThrows(ProbeClockUnavailable::class.java) {
            ProbeServerClock.testedAt(1_790_000_000L, 2L, 1L)
        }
    }
}
