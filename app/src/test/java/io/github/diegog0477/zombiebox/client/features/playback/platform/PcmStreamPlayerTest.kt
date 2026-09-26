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

    private class FakeAudioOutput(private val released: () -> Unit) : PcmAudioOutput {
        private var head = 0
        private val closed = AtomicBoolean(false)

        override fun play() = Unit

        override fun pause() = Unit

        override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
            head += length / PcmStreamPolicy.BYTES_PER_FRAME
            return length
        }

        override fun playbackHeadPosition(): Int = head

        override fun volume(gain: Float): Boolean = true

        override fun stop() = Unit

        override fun release() {
            if (closed.compareAndSet(false, true)) released()
        }
    }
}
