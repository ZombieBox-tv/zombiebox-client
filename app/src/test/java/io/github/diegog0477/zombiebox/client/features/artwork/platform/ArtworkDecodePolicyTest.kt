package io.github.diegog0477.zombiebox.client.features.artwork.platform

import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRequestRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkDecodePolicyTest {
    @Test
    fun lowMemoryAudioRequestDecodesSquare600WithinFourMiB() {
        val budget = ImageBudget(lowMemory = true)
        val edge = budget.targetEdge(ArtworkRequestRole.AUDIO)
        val decodedBytes = edge.toLong() * edge * 2

        assertEquals(600, edge)
        assertEquals(4L * 1024 * 1024, budget.decodedBytes)
        assertTrue(budget.decodedBytesWithinBudget(decodedBytes))
        assertFalse(budget.decodedBytesWithinBudget(budget.decodedBytes + 1))
        assertEquals(1, artworkSampleSize(600, 600, edge, ArtworkRequestRole.AUDIO))
        assertEquals(2, artworkSampleSize(960, 960, edge, ArtworkRequestRole.AUDIO))
    }

    @Test
    fun defaultCardAndHeroSamplingKeepTheirExistingWidthTargets() {
        val budget = ImageBudget(lowMemory = true)

        assertEquals(240, budget.targetEdge(ArtworkRequestRole.DEFAULT))
        assertEquals(640, budget.targetEdge(ArtworkRequestRole.HERO))
        assertEquals(4, artworkSampleSize(960, 540, 240, ArtworkRequestRole.DEFAULT))
        assertEquals(2, artworkSampleSize(960, 540, 640, ArtworkRequestRole.HERO))
    }
}
