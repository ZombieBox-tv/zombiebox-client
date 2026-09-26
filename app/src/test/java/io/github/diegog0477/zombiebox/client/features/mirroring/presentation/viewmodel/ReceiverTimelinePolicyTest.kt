package io.github.diegog0477.zombiebox.client.features.mirroring.presentation.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverTimelinePolicyTest {
    private fun observe(
        previous: ReceiverTimelinePolicy.State = ReceiverTimelinePolicy.State(),
        session: String = "receiver-session",
        item: String = "track-1",
        known: Boolean = true,
        position: Int = 1_000,
        duration: Int = 30_000,
        age: Int = 0,
        status: String = "PLAYING",
        now: Long = 1_000,
    ) =
        ReceiverTimelinePolicy.observe(
            previous,
            session,
            item,
            known,
            position,
            duration,
            age,
            status,
            now,
        )

    @Test
    fun freshSampleAgeIsTranslatedToMonotonicAnchorAndPositionClampsAtDuration() {
        val state = observe(position = 1_000, duration = 5_000, age = 200)
        assertEquals(1_000, state.reportedPositionMs)
        assertEquals(200, state.reportedSampleAgeMs)
        assertEquals(200L, ReceiverTimelinePolicy.sampleAgeMs(state, 1_000))
        assertEquals(1_200, ReceiverTimelinePolicy.estimate(state, 1_000)?.positionMs)
        assertEquals(1_500, ReceiverTimelinePolicy.estimate(state, 1_300)?.positionMs)
        assertNull(ReceiverTimelinePolicy.estimate(state, 6_001))

        val nearEnd = observe(position = 4_900, duration = 5_000, age = 500, now = 2_000)
        assertEquals(5_000, ReceiverTimelinePolicy.estimate(nearEnd, 2_000)?.positionMs)
        assertEquals(5_000, ReceiverTimelinePolicy.estimate(nearEnd, 4_000)?.positionMs)
    }

    @Test
    fun staleAndUnknownSamplesDoNotClaimMeasuredSenderPosition() {
        val initial = observe(position = 4_000, now = 1_000)
        val stale =
            observe(
                previous = initial,
                age = ReceiverTimelinePolicy.MAX_SAMPLE_AGE_MS.toInt() + 1,
                now = 1_500,
            )
        assertFalse(stale.senderPositionKnown)
        assertNull(ReceiverTimelinePolicy.estimate(stale, 1_500))
        assertEquals(1_000, stale.reportedPositionMs)
        assertEquals(5_001L, ReceiverTimelinePolicy.sampleAgeMs(stale, 1_500))
        assertEquals(4_500, ReceiverTimelinePolicy.estimateForDisplay(stale, 1_500)?.positionMs)
        assertFalse(
            ReceiverTimelinePolicy.estimateForDisplay(stale, 1_500)?.senderMeasurementFresh ?: true
        )

        val unknown = observe(previous = initial, known = false, status = "PAUSED", now = 2_000)
        assertFalse(unknown.senderPositionKnown)
        assertNull(ReceiverTimelinePolicy.estimate(unknown, 20_000))
        assertEquals(5_000, ReceiverTimelinePolicy.estimateForDisplay(unknown, 20_000)?.positionMs)
    }

    @Test
    fun playingDriftIsSlewedAtNoMoreThanTenPercentOfWallRate() {
        val initial = observe(position = 10_000, now = 1_000)
        val behind = observe(previous = initial, position = 10_700, now = 2_000)

        assertEquals(11_000, ReceiverTimelinePolicy.estimate(behind, 2_000)?.positionMs)
        assertEquals(11_450, ReceiverTimelinePolicy.estimate(behind, 2_500)?.positionMs)
        assertEquals(11_900, ReceiverTimelinePolicy.estimate(behind, 3_000)?.positionMs)

        val ahead = observe(previous = initial, position = 11_700, now = 2_000)
        assertEquals(11_000, ReceiverTimelinePolicy.estimate(ahead, 2_000)?.positionMs)
        assertEquals(11_550, ReceiverTimelinePolicy.estimate(ahead, 2_500)?.positionMs)
        assertEquals(12_100, ReceiverTimelinePolicy.estimate(ahead, 3_000)?.positionMs)
        assertEquals(18_700, ReceiverTimelinePolicy.estimateForDisplay(ahead, 9_000)?.positionMs)
    }

    @Test
    fun multiSecondDriftResynchronizesToMeasuredSource() {
        val initial = observe(position = 1_000, now = 1_000)
        val corrected = observe(previous = initial, position = 6_000, now = 2_000)

        assertEquals(6_000, ReceiverTimelinePolicy.estimate(corrected, 2_000)?.positionMs)
        assertEquals(6_500, ReceiverTimelinePolicy.estimate(corrected, 2_500)?.positionMs)
    }

    @Test
    fun trackOrSessionChangeStartsFromNewMeasurement() {
        val initial = observe(position = 20_000, now = 1_000)
        val nextTrack = observe(previous = initial, item = "track-2", position = 800, now = 2_000)
        assertEquals(800, ReceiverTimelinePolicy.estimate(nextTrack, 2_000)?.positionMs)

        val nextSession =
            observe(
                previous = nextTrack,
                session = "reconnected-session",
                position = 250,
                now = 3_000,
            )
        assertEquals(250, ReceiverTimelinePolicy.estimate(nextSession, 3_000)?.positionMs)
    }

    @Test
    fun trackChangeDoesNotCarryAnUnconfirmedPausedPosition() {
        val playing = observe(position = 10_000, now = 1_000)
        val unconfirmedPause =
            observe(previous = playing, position = 25_000, status = "PAUSED", now = 2_000)
        assertEquals(25_000L, unconfirmedPause.pendingPausedPositionMs)

        val nextTrack =
            observe(
                previous = unconfirmedPause,
                item = "track-2",
                position = 750,
                status = "PAUSED",
                now = 3_000,
            )

        assertEquals(750, ReceiverTimelinePolicy.estimate(nextTrack, 3_000)?.positionMs)
        assertNull(nextTrack.pendingPausedPositionMs)
    }

    @Test
    fun pausedTimelineDoesNotAdvanceAndResumeContinuesItsPosition() {
        val paused = observe(position = 10_000, status = "PAUSED", now = 1_000)
        assertEquals(10_000, ReceiverTimelinePolicy.estimate(paused, 6_000)?.positionMs)

        val resumed = observe(previous = paused, position = 10_500, status = "PLAYING", now = 6_000)
        assertTrue(resumed.senderPositionKnown)
        assertEquals(10_000, ReceiverTimelinePolicy.estimate(resumed, 6_000)?.positionMs)
        assertEquals(11_100, ReceiverTimelinePolicy.estimate(resumed, 7_000)?.positionMs)
    }

    @Test
    fun pausedSenderPositionIsStableBetweenReports() {
        val initial = observe(position = 10_000, status = "PAUSED", now = 1_000)
        val updated = observe(previous = initial, position = 10_500, status = "PAUSED", now = 2_000)

        assertEquals(10_500, ReceiverTimelinePolicy.estimate(updated, 2_000)?.positionMs)
        assertEquals(10_500, ReceiverTimelinePolicy.estimateForDisplay(updated, 60_000)?.positionMs)
    }

    @Test
    fun repeatedConflictingPausedSamplesHoldPriorEstimateUntilSenderSeeks() {
        val playing = observe(position = 10_000, now = 1_000)
        val firstPause =
            observe(previous = playing, position = 24_000, status = "PAUSED", now = 2_000)

        assertEquals(
            11_000,
            ReceiverTimelinePolicy.estimateForDisplay(firstPause, 2_000)?.positionMs,
        )
        assertNull(ReceiverTimelinePolicy.estimate(firstPause, 2_000))
        assertEquals(24_000, firstPause.reportedPositionMs)
        assertEquals(24_000L, firstPause.pendingPausedPositionMs)
        assertEquals(
            11_000,
            ReceiverTimelinePolicy.estimateForDisplay(firstPause, 60_000)?.positionMs,
        )

        val staleAgain =
            observe(previous = firstPause, position = 24_500, status = "PAUSED", now = 3_000)

        assertEquals(
            11_000,
            ReceiverTimelinePolicy.estimateForDisplay(staleAgain, 3_000)?.positionMs,
        )
        assertNull(ReceiverTimelinePolicy.estimate(staleAgain, 3_000))
        assertEquals(24_500L, staleAgain.pendingPausedPositionMs)

        val stableSeek =
            observe(previous = staleAgain, position = 28_000, status = "PAUSED", now = 4_000)

        assertEquals(28_000, ReceiverTimelinePolicy.estimate(stableSeek, 4_000)?.positionMs)
        assertNull(stableSeek.pendingPausedPositionMs)
    }

    @Test
    fun closePlayToPauseSampleRemainsSourceAuthoritativeImmediately() {
        val playing = observe(position = 10_000, now = 1_000)
        val paused = observe(previous = playing, position = 11_500, status = "PAUSED", now = 2_000)

        assertEquals(11_500, ReceiverTimelinePolicy.estimate(paused, 2_000)?.positionMs)
        assertNull(paused.pendingPausedPositionMs)
    }

    @Test
    fun locallyPausedOutputKeepsItsPositionWhileSenderContinues() {
        val playing = observe(position = 10_000, now = 1_000)
        val locallyPaused = ReceiverTimelinePolicy.setLocallyPaused(playing, true, 2_000)
        val senderContinues =
            ReceiverTimelinePolicy.observe(
                previous = locallyPaused,
                sessionId = "receiver-session",
                itemKey = "track-1",
                senderPositionKnown = true,
                senderPositionMs = 14_000,
                senderDurationMs = 30_000,
                senderPositionAgeMs = 0,
                playbackState = "PLAYING",
                nowElapsedRealtimeMs = 5_000,
                locallyPaused = true,
            )

        assertEquals(11_000, ReceiverTimelinePolicy.estimate(senderContinues, 5_000)?.positionMs)
        assertEquals(
            11_000,
            ReceiverTimelinePolicy.estimateForDisplay(senderContinues, 60_000)?.positionMs,
        )
        assertTrue(senderContinues.senderPositionKnown)
    }

    @Test
    fun playingBufferingPauseAndResumePreservePositionWithoutInferringPauseFromSilence() {
        val playing = observe(position = 10_000, now = 1_000)
        val buffering =
            observe(previous = playing, known = false, status = "BUFFERING", now = 1_700)

        assertEquals("BUFFERING", buffering.playbackState)
        assertFalse(buffering.locallyPaused)
        assertEquals(
            10_700,
            ReceiverTimelinePolicy.estimateForDisplay(buffering, 10_000)?.positionMs,
        )

        val paused =
            observe(previous = buffering, position = 10_600, status = "PAUSED", now = 2_000)
        assertEquals(10_600, ReceiverTimelinePolicy.estimate(paused, 2_000)?.positionMs)
        assertEquals(10_600, ReceiverTimelinePolicy.estimate(paused, 2_500)?.positionMs)
        assertEquals(10_600, ReceiverTimelinePolicy.estimate(paused, 3_000)?.positionMs)
        assertEquals(10_600, ReceiverTimelinePolicy.estimateForDisplay(paused, 60_000)?.positionMs)

        val resumed =
            observe(
                previous = paused,
                position = 10_600,
                age = 500,
                status = "PLAYING",
                now = 60_000,
            )
        assertEquals(10_600, ReceiverTimelinePolicy.estimate(resumed, 60_000)?.positionMs)
        assertEquals(11_150, ReceiverTimelinePolicy.estimate(resumed, 60_500)?.positionMs)
    }

    @Test
    fun staleSampleAndLongLocalPauseKeepAnUnclaimedContinuousEstimate() {
        val playing = observe(position = 10_000, now = 1_000)
        val locallyPaused = ReceiverTimelinePolicy.setLocallyPaused(playing, true, 1_500)
        assertEquals(10_500, ReceiverTimelinePolicy.estimate(locallyPaused, 1_500)?.positionMs)

        val staleDuringLongPause =
            observe(
                    previous = locallyPaused,
                    known = false,
                    status = "PLAYING",
                    now = 60_000,
                    // This state models only local PCM pause. Gateway still reports PLAYING.
                )
                .copy(locallyPaused = true)
        val held = ReceiverTimelinePolicy.estimateForDisplay(staleDuringLongPause, 180_000)
        assertEquals(10_500, held?.positionMs)
        assertFalse(held?.senderMeasurementFresh ?: true)
        assertEquals("PLAYING", staleDuringLongPause.playbackState)

        val resumed = ReceiverTimelinePolicy.setLocallyPaused(staleDuringLongPause, false, 180_000)
        assertEquals(
            10_500,
            ReceiverTimelinePolicy.estimateForDisplay(resumed, 180_000)?.positionMs,
        )
        assertEquals(
            11_500,
            ReceiverTimelinePolicy.estimateForDisplay(resumed, 181_000)?.positionMs,
        )
    }

    @Test
    fun playPauseToggleNeedsAConfirmedRemoteState() {
        assertEquals(false, ReceiverTimelinePolicy.desiredPlayStateForToggle("PLAYING"))
        assertEquals(true, ReceiverTimelinePolicy.desiredPlayStateForToggle("PAUSED"))
        assertNull(ReceiverTimelinePolicy.desiredPlayStateForToggle("BUFFERING"))
        assertNull(ReceiverTimelinePolicy.desiredPlayStateForToggle(""))
    }

    @Test
    fun localOutputPauseFreezesTimelineWithoutClaimingSenderPause() {
        val playing = observe(position = 10_000, status = "PLAYING", now = 1_000)
        val locallyPaused = ReceiverTimelinePolicy.setLocallyPaused(playing, true, 2_000)

        assertEquals("PLAYING", locallyPaused.playbackState)
        assertEquals("PAUSED", ReceiverTimelinePolicy.displayState("PLAYING", true))
        assertEquals("PLAYING", ReceiverTimelinePolicy.displayState("PLAYING", false))
        assertTrue(locallyPaused.locallyPaused)
        assertEquals(11_000, ReceiverTimelinePolicy.estimate(locallyPaused, 2_000)?.positionMs)
        assertEquals(11_000, ReceiverTimelinePolicy.estimate(locallyPaused, 5_000)?.positionMs)

        val resumed = ReceiverTimelinePolicy.setLocallyPaused(locallyPaused, false, 5_000)
        assertEquals(11_000, ReceiverTimelinePolicy.estimate(resumed, 5_000)?.positionMs)
        assertEquals(12_000, ReceiverTimelinePolicy.estimate(resumed, 6_000)?.positionMs)
    }
}
