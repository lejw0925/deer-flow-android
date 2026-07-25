package com.deerflow.mobile.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactDownloadTest {
    @Test
    fun streamWritesAllBytesAndReportsProgressWithoutCreatingAnInMemoryPayload() = runBlocking {
        val source = ByteArray(48 * 1024) { (it % 251).toByte() }
        val destination = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()

        val written = copyArtifactStream(
            input = ByteArrayInputStream(source),
            output = destination,
            expectedLength = source.size.toLong(),
            onBytesWritten = progress::add,
        )

        assertEquals(source.size.toLong(), written)
        assertTrue(destination.toByteArray().contentEquals(source))
        assertEquals(source.size.toLong(), progress.last())
    }

    @Test
    fun streamRejectsDeclaredLengthMismatch() = runBlocking {
        val error = runCatching {
            copyArtifactStream(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                output = ByteArrayOutputStream(),
                expectedLength = 4L,
            )
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(error?.message?.contains("Content-Length") == true)
    }

    @Test
    fun streamRejectsBodiesThatExceedTheOneGiBLimit() = runBlocking {
        val error = runCatching {
            copyArtifactStream(
                input = ByteArrayInputStream(ByteArray(8)),
                output = ByteArrayOutputStream(),
                expectedLength = MAX_ARTIFACT_DOWNLOAD_BYTES + 1L,
            )
        }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals(413, (error as? ApiException)?.statusCode)
    }

    @Test
    fun streamRejectsUnknownLengthBodiesThatGrowPastTheLimit() = runBlocking {
        val error = runCatching {
            copyArtifactStream(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                output = ByteArrayOutputStream(),
                expectedLength = -1L,
                maxBytes = 3L,
            )
        }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals(413, (error as? ApiException)?.statusCode)
    }

    @Test
    fun streamAllowsUnknownContentLength() = runBlocking {
        val destination = ByteArrayOutputStream()
        val written = copyArtifactStream(
            input = ByteArrayInputStream("unknown".toByteArray()),
            output = destination,
            expectedLength = -1L,
        )

        assertEquals(7L, written)
        assertFalse(destination.size() == 0)
    }
}
