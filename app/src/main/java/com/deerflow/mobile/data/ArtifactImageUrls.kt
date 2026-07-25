package com.deerflow.mobile.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private fun encodePathSegment(segment: String): String {
    val decoded = runCatching { java.net.URLDecoder.decode(segment, StandardCharsets.UTF_8) }
        .getOrDefault(segment)
    return URLEncoder.encode(decoded, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

private fun encodeArtifactPath(filepath: String): String =
    filepath.split('/').joinToString("/") { segment ->
        if (segment.isEmpty()) segment else encodePathSegment(segment)
    }

private fun splitPathSuffix(src: String): Pair<String, String> {
    val pathEnd = src.indexOfFirst { it == '?' || it == '#' }.takeIf { it >= 0 } ?: src.length
    return src.substring(0, pathEnd) to src.substring(pathEnd)
}

fun resolveArtifactURL(serverUrl: String, threadId: String, absolutePath: String): String {
    val base = serverUrl.trimEnd('/')
    val encodedThreadId = encodePathSegment(threadId)
    val path = if (absolutePath.startsWith("/")) absolutePath else "/$absolutePath"
    return "$base/api/threads/$encodedThreadId/artifacts${encodeArtifactPath(path)}"
}

fun resolveMarkdownArtifactURL(src: String, serverUrl: String, threadId: String): String {
    val (path, suffix) = splitPathSuffix(src)
    return resolveArtifactURL(serverUrl, threadId, path) + suffix
}

/**
 * Mirrors web `resolveMessageImageURL`: absolute `/mnt/` paths become artifact
 * URLs; unique relative artifact filenames resolve against the thread artifact
 * list; external and ambiguous sources are left unchanged.
 */
fun resolveMessageImageURL(
    src: String,
    serverUrl: String,
    threadId: String,
    artifactPaths: List<String>,
): String {
    if (src.startsWith("/mnt/")) {
        return resolveMarkdownArtifactURL(src, serverUrl, threadId)
    }
    val (relativePath, suffix) = splitPathSuffix(src)
    val normalizedPath = relativePath.replace(Regex("^(?:\\./)+"), "")
    val decodedNormalizedPath = normalizedPath.split('/').joinToString("/") { segment ->
        runCatching { java.net.URLDecoder.decode(segment, StandardCharsets.UTF_8) }.getOrDefault(segment)
    }
    if (
        normalizedPath.isEmpty() ||
        normalizedPath.startsWith("/") ||
        Regex("^[a-z][a-z\\d+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(normalizedPath) ||
        normalizedPath.startsWith("//") ||
        normalizedPath.split('/').contains("..")
    ) {
        return src
    }
    val matches = artifactPaths.filter { it.endsWith("/$decodedNormalizedPath") }
    if (matches.size != 1) return src
    return resolveArtifactURL(serverUrl, threadId, matches.single()) + suffix
}

fun isInlineDisplayableImageUrl(url: String): Boolean =
    url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:image/")
