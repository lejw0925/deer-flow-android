package com.deerflow.mobile.run

import android.content.Context
import android.util.Log
import com.deerflow.mobile.data.ApiException
import com.deerflow.mobile.data.ChatMessage
import com.deerflow.mobile.data.confirmsPendingUserMessage
import com.deerflow.mobile.data.DeerFlowApi
import com.deerflow.mobile.data.GatewayRunInfo
import com.deerflow.mobile.data.GatewayRunStatus
import com.deerflow.mobile.data.HumanInputResponse
import com.deerflow.mobile.data.RegeneratePreparation
import com.deerflow.mobile.data.RunOptions
import com.deerflow.mobile.data.RunState
import com.deerflow.mobile.data.RunStatus
import com.deerflow.mobile.data.ensureStartedAt
import com.deerflow.mobile.data.SettingsStore
import com.deerflow.mobile.data.StreamUpdate
import com.deerflow.mobile.data.StreamResult
import com.deerflow.mobile.data.ThreadSnapshot
import com.deerflow.mobile.data.TodoItem
import com.deerflow.mobile.data.UploadedFileInfo
import com.deerflow.mobile.data.WebViewSessionCookieStore
import com.deerflow.mobile.data.WorkspaceCache
import com.deerflow.mobile.data.applySubagentProgress
import com.deerflow.mobile.data.hasOpenHumanInputRequest
import com.deerflow.mobile.data.mergeStreamChunk
import com.deerflow.mobile.data.mergeStreamPatch
import com.deerflow.mobile.data.mergeStreamSnapshot
import com.deerflow.mobile.data.projectVisibleMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Identifies one independently resumable Gateway run. */
data class RunKey(
    val serverUrl: String,
    val threadId: String,
)

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
) {
    val key: RunKey get() = RunKey(serverUrl, threadId)
}

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
    /** Latest tool name observed on the stream (for Live Update icons). */
    val latestToolName: String? = null,
    /** Latest Gateway middleware notice, visible while the run is active. */
    val runNotice: StreamUpdate.RunNotice? = null,
    val error: String? = null,
    val revision: Long = 0,
) {
    val key: RunKey get() = RunKey(serverUrl, threadId)

    /** The UI sees canonical server history plus a single unconfirmed outgoing message. */
    val messages: List<ChatMessage>
        get() = projectVisibleMessages(serverMessages, pendingUserMessage, pendingUserIndex)
}

/**
 * Application-wide registry for independently streaming conversations.
 *
 * A [RunSessionCoordinator] owns all mutable transport state for one [RunKey]. The registry only
 * coordinates their lifecycle and publishes a keyed projection to the UI and foreground service.
 */
class RunCoordinator private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val cache = WorkspaceCache(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionLock = Any()
    private val sessions = mutableMapOf<RunKey, RunSessionCoordinator>()
    private val mutableStates = MutableStateFlow<Map<RunKey, CoordinatedRunState>>(emptyMap())

    val states: StateFlow<Map<RunKey, CoordinatedRunState>> = mutableStates.asStateFlow()

    suspend fun start(request: CoordinatedRunRequest): Boolean {
        val session = sessionFor(request.key)
        val started = session.start(request)
        publishState(request.key, session.state.value)
        return started
    }

    suspend fun resume(
        serverUrl: String,
        threadId: String,
        title: String,
        saved: RunState,
    ): Boolean {
        val key = RunKey(serverUrl, threadId)
        val resumed = sessionFor(key).resume(serverUrl, threadId, title, saved)
        sessionIfPresent(key)?.let { publishState(key, it.state.value) }
        return resumed
    }

    /** Restores every persisted active conversation for this server, rather than only the newest. */
    suspend fun recoverAll(serverUrlOverride: String? = null): Boolean {
        val serverUrl = serverUrlOverride ?: SettingsStore(appContext).read().serverUrl
        val recoverable = cache.loadActiveRuns(serverUrl)
        var recovered = false
        for (run in recoverable) {
            recovered = resume(serverUrl, run.threadId, run.title, run.run) || recovered
        }
        return recovered
    }

    suspend fun cancel(key: RunKey): ThreadSnapshot? {
        val session = sessionIfPresent(key) ?: return null
        val snapshot = session.cancelActive()
        publishState(key, session.state.value)
        return snapshot
    }

    fun abandon(key: RunKey) {
        val session = sessionIfPresent(key) ?: return
        session.abandonActive()
        publishState(key, session.state.value)
    }

    fun abandonAll(serverUrl: String? = null) {
        val entries = synchronized(sessionLock) {
            sessions.filterKeys { serverUrl == null || it.serverUrl == serverUrl }.toList()
        }
        entries.forEach { (key, session) ->
            session.abandonActive()
            publishState(key, session.state.value)
        }
    }

    fun isActive(serverUrl: String, threadId: String): Boolean =
        states.value[RunKey(serverUrl, threadId)]?.run?.active == true

    fun hasActiveRuns(serverUrl: String? = null): Boolean = states.value.any { (key, state) ->
        state.run.active && (serverUrl == null || key.serverUrl == serverUrl)
    }

    fun stateFor(serverUrl: String, threadId: String): CoordinatedRunState? =
        states.value[RunKey(serverUrl, threadId)]

    /** Releases a completed state after the UI has consumed it, including its cached message graph. */
    fun acknowledgeTerminal(key: RunKey, revision: Long) {
        val state = states.value[key] ?: return
        if (state.run.active || state.run.awaitingInput || state.revision != revision) return
        mutableStates.update { it - key }
        synchronized(sessionLock) { sessions.remove(key) }?.dispose()
    }

    private fun sessionFor(key: RunKey): RunSessionCoordinator = synchronized(sessionLock) {
        sessions[key] ?: RunSessionCoordinator(appContext).also { session ->
            sessions[key] = session
            session.attachStateObserver(scope.launch {
                session.state.collect { state ->
                    if (state != null) publishState(key, state)
                }
            })
        }
    }

    private fun sessionIfPresent(key: RunKey): RunSessionCoordinator? = synchronized(sessionLock) { sessions[key] }

    private fun publishState(key: RunKey, state: CoordinatedRunState?) {
        mutableStates.update { current -> if (state == null) current - key else current + (key to state) }
        RunService.synchronize(appContext, states.value)
    }

    companion object {
        @Volatile private var instance: RunCoordinator? = null

        fun get(context: Context): RunCoordinator = instance ?: synchronized(this) {
            instance ?: RunCoordinator(context).also { instance = it }
        }
    }
}

private sealed interface ResumePreflight {
    data class Active(val run: GatewayRunInfo) : ResumePreflight
    data class Terminal(val run: GatewayRunInfo) : ResumePreflight
    data object Missing : ResumePreflight
    data object Unknown : ResumePreflight
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

private class RunSessionCoordinator(context: Context) {
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
    @Volatile private var stateObserverJob: Job? = null

    fun attachStateObserver(job: Job) {
        stateObserverJob?.cancel()
        stateObserverJob = job
    }

    suspend fun start(request: CoordinatedRunRequest): Boolean = operationMutex.withLock {
        if (mutableState.value?.run?.active == true) return@withLock false
        clearPendingChunks()
        val pending = request.initialMessages.firstOrNull {
            it.id == request.clientMessageId && it.role == com.deerflow.mobile.data.MessageRole.User
        }
        val serverMessages = request.initialMessages.filterNot { it.id == request.clientMessageId }
        val initial = CoordinatedRunState(
            serverUrl = request.serverUrl,
            threadId = request.threadId,
            title = request.title,
            run = RunState(
                status = RunStatus.Connecting,
                clientMessageId = request.clientMessageId,
                startedAtEpochMs = System.currentTimeMillis(),
            ),
            serverMessages = serverMessages,
            pendingUserMessage = pending,
            pendingUserIndex = pending?.let { request.initialMessages.indexOf(it) },
            todos = request.initialTodos,
            artifacts = request.initialArtifacts,
        )
        mutableState.value = initial
        persist(initial)
        launchStream(request, resume = null)
        true
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
        val request = CoordinatedRunRequest(
            serverUrl = serverUrl,
            threadId = threadId,
            title = title,
            message = "",
            options = RunOptions(),
            clientMessageId = saved.clientMessageId ?: java.util.UUID.randomUUID().toString(),
            initialMessages = cache.loadMessages(serverUrl, threadId),
        )
        val initial = CoordinatedRunState(
            serverUrl = serverUrl,
            threadId = threadId,
            title = title,
            run = saved.copy(status = RunStatus.Reconnecting).ensureStartedAt(),
            serverMessages = request.initialMessages,
        )
        // Publish a recovery owner before the terminal preflight. A terminal response must still
        // be able to clear the Room row when this process has no prior in-memory coordinator.
        clearPendingChunks()
        mutableState.value = initial
        val resumable = when (val preflight = preflightRun(api, threadId, saved)) {
            is ResumePreflight.Active -> (
                resumableRun(saved, preflight.run.runId)?.copy(
                    status = RunStatus.Reconnecting,
                    gatewayStatus = preflight.run.status,
                ) ?: saved.copy(
                    status = RunStatus.Reconnecting,
                    runId = preflight.run.runId,
                    lastEventId = null,
                    gatewayStatus = preflight.run.status,
                )
            ).ensureStartedAt()
            is ResumePreflight.Terminal -> {
                finishTerminal(
                    api = api,
                    request = request,
                    current = initial,
                    gatewayStatus = preflight.run.status,
                    stopReason = preflight.run.stopReason,
                )
                return@withLock false
            }
            ResumePreflight.Missing -> {
                finishMissingRun(api, request, initial)
                return@withLock false
            }
            ResumePreflight.Unknown -> saved.copy(status = RunStatus.Reconnecting).ensureStartedAt()
        }

        val reconnecting = CoordinatedRunState(
            serverUrl = serverUrl,
            threadId = threadId,
            title = title,
            run = resumable,
            serverMessages = request.initialMessages,
        )
        mutableState.value = reconnecting
        persist(reconnecting)
        launchStream(request, resumable)
        true
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
    }

    fun dispose() {
        clearPendingChunks()
        activeApi?.cancelActiveStream()
        streamJob?.cancel()
        stateObserverJob?.cancel()
        stateObserverJob = null
        scope.cancel()
    }

    fun isActive(serverUrl: String, threadId: String): Boolean =
        mutableState.value?.let { it.serverUrl == serverUrl && it.threadId == threadId && it.run.active } == true

    private fun launchStream(request: CoordinatedRunRequest, resume: RunState?) {
        val api = DeerFlowApi(request.serverUrl, WebViewSessionCookieStore())
        activeApi = api
        streamJob = scope.launch {
            val owningJob = coroutineContext[Job]
            try {
                var resumeState = resume
                while (true) {
                    when (val preflight = resumeState?.let { preflightRun(api, request.threadId, it) }) {
                        is ResumePreflight.Active -> {
                            val existing = resumeState ?: return@launch
                            val activeRun = resumableRun(existing, preflight.run.runId)?.copy(
                                status = RunStatus.Reconnecting,
                                gatewayStatus = preflight.run.status,
                            ) ?: existing.copy(
                                status = RunStatus.Reconnecting,
                                runId = preflight.run.runId,
                                lastEventId = null,
                                gatewayStatus = preflight.run.status,
                            )
                            resumeState = retainReconnecting(request, activeRun)
                        }
                        is ResumePreflight.Terminal -> {
                            val current = currentRunState(request) ?: return@launch
                            finishTerminal(
                                api = api,
                                request = request,
                                current = current,
                                gatewayStatus = preflight.run.status,
                                stopReason = preflight.run.stopReason,
                            )
                            return@launch
                        }
                        ResumePreflight.Missing -> {
                            val current = currentRunState(request) ?: return@launch
                            finishMissingRun(api, request, current)
                            return@launch
                        }
                        ResumePreflight.Unknown,
                        null -> Unit
                    }

                    var awaitingHumanInput = false
                    val result = try {
                        api.streamMessage(
                            threadId = request.threadId,
                            message = request.message,
                            options = request.options,
                            clientMessageId = request.clientMessageId,
                            files = request.files,
                            resume = resumeState,
                            humanInputResponse = request.humanInputResponse,
                            regenerate = request.regenerate,
                            shouldStop = { awaitingHumanInput },
                            onUpdate = { update ->
                                awaitingHumanInput = applyStreamUpdate(request, update) || awaitingHumanInput
                            },
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        flushPendingChunks(request.serverUrl, request.threadId)
                        val current = currentRunState(request) ?: return@launch
                        resumeState = retainReconnecting(
                            request,
                            current.run.copy(
                                status = RunStatus.Reconnecting,
                                reconnectAttempt = current.run.reconnectAttempt + 1,
                            ),
                        )
                        delay(reconnectDelay(resumeState?.reconnectAttempt ?: 1))
                        continue
                    }

                    flushPendingChunks(request.serverUrl, request.threadId)
                    val current = currentRunState(request) ?: return@launch
                    when (result) {
                        is StreamResult.AwaitingHumanInput -> {
                            finishAwaitingHumanInput(current, result)
                            return@launch
                        }
                        is StreamResult.TerminalEnd -> {
                            val serverRun = result.runId?.let { runId ->
                                try {
                                    api.getRun(request.threadId, runId)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            val gatewayStatus = serverRun?.status?.takeIf { it.terminal }
                                ?: result.gatewayStatus.takeIf { it.terminal }
                                ?: current.error?.let { GatewayRunStatus.Error }
                                ?: GatewayRunStatus.Unknown
                            finishTerminal(
                                api = api,
                                request = request,
                                current = current,
                                gatewayStatus = gatewayStatus,
                                stopReason = serverRun?.stopReason,
                            )
                            return@launch
                        }
                        is StreamResult.RetryableDisconnect -> {
                            val reconnecting = current.run.copy(
                                status = RunStatus.Reconnecting,
                                runId = result.runId ?: current.run.runId,
                                lastEventId = result.lastEventId ?: current.run.lastEventId,
                                reconnectAttempt = maxOf(current.run.reconnectAttempt, result.reconnectAttempt, 1),
                            )
                            resumeState = retainReconnecting(request, reconnecting)
                            delay(reconnectDelay(resumeState?.reconnectAttempt ?: 1))
                        }
                        is StreamResult.HttpFailure -> {
                            if (result.statusCode == 404) {
                                finishMissingRun(api, request, current)
                                return@launch
                            }
                            if (!isRetryableStreamHttpFailure(result.statusCode)) {
                                finishRejectedStream(current, result.message)
                                return@launch
                            }
                            val reconnecting = current.run.copy(
                                status = RunStatus.Reconnecting,
                                runId = result.runId ?: current.run.runId,
                                lastEventId = result.lastEventId ?: current.run.lastEventId,
                                reconnectAttempt = current.run.reconnectAttempt + 1,
                            )
                            // A 409 can be a different worker owning the stream. Its run status is
                            // polled before the next join rather than being treated as terminal.
                            resumeState = retainReconnecting(request, reconnecting)
                            delay(reconnectDelay(resumeState?.reconnectAttempt ?: 1))
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Explicit stop or server change owns the visible terminal state.
            } finally {
                if (streamJob === owningJob) streamJob = null
                if (activeApi === api) activeApi = null
            }
        }
    }

    private fun currentRunState(request: CoordinatedRunRequest): CoordinatedRunState? =
        mutableState.value?.takeIf {
            it.serverUrl == request.serverUrl && it.threadId == request.threadId
        }

    private suspend fun preflightRun(
        api: DeerFlowApi,
        threadId: String,
        saved: RunState,
    ): ResumePreflight {
        val knownRunId = saved.runId
        if (!knownRunId.isNullOrBlank()) {
            val run = try {
                api.getRun(threadId, knownRunId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ApiException) {
                return if (error.statusCode == 404) ResumePreflight.Missing else ResumePreflight.Unknown
            } catch (_: Exception) {
                return ResumePreflight.Unknown
            }
            return run.toResumePreflight()
        }

        val latest = try {
            api.latestRun(threadId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return ResumePreflight.Unknown
        return latest.toResumePreflight()
    }

    private fun GatewayRunInfo.toResumePreflight(): ResumePreflight = when {
        status.active -> ResumePreflight.Active(this)
        status.terminal -> ResumePreflight.Terminal(this)
        else -> ResumePreflight.Unknown
    }

    private suspend fun retainReconnecting(
        request: CoordinatedRunRequest,
        run: RunState,
    ): RunState? {
        val current = currentRunState(request) ?: return null
        val retained = run.copy(
            status = RunStatus.Reconnecting,
            clientMessageId = run.clientMessageId ?: current.run.clientMessageId,
            startedAtEpochMs = run.startedAtEpochMs ?: current.run.startedAtEpochMs,
        ).ensureStartedAt()
        val next = current.copy(run = retained, revision = current.revision + 1)
        persistAndPublish(next)
        return retained
    }

    private suspend fun finishTerminal(
        api: DeerFlowApi,
        request: CoordinatedRunRequest,
        current: CoordinatedRunState,
        gatewayStatus: GatewayRunStatus,
        stopReason: String?,
    ) {
        val snapshot = try {
            awaitTerminalSnapshot(api, request, current)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val terminalError = terminalErrorMessage(gatewayStatus, stopReason, current.error)
        val completed = snapshot?.let {
            completeWithSnapshot(current, it, gatewayStatus, terminalError)
        } ?: completeWithoutSnapshot(current, gatewayStatus, terminalError)
        logUnconfirmedPending(current, completed)
        persistAndPublish(completed, clearRun = true)
    }

    private suspend fun finishMissingRun(
        api: DeerFlowApi,
        request: CoordinatedRunRequest,
        current: CoordinatedRunState,
    ) {
        val snapshot = try {
            api.threadState(request.threadId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val completed = snapshot?.let {
            completeWithSnapshot(current, it, GatewayRunStatus.Unknown, null)
        } ?: completeWithoutSnapshot(current, GatewayRunStatus.Unknown, null)
        logUnconfirmedPending(current, completed)
        persistAndPublish(completed, clearRun = true)
    }

    private suspend fun finishRejectedStream(current: CoordinatedRunState, message: String) {
        val completed = completeWithoutSnapshot(current, GatewayRunStatus.Error, message)
        logUnconfirmedPending(current, completed)
        persistAndPublish(completed, clearRun = true)
    }

    private suspend fun finishAwaitingHumanInput(
        current: CoordinatedRunState,
        result: StreamResult.AwaitingHumanInput,
    ) {
        persistAndPublish(
            awaitHumanInput(
                current = current,
                runId = result.runId,
                lastEventId = result.lastEventId,
            ),
        )
    }

    private fun terminalErrorMessage(
        gatewayStatus: GatewayRunStatus,
        stopReason: String?,
        streamedError: String?,
    ): String? = when (gatewayStatus) {
        GatewayRunStatus.Error -> stopReason ?: streamedError ?: "The run failed."
        GatewayRunStatus.Timeout -> stopReason ?: streamedError ?: "The run timed out."
        GatewayRunStatus.Interrupted -> stopReason ?: streamedError ?: "The run was interrupted."
        else -> null
    }

    private fun reconnectDelay(attempt: Int): Long =
        RECONNECT_DELAY_MS * attempt.coerceIn(1, MAX_RECONNECT_DELAY_MULTIPLIER)

    private fun applyStreamUpdate(request: CoordinatedRunRequest, update: StreamUpdate): Boolean {
        if (update is StreamUpdate.MessageChunk) {
            val next = queueMessageChunk(request, update.value)
            if (next != null && hasOpenHumanInputRequest(next.messages)) {
                flushPendingChunks(request.serverUrl, request.threadId)
                return true
            }
            return false
        }

        flushPendingChunks(request.serverUrl, request.threadId)
        val current = mutableState.value ?: return false
        if (current.serverUrl != request.serverUrl || current.threadId != request.threadId) return false
        val next = reduceRunState(current, update)
        // `end` and SSE error frames are transport observations. The stream owner clears the
        // active Room row only after it receives TerminalEnd or a terminal run preflight.
        mutableState.value = next
        scope.launch { persist(next) }
        return hasOpenHumanInputRequest(next.messages)
    }

    private fun queueMessageChunk(request: CoordinatedRunRequest, chunk: ChatMessage): CoordinatedRunState? =
        synchronized(chunkUpdateLock) {
            val current = pendingChunks.peek()
                ?.takeIf { it.serverUrl == request.serverUrl && it.threadId == request.threadId }
                ?: mutableState.value
                ?: return@synchronized null
            if (current.serverUrl != request.serverUrl || current.threadId != request.threadId) return@synchronized null

            val next = pendingChunks.append(current, chunk)
            if (chunkFlushJob == null) {
                chunkFlushJob = scope.launch {
                    delay(CHUNK_UI_PUBLISH_INTERVAL_MS)
                    flushPendingChunks(request.serverUrl, request.threadId, cancelScheduledJob = false)
                }
            }
            next
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

    companion object {
        private const val CHUNK_UI_PUBLISH_INTERVAL_MS = 80L
        private const val TERMINAL_STATE_INITIAL_DELAY_MS = 1_000L
        private val TERMINAL_STATE_RETRY_DELAYS_MS = longArrayOf(500L, 1_000L, 2_000L)
        private const val RECONNECT_DELAY_MS = 700L
        private const val MAX_RECONNECT_DELAY_MULTIPLIER = 8
    }
}

internal fun completeWithSnapshot(
    current: CoordinatedRunState,
    snapshot: ThreadSnapshot,
    gatewayStatus: GatewayRunStatus = GatewayRunStatus.Unknown,
    terminalError: String? = null,
): CoordinatedRunState {
    val serverMessages = mergeStreamSnapshot(current.serverMessages, snapshot)
        .map { it.copy(isStreaming = false) }
    val confirmed = current.pendingUserMessage?.let { pending ->
        serverMessages.any { confirmsPendingUserMessage(pending, it) }
    } != false
    return current.copy(
        title = if (snapshot.hasTitle) snapshot.title else current.title,
        run = RunState(gatewayStatus = gatewayStatus),
        serverMessages = serverMessages,
        pendingUserMessage = current.pendingUserMessage?.takeIf { !confirmed },
        pendingUserIndex = current.pendingUserIndex.takeIf { !confirmed },
        todos = if (snapshot.hasTodos) snapshot.todos else current.todos,
        artifacts = if (snapshot.hasArtifacts) snapshot.artifacts else current.artifacts,
        error = terminalError,
        revision = current.revision + 1,
    )
}

internal fun completeWithoutSnapshot(
    current: CoordinatedRunState,
    gatewayStatus: GatewayRunStatus,
    terminalError: String?,
): CoordinatedRunState = current.copy(
    run = RunState(gatewayStatus = gatewayStatus),
    serverMessages = current.serverMessages.map { it.copy(isStreaming = false) },
    error = terminalError,
    revision = current.revision + 1,
)

/** Persist the acknowledged prompt so reopening the thread cannot lose the clarification card. */
internal fun awaitHumanInput(
    current: CoordinatedRunState,
    runId: String?,
    lastEventId: String?,
): CoordinatedRunState = current.copy(
    run = current.run.copy(
        status = RunStatus.AwaitingInput,
        runId = runId ?: current.run.runId,
        lastEventId = lastEventId ?: current.run.lastEventId,
    ),
    serverMessages = current.messages.map { it.copy(isStreaming = false) },
    pendingUserMessage = null,
    pendingUserIndex = null,
    revision = current.revision + 1,
)

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

/** Client errors are stable request failures; only contention, throttling, or server errors retry. */
internal fun isRetryableStreamHttpFailure(statusCode: Int): Boolean =
    statusCode == 408 || statusCode == 409 || statusCode == 425 || statusCode == 429 || statusCode >= 500

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
            run = current.run.copy(
                status = RunStatus.Reconnecting,
                reconnectAttempt = maxOf(current.run.reconnectAttempt, update.attempt),
            ),
            revision = revision,
        )
        is StreamUpdate.MessageChunk -> {
            val toolName = update.value.blocks
                .filterIsInstance<com.deerflow.mobile.data.MessageBlock.ToolCall>()
                .lastOrNull()
                ?.name
                ?.takeIf { it.isNotBlank() && it != "task" }
            current.copy(
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
                latestToolName = toolName ?: current.latestToolName,
                revision = revision,
            )
        }
        is StreamUpdate.Patch -> {
            val serverMessages = mergeStreamPatch(current.serverMessages, update.value)
            val pending = current.pendingUserMessage?.takeUnless { pending ->
                serverMessages.any { server -> confirmsPendingUserMessage(pending, server) }
            }
            val toolName = serverMessages
                .asReversed()
                .flatMap { it.blocks }
                .filterIsInstance<com.deerflow.mobile.data.MessageBlock.ToolCall>()
                .firstOrNull { it.name.isNotBlank() && it.name != "task" }
                ?.name
            current.copy(
                title = update.value.title ?: current.title,
                serverMessages = serverMessages,
                pendingUserMessage = pending,
                pendingUserIndex = current.pendingUserIndex.takeIf { pending != null },
                todos = update.value.todos ?: current.todos,
                artifacts = mergeArtifacts(current.artifacts, update.value.artifacts),
                latestToolName = toolName ?: current.latestToolName,
                revision = revision,
            )
        }
        is StreamUpdate.SubagentProgress -> {
            val toolName = update.step?.toolName
                ?: update.step?.toolCalls?.firstOrNull()
            current.copy(
                serverMessages = applySubagentProgress(current.serverMessages, update),
                latestToolName = toolName ?: current.latestToolName,
                revision = revision,
            )
        }
        is StreamUpdate.RunNotice -> current.copy(
            runNotice = update,
            revision = revision,
        )
        is StreamUpdate.Failure -> current.copy(
            run = current.run.copy(status = RunStatus.Reconnecting),
            error = update.message,
            revision = revision,
        )
        StreamUpdate.Finished -> current.copy(
            serverMessages = current.serverMessages.map { it.copy(isStreaming = false) },
            revision = revision,
        )
    }
}

private fun mergeArtifacts(current: List<String>, patch: List<String>?): List<String> =
    if (patch == null) current else (current + patch).distinct()
