package com.deerflow.mobile.run

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deerflow.mobile.data.ChatMessage
import com.deerflow.mobile.data.DeerFlowApi
import com.deerflow.mobile.data.MessageBlock
import com.deerflow.mobile.data.MessageRole
import com.deerflow.mobile.data.RunOptions
import com.deerflow.mobile.data.RunState
import com.deerflow.mobile.data.RunStatus
import com.deerflow.mobile.data.WebViewSessionCookieStore
import com.deerflow.mobile.data.WorkspaceCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunCoordinatorInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val coordinator = RunCoordinator.get(context)

    @Before
    fun grantNotificationPermission() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    @After
    fun cleanUp() {
        coordinator.abandonAll()
    }

    @Test
    fun applicationCoordinatorCompletesAfterCallerReturns() = runBlocking {
        coordinator.abandonAll()
        val serverUrl = "http://10.0.2.2:2027"
        val api = DeerFlowApi(serverUrl, WebViewSessionCookieStore())
        val thread = api.createThread()

        coordinator.start(
            CoordinatedRunRequest(
                serverUrl = serverUrl,
                threadId = thread.id,
                title = thread.title,
                message = "Keep running without a screen observer",
                options = RunOptions(),
            ),
        )

        var completed: CoordinatedRunState? = null
        withTimeout(20_000) {
            while (completed == null) {
                val current = coordinator.stateFor(serverUrl, thread.id)
                if (current != null && !current.run.active && current.messages.isNotEmpty()) {
                    completed = current
                } else {
                    delay(100)
                }
            }
        }
        val result = checkNotNull(completed)

        assertFalse(result.run.active)
        assertTrue(result.messages.any { it.text.contains("concise plan") })
        assertNull(WorkspaceCache(context).loadRun(serverUrl, thread.id))
        assertEquals(thread.id, coordinator.stateFor(serverUrl, thread.id)?.threadId)
    }

    @Test
    fun serviceRecoveryResumesEveryRunRecordedBeforeProcessRestart() = runBlocking {
        coordinator.abandonAll()
        val serverUrl = "http://10.0.2.2:2027"
        val api = DeerFlowApi(serverUrl, WebViewSessionCookieStore())
        val first = api.createThread()
        val second = api.createThread()
        val cache = WorkspaceCache(context)
        cache.saveThreads(serverUrl, listOf(first, second))
        cache.saveRun(
            serverUrl,
            first.id,
            RunState(RunStatus.Reconnecting, runId = "fixture-recovered-run-${first.id}", lastEventId = "event-2"),
        )
        cache.saveRun(
            serverUrl,
            second.id,
            RunState(RunStatus.Reconnecting, runId = "fixture-recovered-run-${second.id}", lastEventId = "event-3"),
        )

        assertTrue(RunService.recover(context, serverUrl))
        var completedFirst: CoordinatedRunState? = null
        var completedSecond: CoordinatedRunState? = null
        withTimeout(10_000) {
            while (completedFirst == null || completedSecond == null) {
                coordinator.stateFor(serverUrl, first.id)?.let { current ->
                    if (!current.run.active && current.messages.isNotEmpty()) completedFirst = current
                }
                coordinator.stateFor(serverUrl, second.id)?.let { current ->
                    if (!current.run.active && current.messages.isNotEmpty()) completedSecond = current
                }
                delay(100)
            }
        }

        assertTrue(checkNotNull(completedFirst).messages.any { it.text.contains("process restart") })
        assertTrue(checkNotNull(completedSecond).messages.any { it.text.contains("process restart") })
        assertNull(cache.loadRun(serverUrl, first.id))
        assertNull(cache.loadRun(serverUrl, second.id))
        cache.deleteThread(serverUrl, first.id)
        cache.deleteThread(serverUrl, second.id)
    }

    @Test
    fun patchSequenceKeepsOptimisticPromptAndAccumulatesReasoningAndTools() = runBlocking {
        coordinator.abandonAll()
        val serverUrl = "http://10.0.2.2:2027"
        val api = DeerFlowApi(serverUrl, WebViewSessionCookieStore())
        val thread = api.createThread()
        val clientMessageId = "patch-client-message"
        val optimistic = ChatMessage(clientMessageId, MessageRole.User, "Verify stream patches")

        coordinator.start(
            CoordinatedRunRequest(
                serverUrl = serverUrl,
                threadId = thread.id,
                title = thread.title,
                message = optimistic.text,
                options = RunOptions(),
                clientMessageId = clientMessageId,
                initialMessages = listOf(optimistic),
            ),
        )

        var userWasVisibleAtEveryStage = true
        var sawReasoning = false
        var sawTwoToolCalls = false
        var sawToolResult = false
        var completed: CoordinatedRunState? = null
        withTimeout(20_000) {
            while (completed == null) {
                val current = coordinator.stateFor(serverUrl, thread.id)
                if (current != null) {
                    userWasVisibleAtEveryStage = userWasVisibleAtEveryStage && current.messages.any { it.id == clientMessageId }
                    val assistant = current.messages.firstOrNull { it.role == MessageRole.Assistant }
                    val blocks = assistant?.blocks.orEmpty()
                    sawReasoning = sawReasoning || blocks.any { it is MessageBlock.Reasoning }
                    sawTwoToolCalls = sawTwoToolCalls || blocks.filterIsInstance<MessageBlock.ToolCall>().size >= 2
                    sawToolResult = sawToolResult || current.messages.any { it.blocks.any { block -> block is MessageBlock.ToolResult } }
                    if (!current.run.active) completed = current
                }
                delay(50)
            }
        }
        val result = checkNotNull(completed)

        assertTrue(userWasVisibleAtEveryStage)
        assertTrue(sawReasoning)
        assertTrue(sawTwoToolCalls)
        assertTrue(sawToolResult)
        assertEquals(1, result.messages.count { it.id == clientMessageId })
        assertNull(result.pendingUserMessage)
    }

    @Test
    fun stoppingOneConcurrentConversationDoesNotInterruptTheOther() = runBlocking {
        coordinator.abandonAll()
        val serverUrl = "http://10.0.2.2:2027"
        val api = DeerFlowApi(serverUrl, WebViewSessionCookieStore())
        val first = api.createThread()
        val second = api.createThread()
        val firstPrompt = "Concurrent conversation A"
        val secondPrompt = "Concurrent conversation B"
        val firstClientMessageId = "concurrent-client-a"
        val secondClientMessageId = "concurrent-client-b"

        assertTrue(
            coordinator.start(
                CoordinatedRunRequest(
                    serverUrl = serverUrl,
                    threadId = first.id,
                    title = first.title,
                    message = firstPrompt,
                    options = RunOptions(),
                    clientMessageId = firstClientMessageId,
                    initialMessages = listOf(ChatMessage(firstClientMessageId, MessageRole.User, firstPrompt)),
                ),
            ),
        )
        assertTrue(
            coordinator.start(
                CoordinatedRunRequest(
                    serverUrl = serverUrl,
                    threadId = second.id,
                    title = second.title,
                    message = secondPrompt,
                    options = RunOptions(),
                    clientMessageId = secondClientMessageId,
                    initialMessages = listOf(ChatMessage(secondClientMessageId, MessageRole.User, secondPrompt)),
                ),
            ),
        )

        withTimeout(5_000) {
            while (
                coordinator.stateFor(serverUrl, first.id)?.run?.active != true ||
                coordinator.stateFor(serverUrl, second.id)?.run?.active != true
            ) {
                delay(25)
            }
        }

        coordinator.cancel(RunKey(serverUrl, second.id))

        assertFalse(checkNotNull(coordinator.stateFor(serverUrl, second.id)).run.active)
        assertTrue(checkNotNull(coordinator.stateFor(serverUrl, first.id)).run.active)

        var completedFirst: CoordinatedRunState? = null
        withTimeout(20_000) {
            while (completedFirst == null) {
                coordinator.stateFor(serverUrl, first.id)?.let { current ->
                    if (!current.run.active && current.messages.isNotEmpty()) completedFirst = current
                }
                delay(50)
            }
        }

        val firstMessages = checkNotNull(completedFirst).messages
        val secondMessages = checkNotNull(coordinator.stateFor(serverUrl, second.id)).messages
        assertTrue(firstMessages.any { it.text.contains(firstPrompt) })
        assertFalse(firstMessages.any { it.text.contains(secondPrompt) })
        assertTrue(secondMessages.any { it.text.contains(secondPrompt) })
        assertFalse(secondMessages.any { it.text.contains(firstPrompt) })
    }
}
