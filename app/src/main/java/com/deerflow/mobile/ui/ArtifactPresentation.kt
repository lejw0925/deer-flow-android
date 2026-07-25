package com.deerflow.mobile.ui

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader

internal const val ARTIFACT_CONFIRMATION_BYTES = 10L * 1024 * 1024
internal const val MAX_INLINE_ARTIFACT_TEXT_BYTES = 512L * 1024
internal const val MAX_ARTIFACT_TEXT_PREVIEW_BYTES = 256L * 1024

internal data class ArtifactTextPreview(
    val text: String,
    val truncated: Boolean,
)

internal fun requiresArtifactDownloadConfirmation(totalBytes: Long?): Boolean =
    totalBytes == null || totalBytes > ARTIFACT_CONFIRMATION_BYTES

internal fun isTextArtifact(mimeType: String, filename: String): Boolean {
    if (mimeType in setOf("text/html", "application/xhtml+xml", "image/svg+xml")) return false
    if (mimeType.startsWith("text/") || mimeType in setOf("application/json", "application/xml", "application/javascript")) return true
    return filename.substringAfterLast('.', "").lowercase() in setOf(
        "md", "markdown", "txt", "json", "yaml", "yml", "toml", "csv", "tsv",
        "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "css", "xml", "sh", "sql",
        "go", "rs", "rb", "php", "c", "h", "cpp", "hpp",
    )
}

internal fun readArtifactTextPreview(file: File): ArtifactTextPreview {
    val truncated = file.length() > MAX_INLINE_ARTIFACT_TEXT_BYTES
    return ArtifactTextPreview(
        text = readUtf8(file, if (truncated) MAX_ARTIFACT_TEXT_PREVIEW_BYTES else null),
        truncated = truncated,
    )
}

private fun readUtf8(file: File, byteLimit: Long?): String {
    val initialCapacity = minOf(file.length(), byteLimit ?: file.length(), MAX_INLINE_ARTIFACT_TEXT_BYTES).toInt()
    val text = StringBuilder(initialCapacity)
    file.inputStream().use { input ->
        val boundedInput = byteLimit?.let { ByteLimitInputStream(input, it) } ?: input
        InputStreamReader(boundedInput, Charsets.UTF_8).use { reader ->
            val buffer = CharArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                text.append(buffer, 0, count)
            }
        }
    }
    return text.toString()
}

private class ByteLimitInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
    override fun read(): Int {
        if (remaining <= 0L) return -1
        val value = super.read()
        if (value >= 0) remaining -= 1
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0L) return -1
        val count = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count > 0) remaining -= count
        return count
    }
}
