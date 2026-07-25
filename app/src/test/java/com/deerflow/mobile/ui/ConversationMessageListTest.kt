package com.deerflow.mobile.ui

import com.deerflow.mobile.data.ChatMessage
import com.deerflow.mobile.data.ChatMessageGroup
import com.deerflow.mobile.data.MessageRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMessageListTest {
    private val processing = ChatMessageGroup.Processing(
        key = "processing:assistant-1",
        messages = listOf(ChatMessage("assistant-1", MessageRole.Assistant, "")),
    )

    @Test
    fun followsLatestMessagesWhenNoProcessingStepsAreExpanded() {
        assertTrue(shouldAutoFollowConversation(listOf(processing), runActive = true, emptySet()))
    }

    @Test
    fun retainsScrollPositionWhenProcessingStepsAreExpanded() {
        assertFalse(shouldAutoFollowConversation(listOf(processing), runActive = true, setOf(processing.key)))
    }

    @Test
    fun doesNotFollowAnEmptyConversation() {
        assertFalse(shouldAutoFollowConversation(emptyList(), runActive = false, emptySet()))
    }

    @Test
    fun followsAgainWhenTheExpandedProcessingGroupHasFinished() {
        val finalMessage = ChatMessageGroup.Message(ChatMessage("assistant-final", MessageRole.Assistant, "Done"))

        assertTrue(
            shouldAutoFollowConversation(
                messageGroups = listOf(processing, finalMessage),
                runActive = false,
                expandedProcessingGroups = setOf(processing.key),
            ),
        )
    }
}
