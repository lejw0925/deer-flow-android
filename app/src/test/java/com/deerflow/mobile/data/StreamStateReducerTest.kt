package com.deerflow.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamStateReducerTest {
    @Test
    fun `gateway user echo replaces the matching client id message`() {
        val merged = mergeStreamChunk(
            messages = listOf(ChatMessage("client-id", MessageRole.User, "Explain reconnection")),
            chunk = ChatMessage("client-id__user", MessageRole.User, "Explain reconnection"),
        )

        assertEquals(listOf("client-id__user"), merged.map(ChatMessage::id))
    }

    @Test
    fun `normalized gateway user id confirms only the pending optimistic message`() {
        val pending = ChatMessage("client-id", MessageRole.User, "Explain reconnection")
        val visible = projectVisibleMessages(
            serverMessages = listOf(
                ChatMessage("history-id", MessageRole.User, "Explain reconnection"),
                ChatMessage("run-id__user", MessageRole.User, "Explain reconnection"),
            ),
            pendingUserMessage = pending,
            pendingUserIndex = 2,
        )

        assertEquals(listOf("history-id", "run-id__user"), visible.map(ChatMessage::id))
    }
}
