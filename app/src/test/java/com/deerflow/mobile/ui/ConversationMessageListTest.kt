package com.deerflow.mobile.ui

import com.deerflow.mobile.data.ChatMessage
import com.deerflow.mobile.data.ChatMessageGroup
import com.deerflow.mobile.data.MessageRole
import org.junit.Assert.assertEquals
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
        assertTrue(shouldAutoFollowConversation(listOf(processing), runActive = true, emptySet(), userPinnedToBottom = true))
    }

    @Test
    fun retainsScrollPositionWhenProcessingStepsAreExpanded() {
        assertFalse(shouldAutoFollowConversation(listOf(processing), runActive = true, setOf(processing.key), userPinnedToBottom = true))
    }

    @Test
    fun doesNotFollowAnEmptyConversation() {
        assertFalse(shouldAutoFollowConversation(emptyList(), runActive = false, emptySet(), userPinnedToBottom = true))
    }

    @Test
    fun followsAgainWhenTheExpandedProcessingGroupHasFinished() {
        val finalMessage = ChatMessageGroup.Message(ChatMessage("assistant-final", MessageRole.Assistant, "Done"))

        assertTrue(
            shouldAutoFollowConversation(
                messageGroups = listOf(processing, finalMessage),
                runActive = false,
                expandedProcessingGroups = setOf(processing.key),
                userPinnedToBottom = true,
            ),
        )
    }

    @Test
    fun doesNotFollowWhenUserHasScrolledAwayFromBottom() {
        assertFalse(
            shouldAutoFollowConversation(
                messageGroups = listOf(processing),
                runActive = true,
                expandedProcessingGroups = emptySet(),
                userPinnedToBottom = false,
            ),
        )
    }

    @Test
    fun nearBottomWhenCannotScrollForward() {
        assertTrue(
            isConversationNearBottom(
                totalItems = 5,
                lastVisibleIndex = 4,
                lastVisibleOffset = 0,
                lastVisibleSize = 2000,
                viewportEndOffset = 800,
                canScrollForward = false,
                thresholdPx = 120,
            ),
        )
    }

    @Test
    fun nearBottomWhenLastItemFullyVisible() {
        assertTrue(
            isConversationNearBottom(
                totalItems = 5,
                lastVisibleIndex = 4,
                lastVisibleOffset = 100,
                lastVisibleSize = 200,
                viewportEndOffset = 800,
                canScrollForward = true,
                thresholdPx = 120,
            ),
        )
    }

    @Test
    fun notNearBottomWhenEarlierItemIsLastVisible() {
        assertFalse(
            isConversationNearBottom(
                totalItems = 5,
                lastVisibleIndex = 2,
                lastVisibleOffset = 0,
                lastVisibleSize = 400,
                viewportEndOffset = 800,
                canScrollForward = true,
                thresholdPx = 120,
            ),
        )
    }

    @Test
    fun nearBottomWhenLastTallItemBottomIsWithinThreshold() {
        assertTrue(
            isConversationNearBottom(
                totalItems = 3,
                lastVisibleIndex = 2,
                lastVisibleOffset = -100,
                lastVisibleSize = 1000,
                viewportEndOffset = 800,
                canScrollForward = true,
                thresholdPx = 120,
            ),
        )
    }

    @Test
    fun notNearBottomWhenLastTallItemBottomIsFarBelowViewport() {
        assertFalse(
            isConversationNearBottom(
                totalItems = 3,
                lastVisibleIndex = 2,
                lastVisibleOffset = 0,
                lastVisibleSize = 2000,
                viewportEndOffset = 800,
                canScrollForward = true,
                thresholdPx = 120,
            ),
        )
    }

    @Test
    fun scrollOffsetPinsTallItemBottomInViewport() {
        assertEquals(1200, conversationScrollOffsetForBottom(itemSize = 2000, viewportSize = 800))
        assertEquals(0, conversationScrollOffsetForBottom(itemSize = 400, viewportSize = 800))
    }
}
