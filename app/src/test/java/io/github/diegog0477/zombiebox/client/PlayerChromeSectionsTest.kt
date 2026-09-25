package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.playback.presentation.ui.PlayerChromeSections
import org.junit.Assert.*
import org.junit.Test

class PlayerChromeSectionsTest {
    @Test
    fun lowerPanelOpensOnDemandAndSurvivesRelatedPagination() {
        val state = PlayerChromeSections()
        state.bind("video-a", related = true, details = true)
        assertFalse(state.expanded)
        assertTrue(state.expand())
        state.bind("video-a", related = true, details = true)
        assertTrue(state.expanded)
        assertTrue(state.collapse())
        assertFalse(state.expanded)
        state.bind("video-a", related = true, details = true)
        assertFalse(state.expanded)
    }

    @Test
    fun newVideoAndUnavailableContentResetThePanel() {
        val state = PlayerChromeSections()
        state.bind("video-a", related = true, details = false)
        assertTrue(state.expand())
        state.bind("video-b", related = true, details = false)
        assertFalse(state.expanded)
        assertTrue(state.expand())
        state.bind("video-b", related = false, details = false)
        assertFalse(state.expanded)
        assertFalse(state.expand())
    }
}
