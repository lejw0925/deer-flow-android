package com.deerflow.mobile.run

import com.deerflow.mobile.data.ChatMessage
import com.deerflow.mobile.data.GatewayRunStatus
import com.deerflow.mobile.data.HumanInputRequest
import com.deerflow.mobile.data.MessageBlock
import com.deerflow.mobile.data.MessageRole
import com.deerflow.mobile.data.RunState
import com.deerflow.mobile.data.RunStatus
import com.deerflow.mobile.data.RunNoticeKind
import com.deerflow.mobile.data.StreamMessageOperation
import com.deerflow.mobile.data.StreamPatch
import com.deerflow.mobile.data.StreamUpdate
import com.deerflow.mobile.data.ThreadSnapshot
import com.deerflow.mobile.data.TodoItem
import com.deerflow.mobile.data.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCoordinatorTest {
    private val initial = CoordinatedRunState(
        serverUrl = "https://deerflow.example",
        threadId = "thread-1",
        title = "Research",
        run = RunState(RunStatus.Connecting),
        serverMessages = emptyList(),
    )

    @Test
    fun `started and event id updates preserve resumable coordinates`() {
        val started = reduceRunState(initial, StreamUpdate.Started("run-1"))
        val checkpointed = reduceRunState(started, StreamUpdate.EventId("event-7"))

        assertEquals(RunStatus.Streaming, checkpointed.run.status)
        assertEquals("run-1", checkpointed.run.runId)
        assertEquals("event-7", checkpointed.run.lastEventId)
        assertEquals(2, checkpointed.revision)
    }

    @Test
    fun `stream updates keep a requested stop visible until the Gateway confirms it`() {
        val stopping = initial.copy(
            run = RunState(RunStatus.Stopping, runId = "run-1", lastEventId = "event-3"),
        )

        val started = reduceRunState(stopping, StreamUpdate.Started("run-1"))
        val chunked = reduceRunState(
            started,
            StreamUpdate.MessageChunk(ChatMessage("ai-1", MessageRole.Assistant, "Still finishing")),
        )
        val reconnecting = reduceRunState(chunked, StreamUpdate.Reconnecting(attempt = 1))
        val failed = reduceRunState(reconnecting, StreamUpdate.Failure("Transient transport error"))

        assertEquals(RunStatus.Stopping, started.run.status)
        assertEquals(RunStatus.Stopping, chunked.run.status)
        assertEquals(RunStatus.Stopping, reconnecting.run.status)
        assertEquals(RunStatus.Stopping, failed.run.status)
        assertEquals("run-1", failed.run.runId)
    }

    @Test
    fun `stream terminal outcomes defer while cancellation confirmation is in flight`() {
        assertTrue(shouldDeferStreamOutcome(true, RunState(RunStatus.Stopping)))
        assertTrue(shouldDeferStreamOutcome(true, RunState(RunStatus.Streaming)))
        assertFalse(shouldDeferStreamOutcome(false, RunState(RunStatus.Stopping)))
        assertFalse(shouldDeferStreamOutcome(true, RunState(RunStatus.Idle)))
    }

    @Test
    fun `persisted active run remains resumable while the active runs list catches up`() {
        val saved = RunState(RunStatus.Reconnecting, runId = "run-1", lastEventId = "event-7")

        assertEquals(saved, resumableRun(saved, activeRunId = null))
    }

    @Test
    fun `server advertised replacement run clears an incompatible event checkpoint`() {
        val saved = RunState(RunStatus.Reconnecting, runId = "run-1", lastEventId = "event-7")

        assertEquals(
            RunState(RunStatus.Reconnecting, runId = "run-2"),
            resumableRun(saved, activeRunId = "run-2"),
        )
    }

    @Test
    fun `message chunks remain available when the UI observer is recreated`() {
        val first = reduceRunState(
            initial,
            StreamUpdate.MessageChunk(ChatMessage("ai-1", MessageRole.Assistant, "Hello ")),
        )
        val second = reduceRunState(
            first,
            StreamUpdate.MessageChunk(ChatMessage("ai-1", MessageRole.Assistant, "again")),
        )

        assertEquals("Hello again", second.messages.single().text)
        assertTrue(second.messages.single().isStreaming)
    }

    @Test
    fun `subagent progress updates its card and the latest live tool`() {
        val current = initial.copy(
            serverMessages = listOf(
                ChatMessage(
                    "ai-1",
                    MessageRole.Assistant,
                    "",
                    blocks = listOf(
                        MessageBlock.Subtask(
                            callId = "task-1",
                            subagentType = "general-purpose",
                            description = "Inspect the API",
                            prompt = "Read the API contract",
                        ),
                    ),
                ),
            ),
        )
        val running = reduceRunState(
            current,
            StreamUpdate.SubagentProgress(
                taskId = "task-1",
                step = MessageBlock.SubtaskStep(
                    messageIndex = 1,
                    kind = "tool",
                    text = "",
                    toolName = "web_search",
                ),
            ),
        )
        val completed = reduceRunState(
            running,
            StreamUpdate.SubagentProgress(
                taskId = "task-1",
                status = MessageBlock.SubtaskStatus.Completed,
                result = "Verified",
            ),
        )

        val subtask = completed.serverMessages.single().blocks.filterIsInstance<MessageBlock.Subtask>().single()
        assertEquals("web_search", completed.latestToolName)
        assertEquals(MessageBlock.SubtaskStatus.Completed, subtask.status)
        assertEquals("Verified", subtask.result)
        assertEquals(listOf("web_search"), subtask.steps.map(MessageBlock.SubtaskStep::toolName))
    }

    @Test
    fun `gateway middleware notice is retained without changing the active run`() {
        val notice = StreamUpdate.RunNotice(
            kind = RunNoticeKind.LlmRetry,
            message = "Retrying after a rate limit.",
            attempt = 2,
            maxAttempts = 3,
            waitMillis = 250,
        )

        val reduced = reduceRunState(initial, notice)

        assertEquals(RunStatus.Connecting, reduced.run.status)
        assertEquals(notice, reduced.runNotice)
    }

    @Test
    fun `unclosed upload context never replaces an existing conversation title`() {
        val generatedTitleFailure = "<current_uploads>The following files were uploaded in this message:\n- report.pdf"
        val patched = reduceRunState(initial, StreamUpdate.Patch(StreamPatch(title = generatedTitleFailure)))
        val completed = completeWithSnapshot(
            initial,
            ThreadSnapshot(title = generatedTitleFailure, messages = emptyList()),
        )

        assertEquals("Research", patched.title)
        assertEquals("Research", completed.title)
    }

    @Test
    fun `only transient stream http failures are retryable`() {
        assertFalse(isRetryableStreamHttpFailure(400))
        assertFalse(isRetryableStreamHttpFailure(401))
        assertFalse(isRetryableStreamHttpFailure(422))
        assertTrue(isRetryableStreamHttpFailure(408))
        assertTrue(isRetryableStreamHttpFailure(409))
        assertTrue(isRetryableStreamHttpFailure(429))
        assertTrue(isRetryableStreamHttpFailure(500))
    }

    @Test
    fun `pending chunks publish their fully merged text in one refresh`() {
        val pending = PendingChunkState()
        pending.append(initial, ChatMessage("ai-1", MessageRole.Assistant, "Hello "))
        pending.append(initial, ChatMessage("ai-1", MessageRole.Assistant, "again"))

        val published = pending.take(initial.serverUrl, initial.threadId)

        assertEquals("Hello again", published?.messages?.single()?.text)
        assertNull(pending.peek())
    }

    @Test
    fun `tool-only patch preserves optimistic user reasoning and prior tool records`() {
        val user = ChatMessage("client-1", MessageRole.User, "Research this")
        val reasoning = ChatMessage(
            "ai-1",
            MessageRole.Assistant,
            "",
            blocks = listOf(
                MessageBlock.Reasoning("Inspect sources"),
                MessageBlock.ToolCall("search", "{\"q\":\"DeerFlow\"}", "call-1"),
            ),
        )
        val current = initial.copy(
            serverMessages = listOf(reasoning),
            pendingUserMessage = user,
            pendingUserIndex = 0,
        )
        val toolResult = ChatMessage(
            "tool-2",
            MessageRole.Tool,
            "second result",
            blocks = listOf(MessageBlock.ToolResult("call-2", "fetch", "second result")),
        )

        val reduced = reduceRunState(
            current,
            StreamUpdate.Patch(StreamPatch(messages = listOf(StreamMessageOperation.Upsert(toolResult)))),
        )

        assertEquals(listOf("client-1", "ai-1", "tool-2"), reduced.messages.map(ChatMessage::id))
        assertTrue(reduced.messages.first().role == MessageRole.User)
        assertTrue(reduced.serverMessages.single { it.id == "ai-1" }.blocks.any { it is MessageBlock.Reasoning })
        assertTrue(reduced.messages.last().blocks.any { it is MessageBlock.ToolResult })
    }

    @Test
    fun `patch fields fold in order and empty todos replace previous values`() {
        val current = initial.copy(
            title = "Original",
            todos = listOf(TodoItem("Keep", "pending")),
            artifacts = listOf("existing.md"),
        )
        val first = reduceRunState(
            current,
            StreamUpdate.Patch(
                StreamPatch(
                    title = "First",
                    todos = listOf(TodoItem("Write", "in_progress")),
                    artifacts = listOf("report.md", "existing.md"),
                ),
            ),
        )
        val second = reduceRunState(
            first,
            StreamUpdate.Patch(
                StreamPatch(
                    title = "Final",
                    todos = emptyList(),
                    artifacts = listOf("report.md", "chart.png"),
                ),
            ),
        )

        assertEquals("Final", second.title)
        assertEquals(emptyList<TodoItem>(), second.todos)
        assertEquals(listOf("existing.md", "report.md", "chart.png"), second.artifacts)
    }

    @Test
    fun `remove and remove all only affect the server message layer`() {
        val pending = ChatMessage("client-1", MessageRole.User, "Keep me")
        val current = initial.copy(
            serverMessages = listOf(
                ChatMessage("human-1", MessageRole.User, "Old question"),
                ChatMessage("ai-1", MessageRole.Assistant, "Old answer"),
            ),
            pendingUserMessage = pending,
            pendingUserIndex = 2,
        )
        val removed = reduceRunState(
            current,
            StreamUpdate.Patch(StreamPatch(messages = listOf(StreamMessageOperation.Remove("ai-1")))),
        )
        val cleared = reduceRunState(
            removed,
            StreamUpdate.Patch(StreamPatch(messages = listOf(StreamMessageOperation.RemoveAll))),
        )

        assertEquals(listOf("human-1", "client-1"), removed.messages.map(ChatMessage::id))
        assertEquals(emptyList<ChatMessage>(), cleared.serverMessages)
        assertEquals(listOf("client-1"), cleared.messages.map(ChatMessage::id))
    }

    @Test
    fun `assistant before human echo keeps optimistic user bubble until exact id arrives`() {
        val pending = ChatMessage("client-1", MessageRole.User, "Where is the report?")
        val waiting = initial.copy(pendingUserMessage = pending, pendingUserIndex = 0)
        val assistantFirst = reduceRunState(
            waiting,
            StreamUpdate.MessageChunk(ChatMessage("ai-1", MessageRole.Assistant, "I will check.")),
        )
        val confirmed = reduceRunState(
            assistantFirst,
            StreamUpdate.Patch(
                StreamPatch(
                    messages = listOf(
                        StreamMessageOperation.Upsert(ChatMessage("client-1", MessageRole.User, "Where is the report?")),
                    ),
                ),
            ),
        )

        assertEquals(listOf("client-1", "ai-1"), assistantFirst.messages.map(ChatMessage::id))
        assertEquals(1, confirmed.messages.count { it.id == "client-1" })
        assertNull(confirmed.pendingUserMessage)
    }

    @Test
    fun `gateway user echo with a normalized id confirms the optimistic message`() {
        val pending = ChatMessage("client-1", MessageRole.User, "Where is the report?")
        val waiting = initial.copy(pendingUserMessage = pending, pendingUserIndex = 0)
        val confirmed = reduceRunState(
            waiting,
            StreamUpdate.Patch(
                StreamPatch(
                    messages = listOf(
                        StreamMessageOperation.Upsert(
                            ChatMessage("run-1__user", MessageRole.User, "Where is the report?"),
                        ),
                    ),
                ),
            ),
        )
        val completed = completeWithSnapshot(
            waiting,
            ThreadSnapshot(
                title = "Research",
                messages = listOf(ChatMessage("run-1__user", MessageRole.User, "Where is the report?")),
            ),
        )

        assertEquals(listOf("run-1__user"), confirmed.messages.map(ChatMessage::id))
        assertNull(confirmed.pendingUserMessage)
        assertNull(completed.pendingUserMessage)
    }

    @Test
    fun `terminal snapshot conservatively keeps an unconfirmed user message`() {
        val pending = ChatMessage("client-1", MessageRole.User, "Still visible")
        val completed = completeWithSnapshot(
            initial.copy(pendingUserMessage = pending, pendingUserIndex = 0),
            ThreadSnapshot(
                title = "Research",
                messages = listOf(ChatMessage("ai-1", MessageRole.Assistant, "Partial answer")),
            ),
        )

        assertEquals(listOf("client-1", "ai-1"), completed.messages.map(ChatMessage::id))
        assertEquals("client-1", completed.pendingUserMessage?.id)
    }

    @Test
    fun `terminal snapshot does not erase a final answer that the state endpoint has not persisted yet`() {
        val current = initial.copy(
            serverMessages = listOf(
                ChatMessage("run-1__user", MessageRole.User, "Find the source"),
                ChatMessage("ai-final", MessageRole.Assistant, "Final answer with sources.", isStreaming = true),
            ),
            pendingUserMessage = ChatMessage("client-1", MessageRole.User, "Find the source"),
            pendingUserIndex = 0,
        )
        val completed = completeWithSnapshot(
            current,
            ThreadSnapshot(
                title = "Research",
                messages = listOf(ChatMessage("run-1__user", MessageRole.User, "Find the source")),
            ),
        )

        assertEquals("Final answer with sources.", completed.serverMessages.last().text)
        assertFalse(completed.serverMessages.last().isStreaming)
        assertFalse(terminalSnapshotNeedsRetry(current, ThreadSnapshot("Research", current.serverMessages)))
    }

    @Test
    fun `full snapshot replaces the markdown block created from the first streamed token`() {
        val streamed = initial.copy(
            serverMessages = listOf(ChatMessage("ai-final", MessageRole.Assistant, "Android", isStreaming = true)),
        )
        val completed = completeWithSnapshot(
            streamed,
            ThreadSnapshot(
                "Research",
                listOf(ChatMessage("ai-final", MessageRole.Assistant, "Android stream final confirmed.")),
            ),
        )

        val message = completed.serverMessages.single()
        assertEquals("Android stream final confirmed.", message.text)
        assertEquals(
            "Android stream final confirmed.",
            message.blocks.filterIsInstance<MessageBlock.Markdown>().single().text,
        )
    }

    @Test
    fun `terminal state retries only while both stream and state end at a tool step`() {
        val pending = ChatMessage("client-1", MessageRole.User, "Research this")
        val toolStep = ChatMessage(
            "ai-tools",
            MessageRole.Assistant,
            "",
            blocks = listOf(MessageBlock.ToolCall("web_search", "{\"query\":\"DeerFlow\"}", "call-1")),
        )
        val partial = initial.copy(
            serverMessages = listOf(ChatMessage("run-1__user", MessageRole.User, pending.text), toolStep),
            pendingUserMessage = pending,
            pendingUserIndex = 0,
        )
        val finalSnapshot = ThreadSnapshot(
            "Research",
            partial.serverMessages + ChatMessage("ai-final", MessageRole.Assistant, "Completed research."),
        )

        assertTrue(terminalSnapshotNeedsRetry(partial, ThreadSnapshot("Research", partial.serverMessages)))
        assertFalse(terminalSnapshotNeedsRetry(partial, finalSnapshot))
    }

    @Test
    fun `terminal state retries a usage-bearing first token until the final checkpoint is visible`() {
        val pending = ChatMessage("client-1", MessageRole.User, "Confirm the stream")
        val firstToken = ChatMessage(
            "ai-final",
            MessageRole.Assistant,
            "Android",
            isStreaming = true,
            tokenUsage = TokenUsage(inputTokens = 10, outputTokens = 9, totalTokens = 19),
        )
        val current = initial.copy(
            serverMessages = listOf(ChatMessage("run-1__user", MessageRole.User, pending.text), firstToken),
            pendingUserMessage = pending,
            pendingUserIndex = 0,
        )
        val complete = ThreadSnapshot(
            "Research",
            listOf(
                ChatMessage("run-1__user", MessageRole.User, pending.text),
                firstToken.copy(text = "Android stream final confirmed.", isStreaming = false),
            ),
        )

        assertTrue(terminalSnapshotNeedsRetry(current, ThreadSnapshot("Research", current.serverMessages)))
        assertFalse(terminalSnapshotNeedsRetry(current, complete))
    }

    @Test
    fun `terminal snapshot only overrides a failed stream when it contains the clients answer`() {
        val completed = ThreadSnapshot(
            title = "Research",
            messages = listOf(
                ChatMessage("client-1", MessageRole.User, "Recover this run"),
                ChatMessage("ai-1", MessageRole.Assistant, "Recovered final answer"),
            ),
        )
        val failed = ThreadSnapshot(
            title = "Research",
            messages = listOf(ChatMessage("client-1", MessageRole.User, "Recover this run")),
        )

        assertTrue(snapshotCompletesClientMessage(completed, "client-1"))
        assertFalse(snapshotCompletesClientMessage(failed, "client-1"))
        assertFalse(snapshotCompletesClientMessage(completed, "other-client"))
    }

    @Test
    fun `reasoning fragments and multiple tool calls accumulate without rollback`() {
        val reasoningFirst = ChatMessage(
            "ai-1",
            MessageRole.Assistant,
            "",
            blocks = listOf(MessageBlock.Reasoning("Inspect "), MessageBlock.ToolCall("search", "{", "call-1")),
        )
        val reasoningSecond = ChatMessage(
            "ai-1",
            MessageRole.Assistant,
            "",
            blocks = listOf(MessageBlock.Reasoning("evidence"), MessageBlock.ToolCall("fetch", "{}", "call-2")),
        )
        val streamed = reduceRunState(
            reduceRunState(initial, StreamUpdate.MessageChunk(reasoningFirst)),
            StreamUpdate.MessageChunk(reasoningSecond),
        )
        val shortPatch = reduceRunState(
            streamed,
            StreamUpdate.Patch(
                StreamPatch(
                    messages = listOf(
                        StreamMessageOperation.Upsert(
                            ChatMessage(
                                "ai-1",
                                MessageRole.Assistant,
                                "",
                                blocks = listOf(
                                    MessageBlock.Reasoning("Inspect"),
                                    MessageBlock.ToolCall("search", "", "call-1"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val blocks = shortPatch.serverMessages.single().blocks

        assertEquals("Inspect evidence", blocks.filterIsInstance<MessageBlock.Reasoning>().single().text)
        assertEquals(listOf("call-1", "call-2"), blocks.filterIsInstance<MessageBlock.ToolCall>().map(MessageBlock.ToolCall::id))
    }

    @Test
    fun `short patch and duplicate SSE event are idempotent`() {
        val streaming = initial.copy(
            run = RunState(RunStatus.Streaming, runId = "run-1"),
            serverMessages = listOf(
                ChatMessage(
                    "ai-1",
                    MessageRole.Assistant,
                    "Complete answer",
                    isStreaming = true,
                    blocks = listOf(MessageBlock.ToolCall("search", "{}", "call-1")),
                ),
            ),
        )
        val patch = StreamUpdate.Patch(
            StreamPatch(
                messages = listOf(
                    StreamMessageOperation.Upsert(
                        ChatMessage(
                            "ai-1",
                            MessageRole.Assistant,
                            "Complete",
                            blocks = listOf(MessageBlock.ToolCall("fetch", "{}", "call-2")),
                        ),
                    ),
                ),
                artifacts = listOf("report.md"),
            ),
        )
        val once = reduceRunState(streaming, patch)
        val twice = reduceRunState(once, patch)
        val message = twice.serverMessages.single()

        assertEquals("Complete answer", message.text)
        assertTrue(message.isStreaming)
        assertEquals(listOf("call-1", "call-2"), message.blocks.filterIsInstance<MessageBlock.ToolCall>().map(MessageBlock.ToolCall::id))
        assertEquals(listOf("report.md"), twice.artifacts)
    }

    @Test
    fun `finished marker keeps active run coordinates until terminal state is confirmed`() {
        val streaming = initial.copy(
            run = RunState(RunStatus.Streaming, runId = "run-1", lastEventId = "event-9"),
            serverMessages = listOf(ChatMessage("ai-1", MessageRole.Assistant, "Done", isStreaming = true)),
        )

        val finished = reduceRunState(streaming, StreamUpdate.Finished)

        assertTrue(finished.run.active)
        assertEquals(RunStatus.Streaming, finished.run.status)
        assertEquals("run-1", finished.run.runId)
        assertEquals("event-9", finished.run.lastEventId)
        assertFalse(finished.messages.single().isStreaming)
    }

    @Test
    fun `human input request ends local thinking while retaining the request and prompt`() {
        val request = HumanInputRequest(
            source = "ask_clarification",
            requestId = "clarification-1",
            toolCallId = "call-1",
            title = null,
            question = "Which environment should I use?",
            context = null,
            inputMode = "free_text",
            options = emptyList(),
        )
        val pending = ChatMessage("client-1", MessageRole.User, "Deploy the service")
        val current = initial.copy(
            run = RunState(RunStatus.Streaming, runId = "run-1", lastEventId = "event-1"),
            serverMessages = listOf(
                ChatMessage(
                    "clarification-1",
                    MessageRole.Tool,
                    "Which environment should I use?",
                    blocks = listOf(MessageBlock.HumanInput(request)),
                ),
            ),
            pendingUserMessage = pending,
            pendingUserIndex = 0,
        )

        val waiting = awaitHumanInput(current, runId = "run-1", lastEventId = "event-2")

        assertEquals(RunStatus.AwaitingInput, waiting.run.status)
        assertFalse(waiting.run.active)
        assertTrue(waiting.run.awaitingInput)
        assertEquals("event-2", waiting.run.lastEventId)
        assertNull(waiting.pendingUserMessage)
        assertEquals(listOf("client-1", "clarification-1"), waiting.serverMessages.map(ChatMessage::id))
    }

    @Test
    fun `reply stream does not re-interrupt an existing human input request`() {
        val request = HumanInputRequest(
            source = "ask_clarification",
            requestId = "clarification-1",
            toolCallId = "call-1",
            title = null,
            question = "Which environment should I use?",
            context = null,
            inputMode = "free_text",
            options = emptyList(),
        )
        val waiting = initial.copy(
            run = RunState(RunStatus.AwaitingInput, runId = "run-1", lastEventId = "event-2"),
            serverMessages = listOf(
                ChatMessage(
                    "clarification-1",
                    MessageRole.Tool,
                    "Which environment should I use?",
                    blocks = listOf(MessageBlock.HumanInput(request)),
                ),
            ),
        )

        val started = reduceRunState(waiting, StreamUpdate.Started("reply-run"))
        val checkpointed = reduceRunState(started, StreamUpdate.EventId("event-3"))

        assertFalse(shouldAwaitHumanInput(waiting, started))
        assertFalse(shouldAwaitHumanInput(started, checkpointed))

        val followUp = reduceRunState(
            checkpointed,
            StreamUpdate.MessageChunk(
                ChatMessage(
                    "clarification-2",
                    MessageRole.Tool,
                    "Which deployment region?",
                    blocks = listOf(MessageBlock.HumanInput(request.copy(requestId = "clarification-2"))),
                ),
            ),
        )
        assertTrue(shouldAwaitHumanInput(checkpointed, followUp))
    }

    @Test
    fun `sse error and finished marker keep the run reconnectable until a terminal preflight`() {
        val streaming = initial.copy(
            run = RunState(RunStatus.Streaming, runId = "run-1"),
            error = null,
            serverMessages = listOf(ChatMessage("ai-1", MessageRole.Assistant, "Partial", isStreaming = true)),
        )

        val failed = reduceRunState(streaming, StreamUpdate.Failure("Gateway rejected the run"))
        val finished = reduceRunState(failed, StreamUpdate.Finished)

        assertTrue(finished.run.active)
        assertEquals(RunStatus.Reconnecting, finished.run.status)
        assertEquals("Gateway rejected the run", finished.error)
        assertFalse(finished.messages.single().isStreaming)
    }

    @Test
    fun `terminal gateway outcomes clear persistence state but retain the outcome for the ui`() {
        val current = initial.copy(
            run = RunState(RunStatus.Reconnecting, runId = "run-1", startedAtEpochMs = 123L),
            serverMessages = listOf(ChatMessage("ai-1", MessageRole.Assistant, "Partial", isStreaming = true)),
        )
        val snapshot = ThreadSnapshot("Research", listOf(ChatMessage("ai-1", MessageRole.Assistant, "Final")))

        val success = completeWithSnapshot(current, snapshot, GatewayRunStatus.Success)
        val error = completeWithSnapshot(current, snapshot, GatewayRunStatus.Error, "Provider unavailable")
        val timeout = completeWithSnapshot(current, snapshot, GatewayRunStatus.Timeout, "Deadline elapsed")
        val interrupted = completeWithSnapshot(current, snapshot, GatewayRunStatus.Interrupted, "Stopped")

        assertFalse(success.run.active)
        assertEquals(RunStatus.Idle, success.run.status)
        assertEquals(GatewayRunStatus.Success, success.run.gatewayStatus)
        assertEquals(123L, success.run.startedAtEpochMs)
        assertEquals(GatewayRunStatus.Error, error.run.gatewayStatus)
        assertEquals("Provider unavailable", error.error)
        assertEquals(GatewayRunStatus.Timeout, timeout.run.gatewayStatus)
        assertEquals("Deadline elapsed", timeout.error)
        assertEquals(GatewayRunStatus.Interrupted, interrupted.run.gatewayStatus)
        assertEquals("Stopped", interrupted.error)
    }
}
