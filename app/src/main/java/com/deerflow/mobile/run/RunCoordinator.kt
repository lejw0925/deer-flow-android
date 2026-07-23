package com.deerflow.mobile.run

import android.content.Context
import android.util.Log
import com.deerflow.mobile.data.ApiException
import com.deerflow.mobile.data.ChatMessage
import com.deerflow.mobile.data.confirmsPendingUserMessage
import com.deerflow.mobile.data.DeerFlowApi
import com.deerflow.mobile.data.HumanInputResponse
import com.deerflow.mobile.data.RegeneratePreparation
import com.deerflow.mobile.data.RunOptions
import com.deerflow.mobile.data.RunState
import com.deerflow.mobile.data.RunStatus
import com.deerflow.mobile.data.SettingsStore
import com.deerflow.mobile.data.StreamUpdate
import com.deerflow.mobile.data.ThreadSnapshot
import com.deerflow.mobile.data.TodoItem
import com.deerflow.mobile.data.UploadedFileInfo
import com.deerflow.mobile.data.WebViewSessionCookieStore
import com.deerflow.mobile.data.WorkspaceCache
import com.deerflow.mobile.data.mergeStreamChunk
import com.deerflow.mobile.data.mergeStreamPatch
import com.deerflow.mobile.data.mergeStreamSnapshot
import com.deerflow.mobile.data.projectVisibleMessages
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CoordinatedRunRequest(
    val serverUrl: String,
    val threadId: String,
    val title: String,
    val message: String,
    val options: RunOptions,
    val clientMessageId: String = java.util.UUID.randomUUID().toString(),
    val files: List<UploadedFileInfo> = emptyList(),
    val humanInputResponse: HumanInputResponse? = null,
    val regenerate: RegeneratePreparation? = null,
    val initialMessages: List<ChatMessage> = emptyList(),
    val initialTodos: List<TodoItem> = emptyList(),
    val initialArtifacts: List<String> = emptyList(),
    val failureMessages: List<ChatMessage>? = null,
)

data class CoordinatedRunState(
    val serverUrl: String,
    val threadId: String,
    val title: String,
    val run: RunState,
    val serverMessages: List<ChatMessage>,
    val pendingUserMessage: ChatMessage? = null,
    val pendingUserIndex: Int? = null,
    val todos: List<TodoItem> = emptyList(),
    val artifacts: List<String> = emptyList(),
    val error: String? = null,
    val revision: Long = 0,
) {
    /** The UI sees canonical server history plus a single unconfirmed outgoing message. */
    val messages: List<ChatMessage>
        get() = projectVisibleMessages(serverMessages, pendingUserMessage, pendingUserIndex)
}

/** Holds the newest merged text state until the next UI publication window. */
internal class PendingChunkState {
    private var pending: CoordinatedRunState? = null

    fun peek(): CoordinatedRunState? = pending

    fun append(current: CoordinatedRunState, chunk: ChatMessage): CoordinatedRunState {
        val base = pending?.takeIf {
            it.serverUrl == current.serverUrl && it.threadId == current.threadId
        } ?: current
        return reduceRunState(base, StreamUpdate.MessageChunk(chunk)).also { pending = it }
    }

    fun take(serverUrl: String, threadId: String): CoordinatedRunState? {
        val value = pending?.takeIf { it.serverUrl == serverUrl && it.threadId == threadId }
        if (value != null) pending = null
        return value
    }

    fun clear() {
        pending = null
    }
}

class RunCoordinator private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val cache = WorkspaceCache(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val persistenceMutex = Mutex()
    private val mutableState = MutableStateFlow<CoordinatedRunState?>(null)
    private val chunkUpdateLock = Any()
    private val pendingChunks = PendingChunkState()
    private var chunkFlushJob: Job? = null

    val state: StateFlow<CoordinatedRunState?> = mutableState.asStateFlow()

    @Volatile private var activeApi: DeerFlowApi? = null
    @Volatile private var streamJob: Job? = null

    suspend fun start(request: CoordinatedRunRequest) = operationMutex.withLock {
        if (mutableState.value?.run?.active == true) return@withLock
        clearPendingChunks()
        val pending = request.initialMessages.firstOrNull {
            it.id == request.clientMessageId && it.role == com.deerflow.mobile.data.MessageRole.User
        }
        val serverMessages = request.initialMessages.filterNot { it.id == request.clientMessageId }
        val initial = CoordinatedRunState(
            serverUrl = request.serverUrl,
            threadId = request.threadId,
            title = request.title,
            run = RunState(RunStatus.Connecting),
            serverMessages = serverMessages,
            pendingUserMessage = pending,
            pendingUserIndex = pending?.let { request.initialMessages.indexOf(it) },
            todos = request.initialTodos,
            artifacts = request.initialArtifacts,
        )
        mutableState.value = initial
        persist(initial)
        RunService.start(appContext, request.title, request.serverUrl, request.threadId)
        launchStream(request, resume = null)
    }

    suspend fun resume(
        serverUrl: String,
        threadId: String,
        title: String,
        saved: RunState,
    ): Boolean = operationMutex.withLock {
        val current = mutableState.value
        if (current?.serverUrl == serverUrl && current.threadId == threadId && current.run.active) {
            return@withLock true
        }

        val api = DeerFlowApi(serverUrl, WebViewSessionCookieStore())
        val resumable = resolveResumableRun(api, threadId, saved)
        if (resumable == null) {
            val snapshot = runCatching { api.threadState(threadId) }.getOrNull()
            cache.saveRun(serverUrl, threadId, RunState())
            snapshot?.let {
                cache.saveMessages(serverUrl, threadId, it.messages)
                mutableState.value = CoordinatedRunState(
                    serverUrl = serverUrl,
                    threadId = threadId,
                    title = it.title,
                    run = RunState(),
                    serverMessages = it.messages,
                    todos = it.todos,
                    artifacts = it.artifacts,
                )
            }
            return@withLock false
        }

        val request = CoordinatedRunRequest(
            serverUrl = serverUrl,
            threadId = threadId,
            title = title,
            message = "",
            options = RunOptions(),
            initialMessages = cache.loadMessages(serverUrl, threadId),
        )
        clearPendingChunks()
        val reconnecting = CoordinatedRunState(
            serverUrl = serverUrl,
            threadId = threadId,
            title = title,
            run = resumable.copy(status = RunStatus.Reconnecting),
            serverMessages = request.initialMessages,
        )
        mutableState.value = reconnecting
        persist(reconnecting)
        RunService.start(appContext, title, serverUrl, threadId)
        launchStream(request, resumable)
        true
    }

    suspend fun recoverLatest(serverUrlOverride: String? = null): Boolean {
        val serverUrl = serverUrlOverride ?: SettingsStore(appContext).read().serverUrl
        val recoverable = cache.loadLatestActiveRun(serverUrl) ?: return false
        return resume(serverUrl, recoverable.threadId, recoverable.title, recoverable.run)
    }

    suspend fun cancelActive(): ThreadSnapshot? = operationMutex.withLock {
        val initial = mutableState.value ?: return@withLock null
        flushPendingChunks(initial.serverUrl, initial.threadId)
        val current = mutableState.value ?: return@withLock null
        if (!current.run.active) return@withLock null
        mutableState.value = current.copy(
            run = current.run.copy(status = RunStatus.Stopping),
            revision = current.revision + 1,
        )

        val api = activeApi ?: DeerFlowApi(current.serverUrl, WebViewSessionCookieStore())
        api.cancelActiveStream()
        streamJob?.cancelAndJoin()
        current.run.runId?.let { runId -> runCatching { api.cancelRun(current.threadId, runId) } }
        val snapshot = runCatching { api.threadState(current.threadId) }.getOrNull()
        val stopped = current.copy(
            title = snapshot?.title ?: current.title,
            run = RunState(),
            serverMessages = snapshot?.messages ?: current.serverMessages.map { it.copy(isStreaming = false) },
            pendingUserMessage = current.pendingUserMessage?.takeUnless { pending ->
                snapshot?.messages?.any { confirmsPendingUserMessage(pending, it) } == true
            },
            pendingUserIndex = current.pendingUserIndex.takeIf {
                current.pendingUserMessage?.let { pending ->
                    snapshot?.messages?.any { confirmsPendingUserMessage(pending, it) } != true
                } == true
            },
            todos = snapshot?.todos ?: current.todos,
            artifacts = snapshot?.artifacts ?: current.artifacts,
            revision = current.revision + 2,
        )
        mutableState.value = stopped
        persist(stopped, clearRun = true)
        activeApi = null
        streamJob = null
        RunService.stop(appContext)
        snapshot
    }

    fun abandonActive() {
        clearPendingChunks()
        val current = mutableState.value
        activeApi?.cancelActiveStream()
        streamJob?.cancel()
        activeApi = null
        streamJob = null
        mutableState.value = null
        if (current != null) {
            scope.launch {
                persistenceMutex.withLock {
                    val replacement = mutableState.value
                    val sameRunRestarted = replacement?.serverUrl == current.serverUrl &&
                        replacement.threadId == current.threadId && replacement.run.active
                    if (!sameRunRestarted) {
                        cache.saveRun(current.serverUrl, current.threadId, RunState())
                    }
                }
            }
        }
        RunService.stop(appContext)
    }

    fun isActive(serverUrl: String, threadId: String): Boolean =
        mutableState.value?.let { it.serverUrl == serverUrl && it.threadId == threadId && it.run.active } == true

    private fun launchStream(request: CoordinatedRunRequest, resume: RunState?) {
        val api = DeerFlowApi(request.serverUrl, WebViewSessionCookieStore())
        activeApi = api
        streamJob = scope.launch {
            val owningJob = coroutineContext[Job]
            try {
                RunService.update(
                    appContext,
                    if (resume == null) RunProgress.Connecting else RunProgress.Reconnecting,
                    request.title,
                )
                api.streamMessage(
                    threadId = request.threadId,
                    message = request.message,
                    options = request.options,
                    clientMessageId = request.clientMessageId,
                    files = request.files,
                    resume = resume,
                    humanInputResponse = request.humanInputResponse,
                    regenerate = request.regenerate,
                    onUpdate = { update -> applyStreamUpdate(request, update) },
                )
                flushPendingChunks(request.serverUrl, request.threadId)
                val previous = mutableState.value ?: return@launch
                if (previous.serverUrl != request.serverUrl || previous.threadId != request.threadId) return@launch
                val snapshot = awaitTerminalSnapshot(api, request, previous)
                if (
                    previous.run.status == RunStatus.Failed &&
                    !snapshotCompletesClientMessage(snapshot, request.clientMessageId)
                ) {
                    persist(previous)
                    return@launch
                }
                val completed = completeWithSnapshot(previous, snapshot)
                logUnconfirmedPending(previous, completed)
                persistAndPublish(completed, clearRun = true)
                RunService.complete(appContext, completed.title)
            } catch (_: CancellationException) {
                // Explicit stop or server change owns the visible terminal state.
            } catch (error: Exception) {
                flushPendingChunks(request.serverUrl, request.threadId)
                val previous = mutableState.value ?: return@launch
                if (previous.serverUrl != request.serverUrl || previous.threadId != request.threadId) return@launch
                if (resume != null && error.isTerminalResumeError()) {
                    val snapshot = runCatching { api.threadState(request.threadId) }.getOrNull()
                    if (snapshot != null) {
                        val completed = completeWithSnapshot(previous, snapshot)
                        logUnconfirmedPending(previous, completed)
                        persistAndPublish(completed, clearRun = true)
                        RunService.complete(appContext, completed.title)
                        return@launch
                    }
                }
                val message = error.userMessage(request.serverUrl, "The run stopped unexpectedly.")
                val failed = previous.copy(
                    run = RunState(RunStatus.Failed),
                    serverMessages = (request.failureMessages ?: previous.serverMessages).map { it.copy(isStreaming = false) },
                    error = message,
                    revision = previous.revision + 1,
                )
                persistAndPublish(failed)
                RunService.fail(appContext, message, failed.title)
            } finally {
                if (streamJob === owningJob) streamJob = null
                if (activeApi === api) activeApi = null
            }
        }
    }

    private fun applyStreamUpdate(request: CoordinatedRunRequest, update: StreamUpdate) {
        if (update is StreamUpdate.MessageChunk) {
            queueMessageChunk(request, update.value)
            return
        }

        flushPendingChunks(request.serverUrl, request.threadId)
        val current = mutableState.value ?: return
        if (current.serverUrl != request.serverUrl || current.threadId != request.threadId) return
        val next = reduceRunState(current, update)
        // launchStream publishes Idle after the final snapshot and Room cleanup commit together.
        val deferSuccessfulFinish = update == StreamUpdate.Finished && current.run.status != RunStatus.Failed
        if (!deferSuccessfulFinish) {
            mutableState.value = next
            scope.launch { persist(next) }
        }

        when (update) {
            is StreamUpdate.Started -> RunService.update(appContext, runProgressUpdate(RunProgress.Working, next.todos), next.title)
            is StreamUpdate.Reconnecting -> RunService.update(appContext, runProgressUpdate(RunProgress.Reconnecting, next.todos), next.title)
            is StreamUpdate.Patch -> RunService.update(appContext, runProgressUpdate(RunProgress.Working, next.todos), next.title)
            StreamUpdate.Finished -> Unit
            is StreamUpdate.Failure -> RunService.fail(appContext, update.message, next.title)
            is StreamUpdate.EventId -> Unit
            is StreamUpdate.MessageChunk -> error("Message chunks are buffered above")
        }
    }

    private fun queueMessageChunk(request: CoordinatedRunRequest, chunk: ChatMessage) {
        synchronized(chunkUpdateLock) {
            val current = pendingChunks.peek()
                ?.takeIf { it.serverUrl == request.serverUrl && it.threadId == request.threadId }
                ?: mutableState.value
                ?: return
            if (current.serverUrl != request.serverUrl || current.threadId != request.threadId) return

            pendingChunks.append(current, chunk)
            if (chunkFlushJob == null) {
                chunkFlushJob = scope.launch {
                    delay(CHUNK_UI_PUBLISH_INTERVAL_MS)
                    flushPendingChunks(request.serverUrl, request.threadId, cancelScheduledJob = false)
                }
            }
        }
    }

    private fun flushPendingChunks(
        serverUrl: String,
        threadId: String,
        cancelScheduledJob: Boolean = true,
    ) {
        val scheduledJob: Job?
        val next: CoordinatedRunState?
        synchronized(chunkUpdateLock) {
            scheduledJob = chunkFlushJob
            chunkFlushJob = null
            next = pendingChunks.take(serverUrl, threadId)
            if (next != null) mutableState.value = next
        }
        if (cancelScheduledJob) scheduledJob?.cancel()
        next?.let { state ->
            scope.launch { persist(state) }
            RunService.update(appContext, runProgressUpdate(RunProgress.Responding, state.todos), state.title)
        }
    }

    private fun clearPendingChunks() {
        val scheduledJob = synchronized(chunkUpdateLock) {
            chunkFlushJob.also {
                chunkFlushJob = null
                pendingChunks.clear()
            }
        }
        scheduledJob?.cancel()
    }

    /**
     * `end` closes the SSE response before some Gateway deployments have made
     * the final graph checkpoint visible through `/state`. A normal completed
     * answer still performs exactly one state read. Only a state ending in a
     * tool step gets a short, bounded reconciliation window.
     */
    private suspend fun awaitTerminalSnapshot(
        api: DeerFlowApi,
        request: CoordinatedRunRequest,
        initial: CoordinatedRunState,
    ): ThreadSnapshot {
        RunService.update(appContext, RunProgress.Finalizing, initial.title)
        // Let the Gateway commit its final checkpoint before consuming the normal one-shot state read.
        delay(TERMINAL_STATE_INITIAL_DELAY_MS)
        var snapshot = api.threadState(request.threadId)
        if (!terminalSnapshotNeedsRetry(initial, snapshot)) return snapshot

        for (delayMillis in TERMINAL_STATE_RETRY_DELAYS_MS) {
            delay(delayMillis)
            val current = mutableState.value ?: return snapshot
            if (
                current.serverUrl != request.serverUrl ||
                current.threadId != request.threadId ||
                !current.run.active
            ) {
                return snapshot
            }
            snapshot = api.threadState(request.threadId)
            if (!terminalSnapshotNeedsRetry(current, snapshot)) return snapshot
        }
        Log.w("RunCoordinator", "Terminal state remained incomplete after ${TERMINAL_STATE_RETRY_DELAYS_MS.size} retries for ${request.threadId}.")
        return snapshot
    }

    private suspend fun resolveResumableRun(api: DeerFlowApi, threadId: String, saved: RunState): RunState? {
        return resumableRun(saved, api.latestActiveRun(threadId)?.runId)
    }

    private suspend fun persist(state: CoordinatedRunState, clearRun: Boolean = false) {
        persistenceMutex.withLock {
            val latest = mutableState.value
            if (!clearRun && latest != null && latest.revision > state.revision) return
            cache.saveRun(state.serverUrl, state.threadId, if (clearRun) RunState() else state.run)
            cache.saveMessages(state.serverUrl, state.threadId, state.serverMessages)
        }
    }

    private suspend fun persistAndPublish(state: CoordinatedRunState, clearRun: Boolean = false) {
        persistenceMutex.withLock {
            val latest = mutableState.value ?: return
            if (
                latest.serverUrl != state.serverUrl ||
                latest.threadId != state.threadId ||
                latest.revision > state.revision
            ) return
            cache.saveRun(state.serverUrl, state.threadId, if (clearRun) RunState() else state.run)
            cache.saveMessages(state.serverUrl, state.threadId, state.serverMessages)
            mutableState.value = state
        }
    }

    private fun Exception.userMessage(serverUrl: String, fallback: String): String = when (this) {
        is ApiException -> message
        is IOException -> "$fallback Check $serverUrl."
        else -> message?.takeIf { it.isNotBlank() } ?: fallback
    }

    companion object {
        private const val CHUNK_UI_PUBLISH_INTERVAL_MS = 80L
        private const val TERMINAL_STATE_INITIAL_DELAY_MS = 1_000L
        private val TERMINAL_STATE_RETRY_DELAYS_MS = longArrayOf(500L, 1_000L, 2_000L)

        @Volatile private var instance: RunCoordinator? = null

        fun get(context: Context): RunCoordinator = instance ?: synchronized(this) {
            instance ?: RunCoordinator(context).also { instance = it }
        }
    }
}

internal fun completeWithSnapshot(current: CoordinatedRunState, snapshot: ThreadSnapshot): CoordinatedRunState {
    val serverMessages = mergeStreamSnapshot(current.serverMessages, snapshot)
        .map { it.copy(isStreaming = false) }
    val confirmed = current.pendingUserMessage?.let { pending ->
        serverMessages.any { confirmsPendingUserMessage(pending, it) }
    } != false
    return current.copy(
        title = if (snapshot.hasTitle) snapshot.title else current.title,
        run = RunState(),
        serverMessages = serverMessages,
        pendingUserMessage = current.pendingUserMessage?.takeIf { !confirmed },
        pendingUserIndex = current.pendingUserIndex.takeIf { !confirmed },
        todos = if (snapshot.hasTodos) snapshot.todos else current.todos,
        artifacts = if (snapshot.hasArtifacts) snapshot.artifacts else current.artifacts,
        revision = current.revision + 1,
    )
}

/** True when the completed state still ends at a tool/reasoning step rather than an answer. */
internal fun terminalSnapshotNeedsRetry(current: CoordinatedRunState, snapshot: ThreadSnapshot): Boolean {
    val streamed = terminalAssistantAnswer(current.serverMessages, current.pendingUserMessage)
    val persisted = terminalAssistantAnswer(snapshot.messages, current.pendingUserMessage)
    if (persisted == null) return streamed == null
    if (persisted.hasSuspiciouslyShortTextForUsage()) return true
    return streamed?.let { live ->
        live.isStreaming && live.id == persisted.id && live.text == persisted.text &&
            live.hasSuspiciouslyShortTextForUsage()
    } == true
}

private fun terminalAssistantAnswer(
    messages: List<ChatMessage>,
    pendingUserMessage: ChatMessage?,
): ChatMessage? {
    val messageWindow = pendingUserMessage?.let { pending ->
        val userIndex = messages.indexOfLast { confirmsPendingUserMessage(pending, it) }
        if (userIndex < 0) {
            val streamedAssistant = messages.lastOrNull {
                it.role == com.deerflow.mobile.data.MessageRole.Assistant && it.isStreaming
            } ?: return null
            return streamedAssistant.takeIf { it.text.isNotBlank() && it.blocks.none { block ->
                block is com.deerflow.mobile.data.MessageBlock.ToolCall
            } }
        }
        messages.drop(userIndex + 1)
    } ?: messages.drop(messages.indexOfLast { it.role == com.deerflow.mobile.data.MessageRole.User } + 1)
    val assistant = messageWindow.lastOrNull { it.role == com.deerflow.mobile.data.MessageRole.Assistant }
        ?: return null
    return assistant.takeIf { it.text.isNotBlank() && it.blocks.none { block ->
        block is com.deerflow.mobile.data.MessageBlock.ToolCall
    } }
}

/** A persisted usage total must not be paired with only the first emitted token. */
private fun ChatMessage.hasSuspiciouslyShortTextForUsage(): Boolean {
    val outputTokens = tokenUsage?.outputTokens ?: return false
    return outputTokens > 1 && text.codePointCount(0, text.length) < outputTokens
}

internal fun snapshotCompletesClientMessage(snapshot: ThreadSnapshot, clientMessageId: String): Boolean {
    val userIndex = snapshot.messages.indexOfFirst { message ->
        message.role == com.deerflow.mobile.data.MessageRole.User &&
            (message.id == clientMessageId || message.id == "${clientMessageId}__user")
    }
    return userIndex >= 0 && snapshot.messages.drop(userIndex + 1).any { message ->
        message.role == com.deerflow.mobile.data.MessageRole.Assistant && message.text.isNotBlank()
    }
}

private fun logUnconfirmedPending(before: CoordinatedRunState, after: CoordinatedRunState) {
    if (before.pendingUserMessage != null && after.pendingUserMessage != null) {
        Log.d("RunCoordinator", "Terminal state did not confirm optimistic message ${after.pendingUserMessage.id}; retaining it.")
    }
}

internal fun resumableRun(saved: RunState, activeRunId: String?): RunState? = when {
    !activeRunId.isNullOrBlank() -> saved.copy(
        runId = activeRunId,
        lastEventId = saved.lastEventId.takeIf { saved.runId == activeRunId },
    )
    saved.active && !saved.runId.isNullOrBlank() -> saved
    else -> null
}

private fun Exception.isTerminalResumeError(): Boolean =
    this is ApiException && statusCode in setOf(404, 409)

internal fun reduceRunState(current: CoordinatedRunState, update: StreamUpdate): CoordinatedRunState {
    val revision = current.revision + 1
    return when (update) {
        is StreamUpdate.Started -> current.copy(
            run = current.run.copy(status = RunStatus.Streaming, runId = update.runId ?: current.run.runId),
            revision = revision,
        )
        is StreamUpdate.EventId -> current.copy(
            run = current.run.copy(lastEventId = update.value),
            revision = revision,
        )
        is StreamUpdate.Reconnecting -> current.copy(
            run = current.run.copy(status = RunStatus.Reconnecting, reconnectAttempt = update.attempt),
            revision = revision,
        )
        is StreamUpdate.MessageChunk -> current.copy(
            run = current.run.copy(status = RunStatus.Streaming),
            serverMessages = mergeStreamChunk(current.serverMessages, update.value),
            pendingUserMessage = current.pendingUserMessage?.takeUnless { pending ->
                confirmsPendingUserMessage(pending, update.value)
            },
            pendingUserIndex = current.pendingUserIndex.takeUnless {
                current.pendingUserMessage?.let { pending ->
                    confirmsPendingUserMessage(pending, update.value)
                } == true
            },
            revision = revision,
        )
        is StreamUpdate.Patch -> {
            val serverMessages = mergeStreamPatch(current.serverMessages, update.value)
            val pending = current.pendingUserMessage?.takeUnless { pending ->
                serverMessages.any { server -> confirmsPendingUserMessage(pending, server) }
            }
            current.copy(
                title = update.value.title ?: current.title,
                serverMessages = serverMessages,
                pendingUserMessage = pending,
                pendingUserIndex = current.pendingUserIndex.takeIf { pending != null },
                todos = update.value.todos ?: current.todos,
                artifacts = mergeArtifacts(current.artifacts, update.value.artifacts),
                revision = revision,
            )
        }
        is StreamUpdate.Failure -> current.copy(
            run = RunState(RunStatus.Failed),
            serverMessages = current.serverMessages.map { it.copy(isStreaming = false) },
            error = update.message,
            revision = revision,
        )
        StreamUpdate.Finished -> current.copy(
            run = if (current.run.status == RunStatus.Failed) current.run else RunState(),
            serverMessages = current.serverMessages.map { it.copy(isStreaming = false) },
            revision = revision,
        )
    }
}

private fun mergeArtifacts(current: List<String>, patch: List<String>?): List<String> =
    if (patch == null) current else (current + patch).distinct()
