package io.github.diegog0477.zombiebox.client.features.diagnostics

import io.github.diegog0477.zombiebox.client.features.diagnostics.platform.MediaProbePlayback
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [MediaProbePlayback.Companion.probeHttpEvidence].
 *
 * Uses minimal inline TCP fixtures so no third-party mock-server library is needed. Each fixture
 * accepts one connection, writes a canned HTTP/1.0 response, and closes the socket.
 */
class MediaProbeHttpEvidenceTest {

    // ---- helpers ----------------------------------------------------------------

    /**
     * Starts a ServerSocket on an ephemeral port, runs [serve] in a daemon thread for the first
     * accepted connection, and returns the bound port.
     */
    private fun stubServer(serve: (Socket) -> Unit): Int {
        val server = ServerSocket(0)
        Thread {
                try {
                    val client = server.accept()
                    serve(client)
                    client.close()
                } finally {
                    server.close()
                }
            }
            .also { it.isDaemon = true }
            .start()
        return server.localPort
    }

    private fun httpResponse(
        code: Int,
        body: String = "",
        contentType: String = "text/plain",
    ): ByteArray {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val reasonPhrase =
            when (code) {
                200 -> "OK"
                404 -> "Not Found"
                405 -> "Method Not Allowed"
                else -> "Unknown"
            }
        val header = buildString {
            append("HTTP/1.0 $code $reasonPhrase\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("\r\n")
        }
        return header.toByteArray(Charsets.US_ASCII) + bodyBytes
    }

    // ---- tests ------------------------------------------------------------------

    @Test
    fun nonHttpUrlReturnsEmpty() {
        // Local assets and content:// URIs must not trigger network I/O.
        assertEquals("", MediaProbePlayback.probeHttpEvidence("file:///sdcard/test.mp4"))
        assertEquals("", MediaProbePlayback.probeHttpEvidence("content://media/external/1"))
        assertEquals("", MediaProbePlayback.probeHttpEvidence("rtsp://192.168.1.1/stream"))
        assertEquals("", MediaProbePlayback.probeHttpEvidence(""))
    }

    @Test
    fun returns200AndContentTypeOnSuccessfulHead() {
        val port = stubServer { socket ->
            // Drain the request headers first so the client doesn't get a connection reset.
            val input = socket.getInputStream()
            val buf = ByteArray(1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                val s = String(buf, 0, n, Charsets.ISO_8859_1)
                // HEAD request: no body; stop after blank line.
                if (s.contains("\r\n\r\n")) break
            }
            socket.getOutputStream().write(httpResponse(200, contentType = "application/x-mpegurl"))
        }
        val result = MediaProbePlayback.probeHttpEvidence("http://127.0.0.1:$port/test.m3u8", 3000)
        // Expect "http=200,application/x-mpegurl"
        assertTrue("Expected http=200 prefix, got: $result", result.startsWith("http=200"))
        assertTrue(
            "Expected content-type in result, got: $result",
            result.contains("application/x-mpegurl"),
        )
    }

    @Test
    fun returns404CodeOnNotFound() {
        val port = stubServer { socket ->
            val input = socket.getInputStream()
            val buf = ByteArray(1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (String(buf, 0, n, Charsets.ISO_8859_1).contains("\r\n\r\n")) break
            }
            socket.getOutputStream().write(httpResponse(404))
        }
        val result = MediaProbePlayback.probeHttpEvidence("http://127.0.0.1:$port/missing", 3000)
        assertEquals("http=404", result)
    }

    @Test
    fun fallsBackToGetWhenHeadReturns405() {
        // First connection: respond 405 to HEAD.
        // Second connection: respond 200 with content-type to GET.
        val server = ServerSocket(0)
        val port = server.localPort
        Thread {
                try {
                    // Handle HEAD → 405
                    val headClient = server.accept()
                    val headInput = headClient.getInputStream()
                    val buf = ByteArray(1024)
                    while (true) {
                        val n = headInput.read(buf)
                        if (n < 0) break
                        if (String(buf, 0, n, Charsets.ISO_8859_1).contains("\r\n\r\n")) break
                    }
                    headClient.getOutputStream().write(httpResponse(405))
                    headClient.close()

                    // Handle GET → 200
                    val getClient = server.accept()
                    val getInput = getClient.getInputStream()
                    while (true) {
                        val n = getInput.read(buf)
                        if (n < 0) break
                        if (String(buf, 0, n, Charsets.ISO_8859_1).contains("\r\n\r\n")) break
                    }
                    getClient.getOutputStream().write(httpResponse(200, contentType = "video/mp4"))
                    getClient.close()
                } finally {
                    server.close()
                }
            }
            .also { it.isDaemon = true }
            .start()

        val result = MediaProbePlayback.probeHttpEvidence("http://127.0.0.1:$port/clip.mp4", 3000)
        assertTrue(
            "Expected http=200 prefix after GET fallback, got: $result",
            result.startsWith("http=200"),
        )
        assertTrue("Expected video/mp4 in result, got: $result", result.contains("video/mp4"))
    }

    @Test
    fun returnsIoErrorOnConnectionRefused() {
        // Pick a port that nothing is listening on; connect will be refused immediately.
        val port: Int
        ServerSocket(0).use { port = it.localPort }
        // Port is now closed/released; connection should be refused.
        val result = MediaProbePlayback.probeHttpEvidence("http://127.0.0.1:$port/probe", 2000)
        // Connection refused is an IOException.
        assertEquals("http=io_error", result)
    }

    @Test
    fun returnsTimeoutStringWhenServerDoesNotRespond() {
        // Server accepts the connection but never writes a response => readTimeout fires.
        val server = ServerSocket(0)
        val port = server.localPort
        Thread {
                try {
                    val client = server.accept()
                    Thread.sleep(10_000) // Hold the socket without responding.
                    client.close()
                } catch (_: InterruptedException) {} finally {
                    server.close()
                }
            }
            .also { it.isDaemon = true }
            .start()

        val start = System.currentTimeMillis()
        val result = MediaProbePlayback.probeHttpEvidence("http://127.0.0.1:$port/slow", 500)
        val elapsed = System.currentTimeMillis() - start
        // Must timeout, not hang; allow generous margin (≤ 4× timeout for both HEAD + GET paths).
        assertTrue("Did not complete within bound, elapsed=$elapsed ms", elapsed < 4000)
        assertTrue(
            "Expected http=timeout or http=io_error, got: $result",
            result == "http=timeout" || result == "http=io_error",
        )
    }
}
