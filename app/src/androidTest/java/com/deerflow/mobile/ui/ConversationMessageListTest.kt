package com.deerflow.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.deerflow.mobile.data.ChatMessage
import com.deerflow.mobile.data.MessageBlock
import com.deerflow.mobile.data.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConversationMessageListTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun expandedProcessingStepsKeepTheirViewportWhenNewToolArrives() {
        var messages = mutableStateOf(processingMessages(toolCount = 2))
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState
        lateinit var scrollToHistory: () -> Unit

        compose.setContent {
            MaterialTheme {
                listState = remember { androidx.compose.foundation.lazy.LazyListState() }
                val scrollTarget = remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(scrollTarget.value) {
                    scrollTarget.value?.let { index -> listState.scrollToItem(index) }
                }
                scrollToHistory = { scrollTarget.value = 1 }
                ConversationMessageList(
                    conversationKey = "thread-1",
                    messages = messages.value,
                    runActive = true,
                    actionBusy = false,
                    onHumanInput = { _, _, _ -> },
                    onCopy = {},
                    onBranch = {},
                    onArtifact = {},
                    listState = listState,
                )
            }
        }

        compose.onNodeWithText("3 more steps").performClick()
        compose.runOnIdle { scrollToHistory() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, listState.firstVisibleItemIndex) }
        compose.runOnIdle {
            messages.value = processingMessages(toolCount = 3)
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(1, listState.firstVisibleItemIndex)
        }
    }

    private fun processingMessages(toolCount: Int): List<ChatMessage> = buildList {
        repeat(5) { index ->
            add(ChatMessage("history-$index", MessageRole.Assistant, "History message $index"))
        }
        add(
            ChatMessage(
                id = "assistant-processing-1",
                role = MessageRole.Assistant,
                text = "",
                blocks = buildList {
                    repeat(toolCount) { index ->
                        add(MessageBlock.Reasoning("Reasoning $index"))
                        add(MessageBlock.ToolCall("web_search", "{\"query\":\"query $index\"}", "call-$index"))
                    }
                },
            ),
        )
        add(
            ChatMessage(
                id = "tool-result-$toolCount",
                role = MessageRole.Tool,
                text = "Tool output ${"x".repeat(toolCount * 8)}",
                blocks = listOf(
                    MessageBlock.ToolResult(
                        "call-${toolCount - 1}",
                        "web_search",
                        "Result ${"x".repeat(toolCount * 8)}",
                    ),
                ),
            ),
        )
    }
}
