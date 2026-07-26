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

    @Test
    fun `subagent progress accumulates steps and terminal state on its matching card`() {
        val initial = listOf(
            ChatMessage(
                "ai-1",
                MessageRole.Assistant,
                "",
                blocks = listOf(
                    MessageBlock.Subtask(
                        callId = "task-1",
                        subagentType = "general-purpose",
                        description = "Inspect the implementation",
                        prompt = "Read the relevant code",
                    ),
                ),
            ),
        )
        val running = applySubagentProgress(
            initial,
            StreamUpdate.SubagentProgress(
                taskId = "task-1",
                modelName = "deerflow-pro",
                step = MessageBlock.SubtaskStep(
                    messageIndex = 2,
                    kind = "tool",
                    text = "Found the reducer",
                    toolName = "read_file",
                ),
            ),
        )
        val completed = applySubagentProgress(
            running,
            StreamUpdate.SubagentProgress(
                taskId = "task-1",
                status = MessageBlock.SubtaskStatus.Completed,
                result = "Reducer updated",
            ),
        )

        val subtask = completed.single().blocks.filterIsInstance<MessageBlock.Subtask>().single()
        assertEquals(MessageBlock.SubtaskStatus.Completed, subtask.status)
        assertEquals("deerflow-pro", subtask.modelName)
        assertEquals("Reducer updated", subtask.result)
        assertEquals(listOf("read_file"), subtask.steps.map(MessageBlock.SubtaskStep::toolName))
    }

    @Test
    fun `subagent progress only falls back when one task is still running`() {
        val oneRunning = listOf(
            ChatMessage(
                "ai-1",
                MessageRole.Assistant,
                "",
                blocks = listOf(
                    MessageBlock.Subtask("call-1", "general-purpose", "Only task", "Inspect it"),
                ),
            ),
        )
        val fallback = applySubagentProgress(
            oneRunning,
            StreamUpdate.SubagentProgress(
                taskId = "not-yet-linked",
                step = MessageBlock.SubtaskStep(1, "ai", "Working"),
            ),
        )
        assertEquals(
            listOf("Working"),
            fallback.single().blocks.filterIsInstance<MessageBlock.Subtask>().single().steps.map(MessageBlock.SubtaskStep::text),
        )

        val twoRunning = oneRunning + ChatMessage(
            "ai-2",
            MessageRole.Assistant,
            "",
            blocks = listOf(MessageBlock.Subtask("call-2", "general-purpose", "Second task", "Inspect it")),
        )
        assertEquals(
            twoRunning,
            applySubagentProgress(
                twoRunning,
                StreamUpdate.SubagentProgress(
                    taskId = "not-yet-linked",
                    step = MessageBlock.SubtaskStep(1, "ai", "Must not pick a task"),
                ),
            ),
        )
    }

    @Test
    fun `subtask display steps stay ordered and omit a duplicate final answer`() {
        val display = subtaskStepsForDisplay(
            listOf(
                MessageBlock.SubtaskStep(3, "ai", "Final answer"),
                MessageBlock.SubtaskStep(1, "ai", "", toolCalls = listOf("web_search")),
                MessageBlock.SubtaskStep(2, "tool", "Search complete", toolName = "web_search"),
            ),
            MessageBlock.SubtaskStatus.Completed,
        )

        assertEquals(listOf(1, 2), display.map(MessageBlock.SubtaskStep::messageIndex))
    }
}
