package com.deerflow.mobile.data

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactApiTest {
    @Test
    fun probeUsesRangeDownloadQueryAndParsesContentRangeTotal() = runBlocking {
        val server = ArtifactHttpServer(
            listOf(
                ArtifactResponse(
                    status = 206,
                    body = "a".toByteArray(),
                    headers = mapOf(
                        "Content-Range" to "bytes 0-0/9437184",
                        "Content-Disposition" to "attachment; filename=fixture.txt",
                        "Content-Type" to "text/plain; charset=utf-8",
                    ),
                ),
            ),
        )
        try {
            val probe = DeerFlowApi(server.url, ArtifactNoopSessionCookieStore).probeArtifact("thread one", "mnt/fixture.txt")

            assertEquals(9L * 1024 * 1024, probe.totalBytes)
            assertEquals("fixture.txt", probe.filename)
            assertEquals("text/plain", probe.mimeType)
            assertEquals("bytes=0-0", server.requests.single().headers["range"])
            assertEquals("identity", server.requests.single().headers["accept-encoding"])
            assertEquals("no-transform", server.requests.single().headers["cache-control"])
            assertTrue(server.requests.single().path.contains("download=true"))
            assertTrue(server.requests.single().path.contains("thread+one"))
        } finally {
            server.close()
        }
    }

    @Test
    fun probeFallsBackToUnknownSizeWhenPartialResponseOmitsContentRange() = runBlocking {
        val server = ArtifactHttpServer(listOf(ArtifactResponse(status = 206, body = byteArrayOf(1))))
        try {
            val probe = DeerFlowApi(server.url, ArtifactNoopSessionCookieStore).probeArtifact("thread", "unknown.txt")

            assertNull(probe.totalBytes)
        } finally {
            server.close()
        }
    }

    @Test
    fun probeUsesContentLengthWhenTheServerIgnoresRange() = runBlocking {
        val source = "range ignored".toByteArray()
        val server = ArtifactHttpServer(listOf(ArtifactResponse(status = 200, body = source)))
        try {
            val probe = DeerFlowApi(server.url, ArtifactNoopSessionCookieStore).probeArtifact("thread", "ignored.txt")

            assertEquals(source.size.toLong(), probe.totalBytes)
            assertEquals("bytes=0-0", server.requests.single().headers["range"])
        } finally {
            server.close()
        }
    }

    @Test
    fun downloadStreamsToCacheAndMakesNoPartFileOnSuccess() = runBlocking {
        val source = "A streamable artifact".toByteArray()
        val server = ArtifactHttpServer(
            listOf(
                ArtifactResponse(status = 206, body = byteArrayOf(source.first()), headers = mapOf("Content-Range" to "bytes 0-0/${source.size}")),
                ArtifactResponse(status = 200, body = source, headers = mapOf("Content-Type" to "text/plain")),
            ),
        )
        val directory = Files.createTempDirectory("artifact-api-test").toFile()
        try {
            val api = DeerFlowApi(server.url, ArtifactNoopSessionCookieStore)
            val probe = api.probeArtifact("thread", "download.txt")
            val progress = mutableListOf<Long>()
            val download = api.downloadArtifact("thread", probe, directory) { downloaded, _ -> progress += downloaded }

            assertEquals("A streamable artifact", download.file.readText())
            assertEquals(source.size.toLong(), download.bytesDownloaded)
            assertEquals(source.size.toLong(), progress.last())
            assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".part") })
            assertEquals(2, server.requests.size)
        } finally {
            directory.deleteRecursively()
            server.close()
        }
    }

    @Test
    fun downloadKnownSizeHtmlRequestsTheCompleteIdentityRange() = runBlocking {
        val source = "<html><body>download me</body></html>".toByteArray()
        val server = ArtifactHttpServer(
            listOf(
                ArtifactResponse(
                    status = 206,
                    body = byteArrayOf(source.first()),
                    headers = mapOf(
                        "Content-Range" to "bytes 0-0/${source.size}",
                        "Content-Type" to "text/html; charset=utf-8",
                    ),
                ),
                ArtifactResponse(
                    status = 206,
                    body = source,
                    headers = mapOf(
                        "Content-Range" to "bytes 0-${source.lastIndex}/${source.size}",
                        "Content-Type" to "text/html; charset=utf-8",
                    ),
                ),
            ),
        )
        val directory = Files.createTempDirectory("artifact-api-test").toFile()
        try {
            val api = DeerFlowApi(server.url, ArtifactNoopSessionCookieStore)
            val probe = api.probeArtifact("thread", "report.html")
            val download = api.downloadArtifact("thread", probe, directory)

            assertEquals(source.toList(), download.file.readBytes().toList())
            assertEquals(source.size.toLong(), download.bytesDownloaded)
            assertEquals("bytes=0-${source.lastIndex}", server.requests[1].headers["range"])
            assertEquals("identity", server.requests[1].headers["accept-encoding"])
            assertEquals("no-transform", server.requests[1].headers["cache-control"])
        } finally {
            directory.deleteRecursively()
            server.close()
        }
    }

    @Test
    fun downloadRejectsPartialResponseThatDoesNotCoverTheWholeArtifact() = runBlocking {
        val server = ArtifactHttpServer(
            listOf(
                ArtifactResponse(status = 206, body = byteArrayOf(1), headers = mapOf("Content-Range" to "bytes 0-0/8")),
                ArtifactResponse(status = 206, body = byteArrayOf(1, 2, 3), headers = mapOf("Content-Range" to "bytes 0-2/8")),
            ),
        )
        val directory = Files.createTempDirectory("artifact-api-test").toFile()
        try {
            val api = DeerFlowApi(server.url, ArtifactNoopSessionCookieStore)
            val probe = api.probeArtifact("thread", "incomplete.html")
            val error = runCatching { api.downloadArtifact("thread", probe, directory) }.exceptionOrNull()

            assertTrue(error is IOException)
            assertEquals("bytes=0-7", server.requests[1].headers["range"])
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
            server.close()
        }
    }

    @Test
    fun downloadRejectsOneGiBPlusOneAtProbeWithoutAFullRequest() = runBlocking {
        val server = ArtifactHttpServer(
            listOf(ArtifactResponse(status = 206, body = byteArrayOf(0), headers = mapOf("Content-Range" to "bytes 0-0/${MAX_ARTIFACT_DOWNLOAD_BYTES + 1L}"))),
        )
        try {
            val error = runCatching {
                DeerFlowApi(server.url, ArtifactNoopSessionCookieStore).probeArtifact("thread", "too-large.bin")
            }.exceptionOrNull()

            assertTrue(error is ApiException)
            assertEquals(413, (error as? ApiException)?.statusCode)
            assertEquals(1, server.requests.size)
        } finally {
            server.close()
        }
    }

    @Test
    fun failedDownloadDeletesThePartFile() = runBlocking {
        val server = ArtifactHttpServer(
            listOf(
                ArtifactResponse(status = 206, body = byteArrayOf(1), headers = mapOf("Content-Range" to "bytes 0-0/8")),
                ArtifactResponse(status = 200, body = byteArrayOf(1, 2, 3), declaredLength = 8L),
            ),
        )
        val directory = Files.createTempDirectory("artifact-api-test").toFile()
        try {
            val api = DeerFlowApi(server.url, ArtifactNoopSessionCookieStore)
            val probe = api.probeArtifact("thread", "broken.bin")
            val error = runCatching { api.downloadArtifact("thread", probe, directory) }.exceptionOrNull()

            assertTrue(error is Exception)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
            server.close()
        }
    }

    @Test
    fun cancelledDownloadDeletesThePartFile() = runBlocking {
        val source = ByteArray(DEFAULT_BUFFER_SIZE * 2) { it.toByte() }
        val server = ArtifactHttpServer(
            listOf(
                ArtifactResponse(status = 206, body = byteArrayOf(source.first()), headers = mapOf("Content-Range" to "bytes 0-0/${source.size}")),
                ArtifactResponse(status = 200, body = source),
            ),
        )
        val directory = Files.createTempDirectory("artifact-api-test").toFile()
        try {
            val api = DeerFlowApi(server.url, ArtifactNoopSessionCookieStore)
            val probe = api.probeArtifact("thread", "cancelled.bin")
            val error = runCatching {
                api.downloadArtifact("thread", probe, directory) { downloaded, _ ->
                    if (downloaded > 0L) throw CancellationException("test cancellation")
                }
            }.exceptionOrNull()

            assertTrue(error is CancellationException)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
            server.close()
        }
    }
}

private data class ArtifactResponse(
    val status: Int,
    val body: ByteArray,
    val headers: Map<String, String> = emptyMap(),
    val declaredLength: Long? = null,
)

private data class ArtifactRequest(val path: String, val headers: Map<String, String>)

private class ArtifactHttpServer(private val responses: List<ArtifactResponse>) : Closeable {
    private val server = ServerSocket(0)
    private val responseIndex = AtomicInteger()
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    val requests = Collections.synchronizedList(mutableListOf<ArtifactRequest>())
    val url: String = "http://127.0.0.1:${server.localPort}"

    init {
        executor.execute {
            try {
                while (!server.isClosed) {
                    val client = server.accept()
                    executor.execute { serve(client) }
                }
            } catch (_: Exception) {
                if (!server.isClosed) throw AssertionError("Artifact HTTP server stopped")
            }
        }
    }

    override fun close() {
        server.close()
        executor.shutdownNow()
    }

    private fun serve(client: Socket) {
        client.use { socket ->
            val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
            val requestLine = reader.readLine() ?: return
            val headers = buildMap {
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) put(line.substring(0, separator).lowercase(), line.substring(separator + 1).trim())
                }
            }
            val start = requestLine.indexOf(' ') + 1
            val end = requestLine.indexOf(' ', start)
            requests += ArtifactRequest(requestLine.substring(start, end), headers)
            val response = responses.getOrNull(responseIndex.getAndIncrement()) ?: ArtifactResponse(404, byteArrayOf())
            val reason = if (response.status == 206) "Partial Content" else if (response.status == 200) "OK" else "Not Found"
            val head = buildString {
                append("HTTP/1.1 ${response.status} $reason\r\n")
                append("Content-Length: ${response.declaredLength ?: response.body.size}\r\n")
                response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.UTF_8)
            socket.getOutputStream().apply {
                write(head)
                write(response.body)
                flush()
            }
        }
    }
}

private object ArtifactNoopSessionCookieStore : SessionCookieStore {
    override fun cookieHeader(url: String): String? = null

    override fun csrfToken(url: String): String? = null

    override fun capture(url: String, responseHeaders: Map<String?, List<String>>) = Unit

    override fun clear() = Unit
}
