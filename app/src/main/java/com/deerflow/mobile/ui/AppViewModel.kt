package com.deerflow.mobile.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deerflow.mobile.ConversationShortcuts
import com.deerflow.mobile.R
import com.deerflow.mobile.data.AgentInfo
import com.deerflow.mobile.data.AgentRunInfo
import com.deerflow.mobile.data.AssistantTurn
import com.deerflow.mobile.data.ApiException
import com.deerflow.mobile.data.ArtifactDownloadLimits
import com.deerflow.mobile.data.ArtifactProbe
import com.deerflow.mobile.data.AttachmentStatus
import com.deerflow.mobile.data.BrowserInput
import com.deerflow.mobile.data.BrowserLiveConnection
import com.deerflow.mobile.data.BrowserLiveEvent
import com.deerflow.mobile.data.BrowserTab
import com.deerflow.mobile.data.BrowserViewSnapshot
import com.deerflow.mobile.data.ChatMessage
import com.deerflow.mobile.data.ChannelConnectResult
import com.deerflow.mobile.data.ChannelProviderInfo
import com.deerflow.mobile.data.ChannelProviders
import com.deerflow.mobile.data.CacheRetentionPolicy
import com.deerflow.mobile.data.CacheStats
import com.deerflow.mobile.data.ComposerState
import com.deerflow.mobile.data.ConversationExportFormat
import com.deerflow.mobile.data.DeerFlowApi
import com.deerflow.mobile.data.GatewayRunStatus
import com.deerflow.mobile.data.DeerFlowUser
import com.deerflow.mobile.data.MessageAttachment
import com.deerflow.mobile.data.MessageBlock
import com.deerflow.mobile.data.MessageRole
import com.deerflow.mobile.data.MemoryData
import com.deerflow.mobile.data.MemoryFact
import com.deerflow.mobile.data.McpConfig
import com.deerflow.mobile.data.McpToolInfo
import com.deerflow.mobile.data.LEAD_AGENT_ID
import com.deerflow.mobile.data.LanguagePreference
import com.deerflow.mobile.data.LarkIntegrationStatus
import com.deerflow.mobile.data.LarkVerification
import com.deerflow.mobile.data.LarkVerificationKind
import com.deerflow.mobile.data.HumanInputRequest
import com.deerflow.mobile.data.HumanInputResponse
import com.deerflow.mobile.data.PendingAttachment
import com.deerflow.mobile.data.RunMode
import com.deerflow.mobile.data.RunDetails
import com.deerflow.mobile.data.RunEventRecord
import com.deerflow.mobile.data.RunRepository
import com.deerflow.mobile.data.RunState
import com.deerflow.mobile.data.RunStatus
import com.deerflow.mobile.data.ScheduledTaskInfo
import com.deerflow.mobile.data.ScheduledTaskRunInfo
import com.deerflow.mobile.data.SettingsStore
import com.deerflow.mobile.data.SsoProvider
import com.deerflow.mobile.data.TaskSchedule
import com.deerflow.mobile.data.ThemePreference
import com.deerflow.mobile.data.ThreadRepository
import com.deerflow.mobile.data.ThreadSummary
import com.deerflow.mobile.data.UploadSource
import com.deerflow.mobile.data.WebViewSessionCookieStore
import com.deerflow.mobile.data.WorkspaceCache
import com.deerflow.mobile.data.WorkspaceCapabilities
import com.deerflow.mobile.data.WorkspaceChanges
import com.deerflow.mobile.data.WorkspaceRepository
import com.deerflow.mobile.data.assistantTurnForMessage
import com.deerflow.mobile.data.isLatestAssistantTurn
import com.deerflow.mobile.data.normalizeServerUrl
import com.deerflow.mobile.data.normalizeArtifactDownloadLimits
import com.deerflow.mobile.data.resolveAgentSelection
import com.deerflow.mobile.data.stripUploadedFilesTag
import com.deerflow.mobile.run.CoordinatedRunRequest
import com.deerflow.mobile.run.CoordinatedRunState
import com.deerflow.mobile.run.RunCoordinator
import com.deerflow.mobile.run.RunKey
import com.deerflow.mobile.run.RunProgress
import com.deerflow.mobile.run.RunService
import java.io.IOException
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val NEW_DRAFT_KEY = "__new__"
private const val ARTIFACT_PROGRESS_UPDATE_BYTES = 128L * 1024L

data class InputPolishUndo(
    val originalText: String,
    val rewrittenText: String,
)

data class AppUiState(
    val serverUrl: String,
    val user: DeerFlowUser? = null,
    val route: AppRoute = AppRoute.Workspace,
    val conversationPageTarget: ConversationPageTarget = ConversationPageTarget.Workspace,
    /** Controls the workspace shortcut row independently from navigation transitions. */
    val showQuickCapabilities: Boolean = true,
    val checkingSession: Boolean = true,
    val loginBusy: Boolean = false,
    val loadingSsoProviders: Boolean = false,
    val ssoProviders: List<SsoProvider> = emptyList(),
    val ssoLoginProvider: SsoProvider? = null,
    val checkingSsoSession: Boolean = false,
    val loadingThreads: Boolean = false,
    val loadingChat: Boolean = false,
    val loadingCapabilities: Boolean = false,
    val loadingMcpConfig: Boolean = false,
    val loadingMcpTools: Boolean = false,
    val loadingChannels: Boolean = false,
    val loadingLarkIntegration: Boolean = false,
    val larkIntegrationBusy: Boolean = false,
    val loadingTasks: Boolean = false,
    val loadingTaskRuns: Boolean = false,
    val loadingAgentRuns: Boolean = false,
    val loadingMemory: Boolean = false,
    val workspaceMutationBusy: Boolean = false,
    val memoryMutationBusy: Boolean = false,
    val messageActionBusy: Boolean = false,
    val exportBusy: Boolean = false,
    val offline: Boolean = false,
    val threads: List<ThreadSummary> = emptyList(),
    val activeRunThreadIds: Set<String> = emptySet(),
    val selectedThread: ThreadSummary? = null,
    val messages: List<ChatMessage> = emptyList(),
    val todos: List<com.deerflow.mobile.data.TodoItem> = emptyList(),
    val artifacts: List<String> = emptyList(),
    val runNotice: com.deerflow.mobile.data.StreamUpdate.RunNotice? = null,
    val loadingRunDetails: Boolean = false,
    val runDetailsThreadId: String? = null,
    val conversationRuns: List<RunDetails> = emptyList(),
    val selectedRunDetailsId: String? = null,
    val runEvents: List<RunEventRecord> = emptyList(),
    val workspaceChanges: WorkspaceChanges? = null,
    val runDetailsError: String? = null,
    val artifactBusy: Boolean = false,
    val artifactSession: ArtifactSession? = null,
    val browser: BrowserUiState = BrowserUiState(),
    val composer: ComposerState = ComposerState(),
    val inputPolishing: Boolean = false,
    val inputPolishUndo: InputPolishUndo? = null,
    /** Storage follows the conversation, while this key belongs to the editor session. */
    val draftStorageKey: String = NEW_DRAFT_KEY,
    val draftSessionKey: String = "new-draft",
    val composerResetToken: Long = 0,
    val run: RunState = RunState(),
    val capabilities: WorkspaceCapabilities = WorkspaceCapabilities(),
    val mcpConfig: McpConfig? = null,
    val mcpTools: List<McpToolInfo> = emptyList(),
    val channelProviders: ChannelProviders? = null,
    val channelConnect: ChannelConnectResult? = null,
    val larkIntegration: LarkIntegrationStatus? = null,
    val larkVerification: LarkVerification? = null,
    val larkIntegrationError: String? = null,
    val defaultAgentId: String = LEAD_AGENT_ID,
    val tasks: List<ScheduledTaskInfo> = emptyList(),
    val taskRunsTaskId: String? = null,
    val taskRuns: List<ScheduledTaskRunInfo> = emptyList(),
    val agentRunsAgentId: String? = null,
    val agentRuns: List<AgentRunInfo> = emptyList(),
    val agentRunsError: String? = null,
    val memory: MemoryData? = null,
    val theme: ThemePreference = ThemePreference.System,
    val language: LanguagePreference = LanguagePreference.System,
    val notifyOnRunCompletion: Boolean = true,
    val cacheRetentionPolicy: CacheRetentionPolicy = CacheRetentionPolicy.KeepUntilCleared,
    val artifactDownloadLimits: ArtifactDownloadLimits = ArtifactDownloadLimits(),
    val cacheStats: CacheStats = CacheStats(),
    val loadingCacheStats: Boolean = false,
    val clearingCache: Boolean = false,
    val modelUnavailableError: String? = null,
    val error: String? = null,
    val notice: String? = null,
) {
    val inConversation: Boolean get() = selectedThread != null
    val canUndoInputPolish: Boolean
        get() = !inputPolishing && inputPolishUndo?.rewrittenText == composer.text
}

enum class BrowserLiveStatus {
    Idle,
    Connecting,
    Live,
    Error,
}

/** Ephemeral Browser Live state. Frames are deliberately never persisted with a thread. */
data class BrowserUiState(
    val visible: Boolean = false,
    val threadId: String? = null,
    val preview: BrowserViewSnapshot? = null,
    val frameBase64: String? = null,
    val url: String = "",
    val tabs: List<BrowserTab> = emptyList(),
    val liveControlEnabled: Boolean = false,
    val status: BrowserLiveStatus = BrowserLiveStatus.Idle,
    val error: String? = null,
)

internal fun browserLiveErrorMessageResource(event: BrowserLiveEvent): Int? = when (event) {
    is BrowserLiveEvent.NavigationRejected -> R.string.browser_live_navigation_rejected
    is BrowserLiveEvent.Failure -> R.string.browser_live_connection_failed
    is BrowserLiveEvent.Closed -> when (event.code) {
        4401 -> R.string.browser_live_unauthenticated
        4404, 4501 -> R.string.browser_live_unavailable
        4409 -> R.string.browser_live_in_use
        4429 -> R.string.browser_live_capacity_reached
        else -> R.string.browser_live_disconnected
    }
    BrowserLiveEvent.Opened,
    is BrowserLiveEvent.Frame,
    is BrowserLiveEvent.Url,
    is BrowserLiveEvent.Tabs,
    -> null
}

enum class ArtifactSessionPhase {
    Probing,
    AwaitingConfirm,
    Downloading,
    Ready,
}

data class ArtifactSession(
    val threadId: String,
    val path: String,
    val filename: String,
    val mimeType: String,
    val totalBytes: Long?,
    val maxDownloadBytes: Long,
    val phase: ArtifactSessionPhase,
    val downloadedBytes: Long = 0L,
    val localPath: String? = null,
    val text: String? = null,
    val textTruncated: Boolean = false,
)

/** Kept for preview-only Compose tests of text artifact rendering. */
data class ArtifactPreviewState(
    val path: String,
    val filename: String,
    val mimeType: String,
    val text: String?,
    val localPath: String,
    val textTruncated: Boolean = false,
)

private fun ChannelProviders.replaceChannelProvider(updated: ChannelProviderInfo): ChannelProviders = copy(
    providers = providers.map { provider -> if (provider.provider == updated.provider) updated else provider },
)

private data class PendingRunDestination(
    val serverUrl: String,
    val threadId: String,
)

private data class ConversationSession(
    val serverUrl: String,
    val threadId: String?,
    val draftStorageKey: String,
    val draftSessionKey: String,
    val messages: List<ChatMessage>,
    val todos: List<com.deerflow.mobile.data.TodoItem>,
    val artifacts: List<String>,
) {
    val submissionKey: String
        get() = "$serverUrl|${threadId ?: "draft:$draftSessionKey"}"
}

data class SharedConversationContent(
    val text: String,
    val attachmentUris: List<Uri>,
)

internal fun applyQuickActionToComposer(
    composer: ComposerState,
    capabilities: WorkspaceCapabilities,
    prompt: String,
    skillKeywords: List<String>,
): ComposerState {
    val matchingSkill = capabilities.skills
        .asSequence()
        .filter { it.enabled }
        .firstOrNull { skill ->
            val searchable = "${skill.name} ${skill.description}".lowercase()
            skillKeywords.any { keyword -> searchable.contains(keyword.lowercase()) }
        }
    val selectedSkills = composer.options.enabledSkills.toMutableSet()
    matchingSkill?.let { selectedSkills += it.name }
    return composer.copy(
        text = prompt,
        options = composer.options.copy(enabledSkills = selectedSkills),
    )
}

internal fun isCurrentNewDraftLoad(state: AppUiState, sessionKey: String): Boolean =
    state.selectedThread == null &&
        state.draftStorageKey == NEW_DRAFT_KEY &&
        state.draftSessionKey == sessionKey &&
        state.composer.text.isBlank() &&
        state.composer.attachments.isEmpty()

internal fun isCurrentConversationSession(
    state: AppUiState,
    serverUrl: String,
    threadId: String?,
    draftSessionKey: String,
): Boolean = state.serverUrl == serverUrl &&
    state.selectedThread?.id == threadId &&
    state.draftSessionKey == draftSessionKey

internal fun isCurrentInputPolish(
    state: AppUiState,
    serverUrl: String,
    threadId: String?,
    draftSessionKey: String,
    originalText: String,
): Boolean = isCurrentConversationSession(state, serverUrl, threadId, draftSessionKey) &&
    state.composer.text == originalText

internal fun isModelUnavailableError(message: String?): Boolean {
    val normalized = message?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return false
    val mentionsModel = listOf("model", "llm", "provider", "模型", "提供商").any(normalized::contains)
    if (!mentionsModel) return false
    return listOf(
        "unavailable",
        "not available",
        "not found",
        "not configured",
        "no configured",
        "circuit breaker",
        "out of quota",
        "billing",
        "authentication or access is invalid",
        "credentials",
        "failed after retries",
        "不可用",
        "未配置",
        "不存在",
        "额度",
        "配额",
        "计费",
        "认证",
        "凭据",
        "熔断",
    ).any(normalized::contains)
}

internal fun modelUnavailableMessage(
    runError: String?,
    messages: List<ChatMessage>,
): String? {
    runError?.takeIf(::isModelUnavailableError)?.let { return it }
    if (runError.isNullOrBlank()) return null
    val latestUserIndex = messages.indexOfLast { it.role == MessageRole.User }
    if (latestUserIndex < 0) return null
    // A completed turn can contain intermediate assistant status text. Only the
    // final user-visible assistant reply is evidence for an opaque model failure.
    return messages
        .drop(latestUserIndex + 1)
        .lastOrNull { it.role == MessageRole.Assistant && it.text.isNotBlank() }
        ?.text
        ?.takeIf(::isModelUnavailableError)
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsStore(application)
    private val cookieStore = WebViewSessionCookieStore()
    private val api = DeerFlowApi(UNCONFIGURED_API_ORIGIN, cookieStore)
    private val cache = WorkspaceCache(application)
    private val conversationShortcuts = ConversationShortcuts(application)
    private val submissionGate = MessageSubmissionGate()
    private val threads = ThreadRepository(api, cache, settings)
    private val runs = RunRepository(api)
    private val workspace = WorkspaceRepository(api, cache)
    private val runCoordinator = RunCoordinator.get(application)
    private val mutableState = MutableStateFlow(
        AppUiState(
            serverUrl = SettingsStore.DEFAULT_SERVER_URL,
            language = currentLanguagePreference(),
        ),
    )
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    private var draftJob: Job? = null
    private var inputPolishJob: Job? = null
    private var attachmentJob: Job? = null
    private var threadLoadJob: Job? = null
    private var artifactDownloadJob: Job? = null
    private var browserConnection: BrowserLiveConnection? = null
    private var browserConnectionId = 0L
    @Volatile private var artifactOperationId = 0L
    private var pendingRunDestination: PendingRunDestination? = null
    private var pendingNewConversation = false
    private var pendingSharedConversation: SharedConversationContent? = null
    private var runRecoveryAttemptedForServer: String? = null
    private var runDetailsRequestId = 0L
    private var inputPolishRequestId = 0L

    init {
        observeCoordinatedRun()
        viewModelScope.launch {
            val saved = settings.read()
            val defaultAgentId = settings.defaultAgent(saved.serverUrl)
            val configuredServerUrl = saved.serverUrl.takeIf { it.isNotBlank() }
            configuredServerUrl?.let(api::updateServerUrl)
            mutableState.update {
                it.copy(
                    serverUrl = configuredServerUrl?.let { api.serverUrl }.orEmpty(),
                    theme = saved.theme,
                    language = currentLanguagePreference(),
                    notifyOnRunCompletion = saved.notifyOnRunCompletion,
                    cacheRetentionPolicy = saved.cacheRetentionPolicy,
                    artifactDownloadLimits = saved.artifactDownloadLimits,
                    defaultAgentId = defaultAgentId,
                    composer = it.composer.copy(
                        options = it.composer.options.copy(assistantId = defaultAgentId),
                    ),
                )
            }
            applyCoordinatedRunStates(runCoordinator.states.value)
            if (configuredServerUrl == null) {
                mutableState.update { it.copy(checkingSession = false, ssoProviders = emptyList()) }
                consumePendingShortcutDestination()
            } else {
                checkSessionNow()
            }
        }
    }

    private fun observeCoordinatedRun() {
        viewModelScope.launch {
            runCoordinator.states.collect { coordinated ->
                applyCoordinatedRunStates(coordinated)
            }
        }
    }

    private fun applyCoordinatedRunStates(coordinated: Map<RunKey, CoordinatedRunState>) {
        val serverUrl = mutableState.value.serverUrl
        mutableState.update { current ->
            current.copy(
                activeRunThreadIds = coordinated
                    .filter { (key, state) -> key.serverUrl == current.serverUrl && state.run.active }
                    .keys
                    .mapTo(mutableSetOf(), RunKey::threadId),
            )
        }
        var terminalStateApplied = false
        coordinated.values
            .filter { it.serverUrl == serverUrl }
            .forEach { state ->
                applyCoordinatedRunState(state)
                if (!state.run.active && !state.run.awaitingInput) {
                    terminalStateApplied = true
                    runCoordinator.acknowledgeTerminal(state.key, state.revision)
                }
            }
        if (terminalStateApplied) refreshThreads()
    }

    private fun applyCoordinatedRunState(coordinated: CoordinatedRunState) {
        mutableState.update { current ->
            val selected = current.selectedThread
            if (current.serverUrl != coordinated.serverUrl) {
                current
            } else {
                val coordinatedTitle = stripUploadedFilesTag(coordinated.title).takeIf(String::isNotBlank)
                val updatedThreads = current.threads.map { thread ->
                    if (thread.id == coordinated.threadId && coordinatedTitle != null) {
                        thread.copy(title = coordinatedTitle)
                    } else {
                        thread
                    }
                }
                val selectedRunError = coordinated.error.takeIf {
                    selected?.id == coordinated.threadId
                }
                val modelUnavailableError = if (selected?.id == coordinated.threadId) {
                    modelUnavailableMessage(selectedRunError, coordinated.messages)
                } else {
                    null
                }
                val retainedModelUnavailableError = when {
                    selected?.id != coordinated.threadId -> current.modelUnavailableError
                    coordinated.run.active -> null
                    modelUnavailableError != null -> modelUnavailableError
                    else -> current.modelUnavailableError
                }
                current.copy(
                    threads = updatedThreads,
                    selectedThread = selected?.takeIf { it.id == coordinated.threadId }?.copy(
                        title = coordinatedTitle ?: selected.title,
                    ) ?: selected,
                    messages = if (selected?.id == coordinated.threadId) coordinated.messages else current.messages,
                    todos = if (selected?.id == coordinated.threadId) coordinated.todos else current.todos,
                    artifacts = if (selected?.id == coordinated.threadId) coordinated.artifacts else current.artifacts,
                    runNotice = if (selected?.id == coordinated.threadId) coordinated.runNotice else current.runNotice,
                    run = if (selected?.id == coordinated.threadId) coordinated.run else current.run,
                    messageActionBusy = if (selected?.id == coordinated.threadId && !coordinated.run.active) false else current.messageActionBusy,
                    modelUnavailableError = retainedModelUnavailableError,
                    error = when {
                        modelUnavailableError != null -> null
                        selectedRunError != null -> selectedRunError
                        else -> current.error
                    },
                )
            }
        }
    }

    fun checkSession() {
        viewModelScope.launch { checkSessionNow() }
    }

    private suspend fun checkSessionNow() {
        if (mutableState.value.serverUrl.isBlank()) {
            mutableState.update { it.copy(checkingSession = false, user = null, ssoProviders = emptyList(), error = null) }
            conversationShortcuts.clear()
            return
        }
        mutableState.update { it.copy(checkingSession = true, error = null) }
        try {
            val user = api.currentUser()
            mutableState.update { it.copy(user = user, checkingSession = false) }
            if (user != null) {
                refreshWorkspace()
                recoverLatestRunIfNeeded()
                consumePendingShortcutDestination()
            } else {
                conversationShortcuts.clear()
                refreshSsoProviders()
            }
        } catch (error: Exception) {
            conversationShortcuts.clear()
            mutableState.update {
                it.copy(
                    checkingSession = false,
                    user = null,
                    error = error.userMessage("Could not reach the DeerFlow server."),
                )
            }
        }
    }

    private fun refreshSsoProviders() {
        if (mutableState.value.loadingSsoProviders) return
        viewModelScope.launch {
            mutableState.update { it.copy(loadingSsoProviders = true) }
            try {
                val providers = api.ssoProviders()
                mutableState.update { it.copy(ssoProviders = providers, loadingSsoProviders = false) }
            } catch (_: Exception) {
                // SSO is optional. A failed provider directory must not block local login.
                mutableState.update { it.copy(ssoProviders = emptyList(), loadingSsoProviders = false) }
            }
        }
    }

    fun beginSsoLogin(provider: SsoProvider) {
        if (provider.id.isBlank()) return
        mutableState.update {
            it.copy(
                ssoLoginProvider = provider,
                checkingSsoSession = false,
                error = null,
            )
        }
    }

    fun ssoLoginUrl(provider: SsoProvider): String = api.ssoLoginUrl(provider.id)

    fun completeSsoLoginIfAvailable() {
        if (mutableState.value.ssoLoginProvider == null || mutableState.value.checkingSsoSession) return
        viewModelScope.launch {
            mutableState.update { it.copy(checkingSsoSession = true) }
            try {
                val user = api.currentUser()
                if (user != null) {
                    mutableState.update {
                        it.copy(
                            user = user,
                            checkingSsoSession = false,
                            ssoLoginProvider = null,
                        )
                    }
                    refreshWorkspace()
                    recoverLatestRunIfNeeded()
                    consumePendingShortcutDestination()
                } else {
                    mutableState.update { it.copy(checkingSsoSession = false) }
                }
            } catch (_: Exception) {
                // A page transition can finish before the Gateway callback sets its session cookie.
                mutableState.update { it.copy(checkingSsoSession = false) }
            }
        }
    }

    fun cancelSsoLogin() {
        mutableState.update { it.copy(ssoLoginProvider = null, checkingSsoSession = false) }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            mutableState.update { it.copy(error = "Enter your email and password.") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(loginBusy = true, error = null) }
            try {
                val user = api.login(email.trim(), password)
                mutableState.update { it.copy(user = user, loginBusy = false) }
                refreshWorkspace()
                recoverLatestRunIfNeeded()
                consumePendingShortcutDestination()
            } catch (error: Exception) {
                mutableState.update { it.copy(loginBusy = false, error = error.userMessage("Sign-in failed.")) }
            }
        }
    }

    fun connectAndLogin(serverUrl: String, email: String, password: String) {
        val normalized = normalizeOrReport(serverUrl) ?: return
        disconnectRun()
        closeBrowser()
        cancelArtifactWork()
        conversationShortcuts.clear()
        api.updateServerUrl(normalized)
        persistSetting { setServerUrl(normalized) }
        mutableState.update { it.copy(serverUrl = normalized, error = null) }
        login(email, password)
    }

    fun saveServerUrl(value: String) {
        val normalized = normalizeOrReport(value) ?: return
        disconnectRun()
        disconnectBrowserLive()
        cancelArtifactWork()
        conversationShortcuts.clear()
        api.updateServerUrl(normalized)
        persistSetting { setServerUrl(normalized) }
        val current = mutableState.value
        mutableState.value = AppUiState(
            serverUrl = normalized,
            checkingSession = true,
            theme = current.theme,
            language = current.language,
            notifyOnRunCompletion = current.notifyOnRunCompletion,
            cacheRetentionPolicy = current.cacheRetentionPolicy,
            artifactDownloadLimits = current.artifactDownloadLimits,
            cacheStats = current.cacheStats,
        )
        checkSession()
    }

    fun openRunDestination(serverUrl: String?, threadId: String?) {
        val normalizedServerUrl = serverUrl?.let { value -> runCatching { normalizeServerUrl(value) }.getOrNull() }
        if (normalizedServerUrl.isNullOrBlank() || threadId.isNullOrBlank()) return
        pendingSharedConversation = null
        pendingNewConversation = false
        pendingRunDestination = PendingRunDestination(normalizedServerUrl, threadId)
        consumePendingShortcutDestination()
    }

    fun openNewConversationShortcut() {
        pendingRunDestination = null
        pendingSharedConversation = null
        pendingNewConversation = true
        consumePendingShortcutDestination()
    }

    fun openSharedConversation(content: SharedConversationContent) {
        if (content.text.isBlank() && content.attachmentUris.isEmpty()) return
        pendingRunDestination = null
        pendingNewConversation = false
        pendingSharedConversation = content
        consumePendingShortcutDestination()
    }

    private fun consumePendingShortcutDestination() {
        val current = mutableState.value
        if (current.checkingSession || current.user == null) return
        pendingSharedConversation?.let { sharedContent ->
            pendingSharedConversation = null
            createThread(sharedContent)
            return
        }
        if (pendingNewConversation) {
            pendingNewConversation = false
            createThread()
            return
        }
        val destination = pendingRunDestination ?: return
        if (current.serverUrl != destination.serverUrl) {
            mutableState.update {
                it.copy(error = "Sign in to ${destination.serverUrl} to open this task.")
            }
            return
        }
        pendingRunDestination = null
        val thread = current.threads.firstOrNull { it.id == destination.threadId }
            ?: ThreadSummary(destination.threadId, "Conversation", "completed", "")
        openThread(thread)
    }

    private suspend fun recoverLatestRunIfNeeded() {
        val serverUrl = api.serverUrl
        if (runRecoveryAttemptedForServer == serverUrl) return
        runRecoveryAttemptedForServer = serverUrl
        runCoordinator.recoverAll(serverUrl)
    }

    fun logout() {
        viewModelScope.launch {
            disconnectRun()
            disconnectBrowserLive()
            cancelArtifactWork()
            conversationShortcuts.clear()
            api.logout()
            val current = mutableState.value
            val updatedCacheStats = if (current.cacheRetentionPolicy == CacheRetentionPolicy.ClearOnSignOut) {
                runCatching {
                    cache.clearAll()
                    cache.stats()
                }.getOrDefault(current.cacheStats)
            } else {
                current.cacheStats
            }
            mutableState.value = AppUiState(
                serverUrl = api.serverUrl,
                checkingSession = false,
                theme = current.theme,
                language = current.language,
                notifyOnRunCompletion = current.notifyOnRunCompletion,
                cacheRetentionPolicy = current.cacheRetentionPolicy,
                artifactDownloadLimits = current.artifactDownloadLimits,
                cacheStats = updatedCacheStats,
            )
            refreshSsoProviders()
        }
    }

    fun refreshWorkspace() {
        refreshThreads()
        refreshCapabilities()
        refreshMcpConfig()
        refreshMcpTools()
        refreshChannels()
        refreshTasks()
    }

    fun refreshThreads() {
        if (mutableState.value.user == null || mutableState.value.loadingThreads) return
        viewModelScope.launch {
            mutableState.update { it.copy(loadingThreads = true, error = null) }
            try {
                val result = threads.threads()
                mutableState.update { current ->
                    val selected = current.selectedThread?.let { active ->
                        result.value.firstOrNull { it.id == active.id } ?: active
                    }
                    current.copy(
                        threads = result.value,
                        selectedThread = selected,
                        loadingThreads = false,
                        offline = result.fromCache,
                    )
                }
                publishConversationShortcuts(result.value)
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(loadingThreads = false, error = error.userMessage("Could not load conversations."))
                }
            }
        }
    }

    private fun publishConversationShortcuts(threads: List<ThreadSummary> = mutableState.value.threads) {
        val current = mutableState.value
        if (current.user == null) conversationShortcuts.clear()
        else conversationShortcuts.publish(current.serverUrl, threads)
    }

    fun refreshCapabilities() {
        if (mutableState.value.user == null) return
        viewModelScope.launch {
            mutableState.update { it.copy(loadingCapabilities = true) }
            val result = try {
                workspace.capabilities()
            } catch (error: Exception) {
                handleAuthenticatedError(error) { state -> state.copy(loadingCapabilities = false) }
                return@launch
            }
            val capabilities = result.value
            val savedDefaultAgentId = settings.defaultAgent(api.serverUrl)
            val savedRunOptions = settings.savedRunOptions(api.serverUrl)
            mutableState.update { current ->
                val agentSelection = resolveAgentSelection(
                    savedDefaultAgentId = savedDefaultAgentId,
                    currentDefaultAgentId = current.defaultAgentId,
                    currentSelectedAgentId = current.composer.options.assistantId,
                    capabilities = capabilities,
                )
                val modelName = current.composer.options.modelName
                    ?: savedRunOptions.modelName?.takeIf { savedModelName ->
                        capabilities.models.any { model -> model.name == savedModelName }
                    }
                    ?: capabilities.models.firstOrNull()?.name
                val availableModes = capabilities.availableRunModes(modelName)
                val requestedMode = if (current.composer.options.modelName == null) {
                    savedRunOptions.mode
                } else {
                    current.composer.options.mode
                }
                val mode = requestedMode.takeIf { it in availableModes }
                    ?: availableModes.first()
                current.copy(
                    capabilities = capabilities,
                    defaultAgentId = agentSelection.defaultAgentId,
                    composer = current.composer.copy(
                        options = current.composer.options.copy(
                            assistantId = agentSelection.selectedAgentId,
                            modelName = modelName,
                            mode = mode,
                            reasoningEffortEnabled = capabilities.supportsReasoningEffort(modelName),
                        ),
                    ),
                    loadingCapabilities = false,
                    offline = current.offline || result.fromCache,
                )
            }
            if (savedDefaultAgentId !in capabilities.agents.map { it.name } + LEAD_AGENT_ID) {
                runCatching { settings.setDefaultAgent(api.serverUrl, LEAD_AGENT_ID) }
            }
        }
    }

    fun refreshMcpConfig() {
        if (mutableState.value.user == null) return
        viewModelScope.launch {
            mutableState.update { it.copy(loadingMcpConfig = true) }
            try {
                val config = workspace.mcpConfig()
                mutableState.update { it.copy(mcpConfig = config, loadingMcpConfig = false) }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        loadingMcpConfig = false,
                        error = error.userMessage("Could not load MCP servers."),
                    )
                }
            }
        }
    }

    fun refreshMcpTools() {
        if (mutableState.value.user == null || mutableState.value.loadingMcpTools) return
        viewModelScope.launch {
            mutableState.update { it.copy(loadingMcpTools = true) }
            try {
                val result = workspace.mcpTools()
                mutableState.update {
                    it.copy(
                        mcpTools = result.value,
                        loadingMcpTools = false,
                        offline = it.offline || result.fromCache,
                    )
                }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        loadingMcpTools = false,
                        error = error.userMessage("Could not load MCP tools."),
                    )
                }
            }
        }
    }

    fun refreshChannels() {
        if (mutableState.value.user == null) return
        viewModelScope.launch {
            mutableState.update { it.copy(loadingChannels = true) }
            try {
                val providers = workspace.channelProviders()
                mutableState.update { it.copy(channelProviders = providers, loadingChannels = false) }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        loadingChannels = false,
                        error = error.userMessage("Could not load Channels."),
                    )
                }
            }
        }
    }

    fun refreshLarkIntegration() {
        if (mutableState.value.user == null || mutableState.value.loadingLarkIntegration) return
        viewModelScope.launch {
            mutableState.update { it.copy(loadingLarkIntegration = true, larkIntegrationError = null) }
            try {
                val status = workspace.larkIntegrationStatus()
                mutableState.update { it.copy(larkIntegration = status, loadingLarkIntegration = false) }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        loadingLarkIntegration = false,
                        larkIntegrationError = error.userMessage("Could not load the Lark integration."),
                    )
                }
            }
        }
    }

    fun installLarkIntegration() {
        if (mutableState.value.user?.role != "admin" || mutableState.value.larkIntegrationBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(larkIntegrationBusy = true, larkIntegrationError = null) }
            try {
                val result = workspace.installLarkIntegration()
                mutableState.update {
                    it.copy(
                        larkIntegration = result.status,
                        larkIntegrationBusy = false,
                        larkVerification = null,
                        notice = result.message.takeIf(String::isNotBlank),
                    )
                }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        larkIntegrationBusy = false,
                        larkIntegrationError = error.userMessage("Could not install the Lark integration."),
                    )
                }
            }
        }
    }

    fun startLarkConfiguration(brand: String) {
        if (mutableState.value.larkIntegrationBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(larkIntegrationBusy = true, larkIntegrationError = null) }
            try {
                val verification = workspace.startLarkConfiguration(brand)
                mutableState.update { it.copy(larkVerification = verification, larkIntegrationBusy = false) }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        larkIntegrationBusy = false,
                        larkIntegrationError = error.userMessage("Could not start Lark setup."),
                    )
                }
            }
        }
    }

    fun completeLarkConfiguration() {
        val verification = mutableState.value.larkVerification
            ?.takeIf { it.kind == LarkVerificationKind.Configuration }
            ?: return
        if (mutableState.value.larkIntegrationBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(larkIntegrationBusy = true, larkIntegrationError = null) }
            try {
                val result = workspace.completeLarkConfiguration(verification)
                mutableState.update {
                    it.copy(
                        larkIntegration = result.status,
                        larkIntegrationBusy = false,
                        larkVerification = null,
                        notice = result.message.takeIf(String::isNotBlank),
                    )
                }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        larkIntegrationBusy = false,
                        larkIntegrationError = error.userMessage("Could not complete Lark setup."),
                    )
                }
            }
        }
    }

    fun startLarkAuthorization() {
        if (mutableState.value.larkIntegrationBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(larkIntegrationBusy = true, larkIntegrationError = null) }
            try {
                val verification = workspace.startLarkAuthorization()
                mutableState.update { it.copy(larkVerification = verification, larkIntegrationBusy = false) }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        larkIntegrationBusy = false,
                        larkIntegrationError = error.userMessage("Could not start Lark authorization."),
                    )
                }
            }
        }
    }

    fun completeLarkAuthorization() {
        val verification = mutableState.value.larkVerification
            ?.takeIf { it.kind == LarkVerificationKind.Authorization }
            ?: return
        if (mutableState.value.larkIntegrationBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(larkIntegrationBusy = true, larkIntegrationError = null) }
            try {
                val result = workspace.completeLarkAuthorization(verification)
                mutableState.update {
                    it.copy(
                        larkIntegration = result.status,
                        larkIntegrationBusy = false,
                        larkVerification = null,
                        notice = result.message.takeIf(String::isNotBlank),
                    )
                }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        larkIntegrationBusy = false,
                        larkIntegrationError = error.userMessage("Could not complete Lark authorization."),
                    )
                }
            }
        }
    }

    fun clearLarkVerification() {
        mutableState.update { it.copy(larkVerification = null, larkIntegrationError = null) }
    }

    fun openRunDetails() {
        val thread = mutableState.value.selectedThread ?: return
        val requestId = ++runDetailsRequestId
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loadingRunDetails = true,
                    runDetailsThreadId = thread.id,
                    runDetailsError = null,
                )
            }
            try {
                val details = runs.details(thread.id)
                val selected = mutableState.value.selectedRunDetailsId
                    ?.takeIf { selectedId -> details.any { it.runId == selectedId } }
                    ?: details.firstOrNull()?.runId
                loadRunDetailsPayload(requestId, thread.id, details, selected)
            } catch (error: Exception) {
                if (isCurrentRunDetailsRequest(requestId, thread.id)) {
                    handleAuthenticatedError(error) {
                        it.copy(
                            loadingRunDetails = false,
                            runDetailsThreadId = thread.id,
                            runDetailsError = error.userMessage("Could not load run details."),
                        )
                    }
                }
            }
        }
    }

    fun selectRunDetails(runId: String) {
        val current = mutableState.value
        val threadId = current.selectedThread?.id ?: return
        if (current.runDetailsThreadId != threadId || current.selectedRunDetailsId == runId) return
        val requestId = ++runDetailsRequestId
        viewModelScope.launch {
            loadRunDetailsPayload(requestId, threadId, current.conversationRuns, runId)
        }
    }

    fun clearRunDetails() {
        runDetailsRequestId += 1
        mutableState.update {
            it.copy(
                loadingRunDetails = false,
                runDetailsThreadId = null,
                conversationRuns = emptyList(),
                selectedRunDetailsId = null,
                runEvents = emptyList(),
                workspaceChanges = null,
                runDetailsError = null,
            )
        }
    }

    private suspend fun loadRunDetailsPayload(
        requestId: Long,
        threadId: String,
        details: List<RunDetails>,
        selectedRunId: String?,
    ) {
        if (selectedRunId == null) {
            if (isCurrentRunDetailsRequest(requestId, threadId)) {
                mutableState.update {
                    it.copy(
                        loadingRunDetails = false,
                        runDetailsThreadId = threadId,
                        conversationRuns = details,
                        selectedRunDetailsId = null,
                        runEvents = emptyList(),
                        workspaceChanges = null,
                    )
                }
            }
            return
        }
        mutableState.update {
            if (it.selectedThread?.id == threadId) {
                it.copy(
                    loadingRunDetails = true,
                    runDetailsThreadId = threadId,
                    conversationRuns = details,
                    selectedRunDetailsId = selectedRunId,
                    runEvents = emptyList(),
                    workspaceChanges = null,
                    runDetailsError = null,
                )
            } else {
                it
            }
        }
        val (events, changes) = coroutineScope {
            val eventsResult = async {
                try {
                    Result.success(runs.events(threadId, selectedRunId))
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
            val changesResult = async {
                try {
                    Result.success(runs.workspaceChanges(threadId, selectedRunId))
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
            eventsResult.await() to changesResult.await()
        }
        if (!isCurrentRunDetailsRequest(requestId, threadId)) return
        val problem = listOfNotNull(events.exceptionOrNull(), changes.exceptionOrNull()).firstOrNull()
        mutableState.update {
            it.copy(
                loadingRunDetails = false,
                runDetailsThreadId = threadId,
                conversationRuns = details,
                selectedRunDetailsId = selectedRunId,
                runEvents = events.getOrDefault(emptyList()),
                workspaceChanges = changes.getOrNull(),
                runDetailsError = (problem as? Exception)?.userMessage("Could not load the complete run audit.")
                    ?: problem?.message?.takeIf(String::isNotBlank),
            )
        }
    }

    private fun isCurrentRunDetailsRequest(requestId: Long, threadId: String): Boolean =
        requestId == runDetailsRequestId && mutableState.value.selectedThread?.id == threadId

    fun refreshTasks() {
        if (mutableState.value.user == null) return
        viewModelScope.launch {
            mutableState.update { it.copy(loadingTasks = true) }
            try {
                val result = workspace.tasks()
                mutableState.update {
                    it.copy(
                        tasks = result.value,
                        loadingTasks = false,
                        offline = it.offline || result.fromCache,
                    )
                }
            } catch (error: Exception) {
                handleAuthenticatedError(error) { it.copy(loadingTasks = false) }
            }
        }
    }

    fun loadTaskRuns(task: ScheduledTaskInfo) {
        val taskId = task.id
        mutableState.update {
            it.copy(
                taskRunsTaskId = taskId,
                taskRuns = emptyList(),
                loadingTaskRuns = true,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val runs = workspace.taskRuns(taskId)
                mutableState.update { current ->
                    if (current.taskRunsTaskId != taskId) current
                    else current.copy(taskRuns = runs, loadingTaskRuns = false)
                }
            } catch (error: Exception) {
                mutableState.update { current ->
                    if (current.taskRunsTaskId != taskId) current
                    else current.copy(
                        loadingTaskRuns = false,
                        error = error.userMessage("Could not load task execution history."),
                    )
                }
            }
        }
    }

    fun openTaskRunConversation(task: ScheduledTaskInfo, run: ScheduledTaskRunInfo) {
        if (run.threadId.isBlank()) return
        val current = mutableState.value
        val thread = current.threads.firstOrNull { it.id == run.threadId }
            ?: ThreadSummary(
                id = run.threadId,
                title = task.title,
                status = run.status,
                updatedAt = run.finishedAt ?: run.startedAt ?: run.createdAt,
            )
        openThread(thread)
    }

    fun loadAgentRuns(agent: AgentInfo) {
        val agentId = agent.name
        mutableState.update {
            it.copy(
                agentRunsAgentId = agentId,
                agentRuns = emptyList(),
                loadingAgentRuns = true,
                agentRunsError = null,
            )
        }
        viewModelScope.launch {
            try {
                val runs = workspace.agentRuns(agentId)
                mutableState.update { current ->
                    if (current.agentRunsAgentId != agentId) current
                    else current.copy(agentRuns = runs, loadingAgentRuns = false)
                }
            } catch (error: Exception) {
                mutableState.update { current ->
                    if (current.agentRunsAgentId != agentId) current
                    else current.copy(
                        loadingAgentRuns = false,
                        agentRunsError = error.userMessage("Could not load agent execution history."),
                    )
                }
            }
        }
    }

    fun openAgentRunConversation(agent: AgentInfo, run: AgentRunInfo) {
        if (run.threadId.isBlank()) return
        selectAgent(agent.name)
        val current = mutableState.value
        val thread = current.threads.firstOrNull { it.id == run.threadId }
            ?: ThreadSummary(
                id = run.threadId,
                title = run.threadTitle?.takeIf(String::isNotBlank) ?: agent.name,
                status = run.status,
                updatedAt = run.updatedAt ?: run.createdAt.orEmpty(),
            )
        openThread(thread)
    }

    fun refreshMemory() {
        if (mutableState.value.user == null || mutableState.value.loadingMemory) return
        viewModelScope.launch {
            mutableState.update { it.copy(loadingMemory = true) }
            try {
                val result = workspace.memory()
                mutableState.update {
                    it.copy(
                        memory = result.value,
                        loadingMemory = false,
                        offline = it.offline || result.fromCache,
                    )
                }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        loadingMemory = false,
                        error = error.userMessage("Could not load memory."),
                    )
                }
            }
        }
    }

    fun openWorkspaceChild(route: AppRoute) {
        require(route.isWorkspaceChild) { "Expected a workspace child route." }
        mutableState.update {
            it.copy(
                route = route,
                error = null,
            )
        }
        if (route == AppRoute.Memory && mutableState.value.memory == null) refreshMemory()
    }

    fun closeWorkspaceChild() {
        mutableState.update { current ->
            if (current.route.isWorkspaceChild) current.copy(route = AppRoute.Workspace, error = null) else current
        }
    }

    private fun newDraftSessionKey(): String = "new-draft-${UUID.randomUUID()}"

    private fun restoreNewDraft(sessionKey: String) {
        threadLoadJob = viewModelScope.launch {
            val draft = threads.loadDraft(NEW_DRAFT_KEY)
            val attachments = runCatching { threads.loadAttachments(NEW_DRAFT_KEY) }.getOrDefault(emptyList())
            mutableState.update { current ->
                if (!isCurrentNewDraftLoad(current, sessionKey)) {
                    current
                } else {
                    current.copy(
                        composer = current.composer.copy(text = draft, attachments = attachments),
                        composerResetToken = current.composerResetToken + 1,
                    )
                }
            }
        }
    }

    private fun cancelNewDraftRestore() {
        val current = mutableState.value
        if (current.selectedThread == null && current.draftStorageKey == NEW_DRAFT_KEY) {
            threadLoadJob?.cancel()
        }
    }

    fun createThread(sharedContent: SharedConversationContent? = null) {
        invalidateInputPolish()
        threadLoadJob?.cancel()
        draftJob?.cancel()
        attachmentJob?.cancel()
        closeBrowser()
        cancelArtifactWork()
        clearRunDetails()
        val sessionKey = newDraftSessionKey()
        val current = mutableState.value
        val assistant = current.defaultAgentId
        val pageTarget = current.workspacePageRoute().asConversationPageTarget()
        val sharedText = sharedContent?.text.orEmpty()
        mutableState.update {
            it.copy(
                conversationPageTarget = pageTarget,
                selectedThread = null,
                messages = emptyList(),
                todos = emptyList(),
                artifacts = emptyList(),
                runNotice = null,
                artifactBusy = false,
                artifactSession = null,
                composer = it.composer.copy(
                    text = sharedText,
                    attachments = emptyList(),
                    options = it.composer.options.copy(assistantId = assistant),
                ),
                loadingChat = false,
                draftStorageKey = NEW_DRAFT_KEY,
                draftSessionKey = sessionKey,
                composerResetToken = it.composerResetToken + 1,
                run = RunState(),
                messageActionBusy = false,
                showQuickCapabilities = false,
                route = AppRoute.Conversation,
                modelUnavailableError = null,
                error = null,
            )
        }
        if (sharedContent == null) {
            draftJob = viewModelScope.launch {
                if (isCurrentNewDraftLoad(mutableState.value, sessionKey)) {
                    threads.saveDraft(NEW_DRAFT_KEY, "")
                }
                if (isCurrentNewDraftLoad(mutableState.value, sessionKey)) {
                    threads.saveAttachments(NEW_DRAFT_KEY, emptyList())
                }
            }
        } else {
            draftJob = viewModelScope.launch {
                threads.saveDraft(NEW_DRAFT_KEY, sharedText)
            }
            if (sharedContent.attachmentUris.isEmpty()) {
                persistAttachmentDraft(NEW_DRAFT_KEY, emptyList())
            } else {
                sharedContent.attachmentUris.forEach(::addAttachment)
            }
        }
    }

    fun openBrowser(browserView: BrowserViewSnapshot? = mutableState.value.messages.latestBrowserView()) {
        connectBrowser(browserView, browserView?.url)
    }

    fun setBrowserLiveControl(enabled: Boolean) {
        val browser = mutableState.value.browser
        if (!browser.visible || browser.liveControlEnabled == enabled) return

        if (enabled) {
            connectBrowser(browser.preview, browser.url)
        } else {
            disconnectBrowserLive()
            mutableState.update { current ->
                if (current.browser.visible && current.browser.threadId == browser.threadId) {
                    current.copy(
                        browser = current.browser.copy(
                            liveControlEnabled = false,
                            status = BrowserLiveStatus.Idle,
                            error = null,
                        ),
                    )
                } else {
                    current
                }
            }
        }
    }

    private fun connectBrowser(browserView: BrowserViewSnapshot?, initialUrl: String?) {
        val current = mutableState.value
        val thread = current.selectedThread ?: return
        if (!current.capabilities.browserControlEnabled) {
            mutableState.update { it.copy(error = getApplication<Application>().getString(R.string.browser_live_unavailable)) }
            return
        }

        val connectionId = replaceBrowserConnection()
        val existingBrowser = current.browser.takeIf { it.visible && it.threadId == thread.id }
        val previewUrl = initialUrl?.takeIf(String::isNotBlank) ?: existingBrowser?.url.orEmpty()
        mutableState.update { state ->
            if (state.selectedThread?.id != thread.id) {
                state
            } else {
                state.copy(
                    browser = BrowserUiState(
                        visible = true,
                        threadId = thread.id,
                        preview = browserView ?: existingBrowser?.preview,
                        frameBase64 = existingBrowser?.frameBase64,
                        url = previewUrl,
                        tabs = existingBrowser?.tabs.orEmpty(),
                        liveControlEnabled = true,
                        status = BrowserLiveStatus.Connecting,
                    ),
                )
            }
        }
        try {
            val connection = api.openBrowserLive(thread.id, previewUrl.takeIf(String::isNotBlank)) { event ->
                viewModelScope.launch { handleBrowserLiveEvent(connectionId, event) }
            }
            if (connectionId == browserConnectionId) {
                browserConnection = connection
            } else {
                connection.close()
            }
        } catch (_: Exception) {
            handleBrowserLiveEvent(
                connectionId,
                BrowserLiveEvent.Failure(getApplication<Application>().getString(R.string.browser_live_connection_failed)),
            )
        }
    }

    fun closeBrowser() {
        disconnectBrowserLive()
        mutableState.update { it.copy(browser = BrowserUiState()) }
    }

    fun sendBrowserInput(input: BrowserInput) {
        val browser = mutableState.value.browser
        if (!browser.visible || !browser.liveControlEnabled || browserConnection?.send(input) == true) return
        mutableState.update { current ->
            if (current.browser.visible) {
                current.copy(browser = current.browser.copy(error = getApplication<Application>().getString(R.string.browser_live_input_failed)))
            } else {
                current
            }
        }
    }

    private fun replaceBrowserConnection(): Long {
        browserConnectionId += 1L
        browserConnection?.close()
        browserConnection = null
        return browserConnectionId
    }

    private fun disconnectBrowserLive() {
        replaceBrowserConnection()
    }

    private fun handleBrowserLiveEvent(connectionId: Long, event: BrowserLiveEvent) {
        if (connectionId != browserConnectionId) return
        if (event is BrowserLiveEvent.Closed || event is BrowserLiveEvent.Failure) browserConnection = null
        mutableState.update { current ->
            val browser = current.browser
            if (!browser.visible || browser.threadId != current.selectedThread?.id) {
                current
            } else {
                val updatedBrowser = when (event) {
                    BrowserLiveEvent.Opened -> browser.copy(status = BrowserLiveStatus.Live, error = null)
                    is BrowserLiveEvent.Frame -> browser.copy(
                        frameBase64 = event.jpegBase64,
                        status = BrowserLiveStatus.Live,
                        error = null,
                    )
                    is BrowserLiveEvent.Url -> browser.copy(url = event.value, status = BrowserLiveStatus.Live, error = null)
                    is BrowserLiveEvent.Tabs -> browser.copy(tabs = event.values, status = BrowserLiveStatus.Live)
                    is BrowserLiveEvent.NavigationRejected -> browser.copy(
                        status = BrowserLiveStatus.Live,
                        error = browserLiveErrorMessage(event),
                    )
                    is BrowserLiveEvent.Closed -> browser.copy(
                        status = BrowserLiveStatus.Error,
                        error = browserLiveErrorMessage(event),
                    )
                    is BrowserLiveEvent.Failure -> browser.copy(
                        status = BrowserLiveStatus.Error,
                        error = browserLiveErrorMessage(event),
                    )
                }
                current.copy(browser = updatedBrowser)
            }
        }
    }

    private fun browserLiveErrorMessage(event: BrowserLiveEvent): String =
        getApplication<Application>().getString(checkNotNull(browserLiveErrorMessageResource(event)))

    fun openThread(thread: ThreadSummary) {
        if (mutableState.value.selectedThread?.id == thread.id && mutableState.value.route == AppRoute.Conversation) return
        invalidateInputPolish()
        threadLoadJob?.cancel()
        closeBrowser()
        cancelArtifactWork()
        clearRunDetails()
        mutableState.update {
            it.copy(
                route = AppRoute.Conversation,
                conversationPageTarget = ConversationPageTarget.Conversation,
                selectedThread = thread,
                messages = emptyList(),
                todos = emptyList(),
                artifacts = emptyList(),
                runNotice = null,
                artifactBusy = false,
                artifactSession = null,
                loadingChat = true,
                draftStorageKey = thread.id,
                draftSessionKey = "thread-draft-${thread.id}-${UUID.randomUUID()}",
                composerResetToken = it.composerResetToken + 1,
                run = RunState(),
                messageActionBusy = false,
                showQuickCapabilities = false,
                modelUnavailableError = null,
                error = null,
            )
        }
        threadLoadJob = viewModelScope.launch {
            val draft = async { threads.loadDraft(thread.id) }
            val attachments = async { runCatching { threads.loadAttachments(thread.id) }.getOrDefault(emptyList()) }
            try {
                val snapshot = threads.snapshot(thread.id)
                val savedDraft = draft.await()
                val savedAttachments = attachments.await()
                mutableState.update { current ->
                    if (!isCurrentThreadLoad(current.selectedThread?.id, thread.id)) {
                        current
                    } else {
                        current.copy(
                            selectedThread = current.selectedThread?.copy(title = snapshot.value.title),
                            messages = snapshot.value.messages,
                            todos = snapshot.value.todos,
                            artifacts = snapshot.value.artifacts,
                            composer = current.composer.copy(text = savedDraft, attachments = savedAttachments),
                            composerResetToken = current.composerResetToken + 1,
                            loadingChat = false,
                            offline = snapshot.fromCache,
                        )
                    }
                }
                if (!isCurrentThreadLoad(mutableState.value.selectedThread?.id, thread.id)) return@launch
                resumeRunIfNeeded(thread.id)
            } catch (error: Exception) {
                val savedDraft = draft.await()
                val savedAttachments = attachments.await()
                mutableState.update {
                    if (!isCurrentThreadLoad(it.selectedThread?.id, thread.id)) {
                        it
                    } else {
                        it.copy(
                            loadingChat = false,
                            composer = it.composer.copy(text = savedDraft, attachments = savedAttachments),
                            composerResetToken = it.composerResetToken + 1,
                            error = error.userMessage("Could not load this conversation."),
                        )
                    }
                }
            }
        }
    }

    fun closeConversation() {
        invalidateInputPolish()
        threadLoadJob?.cancel()
        closeBrowser()
        cancelArtifactWork()
        val current = mutableState.value
        if (current.run.active) {
            mutableState.update {
                it.copy(
                    route = AppRoute.Workspace,
                    conversationPageTarget = ConversationPageTarget.Workspace,
                    showQuickCapabilities = false,
                    artifactBusy = false,
                    artifactSession = null,
                    modelUnavailableError = null,
                    error = null,
                )
            }
            return
        }
        val sessionKey = newDraftSessionKey()
        clearRunDetails()
        mutableState.update {
            it.copy(
                route = AppRoute.Workspace,
                conversationPageTarget = ConversationPageTarget.Workspace,
                selectedThread = null,
                messages = emptyList(),
                todos = emptyList(),
                artifacts = emptyList(),
                runNotice = null,
                artifactBusy = false,
                artifactSession = null,
                composer = it.composer.copy(
                    text = "",
                    attachments = emptyList(),
                    options = it.composer.options.copy(assistantId = it.defaultAgentId),
                ),
                draftStorageKey = NEW_DRAFT_KEY,
                draftSessionKey = sessionKey,
                composerResetToken = it.composerResetToken + 1,
                run = RunState(),
                messageActionBusy = false,
                showQuickCapabilities = current.selectedThread != null,
                modelUnavailableError = null,
                error = null,
            )
        }
        restoreNewDraft(sessionKey)
    }

    fun deleteThread(thread: ThreadSummary) {
        val current = mutableState.value
        if (runCoordinator.isActive(api.serverUrl, thread.id)) {
            mutableState.update { it.copy(error = "Stop the active run before deleting this conversation.") }
            return
        }
        if (current.selectedThread?.id == thread.id) {
            closeBrowser()
            cancelArtifactWork()
        }
        viewModelScope.launch {
            try {
                threads.delete(thread.id)
                mutableState.update {
                    it.copy(
                        threads = it.threads.filterNot { item -> item.id == thread.id },
                        selectedThread = it.selectedThread?.takeUnless { selected -> selected.id == thread.id },
                        messages = if (it.selectedThread?.id == thread.id) emptyList() else it.messages,
                        todos = if (it.selectedThread?.id == thread.id) emptyList() else it.todos,
                        artifacts = if (it.selectedThread?.id == thread.id) emptyList() else it.artifacts,
                        artifactBusy = if (it.selectedThread?.id == thread.id) false else it.artifactBusy,
                        artifactSession = if (it.selectedThread?.id == thread.id) null else it.artifactSession,
                        route = if (it.selectedThread?.id == thread.id) AppRoute.Workspace else it.route,
                        showQuickCapabilities = if (it.selectedThread?.id == thread.id) true else it.showQuickCapabilities,
                    )
                }
                publishConversationShortcuts()
            } catch (error: Exception) {
                handleAuthenticatedError(error) { it.copy(error = error.userMessage("Could not delete this conversation.")) }
            }
        }
    }

    fun renameThread(thread: ThreadSummary, title: String) {
        val value = title.trim()
        if (value.isBlank()) return
        viewModelScope.launch {
            try {
                threads.rename(thread.id, value)
                mutableState.update {
                    it.copy(
                        threads = it.threads.map { item -> if (item.id == thread.id) item.copy(title = value) else item },
                        selectedThread = it.selectedThread?.let { selected -> if (selected.id == thread.id) selected.copy(title = value) else selected },
                    )
                }
                publishConversationShortcuts()
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.userMessage("Could not rename this conversation.")) }
            }
        }
    }

    fun toggleThreadPinned(thread: ThreadSummary) {
        viewModelScope.launch {
            threads.setPinned(thread, !thread.isPinned)
            mutableState.update {
                val updated = it.threads.map { item -> if (item.id == thread.id) item.copy(isPinned = !item.isPinned) else item }
                    .sortedWith(compareByDescending<ThreadSummary> { item -> item.isPinned }.thenByDescending { item -> item.updatedAt })
                it.copy(threads = updated)
            }
            publishConversationShortcuts()
        }
    }

    fun updateDraft(value: String) {
        cancelNewDraftRestore()
        val current = mutableState.value
        if (current.composer.text == value) return
        val clearUndo = current.inputPolishUndo?.rewrittenText != value
        invalidateInputPolish(clearUndo = clearUndo)
        val key = current.draftStorageKey
        mutableState.update { it.copy(composer = it.composer.copy(text = value)) }
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            threads.saveDraft(key, value)
        }
    }

    fun addAttachment(uri: Uri) {
        cancelNewDraftRestore()
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val metadata = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) null else {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    (if (nameIndex >= 0) cursor.getString(nameIndex) else null) to
                        (if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L)
                }
            }
        }.getOrNull()
        val filename = metadata?.first ?: uri.lastPathSegment ?: "attachment"
        val size = metadata?.second ?: -1L
        val mime = runCatching { resolver.getType(uri) }.getOrNull() ?: "application/octet-stream"
        val attachment = PendingAttachment(uri.toString(), filename, mime, size)
        mutableState.update { current ->
            if (current.composer.attachments.any { it.uri == attachment.uri }) current
            else current.copy(composer = current.composer.copy(attachments = current.composer.attachments + attachment))
        }
        persistAttachmentDraft(
            mutableState.value.draftStorageKey,
            mutableState.value.composer.attachments,
        )
    }

    fun removeAttachment(uri: String) {
        mutableState.update { it.copy(composer = it.composer.copy(attachments = it.composer.attachments.filterNot { item -> item.uri == uri })) }
        persistAttachmentDraft(
            mutableState.value.draftStorageKey,
            mutableState.value.composer.attachments,
        )
    }

    fun retryAttachment(uri: String) {
        val current = mutableState.value
        if (current.run.active || current.composer.uploading) return
        val attachments = current.composer.attachments.map { attachment ->
            if (attachment.uri == uri && attachment.status == AttachmentStatus.Failed) {
                attachment.copy(status = AttachmentStatus.Pending, error = null)
            } else {
                attachment
            }
        }
        if (attachments == current.composer.attachments) return
        mutableState.update { it.copy(composer = it.composer.copy(attachments = attachments), error = null) }
        persistAttachmentDraft(current.draftStorageKey, attachments)
        sendMessage()
    }

    fun selectModel(name: String?) {
        mutableState.update { current ->
            val modes = current.capabilities.availableRunModes(name)
            val mode = current.composer.options.mode.takeIf { it in modes } ?: modes.first()
            current.copy(
                composer = current.composer.copy(
                    options = current.composer.options.copy(
                        modelName = name,
                        mode = mode,
                        reasoningEffortEnabled = current.capabilities.supportsReasoningEffort(name),
                    ),
                ),
            )
        }
        persistSelectedRunOptions()
    }

    fun selectAgent(name: String) {
        mutableState.update { it.copy(composer = it.composer.copy(options = it.composer.options.copy(assistantId = name))) }
    }

    fun setDefaultAgent(name: String) {
        val current = mutableState.value
        val available = current.capabilities.agents.map { it.name } + LEAD_AGENT_ID
        if (name !in available || current.loadingCapabilities) return
        viewModelScope.launch {
            try {
                settings.setDefaultAgent(api.serverUrl, name)
                mutableState.update {
                    it.copy(
                        defaultAgentId = name,
                        composer = if (it.selectedThread == null) {
                            it.composer.copy(options = it.composer.options.copy(assistantId = name))
                        } else {
                            it.composer
                        },
                    )
                }
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.userMessage("Could not save the default agent.")) }
            }
        }
    }

    fun selectMode(mode: RunMode) {
        if (mode !in mutableState.value.capabilities.availableRunModes(mutableState.value.composer.options.modelName)) return
        mutableState.update { current ->
            current.copy(composer = current.composer.copy(options = current.composer.options.copy(mode = mode)))
        }
        persistSelectedRunOptions()
    }

    fun toggleSkill(name: String) {
        mutableState.update { current ->
            if (current.capabilities.skills.none { it.name == name && it.enabled }) return@update current
            val skills = current.composer.options.enabledSkills.toMutableSet()
            if (!skills.add(name)) skills.remove(name)
            current.copy(composer = current.composer.copy(options = current.composer.options.copy(enabledSkills = skills)))
        }
    }

    fun enableSkill(name: String) {
        mutableState.update { current ->
            if (current.capabilities.skills.none { it.name == name && it.enabled }) return@update current
            current.copy(
                composer = current.composer.copy(
                    options = current.composer.options.copy(
                        enabledSkills = current.composer.options.enabledSkills + name,
                    ),
                ),
            )
        }
    }

    fun setSkillEnabled(name: String, enabled: Boolean) {
        val current = mutableState.value
        val skill = current.capabilities.skills.firstOrNull { it.name == name } ?: return
        if (current.user?.role != "admin" || current.workspaceMutationBusy || skill.enabled == enabled) return
        viewModelScope.launch {
            mutableState.update { it.copy(workspaceMutationBusy = true, error = null) }
            try {
                val capabilities = workspace.setSkillEnabled(current.capabilities, name, enabled)
                mutableState.update { state ->
                    val selectedSkills = if (enabled) {
                        state.composer.options.enabledSkills
                    } else {
                        state.composer.options.enabledSkills - name
                    }
                    state.copy(
                        capabilities = capabilities,
                        composer = state.composer.copy(options = state.composer.options.copy(enabledSkills = selectedSkills)),
                        workspaceMutationBusy = false,
                    )
                }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        workspaceMutationBusy = false,
                        error = error.userMessage("Could not update this skill."),
                    )
                }
            }
        }
    }

    fun setMcpServerEnabled(name: String, enabled: Boolean) {
        val current = mutableState.value
        val config = current.mcpConfig ?: return
        val server = config.servers.firstOrNull { it.name == name } ?: return
        if (current.user?.role != "admin" || current.workspaceMutationBusy || server.enabled == enabled) return
        viewModelScope.launch {
            mutableState.update { it.copy(workspaceMutationBusy = true, error = null) }
            try {
                val updatedConfig = workspace.setMcpServerEnabled(config, name, enabled)
                mutableState.update { it.copy(mcpConfig = updatedConfig, workspaceMutationBusy = false) }
                refreshMcpTools()
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        workspaceMutationBusy = false,
                        error = error.userMessage("Could not update this MCP server."),
                    )
                }
            }
        }
    }

    fun updateMcpConfiguration(rawJson: String, onUpdated: () -> Unit = {}) {
        val current = mutableState.value
        if (current.user?.role != "admin" || current.workspaceMutationBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(workspaceMutationBusy = true, error = null) }
            try {
                val updatedConfig = workspace.updateMcpConfig(rawJson)
                mutableState.update { it.copy(mcpConfig = updatedConfig, workspaceMutationBusy = false) }
                refreshMcpTools()
                onUpdated()
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        workspaceMutationBusy = false,
                        error = error.userMessage("Could not update MCP configuration."),
                    )
                }
            }
        }
    }

    fun configureChannelProvider(providerId: String, values: Map<String, String>) {
        val current = mutableState.value
        val provider = current.channelProviders?.providers?.firstOrNull { it.provider == providerId } ?: return
        if (current.user?.role != "admin" || current.workspaceMutationBusy || !provider.enabled) return
        viewModelScope.launch {
            mutableState.update { it.copy(workspaceMutationBusy = true, error = null, channelConnect = null) }
            try {
                val updated = workspace.configureChannelProvider(providerId, values)
                mutableState.update { state ->
                    state.copy(
                        channelProviders = state.channelProviders?.replaceChannelProvider(updated),
                        workspaceMutationBusy = false,
                    )
                }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        workspaceMutationBusy = false,
                        error = error.userMessage("Could not configure this Channel."),
                    )
                }
            }
        }
    }

    fun disconnectChannelProvider(providerId: String) {
        val current = mutableState.value
        val provider = current.channelProviders?.providers?.firstOrNull { it.provider == providerId } ?: return
        if (current.user?.role != "admin" || current.workspaceMutationBusy || !provider.configured) return
        viewModelScope.launch {
            mutableState.update { it.copy(workspaceMutationBusy = true, error = null, channelConnect = null) }
            try {
                val updated = workspace.disconnectChannelProvider(providerId)
                mutableState.update { state ->
                    state.copy(
                        channelProviders = state.channelProviders?.replaceChannelProvider(updated),
                        workspaceMutationBusy = false,
                    )
                }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        workspaceMutationBusy = false,
                        error = error.userMessage("Could not disable this Channel."),
                    )
                }
            }
        }
    }

    fun connectChannelProvider(providerId: String) {
        val current = mutableState.value
        val provider = current.channelProviders?.providers?.firstOrNull { it.provider == providerId } ?: return
        if (current.workspaceMutationBusy || !provider.connectable || provider.connectionStatus == "connected") return
        viewModelScope.launch {
            mutableState.update { it.copy(workspaceMutationBusy = true, error = null, channelConnect = null) }
            try {
                val connection = workspace.connectChannelProvider(providerId)
                mutableState.update { it.copy(channelConnect = connection, workspaceMutationBusy = false) }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        workspaceMutationBusy = false,
                        error = error.userMessage("Could not start Channel connection."),
                    )
                }
            }
        }
    }

    fun clearChannelConnect() {
        mutableState.update { it.copy(channelConnect = null) }
    }

    fun applyQuickAction(prompt: String, skillKeywords: List<String>) {
        invalidateInputPolish()
        mutableState.update { state ->
            state.copy(
                composer = applyQuickActionToComposer(
                    composer = state.composer,
                    capabilities = state.capabilities,
                    prompt = prompt,
                    skillKeywords = skillKeywords,
                ),
            )
        }
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            val state = mutableState.value
            threads.saveDraft(state.draftStorageKey, state.composer.text)
        }
    }

    fun polishInput() {
        val initial = mutableState.value
        val originalText = initial.composer.text
        if (
            initial.inputPolishing ||
            initial.run.active ||
            initial.run.awaitingInput ||
            initial.composer.uploading ||
            originalText.isBlank()
        ) return

        inputPolishJob?.cancel()
        val requestId = ++inputPolishRequestId
        val serverUrl = initial.serverUrl
        val threadId = initial.selectedThread?.id
        val draftSessionKey = initial.draftSessionKey
        val draftStorageKey = initial.draftStorageKey
        val locales = getApplication<Application>().resources.configuration.locales
        val locale = if (locales.isEmpty) null else locales[0].toLanguageTag()
        mutableState.update {
            it.copy(
                inputPolishing = true,
                inputPolishUndo = null,
                error = null,
                notice = null,
            )
        }
        inputPolishJob = viewModelScope.launch {
            try {
                val result = api.polishInput(originalText, locale, threadId)
                val rewrittenText = result.rewrittenText.trim()
                var applied = false
                mutableState.update { current ->
                    if (
                        requestId != inputPolishRequestId ||
                        !isCurrentInputPolish(current, serverUrl, threadId, draftSessionKey, originalText)
                    ) {
                        current
                    } else if (!result.changed || rewrittenText.isBlank()) {
                        current.copy(
                            inputPolishing = false,
                            inputPolishUndo = null,
                            notice = getApplication<Application>().getString(R.string.input_polish_no_changes),
                        )
                    } else {
                        applied = true
                        current.copy(
                            composer = current.composer.copy(text = rewrittenText),
                            composerResetToken = current.composerResetToken + 1,
                            inputPolishing = false,
                            inputPolishUndo = InputPolishUndo(originalText, rewrittenText),
                        )
                    }
                }
                if (applied) runCatching { threads.saveDraft(draftStorageKey, rewrittenText) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == inputPolishRequestId) {
                    mutableState.update { current ->
                        if (!isCurrentConversationSession(current, serverUrl, threadId, draftSessionKey)) {
                            current
                        } else {
                            current.copy(
                                inputPolishing = false,
                                inputPolishUndo = null,
                                error = error.userMessage(
                                    getApplication<Application>().getString(R.string.input_polish_failed),
                                ),
                            )
                        }
                    }
                }
            } finally {
                if (requestId == inputPolishRequestId) inputPolishJob = null
            }
        }
    }

    fun cancelInputPolish() {
        invalidateInputPolish()
    }

    fun undoInputPolish() {
        val current = mutableState.value
        val undo = current.inputPolishUndo ?: return
        if (current.inputPolishing || current.composer.text != undo.rewrittenText) return
        invalidateInputPolish()
        mutableState.update {
            it.copy(
                composer = it.composer.copy(text = undo.originalText),
                composerResetToken = it.composerResetToken + 1,
            )
        }
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            runCatching { threads.saveDraft(current.draftStorageKey, undo.originalText) }
        }
    }

    private fun invalidateInputPolish(clearUndo: Boolean = true) {
        val current = mutableState.value
        if (inputPolishJob == null && !current.inputPolishing && (!clearUndo || current.inputPolishUndo == null)) return
        inputPolishRequestId += 1L
        inputPolishJob?.cancel()
        inputPolishJob = null
        mutableState.update {
            it.copy(
                inputPolishing = false,
                inputPolishUndo = if (clearUndo) null else it.inputPolishUndo,
            )
        }
    }

    fun startChatWithAgent(agentId: String) {
        closeWorkspaceChild()
        closeConversation()
        selectAgent(agentId)
    }

    fun saveMemoryFact(
        existing: MemoryFact?,
        content: String,
        category: String,
        confidence: Double,
        onSaved: () -> Unit,
    ) {
        val normalizedContent = content.trim()
        val normalizedCategory = category.trim().ifBlank { "context" }
        if (normalizedContent.isBlank()) {
            mutableState.update { it.copy(error = "Memory fact content cannot be empty.") }
            return
        }
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            mutableState.update { it.copy(error = "Memory confidence must be between 0 and 1.") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(memoryMutationBusy = true, error = null) }
            try {
                val memory = if (existing == null) {
                    workspace.createMemoryFact(normalizedContent, normalizedCategory, confidence)
                } else {
                    workspace.updateMemoryFact(existing.id, normalizedContent, normalizedCategory, confidence)
                }
                mutableState.update { it.copy(memory = memory, memoryMutationBusy = false) }
                onSaved()
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        memoryMutationBusy = false,
                        error = error.userMessage("Could not save this memory fact."),
                    )
                }
            }
        }
    }

    fun deleteMemoryFact(fact: MemoryFact) {
        viewModelScope.launch {
            mutableState.update { it.copy(memoryMutationBusy = true, error = null) }
            try {
                val memory = workspace.deleteMemoryFact(fact.id)
                mutableState.update { it.copy(memory = memory, memoryMutationBusy = false) }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        memoryMutationBusy = false,
                        error = error.userMessage("Could not delete this memory fact."),
                    )
                }
            }
        }
    }

    fun clearMemory() {
        viewModelScope.launch {
            mutableState.update { it.copy(memoryMutationBusy = true, error = null) }
            try {
                val memory = workspace.clearMemory()
                mutableState.update { it.copy(memory = memory, memoryMutationBusy = false) }
            } catch (error: Exception) {
                handleAuthenticatedError(error) {
                    it.copy(
                        memoryMutationBusy = false,
                        error = error.userMessage("Could not clear memory."),
                    )
                }
            }
        }
    }

    fun saveAgent(existing: AgentInfo?, name: String, description: String, model: String?, onSaved: () -> Unit) {
        val normalized = name.trim().lowercase()
        if (!normalized.matches(Regex("^[a-z0-9-]+$"))) {
            mutableState.update { it.copy(error = "Agent names may contain only letters, numbers, and hyphens.") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(workspaceMutationBusy = true) }
            try {
                if (existing == null) workspace.createAgent(normalized, description.trim(), model)
                else workspace.updateAgent(existing.name, description.trim(), model)
                mutableState.update { it.copy(workspaceMutationBusy = false) }
                refreshCapabilities()
                onSaved()
            } catch (error: Exception) {
                mutableState.update { it.copy(workspaceMutationBusy = false, error = error.userMessage("Could not save this agent.")) }
            }
        }
    }

    fun deleteAgent(agent: AgentInfo) {
        viewModelScope.launch {
            mutableState.update { it.copy(workspaceMutationBusy = true) }
            try {
                workspace.deleteAgent(agent.name)
                mutableState.update { it.copy(workspaceMutationBusy = false) }
                refreshCapabilities()
            } catch (error: Exception) {
                mutableState.update { it.copy(workspaceMutationBusy = false, error = error.userMessage("Could not delete this agent.")) }
            }
        }
    }

    fun saveTask(existing: ScheduledTaskInfo?, title: String, prompt: String, schedule: TaskSchedule, timezone: String, onSaved: () -> Unit) {
        val scheduleValue = when (schedule) {
            is TaskSchedule.Cron -> schedule.expression
            is TaskSchedule.Once -> schedule.runAt
        }
        if (title.isBlank() || prompt.isBlank() || scheduleValue.isBlank() || timezone.isBlank()) {
            mutableState.update { it.copy(error = "Complete the title, prompt, schedule, and timezone.") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(workspaceMutationBusy = true) }
            try {
                if (existing == null) workspace.createTask(title.trim(), prompt.trim(), schedule, timezone.trim())
                else workspace.updateTask(existing.id, title.trim(), prompt.trim(), schedule, timezone.trim())
                mutableState.update { it.copy(workspaceMutationBusy = false) }
                refreshTasks()
                onSaved()
            } catch (error: Exception) {
                mutableState.update { it.copy(workspaceMutationBusy = false, error = error.userMessage("Could not save this task.")) }
            }
        }
    }

    fun deleteTask(task: ScheduledTaskInfo) {
        viewModelScope.launch {
            mutableState.update { it.copy(workspaceMutationBusy = true) }
            try {
                workspace.deleteTask(task.id)
                mutableState.update { it.copy(workspaceMutationBusy = false) }
                refreshTasks()
            } catch (error: Exception) {
                mutableState.update { it.copy(workspaceMutationBusy = false, error = error.userMessage("Could not delete this task.")) }
            }
        }
    }

    fun sendMessage() {
        val state = mutableState.value
        val text = state.composer.text.trim()
        if (
            (text.isBlank() && state.composer.attachments.isEmpty()) ||
            state.inputPolishing ||
            state.run.active ||
            state.run.awaitingInput ||
            state.selectedThread?.let { thread ->
                runCoordinator.isActive(api.serverUrl, thread.id) ||
                    runCoordinator.stateFor(api.serverUrl, thread.id)?.run?.awaitingInput == true
            } == true
        ) return

        val composer = state.composer
        val clientMessageId = UUID.randomUUID().toString()
        val optimistic = ChatMessage(
            id = clientMessageId,
            role = MessageRole.User,
            text = text,
            attachments = composer.attachments.map { MessageAttachment(it.filename, it.size, null) },
        )
        val session = ConversationSession(
            serverUrl = api.serverUrl,
            threadId = state.selectedThread?.id,
            draftStorageKey = state.draftStorageKey,
            draftSessionKey = state.draftSessionKey,
            messages = state.messages + optimistic,
            todos = state.todos,
            artifacts = state.artifacts,
        )
        if (!submissionGate.tryAcquire(session.submissionKey)) return
        mutableState.update {
            it.copy(
                messages = session.messages,
                composer = it.composer.copy(text = "", attachments = emptyList(), uploading = composer.attachments.isNotEmpty()),
                composerResetToken = it.composerResetToken + 1,
                error = null,
            )
        }

        viewModelScope.launch {
            val acquiredSubmissionKeys = mutableSetOf(session.submissionKey)
            try {
                runCatching { threads.saveDraft(session.draftStorageKey, "") }
                runCatching { threads.saveAttachments(session.draftStorageKey, composer.attachments) }
                var boundSession = session
                val thread = state.selectedThread ?: try {
                    val created = threads.create(composer.options.assistantId)
                    boundSession = session.copy(threadId = created.id, draftStorageKey = created.id)
                    check(submissionGate.tryAcquire(boundSession.submissionKey)) {
                        "The created conversation already has a pending submission."
                    }
                    acquiredSubmissionKeys += boundSession.submissionKey
                    val copiedAttachments = runCatching {
                        threads.saveAttachments(created.id, composer.attachments)
                    }.isSuccess
                    if (session.draftStorageKey == NEW_DRAFT_KEY && copiedAttachments) {
                        runCatching { threads.saveAttachments(NEW_DRAFT_KEY, emptyList()) }
                    }
                    mutableState.update { current ->
                        val updated = current.copy(
                            threads = listOf(created) + current.threads.filterNot { it.id == created.id },
                        )
                        if (isCurrentConversationSession(current, session.serverUrl, session.threadId, session.draftSessionKey)) {
                            updated.copy(
                                selectedThread = created,
                                draftStorageKey = created.id,
                                route = AppRoute.Conversation,
                                showQuickCapabilities = false,
                            )
                        } else {
                            updated
                        }
                    }
                    publishConversationShortcuts()
                    created
                } catch (error: Exception) {
                    restoreSubmissionDraft(
                        session = session,
                        clientMessageId = clientMessageId,
                        text = text,
                        composer = composer,
                        message = error.userMessage("Could not create a conversation."),
                    )
                    return@launch
                }
                startRun(thread, text, composer, boundSession, clientMessageId)
            } finally {
                acquiredSubmissionKeys.forEach(submissionGate::release)
            }
        }
    }

    private suspend fun startRun(
        thread: ThreadSummary,
        text: String,
        composer: ComposerState,
        session: ConversationSession,
        clientMessageId: String,
    ) {
        val pending = composer.attachments
        mutableState.update { current ->
            if (!isCurrentConversationSession(current, session.serverUrl, session.threadId, session.draftSessionKey)) {
                current
            } else {
                current.copy(
                    run = RunState(RunStatus.Connecting, startedAtEpochMs = System.currentTimeMillis()),
                    composer = current.composer.copy(uploading = pending.isNotEmpty()),
                    error = null,
                )
            }
        }
        runCatching {
            threads.saveAttachments(
                session.draftStorageKey,
                pending.map { file -> file.copy(status = AttachmentStatus.Uploading, error = null) },
            )
        }
        if (pending.isNotEmpty()) {
            RunService.start(getApplication(), thread.title, session.serverUrl, thread.id)
            RunService.update(getApplication(), RunProgress.Uploading, thread.title)
        }
        val uploaded = try {
            if (pending.isEmpty()) {
                emptyList()
            } else {
                runs.upload(thread.id, pending.map(::toUploadSource))
            }
        } catch (error: Exception) {
            val message = error.userMessage("Upload failed.")
            restoreSubmissionDraft(session, clientMessageId, text, composer, message, error.message)
            RunService.fail(getApplication(), message, thread.title)
            return
        }
        val runMessages = session.messages.map { message ->
            if (message.id == clientMessageId) {
                message.copy(attachments = uploaded.map { file -> MessageAttachment(file.filename, file.size, file.virtualPath) })
            } else {
                message
            }
        }
        mutableState.update { current ->
            if (!isCurrentConversationSession(current, session.serverUrl, session.threadId, session.draftSessionKey)) {
                current
            } else {
                current.copy(
                    messages = runMessages,
                    composer = current.composer.copy(uploading = false),
                )
            }
        }
        threads.saveDraft(session.draftStorageKey, "")
        runCatching {
            threads.saveAttachments(session.draftStorageKey, emptyList())
        }
        val started = runCoordinator.start(
            CoordinatedRunRequest(
                serverUrl = session.serverUrl,
                threadId = thread.id,
                title = thread.title,
                message = text,
                options = composer.options,
                clientMessageId = clientMessageId,
                files = uploaded,
                initialMessages = runMessages,
                initialTodos = session.todos,
                initialArtifacts = session.artifacts,
            ),
        )
        if (!started) {
            restoreSubmissionDraft(
                session = session,
                clientMessageId = clientMessageId,
                text = text,
                composer = composer,
                message = "This conversation already has a run in progress.",
            )
        }
    }

    private fun restoreSubmissionDraft(
        session: ConversationSession,
        clientMessageId: String,
        text: String,
        composer: ComposerState,
        message: String,
        attachmentError: String? = null,
    ) {
        val restoredComposer = restoreFailedComposer(composer, text, attachmentError)
        mutableState.update { current ->
            if (!isCurrentConversationSession(current, session.serverUrl, session.threadId, session.draftSessionKey)) {
                current
            } else {
                current.copy(
                    run = RunState(),
                    messages = current.messages.filterNot { chat -> chat.id == clientMessageId },
                    composer = restoredComposer,
                    composerResetToken = current.composerResetToken + 1,
                    error = message,
                )
            }
        }
        viewModelScope.launch {
            threads.saveDraft(session.draftStorageKey, text)
            runCatching { threads.saveAttachments(session.draftStorageKey, restoredComposer.attachments) }
        }
    }

    fun stopRun() {
        val current = mutableState.value
        val thread = current.selectedThread ?: return
        val key = RunKey(api.serverUrl, thread.id)
        if (current.run.status == RunStatus.Stopping || !runCoordinator.isActive(key.serverUrl, key.threadId)) return
        val draftSessionKey = current.draftSessionKey
        mutableState.update { it.copy(run = it.run.copy(status = RunStatus.Stopping)) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val result = runCoordinator.cancel(key)
            val coordinated = runCoordinator.stateFor(key.serverUrl, key.threadId)
            mutableState.update { state ->
                if (!isCurrentConversationSession(state, key.serverUrl, key.threadId, draftSessionKey)) {
                    state
                } else if (result.confirmed) {
                    state.copy(
                        run = coordinated?.run ?: RunState(),
                        messages = coordinated?.messages
                            ?: result.snapshot?.messages
                            ?: state.messages.map { message -> message.copy(isStreaming = false) },
                        todos = coordinated?.todos ?: result.snapshot?.todos ?: state.todos,
                        artifacts = coordinated?.artifacts ?: result.snapshot?.artifacts ?: state.artifacts,
                    )
                } else {
                    state.copy(
                        run = coordinated?.run?.takeIf { it.active }
                            ?: state.run.copy(
                                status = state.run.status.takeUnless { it == RunStatus.Stopping } ?: RunStatus.Streaming,
                            ),
                        error = getApplication<Application>().getString(R.string.stop_not_confirmed),
                    )
                }
            }
        }
    }

    fun submitHumanInput(request: HumanInputRequest, value: String, optionId: String? = null) {
        val answer = value.trim()
        val current = mutableState.value
        val thread = current.selectedThread ?: return
        val serverUrl = api.serverUrl
        if (answer.isBlank() || runCoordinator.isActive(serverUrl, thread.id)) return
        val draftSessionKey = current.draftSessionKey
        val response = HumanInputResponse(
            source = request.source,
            requestId = request.requestId,
            responseKind = if (optionId == null) "text" else "option",
            value = answer,
            optionId = optionId,
        )
        val message = "For your clarification \"${request.question}\", my answer is: $answer"
        val clientMessageId = UUID.randomUUID().toString()
        val hiddenResponse = ChatMessage(
            id = clientMessageId,
            role = MessageRole.User,
            text = message,
            hiddenFromUi = true,
            blocks = listOf(MessageBlock.HumanInputResponseBlock(response)),
        )
        mutableState.update {
            it.copy(run = RunState(RunStatus.Connecting, startedAtEpochMs = System.currentTimeMillis()), error = null)
        }
        viewModelScope.launch {
            val started = runCoordinator.start(
                CoordinatedRunRequest(
                    serverUrl = serverUrl,
                    threadId = thread.id,
                    title = thread.title,
                    message = message,
                    options = current.composer.options,
                    clientMessageId = clientMessageId,
                    humanInputResponse = response,
                    initialMessages = current.messages + hiddenResponse,
                    initialTodos = current.todos,
                    initialArtifacts = current.artifacts,
                ),
            )
            if (!started) {
                mutableState.update { state ->
                    if (isCurrentConversationSession(state, serverUrl, thread.id, draftSessionKey)) {
                        state.copy(
                            run = runCoordinator.stateFor(serverUrl, thread.id)?.run ?: RunState(),
                            error = "This conversation already has a run in progress.",
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun branchConversation(messageId: String) {
        val current = mutableState.value
        val source = current.selectedThread ?: return
        val serverUrl = api.serverUrl
        if (runCoordinator.isActive(serverUrl, source.id) || current.messageActionBusy) return
        val turn = assistantTurnForMessage(current.messages, messageId) ?: return
        val draftSessionKey = current.draftSessionKey
        viewModelScope.launch {
            mutableState.update { state ->
                if (isCurrentConversationSession(state, serverUrl, source.id, draftSessionKey)) {
                    state.copy(messageActionBusy = true, error = null)
                } else {
                    state
                }
            }
            try {
                val branch = createBranch(source, turn)
                mutableState.update { state ->
                    val updated = state.copy(
                        threads = listOf(branch.first) + state.threads.filterNot { item -> item.id == branch.first.id },
                    )
                    if (isCurrentConversationSession(state, serverUrl, source.id, draftSessionKey)) {
                        updated.copy(
                            selectedThread = branch.first,
                            messages = branch.second.messages,
                            todos = branch.second.todos,
                            artifacts = branch.second.artifacts,
                            composer = state.composer.copy(text = "", attachments = emptyList()),
                            draftStorageKey = branch.first.id,
                            draftSessionKey = "thread-draft-${branch.first.id}-${UUID.randomUUID()}",
                            route = AppRoute.Conversation,
                            showQuickCapabilities = false,
                            messageActionBusy = false,
                            offline = false,
                            notice = getApplication<Application>().getString(R.string.conversation_branch_created),
                        )
                    } else {
                        updated
                    }
                }
                refreshThreads()
            } catch (error: Exception) {
                mutableState.update { state ->
                    if (isCurrentConversationSession(state, serverUrl, source.id, draftSessionKey)) {
                        state.copy(
                            messageActionBusy = false,
                            error = error.userMessage("Could not branch this conversation."),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun regenerateResponse(messageId: String) {
        val current = mutableState.value
        val thread = current.selectedThread ?: return
        val serverUrl = api.serverUrl
        if (runCoordinator.isActive(serverUrl, thread.id) || current.messageActionBusy) return
        val turn = assistantTurnForMessage(current.messages, messageId) ?: return
        if (!isLatestAssistantTurn(current.messages, turn)) return
        val originalMessages = current.messages
        val regeneratedMessages = originalMessages.take(turn.firstMessageIndex)
        val draftSessionKey = current.draftSessionKey
        viewModelScope.launch {
            mutableState.update { state ->
                if (isCurrentConversationSession(state, serverUrl, thread.id, draftSessionKey)) {
                    state.copy(messageActionBusy = true, error = null)
                } else {
                    state
                }
            }
            try {
                val preparation = runs.prepareRegenerate(thread.id, turn.targetMessageId)
                mutableState.update { state ->
                    if (isCurrentConversationSession(state, serverUrl, thread.id, draftSessionKey)) {
                        state.copy(
                            messages = regeneratedMessages,
                            run = RunState(RunStatus.Connecting, startedAtEpochMs = System.currentTimeMillis()),
                            messageActionBusy = false,
                        )
                    } else {
                        state
                    }
                }
                val started = runCoordinator.start(
                    CoordinatedRunRequest(
                        serverUrl = serverUrl,
                        threadId = thread.id,
                        title = thread.title,
                        message = "",
                        options = current.composer.options,
                        regenerate = preparation,
                        initialMessages = regeneratedMessages,
                        initialTodos = current.todos,
                        initialArtifacts = current.artifacts,
                        failureMessages = originalMessages,
                    ),
                )
                if (!started) {
                    mutableState.update { state ->
                        if (isCurrentConversationSession(state, serverUrl, thread.id, draftSessionKey)) {
                            state.copy(
                                messages = originalMessages,
                                run = runCoordinator.stateFor(serverUrl, thread.id)?.run ?: RunState(),
                                messageActionBusy = false,
                                error = "This conversation already has a run in progress.",
                            )
                        } else {
                            state
                        }
                    }
                }
            } catch (error: Exception) {
                mutableState.update { state ->
                    if (isCurrentConversationSession(state, serverUrl, thread.id, draftSessionKey)) {
                        state.copy(
                            messages = originalMessages,
                            messageActionBusy = false,
                            error = error.userMessage("Could not regenerate this response."),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    private suspend fun createBranch(
        source: ThreadSummary,
        turn: AssistantTurn,
    ): Pair<ThreadSummary, com.deerflow.mobile.data.ThreadSnapshot> {
        val result = threads.branch(source.id, turn.targetMessageId, turn.messageIds)
        val snapshot = threads.snapshot(result.threadId).value
        return ThreadSummary(
            id = result.threadId,
            title = snapshot.title,
            status = "idle",
            updatedAt = source.updatedAt,
        ) to snapshot
    }

    private suspend fun resumeRunIfNeeded(threadId: String) {
        val serverUrl = api.serverUrl
        val coordinated = runCoordinator.stateFor(serverUrl, threadId)
        if (coordinated != null) {
            applyCoordinatedRunState(coordinated)
            if (coordinated.run.active || coordinated.run.awaitingInput) return
        }
        val thread = mutableState.value.selectedThread?.takeIf { it.id == threadId } ?: return
        val saved = cache.loadRun(serverUrl, threadId)
        if (saved?.active == true) {
            mutableState.update { state ->
                if (state.serverUrl == serverUrl && state.selectedThread?.id == threadId) {
                    state.copy(run = saved.copy(status = RunStatus.Reconnecting))
                } else {
                    state
                }
            }
            runCoordinator.resume(serverUrl, thread.id, thread.title, saved)
            runCoordinator.stateFor(serverUrl, threadId)
                ?.let(::applyCoordinatedRunState)
            return
        }
        if (saved?.awaitingInput == true) {
            mutableState.update { state ->
                if (state.serverUrl == serverUrl && state.selectedThread?.id == threadId) {
                    state.copy(run = saved, messageActionBusy = false)
                } else {
                    state
                }
            }
            return
        }

        // A backgrounded client can lose its local run row while the Gateway keeps
        // the resumable run alive. Discover and attach to that run instead of
        // allowing the next send to create a conflicting run on the same thread.
        val active = try {
            api.latestActiveRun(threadId)
        } catch (_: Exception) {
            return
        }
        if (active == null) {
            if (coordinated?.run?.gatewayStatus != GatewayRunStatus.Unknown || coordinated?.error != null) return
            cache.saveRun(serverUrl, threadId, RunState())
            mutableState.update { current ->
                if (
                    current.serverUrl == serverUrl &&
                    current.selectedThread?.id == threadId &&
                    !runCoordinator.isActive(serverUrl, threadId)
                ) {
                    current.copy(run = RunState(), messageActionBusy = false)
                } else {
                    current
                }
            }
            return
        }
        val recovered = RunState(
            RunStatus.Reconnecting,
            runId = active.runId,
            startedAtEpochMs = System.currentTimeMillis(),
        )
        cache.saveRun(serverUrl, threadId, recovered)
        mutableState.update { state ->
            if (state.serverUrl == serverUrl && state.selectedThread?.id == threadId) {
                state.copy(run = recovered)
            } else {
                state
            }
        }
        runCoordinator.resume(serverUrl, thread.id, thread.title, recovered)
        runCoordinator.stateFor(serverUrl, threadId)
            ?.let(::applyCoordinatedRunState)
    }

    fun pauseTask(task: ScheduledTaskInfo) {
        viewModelScope.launch {
            try {
                workspace.pauseTask(task.id, task.status != "paused")
                refreshTasks()
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.userMessage("Could not update this task.")) }
            }
        }
    }

    fun triggerTask(task: ScheduledTaskInfo) {
        viewModelScope.launch {
            try {
                workspace.triggerTask(task.id)
                refreshTasks()
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.userMessage("Could not run this task.")) }
            }
        }
    }

    fun setTheme(value: ThemePreference) {
        persistSetting { setTheme(value) }
        mutableState.update { it.copy(theme = value) }
    }

    fun setLanguage(value: LanguagePreference) {
        mutableState.update { it.copy(language = value) }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(value.languageTag))
    }

    fun syncLanguagePreference() {
        val current = currentLanguagePreference()
        mutableState.update { state -> if (state.language == current) state else state.copy(language = current) }
    }

    fun setNotifyOnRunCompletion(enabled: Boolean) {
        persistSetting { setNotifyOnRunCompletion(enabled) }
        mutableState.update { it.copy(notifyOnRunCompletion = enabled) }
    }

    fun setCacheRetentionPolicy(value: CacheRetentionPolicy) {
        persistSetting { setCacheRetentionPolicy(value) }
        mutableState.update { it.copy(cacheRetentionPolicy = value) }
    }

    fun setArtifactDownloadLimits(value: ArtifactDownloadLimits) {
        val normalized = normalizeArtifactDownloadLimits(value.autoDownloadBytes, value.manualDownloadBytes)
        persistSetting { setArtifactDownloadLimits(normalized) }
        mutableState.update { it.copy(artifactDownloadLimits = normalized) }
    }

    fun refreshCacheStats() {
        if (mutableState.value.loadingCacheStats || mutableState.value.clearingCache) return
        viewModelScope.launch {
            mutableState.update { it.copy(loadingCacheStats = true) }
            try {
                val stats = cache.stats()
                mutableState.update { it.copy(cacheStats = stats, loadingCacheStats = false) }
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        loadingCacheStats = false,
                        error = error.userMessage("Could not inspect cached data."),
                    )
                }
            }
        }
    }

    fun clearCache() {
        if (mutableState.value.clearingCache) return
        if (runCoordinator.hasActiveRuns(api.serverUrl)) {
            mutableState.update { it.copy(error = "Stop the active run before clearing cached data.") }
            return
        }
        val cancelledArtifactJob = cancelArtifactWork()
        mutableState.update {
            it.copy(
                artifactBusy = false,
                artifactSession = null,
            )
        }
        viewModelScope.launch {
            cancelledArtifactJob?.join()
            mutableState.update { it.copy(clearingCache = true) }
            try {
                cache.clearAll()
                val stats = cache.stats()
                mutableState.update { it.copy(cacheStats = stats, clearingCache = false) }
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        clearingCache = false,
                        error = error.userMessage("Could not clear cached data."),
                    )
                }
            }
        }
    }

    private fun persistSetting(block: suspend SettingsStore.() -> Unit) {
        viewModelScope.launch {
            try {
                settings.block()
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.userMessage("Could not save settings.")) }
            }
        }
    }

    fun dismissError() {
        mutableState.update { it.copy(error = null) }
    }

    fun dismissModelUnavailableError() {
        mutableState.update { it.copy(modelUnavailableError = null) }
    }

    fun showNotice(message: String) {
        mutableState.update { it.copy(notice = message) }
    }

    fun dismissNotice() {
        mutableState.update { it.copy(notice = null) }
    }

    fun reportError(message: String) {
        mutableState.update { it.copy(error = message) }
    }

    private fun persistSelectedRunOptions() {
        val current = mutableState.value
        if (current.serverUrl.isBlank()) return
        viewModelScope.launch {
            try {
                settings.setSavedRunOptions(
                    serverUrl = api.serverUrl,
                    modelName = current.composer.options.modelName,
                    mode = current.composer.options.mode,
                )
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.userMessage("Could not save the selected model.")) }
            }
        }
    }

    fun exportConversation(uri: Uri, format: ConversationExportFormat) {
        val current = mutableState.value
        if (current.exportBusy || current.messages.isEmpty()) return
        mutableState.update { it.copy(exportBusy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val title = current.selectedThread?.title ?: "DeerFlow conversation"
                val content = com.deerflow.mobile.data.exportConversation(title, current.messages, format)
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri)?.use { output ->
                    output.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(content) }
                } ?: throw IOException("Could not open the selected destination.")
                mutableState.update { it.copy(exportBusy = false) }
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(exportBusy = false, error = error.userMessage("Could not export this conversation."))
                }
            }
        }
    }

    fun openArtifact(path: String) {
        val current = mutableState.value
        val thread = current.selectedThread ?: return
        if (path.isBlank() || current.artifactBusy) return
        val limits = current.artifactDownloadLimits
        val operationId = nextArtifactOperation()
        val probingSession = ArtifactSession(
            threadId = thread.id,
            path = path,
            filename = path.substringAfterLast('/').ifBlank { "artifact" },
            mimeType = "",
            totalBytes = null,
            maxDownloadBytes = limits.manualDownloadBytes,
            phase = ArtifactSessionPhase.Probing,
        )
        mutableState.update {
            it.copy(
                artifactBusy = true,
                artifactSession = probingSession,
                error = null,
            )
        }
        artifactDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            val probe = try {
                threads.probeArtifact(thread.id, path, limits.manualDownloadBytes)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { latest ->
                    if (isCurrentArtifactOperation(operationId, latest, thread.id)) {
                        latest.copy(
                            artifactBusy = false,
                            artifactSession = null,
                            error = error.userMessage("Could not inspect this artifact."),
                        )
                    } else {
                        latest
                    }
                }
                return@launch
            }
            currentCoroutineContext().ensureActive()
            val session = ArtifactSession(
                threadId = thread.id,
                path = probe.path,
                filename = probe.filename,
                mimeType = probe.mimeType,
                totalBytes = probe.totalBytes,
                maxDownloadBytes = limits.manualDownloadBytes,
                phase = ArtifactSessionPhase.AwaitingConfirm,
            )
            if (requiresArtifactDownloadConfirmation(probe.totalBytes, limits.autoDownloadBytes)) {
                mutableState.update { latest ->
                    if (!isCurrentArtifactOperation(operationId, latest, thread.id)) latest
                    else latest.copy(
                        artifactBusy = false,
                        artifactSession = session,
                    )
                }
            } else {
                try {
                    downloadArtifact(session, probe, operationId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    updateArtifactDownloadFailure(session, operationId, error)
                }
            }
        }
    }

    fun confirmArtifactDownload() {
        val session = mutableState.value.artifactSession ?: return
        if (session.phase != ArtifactSessionPhase.AwaitingConfirm) return
        if (mutableState.value.selectedThread?.id != session.threadId) return
        val operationId = nextArtifactOperation()
        val probe = ArtifactProbe(
            path = session.path,
            filename = session.filename,
            mimeType = session.mimeType,
            totalBytes = session.totalBytes,
        )
        artifactDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                downloadArtifact(session, probe, operationId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateArtifactDownloadFailure(session, operationId, error)
            }
        }
    }

    fun cancelArtifactDownload() {
        cancelArtifactWork()
        mutableState.update {
            it.copy(
                artifactBusy = false,
                artifactSession = null,
            )
        }
    }

    fun dismissArtifactSession() {
        cancelArtifactWork()
        mutableState.update {
            it.copy(
                artifactBusy = false,
                artifactSession = null,
            )
        }
    }

    private suspend fun downloadArtifact(
        session: ArtifactSession,
        probe: ArtifactProbe,
        operationId: Long,
    ) {
        mutableState.update { latest ->
            if (!isCurrentArtifactOperation(operationId, latest, session.threadId)) latest
            else latest.copy(
                artifactBusy = true,
                artifactSession = session.copy(
                    phase = ArtifactSessionPhase.Downloading,
                    downloadedBytes = 0L,
                    localPath = null,
                    text = null,
                    textTruncated = false,
                ),
                error = null,
            )
        }
        val directory = File(getApplication<Application>().cacheDir, "artifacts")
        var lastReportedBytes = 0L
        val download = threads.downloadArtifact(
            threadId = session.threadId,
            probe = probe,
            directory = directory,
            maxBytes = session.maxDownloadBytes,
        ) { downloadedBytes, totalBytes ->
            val shouldReport = downloadedBytes == 0L ||
                downloadedBytes - lastReportedBytes >= ARTIFACT_PROGRESS_UPDATE_BYTES ||
                (totalBytes != null && downloadedBytes >= totalBytes)
            if (shouldReport) {
                lastReportedBytes = downloadedBytes
                mutableState.update { latest ->
                    val current = latest.artifactSession
                    if (!isCurrentArtifactOperation(operationId, latest, session.threadId) || current == null) {
                        latest
                    } else {
                        latest.copy(
                            artifactSession = current.copy(
                                phase = ArtifactSessionPhase.Downloading,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes ?: current.totalBytes,
                            ),
                        )
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        val text = if (isTextArtifact(download.probe.mimeType, download.probe.filename)) {
            readArtifactTextPreview(download.file)
        } else {
            null
        }
        mutableState.update { latest ->
            if (!isCurrentArtifactOperation(operationId, latest, session.threadId)) {
                latest
            } else {
                latest.copy(
                    artifactBusy = false,
                    artifactSession = session.copy(
                        filename = download.probe.filename,
                        mimeType = download.probe.mimeType,
                        totalBytes = download.probe.totalBytes ?: session.totalBytes,
                        phase = ArtifactSessionPhase.Ready,
                        downloadedBytes = download.probe.totalBytes ?: session.downloadedBytes,
                        localPath = download.file.absolutePath,
                        text = text?.text,
                        textTruncated = text?.truncated == true,
                    ),
                )
            }
        }
    }

    private fun updateArtifactDownloadFailure(
        session: ArtifactSession,
        operationId: Long,
        error: Exception,
    ) {
        mutableState.update { latest ->
            if (!isCurrentArtifactOperation(operationId, latest, session.threadId)) {
                latest
            } else {
                latest.copy(
                    artifactBusy = false,
                    artifactSession = session.copy(
                        phase = ArtifactSessionPhase.AwaitingConfirm,
                        downloadedBytes = 0L,
                        localPath = null,
                        text = null,
                        textTruncated = false,
                    ),
                    error = error.userMessage("Could not download this artifact."),
                )
            }
        }
    }

    fun saveArtifact(uri: Uri) {
        val session = mutableState.value.artifactSession ?: return
        val localPath = session.localPath ?: return
        if (session.phase != ArtifactSessionPhase.Ready) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri)?.use { output ->
                    File(localPath).inputStream().use { input -> input.copyTo(output) }
                } ?: throw IOException("Could not open the selected destination.")
                mutableState.update {
                    it.copy(notice = getApplication<Application>().getString(R.string.artifact_saved, session.filename))
                }
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.userMessage("Could not save this artifact.")) }
            }
        }
    }

    override fun onCleared() {
        disconnectBrowserLive()
        cancelArtifactWork()
        super.onCleared()
    }

    fun reportArtifactOpenFailure() {
        mutableState.update { it.copy(error = "No installed app can open this artifact.") }
    }

    private fun persistAttachmentDraft(threadId: String, attachments: List<PendingAttachment>) {
        attachmentJob?.cancel()
        attachmentJob = viewModelScope.launch {
            runCatching { threads.saveAttachments(threadId, attachments) }
        }
    }

    private fun nextArtifactOperation(): Long {
        artifactDownloadJob?.cancel()
        return ++artifactOperationId
    }

    private fun cancelArtifactWork(): Job? {
        artifactOperationId += 1
        val job = artifactDownloadJob
        job?.cancel()
        artifactDownloadJob = null
        return job
    }

    private fun isCurrentArtifactOperation(operationId: Long, state: AppUiState, threadId: String): Boolean =
        operationId == artifactOperationId && state.selectedThread?.id == threadId

    private fun toUploadSource(file: PendingAttachment): UploadSource {
        val resolver = getApplication<Application>().contentResolver
        val uri = Uri.parse(file.uri)
        return UploadSource(file.filename, file.mimeType, file.size) {
            resolver.openInputStream(uri) ?: throw IOException("Could not open ${file.filename}.")
        }
    }

    private fun normalizeOrReport(value: String): String? = try {
        normalizeServerUrl(value)
    } catch (error: IllegalArgumentException) {
        mutableState.update { it.copy(error = error.message) }
        null
    }

    private fun disconnectRun() {
        runCoordinator.abandonAll()
        mutableState.update { it.copy(run = RunState(), activeRunThreadIds = emptySet()) }
    }

    private fun handleAuthenticatedError(error: Exception, update: (AppUiState) -> AppUiState) {
        if (error is ApiException && error.statusCode == 401) {
            mutableState.update { it.copy(user = null, checkingSession = false, run = RunState(), error = "Your session expired. Sign in again.") }
        } else {
            mutableState.update(update)
        }
    }

    private fun Exception.userMessage(fallback: String): String = when (this) {
        is ApiException -> message
        is IOException -> "$fallback Check ${api.serverUrl}."
        else -> message?.takeIf { it.isNotBlank() } ?: fallback
    }

    companion object {
        private const val NEW_DRAFT_KEY = "__new__"
        const val UNCONFIGURED_API_ORIGIN = "http://127.0.0.1"
    }
}

private fun currentLanguagePreference(): LanguagePreference =
    LanguagePreference.fromLanguageTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

internal fun isCurrentThreadLoad(selectedThreadId: String?, loadedThreadId: String): Boolean =
    selectedThreadId == loadedThreadId

private fun List<ChatMessage>.latestBrowserView(): BrowserViewSnapshot? =
    asReversed()
        .asSequence()
        .flatMap { message -> message.blocks.asReversed().asSequence() }
        .filterIsInstance<MessageBlock.ToolResult>()
        .mapNotNull(MessageBlock.ToolResult::browserView)
        .firstOrNull()
