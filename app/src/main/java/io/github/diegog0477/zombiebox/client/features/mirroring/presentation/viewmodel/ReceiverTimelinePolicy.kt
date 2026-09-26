package io.github.diegog0477.zombiebox.client.features.mirroring.presentation.viewmodel

/** Keeps measured receiver time separate from local decoder progress. */
object ReceiverTimelinePolicy {
    /** Samples older than this are not treated as current sender measurements. */
    const val MAX_SAMPLE_AGE_MS = 5_000L
    const val HARD_RESYNC_LIMIT_MS = 2_000L
    private const val MAX_SLEW_RATE_PERCENT = 10L

    data class State(
        val sessionId: String = "",
        val itemKey: String = "",
        val reportedPositionMs: Int? = null,
        val reportedSampleAgeMs: Int? = null,
        val sampleObservedElapsedRealtimeMs: Long = 0L,
        val hasAnchor: Boolean = false,
        val anchorPositionMs: Long = 0L,
        val anchorElapsedRealtimeMs: Long = 0L,
        val senderSampleElapsedRealtimeMs: Long = 0L,
        val durationMs: Int = 0,
        val playbackState: String = "",
        val locallyPaused: Boolean = false,
        val senderPositionKnown: Boolean = false,
        val correctionMs: Long = 0L,
        val correctionStartedAtMs: Long = 0L,
        val correctionDurationMs: Long = 0L,
    )

    data class Position(
        val positionMs: Int,
        val durationMs: Int,
        val senderMeasurementFresh: Boolean,
    )

    fun displayState(senderState: String, locallyPaused: Boolean): String =
        if (locallyPaused) "PAUSED" else senderState

    /** A DACP play/pause command is a toggle, so only stable remote states define its target. */
    fun desiredPlayStateForToggle(reportedState: String): Boolean? =
        when (reportedState) {
            "PLAYING" -> false
            "PAUSED" -> true
            else -> null
        }

    fun observe(
        previous: State,
        sessionId: String,
        itemKey: String,
        senderPositionKnown: Boolean,
        senderPositionMs: Int,
        senderDurationMs: Int,
        senderPositionAgeMs: Int,
        playbackState: String,
        nowElapsedRealtimeMs: Long,
        locallyPaused: Boolean = false,
    ): State {
        val sameIdentity = previous.sessionId == sessionId && previous.itemKey == itemKey
        val validSample =
            sessionId.isNotEmpty() &&
                itemKey.isNotEmpty() &&
                senderPositionKnown &&
                senderPositionMs >= 0 &&
                senderDurationMs > 0 &&
                senderPositionAgeMs in 0..MAX_SAMPLE_AGE_MS.toInt()
        val reportedPosition = senderPositionMs.takeIf { senderPositionKnown && it >= 0 }
        val reportedAge = senderPositionAgeMs.takeIf { senderPositionKnown && it >= 0 }
        val sampleObservedAt = if (reportedAge != null) nowElapsedRealtimeMs else null

        if (!validSample) {
            if (!sameIdentity) {
                return State(
                    sessionId = sessionId,
                    itemKey = itemKey,
                    reportedPositionMs = reportedPosition,
                    reportedSampleAgeMs = reportedAge,
                    sampleObservedElapsedRealtimeMs = sampleObservedAt ?: 0L,
                    durationMs = senderDurationMs.coerceAtLeast(0),
                    playbackState = playbackState,
                    locallyPaused = locallyPaused,
                )
            }
            val currentPosition = estimatePosition(previous, nowElapsedRealtimeMs)
            return previous.copy(
                reportedPositionMs = reportedPosition ?: previous.reportedPositionMs,
                reportedSampleAgeMs = reportedAge ?: previous.reportedSampleAgeMs,
                sampleObservedElapsedRealtimeMs =
                    sampleObservedAt ?: previous.sampleObservedElapsedRealtimeMs,
                durationMs = senderDurationMs.takeIf { it > 0 } ?: previous.durationMs,
                hasAnchor = currentPosition != null,
                anchorPositionMs = currentPosition?.toLong() ?: previous.anchorPositionMs,
                anchorElapsedRealtimeMs = nowElapsedRealtimeMs,
                playbackState = playbackState,
                locallyPaused = locallyPaused,
                senderPositionKnown = false,
                correctionMs = 0L,
                correctionStartedAtMs = nowElapsedRealtimeMs,
                correctionDurationMs = 0L,
            )
        }

        val duration = senderDurationMs
        val ageMs = senderPositionAgeMs.toLong()
        val samplePosition =
            (senderPositionMs.toLong() + if (playbackState == "PLAYING") ageMs else 0L).coerceIn(
                0L,
                duration.toLong(),
            )

        if (!sameIdentity || !previous.hasAnchor) {
            return State(
                sessionId = sessionId,
                itemKey = itemKey,
                reportedPositionMs = senderPositionMs,
                reportedSampleAgeMs = senderPositionAgeMs,
                sampleObservedElapsedRealtimeMs = nowElapsedRealtimeMs,
                hasAnchor = true,
                anchorPositionMs = samplePosition,
                anchorElapsedRealtimeMs = nowElapsedRealtimeMs,
                senderSampleElapsedRealtimeMs = (nowElapsedRealtimeMs - ageMs).coerceAtLeast(0L),
                durationMs = duration,
                playbackState = playbackState,
                locallyPaused = locallyPaused,
                senderPositionKnown = true,
            )
        }

        val currentPosition =
            estimatePosition(previous, nowElapsedRealtimeMs) ?: samplePosition.toInt()
        if (locallyPaused) {
            return State(
                sessionId = sessionId,
                itemKey = itemKey,
                reportedPositionMs = senderPositionMs,
                reportedSampleAgeMs = senderPositionAgeMs,
                sampleObservedElapsedRealtimeMs = nowElapsedRealtimeMs,
                hasAnchor = true,
                anchorPositionMs = currentPosition.toLong(),
                anchorElapsedRealtimeMs = nowElapsedRealtimeMs,
                senderSampleElapsedRealtimeMs = (nowElapsedRealtimeMs - ageMs).coerceAtLeast(0L),
                durationMs = duration,
                playbackState = playbackState,
                locallyPaused = true,
                senderPositionKnown = true,
            )
        }

        if (playbackState != "PLAYING") {
            return State(
                sessionId = sessionId,
                itemKey = itemKey,
                reportedPositionMs = senderPositionMs,
                reportedSampleAgeMs = senderPositionAgeMs,
                sampleObservedElapsedRealtimeMs = nowElapsedRealtimeMs,
                hasAnchor = true,
                anchorPositionMs = samplePosition,
                anchorElapsedRealtimeMs = nowElapsedRealtimeMs,
                senderSampleElapsedRealtimeMs = (nowElapsedRealtimeMs - ageMs).coerceAtLeast(0L),
                durationMs = duration,
                playbackState = playbackState,
                senderPositionKnown = true,
            )
        }

        val driftMs = samplePosition - currentPosition.toLong()
        if (kotlin.math.abs(driftMs) >= HARD_RESYNC_LIMIT_MS) {
            return State(
                sessionId = sessionId,
                itemKey = itemKey,
                reportedPositionMs = senderPositionMs,
                reportedSampleAgeMs = senderPositionAgeMs,
                sampleObservedElapsedRealtimeMs = nowElapsedRealtimeMs,
                hasAnchor = true,
                anchorPositionMs = samplePosition,
                anchorElapsedRealtimeMs = nowElapsedRealtimeMs,
                senderSampleElapsedRealtimeMs = (nowElapsedRealtimeMs - ageMs).coerceAtLeast(0L),
                durationMs = duration,
                playbackState = playbackState,
                locallyPaused = locallyPaused,
                senderPositionKnown = true,
            )
        }

        // Apply at most 10% extra or reduced wall-clock rate while catching up. The previous
        // fixed one/two second window could turn a 1.9 second correction into nearly 2x speed.
        val slewDuration = kotlin.math.abs(driftMs) * 100L / MAX_SLEW_RATE_PERCENT
        return State(
            sessionId = sessionId,
            itemKey = itemKey,
            reportedPositionMs = senderPositionMs,
            reportedSampleAgeMs = senderPositionAgeMs,
            sampleObservedElapsedRealtimeMs = nowElapsedRealtimeMs,
            hasAnchor = true,
            anchorPositionMs = currentPosition.toLong(),
            anchorElapsedRealtimeMs = nowElapsedRealtimeMs,
            senderSampleElapsedRealtimeMs = (nowElapsedRealtimeMs - ageMs).coerceAtLeast(0L),
            durationMs = duration,
            playbackState = playbackState,
            locallyPaused = locallyPaused,
            senderPositionKnown = true,
            correctionMs = driftMs,
            correctionStartedAtMs = nowElapsedRealtimeMs,
            correctionDurationMs = slewDuration,
        )
    }

    /** Returns a measured-source estimate only while the latest source sample is fresh. */
    fun estimate(state: State, nowElapsedRealtimeMs: Long): Position? {
        if (!hasFreshSenderSample(state, nowElapsedRealtimeMs)) return null
        return estimateForDisplay(state, nowElapsedRealtimeMs)
    }

    /**
     * Retains a continuous display estimate after a sender sample expires. The returned freshness
     * flag stays false, so an old report is never presented as a current sender measurement.
     */
    fun estimateForDisplay(state: State, nowElapsedRealtimeMs: Long): Position? {
        if (!state.hasAnchor || state.durationMs <= 0) return null
        val position = estimatePosition(state, nowElapsedRealtimeMs) ?: return null
        return Position(
            position,
            state.durationMs,
            hasFreshSenderSample(state, nowElapsedRealtimeMs),
        )
    }

    fun hasFreshSenderSample(state: State, nowElapsedRealtimeMs: Long): Boolean {
        if (!state.senderPositionKnown || !state.hasAnchor) return false
        val ageMs = sampleAgeMs(state, nowElapsedRealtimeMs) ?: return false
        return ageMs <= MAX_SAMPLE_AGE_MS
    }

    fun sampleAgeMs(state: State, nowElapsedRealtimeMs: Long): Long? {
        val reportedAge = state.reportedSampleAgeMs ?: return null
        val sinceObservation =
            (nowElapsedRealtimeMs - state.sampleObservedElapsedRealtimeMs).coerceAtLeast(0L)
        return reportedAge.toLong() + sinceObservation
    }

    /** Freezes only the client output timeline; it never changes the sender's reported state. */
    fun setLocallyPaused(previous: State, paused: Boolean, nowElapsedRealtimeMs: Long): State {
        if (previous.locallyPaused == paused) return previous
        val position = estimatePosition(previous, nowElapsedRealtimeMs)
        return previous.copy(
            hasAnchor = position != null,
            anchorPositionMs = position?.toLong() ?: previous.anchorPositionMs,
            anchorElapsedRealtimeMs = nowElapsedRealtimeMs,
            locallyPaused = paused,
            correctionMs = 0L,
            correctionStartedAtMs = nowElapsedRealtimeMs,
            correctionDurationMs = 0L,
        )
    }

    private fun estimatePosition(state: State, nowElapsedRealtimeMs: Long): Int? {
        if (!state.hasAnchor || state.durationMs <= 0) return null
        val elapsedMs = (nowElapsedRealtimeMs - state.anchorElapsedRealtimeMs).coerceAtLeast(0L)
        val advances = state.playbackState == "PLAYING" && !state.locallyPaused
        val basePosition = state.anchorPositionMs + if (advances) elapsedMs else 0L
        val correctionProgress =
            if (
                state.correctionDurationMs <= 0L ||
                    state.playbackState != "PLAYING" ||
                    state.locallyPaused
            ) {
                1.0
            } else {
                ((nowElapsedRealtimeMs - state.correctionStartedAtMs).coerceAtLeast(0L).toDouble() /
                        state.correctionDurationMs.toDouble())
                    .coerceIn(0.0, 1.0)
            }
        val correction = (state.correctionMs * correctionProgress).toLong()
        return (basePosition + correction).coerceIn(0L, state.durationMs.toLong()).toInt()
    }
}
