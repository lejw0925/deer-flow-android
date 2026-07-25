package com.deerflow.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtifactImageUrlTest {
    private val server = "http://127.0.0.1:2026"

    @Test
    fun resolvesAbsoluteMntPathsLikeWeb() {
        assertEquals(
            "http://127.0.0.1:2026/api/threads/thread-1/artifacts/mnt/user-data/outputs/chart.png",
            resolveMessageImageURL(
                src = "/mnt/user-data/outputs/chart.png",
                serverUrl = server,
                threadId = "thread-1",
                artifactPaths = emptyList(),
            ),
        )
    }

    @Test
    fun preservesQueryAndFragmentSuffixes() {
        assertEquals(
            "http://127.0.0.1:2026/api/threads/thread-1/artifacts/mnt/user-data/outputs/chart.png?v=2#detail",
            resolveMessageImageURL(
                src = "/mnt/user-data/outputs/chart.png?v=2#detail",
                serverUrl = server,
                threadId = "thread-1",
                artifactPaths = emptyList(),
            ),
        )
    }

    @Test
    fun resolvesUniqueRelativeArtifactFilenames() {
        val artifacts = listOf(
            "/mnt/user-data/outputs/aws-agent-overview.png",
            "/mnt/user-data/outputs/chart.png",
        )
        assertEquals(
            "http://127.0.0.1:2026/api/threads/thread-1/artifacts/mnt/user-data/outputs/aws-agent-overview.png",
            resolveMessageImageURL(
                src = "aws-agent-overview.png",
                serverUrl = server,
                threadId = "thread-1",
                artifactPaths = artifacts,
            ),
        )
        assertEquals(
            "http://127.0.0.1:2026/api/threads/thread-1/artifacts/mnt/user-data/outputs/chart.png",
            resolveMessageImageURL(
                src = "outputs/chart.png",
                serverUrl = server,
                threadId = "thread-1",
                artifactPaths = artifacts,
            ),
        )
    }

    @Test
    fun doesNotRewriteAmbiguousOrExternalSources() {
        assertEquals(
            "missing.png",
            resolveMessageImageURL("missing.png", server, "thread-1", emptyList()),
        )
        assertEquals(
            "shared.png",
            resolveMessageImageURL(
                "shared.png",
                server,
                "thread-1",
                listOf(
                    "/mnt/user-data/outputs/first/shared.png",
                    "/mnt/user-data/outputs/second/shared.png",
                ),
            ),
        )
        assertEquals(
            "https://example.com/image.png",
            resolveMessageImageURL(
                "https://example.com/image.png",
                server,
                "thread-1",
                listOf("/mnt/user-data/outputs/image.png"),
            ),
        )
        assertEquals(
            "../etc/secret.png",
            resolveMessageImageURL(
                "../etc/secret.png",
                server,
                "thread-1",
                listOf("/mnt/user-data/outputs/secret.png"),
            ),
        )
    }

    @Test
    fun encodesReservedCharactersInThreadAndPath() {
        assertEquals(
            "http://127.0.0.1:2026/api/threads/thread%20%231/artifacts/mnt/user-data/outputs/%E4%B8%AD%20%E6%96%87%23%3F.png",
            resolveArtifactURL(
                serverUrl = server,
                threadId = "thread #1",
                absolutePath = "/mnt/user-data/outputs/中 文#?.png",
            ),
        )
    }
}
