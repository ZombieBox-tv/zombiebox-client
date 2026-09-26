package io.github.diegog0477.zombiebox.client.features.playback.presentation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlayerSymbolDrawableTest {
    @Test
    fun fractionalVectorScaleIsRoundedToDrawablePixelBoundaries() {
        val coordinates =
            listOf(5f, 7f, 12f, 19f).map { unit ->
                TvPlayerSymbolPixelGrid.coordinate(origin = 2.25f, coordinate = unit, scale = 1.5f)
            }

        assertEquals(listOf(10f, 13f, 20f, 31f), coordinates)
        assertTrue(coordinates.all { it % 1f == 0f })
    }
}
