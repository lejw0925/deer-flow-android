package com.deerflow.mobile.ui

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactPresentationTest {
    @Test
    fun confirmationThresholdIsStrictlyGreaterThanTenMiBAndUnknownSizesConfirm() {
        assertFalse(requiresArtifactDownloadConfirmation(ARTIFACT_CONFIRMATION_BYTES))
        assertFalse(requiresArtifactDownloadConfirmation(9L * 1024 * 1024))
        assertTrue(requiresArtifactDownloadConfirmation(ARTIFACT_CONFIRMATION_BYTES + 1L))
        assertTrue(requiresArtifactDownloadConfirmation(null))
    }

    @Test
    fun largeTextPreviewReadsOnlyTheFirst256KiBAndMarksItTruncated() {
        val file = Files.createTempFile("artifact-preview", ".txt").toFile()
        try {
            file.outputStream().use { output ->
                val chunk = "abcdefgh".toByteArray()
                repeat((MAX_INLINE_ARTIFACT_TEXT_BYTES / chunk.size).toInt() + 1) { output.write(chunk) }
            }

            val preview = readArtifactTextPreview(file)

            assertTrue(preview.truncated)
            assertEquals(MAX_ARTIFACT_TEXT_PREVIEW_BYTES.toInt(), preview.text.toByteArray(Charsets.UTF_8).size)
            assertTrue(preview.text.startsWith("abcdefgh"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun smallMarkdownPreviewKeepsFullText() {
        val file = Files.createTempFile("artifact-preview", ".md").toFile()
        try {
            file.writeText("# Heading\n\nFull preview")

            val preview = readArtifactTextPreview(file)

            assertFalse(preview.truncated)
            assertEquals("# Heading\n\nFull preview", preview.text)
        } finally {
            file.delete()
        }
    }
}
