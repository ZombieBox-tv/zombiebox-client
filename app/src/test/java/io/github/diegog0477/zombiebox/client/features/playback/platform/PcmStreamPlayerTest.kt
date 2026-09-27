package io.github.diegog0477.zombiebox.client.features.playback.platform

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmStreamPlayerTest {
    @Test
    fun acceptsOnlyTheAdvertisedPcmEncoding() {
        assertTrue(PcmStreamPolicy.supportsMime(PcmStreamPolicy.MIME_TYPE))
        assertFalse(
            PcmStreamPolicy.supportsMime("audio/x-zombiebox-pcm;channels=2;rate=44100;format=s16le")
        )
        assertFalse(
            PcmStreamPolicy.supportsMime("audio/x-zombiebox-pcm;format=s16le;rate=48000;channels=2")
        )
        assertFalse(PcmStreamPolicy.supportsMime("audio/x-zombiebox-pcm;format=s16le;rate=44100"))
        assertFalse(PcmStreamPolicy.supportsMime(PcmStreamPolicy.MIME_TYPE + ";charset=utf-8"))
        assertFalse(PcmStreamPolicy.supportsMime("audio/mpeg"))
    }

    @Test
    fun onlyAcceptsSuccessfulPcmResponsesAndHttpTicketUrls() {
        assertTrue(PcmStreamPolicy.acceptsResponse(200, PcmStreamPolicy.MIME_TYPE))
        assertFalse(PcmStreamPolicy.acceptsResponse(206, PcmStreamPolicy.MIME_TYPE))
        assertFalse(PcmStreamPolicy.acceptsResponse(302, PcmStreamPolicy.MIME_TYPE))
        assertFalse(PcmStreamPolicy.acceptsResponse(200, "audio/mpeg"))
        assertTrue(PcmStreamPolicy.isSafeUrl("https://gateway.example/stream?ticket=one"))
        assertFalse(PcmStreamPolicy.isSafeUrl("file:///tmp/stream.pcm"))
        assertFalse(PcmStreamPolicy.isSafeUrl("https://user:secret@gateway.example/stream"))
        assertFalse(PcmStreamPolicy.isSafeUrl("https://gateway.example/stream#fragment"))
        assertEquals(22L, PcmStreamPolicy.frameDelta(Int.MAX_VALUE - 10, Int.MIN_VALUE + 11))
    }

    @Test
    fun replacementCancelsTheBlockedReadBeforeOpeningTheNextAudioOutput() {
        val firstRead = CountDownLatch(1)
        val firstClosed = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val outputReleased = CountDownLatch(2)
        val opened = CopyOnWriteArrayList<String>()
        val secondStates = CopyOnWriteArrayList<String>()
        val activeOutputs = AtomicInteger()
        val maximumOutputs = AtomicInteger()
        val secondWasOpenedAfterCancellation = AtomicBoolean(false)
        val transport =
            object : PcmStreamTransport {
                override fun open(url: String): PcmStreamResponse {
                    opened += url
                    if (url.endsWith("/first")) {
                        return BlockingResponse(firstRead, firstClosed)
                    }
                    secondWasOpenedAfterCancellation.set(firstClosed.count == 0L)
                    return PayloadResponse(ByteArray(8 * 1024))
                }
            }
        val player =
            PcmStreamPlayer(transport) {
                val active = activeOutputs.incrementAndGet()
                maximumOutputs.updateAndGet { previous -> maxOf(previous, active) }
                FakeAudioOutput {
                    activeOutputs.decrementAndGet()
                    outputReleased.countDown()
                }
            }

        try {
            player.play("http://gateway.example/first", 0, true) { _, _, _ -> }
            assertTrue("first response did not start reading", firstRead.await(2, TimeUnit.SECONDS))

            player.play("http://gateway.example/second", 0, true) { state, _, _ ->
                secondStates += state
                if (state == "ENDED" || state == "FAILED") secondFinished.countDown()
            }

            assertTrue("replacement did not finish", secondFinished.await(3, TimeUnit.SECONDS))
            assertTrue("audio outputs were not released", outputReleased.await(3, TimeUnit.SECONDS))
            assertEquals(
                listOf("http://gateway.example/first", "http://gateway.example/second"),
                opened,
            )
            assertTrue(secondWasOpenedAfterCancellation.get())
            assertEquals(1, maximumOutputs.get())
            assertTrue(secondStates.contains("PLAYING"))
            assertEquals("ENDED", secondStates.last())
        } finally {
            player.close()
        }
    }

    @Test
    fun replacementWaitsForOldAudioOutputRelease() {
        val firstRead = CountDownLatch(1)
        val firstClosed = CountDownLatch(1)
        val releaseEntered = CountDownLatch(1)
        val allowRelease = CountDownLatch(1)
        val secondOpened = CountDownLatch(1)
        val replacementReturned = CountDownLatch(1)
        val outputs = AtomicInteger()
        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse =
                        if (url.endsWith("/first")) BlockingResponse(firstRead, firstClosed)
                        else {
                            secondOpened.countDown()
                            PayloadResponse(ByteArray(0))
                        }
                }
            ) {
                val number = outputs.incrementAndGet()
                FakeAudioOutput {
                    if (number == 1) {
                        releaseEntered.countDown()
                        assertTrue(allowRelease.await(2, TimeUnit.SECONDS))
                    }
                }
            }

        try {
            player.play("http://gateway.example/first", 0, true) { _, _, _ -> }
            assertTrue(firstRead.await(2, TimeUnit.SECONDS))
            Thread {
                    player.play("http://gateway.example/second", 0, true) { _, _, _ -> }
                    replacementReturned.countDown()
                }
                .start()
            assertTrue(releaseEntered.await(2, TimeUnit.SECONDS))
            assertFalse(secondOpened.await(100, TimeUnit.MILLISECONDS))
            allowRelease.countDown()
            assertTrue(replacementReturned.await(2, TimeUnit.SECONDS))
            assertTrue(secondOpened.await(2, TimeUnit.SECONDS))
        } finally {
            allowRelease.countDown()
            player.close()
        }
    }

    @Test
    fun rejectsRedirectResponsesBeforeCreatingAnAudioTrack() {
        val failed = CountDownLatch(1)
        val outputCreated = AtomicBoolean(false)
        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse =
                        PayloadResponse(ByteArray(0), statusCode = 302)
                }
            ) {
                outputCreated.set(true)
                FakeAudioOutput {}
            }

        try {
            player.play("http://gateway.example/redirect", 0, true) { state, _, _ ->
                if (state == "FAILED") failed.countDown()
            }
            assertTrue("redirect was not rejected", failed.await(2, TimeUnit.SECONDS))
            assertFalse(outputCreated.get())
        } finally {
            player.close()
        }
    }

    @Test
    fun continuesAfterOneReadTimeoutAndSamplesHeadProgress() {
        val ended = CountDownLatch(1)
        val states = CopyOnWriteArrayList<String>()
        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse = TimeoutOnceResponse()
                }
            ) {
                FakeAudioOutput {}
            }

        try {
            player.play("http://gateway.example/temporary-gap", 0, true) { state, _, _ ->
                states += state
                if (state == "ENDED") ended.countDown()
            }
            assertTrue("stream did not recover from a read gap", ended.await(2, TimeUnit.SECONDS))
            assertTrue(states.contains("PLAYING"))
            assertFalse(states.contains("FAILED"))
        } finally {
            player.close()
        }
    }

    @Test
    fun keepsLiveStreamOpenAcrossMoreThanTheOldPauseTimeoutLimit() {
        val ended = CountDownLatch(1)
        val states = CopyOnWriteArrayList<String>()
        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse = TimeoutOnceResponse(7)
                }
            ) {
                FakeAudioOutput {}
            }

        try {
            player.play("http://gateway.example/paused-live-audio", 0, true) { state, _, _ ->
                states += state
                if (state == "ENDED") ended.countDown()
            }
            assertTrue(
                "live stream did not resume after a long read gap",
                ended.await(2, TimeUnit.SECONDS),
            )
            assertTrue(states.contains("PLAYING"))
            assertFalse(states.contains("FAILED"))
        } finally {
            player.close()
        }
    }

    @Test
    fun reportsEndedOnlyAfterQueuedFramesAdvanceThePlaybackHead() {
        val wrote = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val output = DelayedAudioOutput(wrote)
        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse =
                        PayloadResponse(ByteArray(4 * PcmStreamPolicy.BYTES_PER_FRAME))
                }
            ) {
                output
            }

        try {
            player.play("http://gateway.example/short-stream", 0, true) { state, _, _ ->
                if (state == "ENDED") ended.countDown()
            }
            assertTrue("PCM bytes were not written", wrote.await(2, TimeUnit.SECONDS))
            assertFalse(
                "stream ended before queued audio played",
                ended.await(150, TimeUnit.MILLISECONDS),
            )

            output.advanceHead()

            assertTrue("playback head did not drain", ended.await(2, TimeUnit.SECONDS))
        } finally {
            player.close()
        }
    }

    @Test
    fun memoryAndCapacityBoundsAreEnforced() {
        assertEquals(256 * 1024, PcmStreamPolicy.MAX_AUDIO_BUFFER_BYTES)
        assertEquals(256 * 1024, PcmStreamPolicy.TARGET_AUDIO_BUFFER_BYTES)
        assertEquals(1_100, PcmStreamPolicy.STARTUP_PREFILL_MS)
        assertEquals(48_510, PcmStreamPolicy.STARTUP_PREFILL_FRAMES)
        assertEquals(194_040, PcmStreamPolicy.STARTUP_PREFILL_BYTES)
        assertTrue(PcmStreamPolicy.STARTUP_PREFILL_BYTES <= PcmStreamPolicy.MAX_AUDIO_BUFFER_BYTES)

        assertEquals(256 * 1024, PcmStreamPolicy.audioBufferBytes(0))
        assertEquals(256 * 1024, PcmStreamPolicy.audioBufferBytes(64 * 1024))
        assertEquals(256 * 1024, PcmStreamPolicy.audioBufferBytes(256 * 1024))
        assertEquals(256 * 1024, PcmStreamPolicy.audioBufferBytes(512 * 1024))
    }

    @Test
    fun twoBurstArrivalsQueueOneSecondWithoutPlayingUntilThresholdCrossedAndHeadMoves() {
        val burst1Delivered = CountDownLatch(1)
        val allowBurst2 = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val states = CopyOnWriteArrayList<String>()
        val positions = CopyOnWriteArrayList<Int>()
        val output = FakeAudioOutput(autoAdvanceOnPlay = false)
        val totalBurstBytes = (44_100 + 44_100) * PcmStreamPolicy.BYTES_PER_FRAME

        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse =
                        TwoBurstResponse(burst1Delivered, allowBurst2)
                }
            ) {
                output
            }

        try {
            player.play("http://gateway.example/two-bursts", 0, true) { state, pos, _ ->
                states += state
                positions += pos
                if (state == "ENDED") ended.countDown()
            }

            assertTrue("Burst 1 was not delivered", burst1Delivered.await(2, TimeUnit.SECONDS))
            val writeDeadline = System.currentTimeMillis() + 1000
            while (
                output.totalWrittenBytes < 44_100 * PcmStreamPolicy.BYTES_PER_FRAME &&
                    System.currentTimeMillis() < writeDeadline
            ) {
                Thread.sleep(10)
            }

            // Burst 1 (1.0s = 176,400 bytes) queued into stopped output without playing.
            assertEquals(44_100 * PcmStreamPolicy.BYTES_PER_FRAME, output.totalWrittenBytes)
            assertEquals(0, output.playCount)
            assertFalse(output.isPlaying)
            assertFalse("Must not emit PLAYING before threshold", states.contains("PLAYING"))
            assertTrue(positions.all { it == 0 })

            // Allow Burst 2 to deliver and cross the 1100ms threshold (194,040 bytes).
            allowBurst2.countDown()
            val playDeadline = System.currentTimeMillis() + 2000
            while (output.playCount == 0 && System.currentTimeMillis() < playDeadline) {
                Thread.sleep(10)
            }

            assertEquals(1, output.playCount)
            assertTrue(output.isPlaying)
            assertTrue(
                "Must queue at least 1100ms before starting playback",
                output.bytesQueuedAtPlay >= PcmStreamPolicy.STARTUP_PREFILL_BYTES,
            )

            // Even though output started, head has not moved, so no PLAYING or position advance.
            assertEquals(0, output.playbackHeadPosition())
            assertFalse(
                "Must not emit PLAYING before actual head movement",
                states.contains("PLAYING"),
            )
            assertTrue(positions.all { it == 0 })

            // Wait until all burst2 bytes are enqueued before advancing the head; this prevents
            // advanceHead() from racing with remaining burst2 writes that could undercount frames.
            val enqueueDeadline = System.currentTimeMillis() + 2000
            while (
                output.totalWrittenBytes < totalBurstBytes &&
                    System.currentTimeMillis() < enqueueDeadline
            ) {
                Thread.sleep(10)
            }
            assertEquals(
                "All burst bytes must be enqueued before head advance",
                totalBurstBytes,
                output.totalWrittenBytes,
            )

            // Simulate playback head advancement.
            output.advanceHead()

            assertTrue("Playback did not finish", ended.await(3, TimeUnit.SECONDS))
            assertTrue("Must emit PLAYING once head advances", states.contains("PLAYING"))
            assertTrue("Position must advance", positions.any { it > 0 })
            assertEquals("ENDED", states.last())
        } finally {
            allowBurst2.countDown()
            player.close()
        }
    }

    @Test
    fun shortFiniteStreamStartsQueuedAudioOnCleanEofAndDrains() {
        val ended = CountDownLatch(1)
        val states = CopyOnWriteArrayList<String>()
        val output = FakeAudioOutput(autoAdvanceOnPlay = true)
        val shortPayload = ByteArray(2_205 * PcmStreamPolicy.BYTES_PER_FRAME) // 50ms of audio

        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse =
                        PayloadResponse(shortPayload)
                }
            ) {
                output
            }

        try {
            player.play("http://gateway.example/short-finite", 0, true) { state, _, _ ->
                states += state
                if (state == "ENDED") ended.countDown()
            }

            assertTrue("Short finite stream did not drain on EOF", ended.await(2, TimeUnit.SECONDS))
            assertEquals(1, output.playCount)
            assertEquals(shortPayload.size, output.bytesQueuedAtPlay)
            assertTrue(states.contains("PLAYING"))
            assertEquals("ENDED", states.last())
        } finally {
            player.close()
        }
    }

    @Test
    fun zeroByteEofDoesNotStartEmptyOutput() {
        val ended = CountDownLatch(1)
        val states = CopyOnWriteArrayList<String>()
        val output = FakeAudioOutput(autoAdvanceOnPlay = true)

        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse =
                        PayloadResponse(ByteArray(0))
                }
            ) {
                output
            }

        try {
            player.play("http://gateway.example/zero-byte", 0, true) { state, _, _ ->
                states += state
                if (state == "ENDED") ended.countDown()
            }

            assertTrue("Zero-byte stream did not end", ended.await(2, TimeUnit.SECONDS))
            assertEquals(0, output.playCount)
            assertFalse(output.isPlaying)
            assertFalse(states.contains("PLAYING"))
            assertEquals("ENDED", states.last())
        } finally {
            player.close()
        }
    }

    @Test
    fun partialPrefillStartsOnSocketTimeoutWhileEmptyTimeoutDoesNotStart() {
        val ended = CountDownLatch(1)
        val emptyTimeoutsDone = CountDownLatch(1)
        val allowPayload = CountDownLatch(1)
        val states = CopyOnWriteArrayList<String>()
        val output = FakeAudioOutput(autoAdvanceOnPlay = true)
        val partialPayload = ByteArray(4_410 * PcmStreamPolicy.BYTES_PER_FRAME) // 100ms < 1100ms

        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse =
                        EmptyThenPartialTimeoutResponse(
                            partialPayload,
                            emptyTimeoutsDone,
                            allowPayload,
                        )
                }
            ) {
                output
            }

        try {
            player.play("http://gateway.example/timeout-flush", 0, true) { state, _, _ ->
                states += state
                if (state == "ENDED") ended.countDown()
            }

            // Verify that the two initial empty-socket-timeouts (no bytes written yet) do NOT
            // trigger output.play(); the output must stay silent until payload is written and then
            // the tail timeout flushes the partial prefill. The worker is held at the payload gate
            // so these assertions are deterministic.
            assertTrue("Empty timeouts did not fire", emptyTimeoutsDone.await(3, TimeUnit.SECONDS))
            assertEquals("Output must not start during empty-timeout phase", 0, output.playCount)
            assertFalse("Output must not be playing during empty-timeout phase", output.isPlaying)

            // Release the worker to deliver the payload and finish.
            allowPayload.countDown()

            assertTrue("Stream did not recover and complete", ended.await(3, TimeUnit.SECONDS))
            assertEquals(1, output.playCount)
            assertEquals(partialPayload.size, output.bytesQueuedAtPlay)
            assertTrue(states.contains("PLAYING"))
            assertEquals("ENDED", states.last())
        } finally {
            allowPayload.countDown()
            player.close()
        }
    }

    @Test
    fun pauseAndResumeBeforePrefillDoesNotBypassStartupPrefill() {
        val firstChunkWritten = CountDownLatch(1)
        val allowSecondChunk = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val states = CopyOnWriteArrayList<String>()
        val output = FakeAudioOutput(autoAdvanceOnPlay = true)

        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse =
                        GatedChunksResponse(
                            chunk1Size = 4_410 * PcmStreamPolicy.BYTES_PER_FRAME, // 100ms
                            chunk2Size =
                                50_000 * PcmStreamPolicy.BYTES_PER_FRAME, // crosses threshold
                            chunk1Written = firstChunkWritten,
                            allowChunk2 = allowSecondChunk,
                        )
                }
            ) {
                output
            }

        try {
            player.play("http://gateway.example/pause-resume", 0, true) { state, _, _ ->
                states += state
                if (state == "ENDED") ended.countDown()
            }

            assertTrue("First chunk was not written", firstChunkWritten.await(2, TimeUnit.SECONDS))
            assertEquals(0, output.playCount)

            // Pause while prefill is incomplete
            player.pause()
            val pauseDeadline = System.currentTimeMillis() + 1000
            while (!states.contains("PAUSED") && System.currentTimeMillis() < pauseDeadline) {
                Thread.sleep(10)
            }
            assertTrue("Must report PAUSED", states.contains("PAUSED"))

            // Resume while prefill is incomplete: must NOT start output
            player.resume()
            val resumeDeadline = System.currentTimeMillis() + 1000
            while (states.last() != "BUFFERING" && System.currentTimeMillis() < resumeDeadline) {
                Thread.sleep(10)
            }
            assertEquals(0, output.playCount)
            assertFalse(output.isPlaying)

            // Release second chunk to cross threshold
            allowSecondChunk.countDown()

            assertTrue("Stream did not finish after resume", ended.await(3, TimeUnit.SECONDS))
            assertEquals(1, output.playCount)
            assertTrue(states.contains("PLAYING"))
            assertEquals("ENDED", states.last())
        } finally {
            allowSecondChunk.countDown()
            player.close()
        }
    }

    @Test
    fun partialWritesAndFrameCarryAcrossChunkBoundaries() {
        val ended = CountDownLatch(1)
        val states = CopyOnWriteArrayList<String>()
        val output = FakeAudioOutput(autoAdvanceOnPlay = true, writeSliceSize = 4)

        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse =
                        FragmentedResponse(listOf(ByteArray(5), ByteArray(7), ByteArray(4)))
                }
            ) {
                output
            }

        try {
            player.play("http://gateway.example/fragmented", 0, true) { state, _, _ ->
                states += state
                if (state == "ENDED") ended.countDown()
            }

            assertTrue(
                "Fragmented stream did not complete cleanly",
                ended.await(2, TimeUnit.SECONDS),
            )
            assertEquals(16, output.totalWrittenBytes)
            assertEquals("ENDED", states.last())
        } finally {
            player.close()
        }
    }

    @Test
    fun incompleteFrameAtEofFails() {
        val failed = CountDownLatch(1)
        val states = CopyOnWriteArrayList<String>()
        val output = FakeAudioOutput(autoAdvanceOnPlay = true)

        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse =
                        FragmentedResponse(listOf(ByteArray(5)))
                }
            ) {
                output
            }

        try {
            player.play("http://gateway.example/incomplete", 0, true) { state, _, _ ->
                states += state
                if (state == "FAILED") failed.countDown()
            }

            assertTrue("Incomplete frame did not fail at EOF", failed.await(2, TimeUnit.SECONDS))
            assertTrue(states.contains("FAILED"))
        } finally {
            player.close()
        }
    }

    @Test
    fun audioOutputWriteExceedingRequestedBytesFailsAndReleasesWithoutStarting() {
        val failed = CountDownLatch(1)
        val released = CountDownLatch(1)
        val states = CopyOnWriteArrayList<String>()
        // Payload: one full frame so write() is called at least once before the overrun is
        // detected.
        val payload = ByteArray(PcmStreamPolicy.BYTES_PER_FRAME)

        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse = PayloadResponse(payload)
                }
            ) {
                // Faulty output: write() returns requested + one extra frame (overrun).
                OverrunWriteOutput { released.countDown() }
            }

        try {
            player.play("http://gateway.example/overrun-write", 0, true) { state, _, _ ->
                states += state
                if (state == "FAILED") failed.countDown()
            }

            assertTrue("Overrun write did not report FAILED", failed.await(2, TimeUnit.SECONDS))
            assertTrue(
                "Output was not released after overrun failure",
                released.await(2, TimeUnit.SECONDS),
            )
            assertTrue(states.contains("FAILED"))
            // Output must not have been started (play() never called) and must not be playing.
            assertFalse(
                "Output must not be playing after overrun failure",
                states.contains("PLAYING"),
            )
        } finally {
            player.close()
        }
    }

    @Test
    fun cancellationDuringPrefillReleasesOutputImmediately() {
        val readingStarted = CountDownLatch(1)
        val outputReleased = CountDownLatch(1)
        val blockedResponse = BlockingResponse(readingStarted, CountDownLatch(1))

        val player =
            PcmStreamPlayer(
                object : PcmStreamTransport {
                    override fun open(url: String): PcmStreamResponse = blockedResponse
                }
            ) {
                FakeAudioOutput(released = { outputReleased.countDown() })
            }

        try {
            player.play("http://gateway.example/cancel-prefill", 0, true) { _, _, _ -> }
            assertTrue("Prefill read did not start", readingStarted.await(2, TimeUnit.SECONDS))

            player.stop()

            assertTrue(
                "Output was not released on cancellation",
                outputReleased.await(2, TimeUnit.SECONDS),
            )
        } finally {
            player.close()
        }
    }

    private class BlockingResponse(
        private val reading: CountDownLatch,
        private val closed: CountDownLatch,
    ) : PcmStreamResponse {
        override val statusCode = 200
        override val contentType = PcmStreamPolicy.MIME_TYPE

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            reading.countDown()
            if (!closed.await(2, TimeUnit.SECONDS)) throw IOException("read was not cancelled")
            return -1
        }

        override fun close() {
            closed.countDown()
        }
    }

    private class PayloadResponse(
        private val payload: ByteArray,
        override val statusCode: Int = 200,
    ) : PcmStreamResponse {
        override val contentType = PcmStreamPolicy.MIME_TYPE
        private var consumed = false
        private val closed = AtomicBoolean(false)

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (closed.get() || consumed) return -1
            consumed = true
            payload.copyInto(buffer, offset, 0, minOf(payload.size, length))
            return minOf(payload.size, length)
        }

        override fun close() {
            closed.set(true)
        }
    }

    private class TimeoutOnceResponse(private val timeoutCount: Int = 1) : PcmStreamResponse {
        override val statusCode = 200
        override val contentType = PcmStreamPolicy.MIME_TYPE
        private var timeouts = 0
        private var delivered = false

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (timeouts < timeoutCount) {
                timeouts++
                throw SocketTimeoutException("temporary fixture gap")
            }
            if (delivered) return -1
            delivered = true
            val payload = ByteArray(8 * PcmStreamPolicy.BYTES_PER_FRAME)
            payload.copyInto(buffer, offset)
            return payload.size
        }

        override fun close() = Unit
    }

    private class TwoBurstResponse(
        private val burst1Delivered: CountDownLatch,
        private val allowBurst2: CountDownLatch,
    ) : PcmStreamResponse {
        override val statusCode = 200
        override val contentType = PcmStreamPolicy.MIME_TYPE
        private val burst1 = ByteArray(44_100 * PcmStreamPolicy.BYTES_PER_FRAME)
        private val burst2 = ByteArray(44_100 * PcmStreamPolicy.BYTES_PER_FRAME)
        private var offset1 = 0
        private var offset2 = 0
        private var burst1Signalled = false

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (offset1 < burst1.size) {
                val count = minOf(length, burst1.size - offset1)
                burst1.copyInto(buffer, offset, offset1, offset1 + count)
                offset1 += count
                if (offset1 == burst1.size && !burst1Signalled) {
                    burst1Signalled = true
                    burst1Delivered.countDown()
                }
                return count
            }
            if (!allowBurst2.await(2, TimeUnit.SECONDS)) throw IOException("Burst 2 timed out")
            if (offset2 < burst2.size) {
                val count = minOf(length, burst2.size - offset2)
                burst2.copyInto(buffer, offset, offset2, offset2 + count)
                offset2 += count
                return count
            }
            return -1
        }

        override fun close() = Unit
    }

    private class EmptyThenPartialTimeoutResponse(
        private val payload: ByteArray,
        private val emptyTimeoutsDone: CountDownLatch = CountDownLatch(1),
        private val allowPayload: CountDownLatch = CountDownLatch(0),
    ) : PcmStreamResponse {
        override val statusCode = 200
        override val contentType = PcmStreamPolicy.MIME_TYPE
        private var emptyTimeouts = 0
        private var deliveredOffset = 0
        private var flushedTimeout = false
        private var payloadGateOpened = false

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (emptyTimeouts < 2) {
                emptyTimeouts++
                if (emptyTimeouts == 2) emptyTimeoutsDone.countDown()
                throw SocketTimeoutException("empty timeout fixture")
            }
            if (!payloadGateOpened) {
                payloadGateOpened = true
                if (!allowPayload.await(3, TimeUnit.SECONDS))
                    throw IOException("payload gate timed out")
            }
            if (deliveredOffset < payload.size) {
                val count = minOf(payload.size - deliveredOffset, length)
                payload.copyInto(buffer, offset, deliveredOffset, deliveredOffset + count)
                deliveredOffset += count
                return count
            }
            if (!flushedTimeout) {
                flushedTimeout = true
                throw SocketTimeoutException("tail timeout fixture")
            }
            return -1
        }

        override fun close() = Unit
    }

    private class GatedChunksResponse(
        chunk1Size: Int,
        chunk2Size: Int,
        private val chunk1Written: CountDownLatch,
        private val allowChunk2: CountDownLatch,
    ) : PcmStreamResponse {
        override val statusCode = 200
        override val contentType = PcmStreamPolicy.MIME_TYPE
        private val chunk1 = ByteArray(chunk1Size)
        private val chunk2 = ByteArray(chunk2Size)
        private var offset1 = 0
        private var offset2 = 0
        private var chunk1Signalled = false

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (offset1 < chunk1.size) {
                val count = minOf(length, chunk1.size - offset1)
                chunk1.copyInto(buffer, offset, offset1, offset1 + count)
                offset1 += count
                if (offset1 == chunk1.size && !chunk1Signalled) {
                    chunk1Signalled = true
                    chunk1Written.countDown()
                }
                return count
            }
            if (!allowChunk2.await(2, TimeUnit.SECONDS)) throw IOException("Chunk 2 timed out")
            if (offset2 < chunk2.size) {
                val count = minOf(length, chunk2.size - offset2)
                chunk2.copyInto(buffer, offset, offset2, offset2 + count)
                offset2 += count
                return count
            }
            return -1
        }

        override fun close() = Unit
    }

    private class FragmentedResponse(private val chunks: List<ByteArray>) : PcmStreamResponse {
        override val statusCode = 200
        override val contentType = PcmStreamPolicy.MIME_TYPE
        private var index = 0

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (index >= chunks.size) return -1
            val chunk = chunks[index++]
            val count = minOf(chunk.size, length)
            chunk.copyInto(buffer, offset, 0, count)
            return count
        }

        override fun close() = Unit
    }

    private class DelayedAudioOutput(private val wrote: CountDownLatch) : PcmAudioOutput {
        private var head = 0
        private var pendingFrames = 0

        override fun play() = Unit

        override fun pause() = Unit

        override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
            pendingFrames += length / PcmStreamPolicy.BYTES_PER_FRAME
            wrote.countDown()
            return length
        }

        override fun playbackHeadPosition(): Int = head

        override fun volume(gain: Float): Boolean = true

        fun advanceHead() {
            head = pendingFrames
        }

        override fun stop() = Unit

        override fun release() = Unit
    }

    private class FakeAudioOutput(
        private val autoAdvanceOnPlay: Boolean = true,
        private val writeSliceSize: Int? = null,
        private val released: () -> Unit = {},
    ) : PcmAudioOutput {
        constructor(
            released: () -> Unit
        ) : this(autoAdvanceOnPlay = true, writeSliceSize = null, released = released)

        @Volatile
        var playCount = 0
            private set

        @Volatile
        var isPlaying = false
            private set

        @Volatile
        var totalWrittenBytes = 0
            private set

        @Volatile
        var bytesQueuedAtPlay = 0
            private set

        private var head = 0
        private var pendingFrames = 0
        private val closed = AtomicBoolean(false)
        private val lock = Any()

        override fun play() {
            synchronized(lock) {
                isPlaying = true
                playCount++
                if (bytesQueuedAtPlay == 0 && totalWrittenBytes > 0) {
                    bytesQueuedAtPlay = totalWrittenBytes
                }
                if (autoAdvanceOnPlay) {
                    head += pendingFrames
                    pendingFrames = 0
                }
            }
        }

        override fun pause() {
            synchronized(lock) { isPlaying = false }
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
            val toWrite = if (writeSliceSize != null) minOf(length, writeSliceSize) else length
            val frames = toWrite / PcmStreamPolicy.BYTES_PER_FRAME
            synchronized(lock) {
                totalWrittenBytes += toWrite
                if (isPlaying && autoAdvanceOnPlay) {
                    head += frames
                } else {
                    pendingFrames += frames
                }
            }
            return toWrite
        }

        override fun playbackHeadPosition(): Int = synchronized(lock) { head }

        override fun volume(gain: Float): Boolean = true

        fun advanceHead(frames: Int? = null) {
            synchronized(lock) {
                val count = if (frames != null) minOf(frames, pendingFrames) else pendingFrames
                head += count
                pendingFrames -= count
            }
        }

        override fun stop() {
            synchronized(lock) { isPlaying = false }
        }

        override fun release() {
            if (closed.compareAndSet(false, true)) released()
        }
    }

    /** Faulty output whose write() returns requested + one extra frame, triggering overrun. */
    private class OverrunWriteOutput(private val released: () -> Unit) : PcmAudioOutput {
        private val closed = AtomicBoolean(false)

        override fun play() = Unit

        override fun pause() = Unit

        override fun write(buffer: ByteArray, offset: Int, length: Int): Int =
            length + PcmStreamPolicy.BYTES_PER_FRAME

        override fun playbackHeadPosition(): Int = 0

        override fun volume(gain: Float): Boolean = true

        override fun stop() = Unit

        override fun release() {
            if (closed.compareAndSet(false, true)) released()
        }
    }
}
