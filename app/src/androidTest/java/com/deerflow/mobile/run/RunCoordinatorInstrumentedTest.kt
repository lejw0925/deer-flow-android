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
        coordinator.abandonActive()
    }

    @Test
    fun applicationCoordinatorCompletesAfterCallerReturns() = runBlocking {
        coordinator.abandonActive()
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
                val current = coordinator.state.value
                if (current?.threadId == thread.id && !current.run.active && current.messages.isNotEmpty()) {
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
        assertEquals(thread.id, coordinator.state.value?.threadId)
    }

    @Test
    fun serviceRecoveryResumesRunRecordedBeforeProcessRestart() = runBlocking {
        coordinator.abandonActive()
        val serverUrl = "http://10.0.2.2:2027"
        val api = DeerFlowApi(serverUrl, WebViewSessionCookieStore())
        val thread = api.createThread()
        val cache = WorkspaceCache(context)
        cache.saveThreads(serverUrl, listOf(thread))
        cache.saveRun(
            serverUrl,
            thread.id,
            RunState(RunStatus.Reconnecting, runId = "fixture-recovered-run", lastEventId = "event-2"),
        )

        assertTrue(RunService.recover(context, serverUrl))
        var completed: CoordinatedRunState? = null
        withTimeout(10_000) {
            while (completed == null) {
                val current = coordinator.state.value
                if (current?.threadId == thread.id && !current.run.active && current.messages.isNotEmpty()) {
                    completed = current
                } else {
                    delay(100)
                }
            }
        }

        assertTrue(checkNotNull(completed).messages.any { it.text.contains("process restart") })
        assertNull(cache.loadRun(serverUrl, thread.id))
        cache.deleteThread(serverUrl, thread.id)
    }

    @Test
    fun patchSequenceKeepsOptimisticPromptAndAccumulatesReasoningAndTools() = runBlocking {
        coordinator.abandonActive()
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
                val current = coordinator.state.value
                if (current?.threadId == thread.id) {
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
}
