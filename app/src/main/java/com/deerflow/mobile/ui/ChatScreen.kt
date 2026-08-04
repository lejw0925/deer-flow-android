@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.deerflow.mobile.ui

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.core.content.FileProvider
import com.deerflow.mobile.R
import com.deerflow.mobile.data.AttachmentStatus
import com.deerflow.mobile.data.ChatMessageGroup
import com.deerflow.mobile.data.ConversationExportFormat
import com.deerflow.mobile.data.MessageRole
import com.deerflow.mobile.data.PendingAttachment
import com.deerflow.mobile.data.RunMode
import com.deerflow.mobile.data.RunStatus
import com.deerflow.mobile.data.SkillInfo
import com.deerflow.mobile.data.TodoItem
import com.deerflow.mobile.data.groupChatMessages
import com.deerflow.mobile.data.conversationExportFileName
import com.deerflow.mobile.ui.theme.ExpressiveMotion
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    onOpenDrawer: () -> Unit,
    contentPadding: PaddingValues,
) {
    var showAttachments by remember { mutableStateOf(false) }
    var expandedTopSelector by remember { mutableStateOf<TopSelectorKind?>(null) }
    var showAgentPicker by remember { mutableStateOf(false) }
    var showRunDetails by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<ConversationExportFormat?>(null) }
    var editorValue by rememberSaveable(
        state.draftSessionKey,
        state.composerResetToken,
        stateSaver = TextFieldValue.Saver,
    ) {
        mutableStateOf(
            TextFieldValue(
                text = state.composer.text,
                selection = TextRange(state.composer.text.length),
            ),
        )
    }
    val context = LocalContext.current

    LaunchedEffect(state.composer.text) {
        if (editorValue.text != state.composer.text) {
            editorValue = TextFieldValue(
                text = state.composer.text,
                selection = TextRange(state.composer.text.length),
            )
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addAttachment)
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(viewModel::addAttachment)
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) cameraUri?.let(viewModel::addAttachment)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/*")) { uri ->
        val format = pendingExportFormat
        pendingExportFormat = null
        if (uri != null && format != null) viewModel.exportConversation(uri, format)
    }
    val artifactSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) viewModel.saveArtifact(uri)
    }

    Column(Modifier.fillMaxSize().testTag(UiTags.ChatScreen).padding(contentPadding)) {
        ChatTopBar(
            state = state,
            onOpenDrawer = onOpenDrawer,
            onBack = viewModel::closeConversation,
            onModelSelected = viewModel::selectModel,
            onModeSelected = viewModel::selectMode,
            onExport = { format ->
                pendingExportFormat = format
                val title = state.selectedThread?.title ?: "deerflow-conversation"
                exportLauncher.launch(conversationExportFileName(title, format))
            },
            onOpenRunDetails = {
                showRunDetails = true
                viewModel.openRunDetails()
            },
            onOpenBrowser = viewModel::openBrowser,
            expandedSelector = expandedTopSelector,
            onExpandedSelectorChange = { expandedTopSelector = it },
        )
        if (state.offline) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { OfflineBanner() }
            }
        }
        TodoProgressHost(
            conversationKey = state.selectedThread?.id,
            todos = state.todos,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                state.loadingChat -> LoadingIndicator(Modifier.size(32.dp))
                state.messages.isEmpty() -> ChatWelcome(onSuggestion = viewModel::updateDraft)
                else -> ProvideMarkdownImageContext(
                    MarkdownImageContext(
                        serverUrl = state.serverUrl,
                        threadId = state.selectedThread?.id.orEmpty(),
                        artifactPaths = state.artifacts,
                        onOpenArtifact = viewModel::openArtifact,
                    ),
                ) {
                    ConversationMessageList(
                        conversationKey = state.selectedThread?.id,
                        messages = state.messages,
                        runActive = state.run.active,
                        actionBusy = state.messageActionBusy,
                        onHumanInput = viewModel::submitHumanInput,
                        onCopy = { viewModel.showNotice(context.getString(R.string.copied_to_clipboard)) },
                        onBranch = viewModel::branchConversation,
                        onArtifact = viewModel::openArtifact,
                        onBrowser = viewModel::openBrowser,
                        modifier = Modifier.fillMaxSize().widthIn(max = 900.dp),
                    )
                }
            }
        }
        if (state.run.active && state.selectedThread != null) {
            RunActivityRow(
                startedAtEpochMs = state.run.startedAtEpochMs,
                notice = state.runNotice,
            )
        }
        MessageComposer(
            state = state,
            editorValue = editorValue,
            onDraftChange = { value ->
                editorValue = value
                viewModel.updateDraft(value.text)
            },
            onSkillSelected = viewModel::enableSkill,
            onAttachment = { showAttachments = true },
            onAgent = { showAgentPicker = true },
            onQuickAction = viewModel::applyQuickAction,
            onRemoveAttachment = viewModel::removeAttachment,
            onRetryAttachment = viewModel::retryAttachment,
            onPolishInput = viewModel::polishInput,
            onCancelPolish = viewModel::cancelInputPolish,
            onUndoPolish = viewModel::undoInputPolish,
            onSend = viewModel::sendMessage,
            onStop = viewModel::stopRun,
        )
    }

    if (showAttachments) {
        AttachmentSheet(
            onDismiss = { showAttachments = false },
            onCamera = {
                val uri = createCameraUri(context)
                cameraUri = uri
                cameraLauncher.launch(uri)
                showAttachments = false
            },
            onPhotos = {
                photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                showAttachments = false
            },
            onFiles = {
                fileLauncher.launch(arrayOf("*/*"))
                showAttachments = false
            },
        )
    }
    if (showAgentPicker) {
        AgentPickerSheet(state, viewModel, onDismiss = { showAgentPicker = false })
    }
    if (showRunDetails) {
        RunDetailsSheet(
            state = state,
            onDismiss = {
                showRunDetails = false
                viewModel.clearRunDetails()
            },
            onSelectRun = viewModel::selectRunDetails,
            onReload = viewModel::openRunDetails,
        )
    }
    state.modelUnavailableError?.let { message ->
        ModelUnavailableDialog(
            message = message,
            onDismiss = viewModel::dismissModelUnavailableError,
            onChooseModel = {
                viewModel.dismissModelUnavailableError()
                expandedTopSelector = TopSelectorKind.Model
            },
        )
    }
    if (state.browser.visible) {
        BrowserLiveSheet(
            browser = state.browser,
            serverUrl = state.serverUrl,
            onDismiss = viewModel::closeBrowser,
            onLiveControlChange = viewModel::setBrowserLiveControl,
            onInput = viewModel::sendBrowserInput,
        )
    }
    state.artifactSession?.let { session ->
        if (session.phase != ArtifactSessionPhase.Probing) {
            ArtifactSessionDialog(
                session = session,
                onDismiss = viewModel::dismissArtifactSession,
                onDownload = viewModel::confirmArtifactDownload,
                onCancel = viewModel::cancelArtifactDownload,
                onSave = { artifactSaveLauncher.launch(session.filename) },
                onOpen = {
                    val localPath = session.localPath ?: return@ArtifactSessionDialog
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(localPath))
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, session.mimeType.ifBlank { "*/*" })
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        viewModel.reportArtifactOpenFailure()
                    }
                },
            )
        }
    }
}

@Composable
internal fun ModelUnavailableDialog(
    message: String,
    onDismiss: () -> Unit,
    onChooseModel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.model_unavailable_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.model_unavailable_body))
                Text(
                    stringResource(R.string.model_unavailable_details, message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onChooseModel) {
                Text(stringResource(R.string.choose_another_model))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        modifier = Modifier.testTag(UiTags.ModelUnavailableDialog),
    )
}

@Composable
internal fun ConversationMessageList(
    conversationKey: String?,
    messages: List<com.deerflow.mobile.data.ChatMessage>,
    runActive: Boolean,
    actionBusy: Boolean,
    onHumanInput: (com.deerflow.mobile.data.HumanInputRequest, String, String?) -> Unit,
    onCopy: (String) -> Unit,
    onBranch: (String) -> Unit,
    onArtifact: (String) -> Unit,
    onBrowser: (com.deerflow.mobile.data.BrowserViewSnapshot) -> Unit = {},
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val messageGroups = remember(messages) { groupChatMessages(messages) }
    var expandedProcessingGroups by remember(conversationKey) { mutableStateOf(emptySet<String>()) }
    var initialPositionRestored by remember(conversationKey) { mutableStateOf(false) }
    var userPinnedToBottom by remember(conversationKey) { mutableStateOf(true) }
    var programmaticScroll by remember { mutableStateOf(false) }
    val autoFollowEnabled by rememberUpdatedState(
        shouldAutoFollowConversation(
            messageGroups = messageGroups,
            runActive = runActive,
            expandedProcessingGroups = expandedProcessingGroups,
            userPinnedToBottom = userPinnedToBottom,
        ),
    )

    LaunchedEffect(listState, conversationKey) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.conversationNearBottom(),
                programmaticScroll,
            )
        }.collect { (scrolling, nearBottom, programmatic) ->
            // Ignore layout noise while we drive the list; growth during stream must not unpin.
            if (programmatic) return@collect
            when {
                nearBottom -> userPinnedToBottom = true
                // Only unpin from a settled user scroll away from bottom (not content growth).
                !scrolling && !nearBottom -> userPinnedToBottom = false
            }
        }
    }

    LaunchedEffect(conversationKey, messageGroups.size, messages.lastOrNull()?.text?.length, userPinnedToBottom) {
        if (messageGroups.isEmpty()) return@LaunchedEffect
        if (!initialPositionRestored) {
            programmaticScroll = true
            try {
                listState.scrollToConversationEnd(messageGroups.lastIndex, animated = false)
                userPinnedToBottom = true
                initialPositionRestored = true
            } finally {
                programmaticScroll = false
            }
        } else if (autoFollowEnabled) {
            delay(40)
            programmaticScroll = true
            try {
                // Streaming follow keeps animation; scrollToConversationEnd avoids top-align flash.
                listState.scrollToConversationEnd(messageGroups.lastIndex, animated = true)
                userPinnedToBottom = true
            } finally {
                programmaticScroll = false
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.testTag(UiTags.ConversationList),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(messageGroups, key = { _, group -> group.key }) { _, group ->
            ChatMessageGroupItem(
                group = group,
                runActive = runActive,
                actionBusy = actionBusy,
                onHumanInput = onHumanInput,
                onCopy = onCopy,
                onBranch = onBranch,
                onArtifact = onArtifact,
                onBrowser = onBrowser,
                processingStepsExpanded = group.key in expandedProcessingGroups,
                onProcessingStepsExpandedChange = { groupKey, expanded ->
                    expandedProcessingGroups = if (expanded) {
                        expandedProcessingGroups + groupKey
                    } else {
                        expandedProcessingGroups - groupKey
                    }
                },
            )
        }
    }
}

internal fun shouldAutoFollowConversation(
    messageGroups: List<ChatMessageGroup>,
    runActive: Boolean,
    expandedProcessingGroups: Set<String>,
    userPinnedToBottom: Boolean = true,
): Boolean {
    if (!userPinnedToBottom || messageGroups.isEmpty()) return false
    if (!runActive) return true
    val latestProcessingKey = messageGroups.lastOrNull { it is ChatMessageGroup.Processing }?.key
    return latestProcessingKey !in expandedProcessingGroups
}

internal fun isConversationNearBottom(
    totalItems: Int,
    lastVisibleIndex: Int,
    lastVisibleOffset: Int,
    lastVisibleSize: Int,
    viewportEndOffset: Int,
    canScrollForward: Boolean,
    thresholdPx: Int = CONVERSATION_BOTTOM_THRESHOLD_PX,
): Boolean {
    if (totalItems <= 0) return true
    // Primary signal: LazyList reports no further forward scroll (accounts for contentPadding).
    if (!canScrollForward) return true
    if (lastVisibleIndex < totalItems - 1) return false
    val distanceFromBottom = (lastVisibleOffset + lastVisibleSize) - viewportEndOffset
    return distanceFromBottom <= thresholdPx
}

internal fun conversationScrollOffsetForBottom(itemSize: Int, viewportSize: Int): Int =
    (itemSize - viewportSize).coerceAtLeast(0)

private const val CONVERSATION_BOTTOM_THRESHOLD_PX = 240

private fun LazyListState.conversationNearBottom(thresholdPx: Int = CONVERSATION_BOTTOM_THRESHOLD_PX): Boolean {
    val info = layoutInfo
    if (info.totalItemsCount <= 0) return true
    if (!canScrollForward) return true
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return true
    return isConversationNearBottom(
        totalItems = info.totalItemsCount,
        lastVisibleIndex = lastVisible.index,
        lastVisibleOffset = lastVisible.offset,
        lastVisibleSize = lastVisible.size,
        viewportEndOffset = info.viewportEndOffset,
        canScrollForward = canScrollForward,
        thresholdPx = thresholdPx,
    )
}

/**
 * Jump to the conversation end without a visible top-align flash.
 * Prefer a single scrollToItem with a bottom-pinning offset when item size is known.
 */
private suspend fun LazyListState.scrollToConversationEnd(lastIndex: Int, animated: Boolean = false) {
    if (lastIndex < 0) return
    val viewportSize = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(0)
    val known = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastIndex }
    val targetOffset = if (known != null) {
        conversationScrollOffsetForBottom(known.size, viewportSize)
    } else {
        // Last item not laid out yet: one jump with a large offset (LazyList clamps).
        // Avoid scrollOffset=0 first, which pins the item top and flashes for tall bubbles.
        Int.MAX_VALUE / 4
    }
    if (animated && known != null) {
        animateScrollToItem(lastIndex, scrollOffset = targetOffset)
    } else {
        scrollToItem(lastIndex, scrollOffset = targetOffset)
    }
    // Correct after layout if the first jump used a fallback offset on a short last item.
    val after = layoutInfo
    val item = after.visibleItemsInfo.firstOrNull { it.index == lastIndex } ?: return
    val corrected = conversationScrollOffsetForBottom(
        itemSize = item.size,
        viewportSize = (after.viewportEndOffset - after.viewportStartOffset).coerceAtLeast(0),
    )
    if (corrected != targetOffset && firstVisibleItemIndex == lastIndex) {
        scrollToItem(lastIndex, scrollOffset = corrected)
    }
}

@Composable
internal fun ChatTopBar(
    state: AppUiState,
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
    onModelSelected: (String?) -> Unit,
    onModeSelected: (RunMode) -> Unit,
    onExport: (ConversationExportFormat) -> Unit,
    onOpenRunDetails: () -> Unit = {},
    onOpenBrowser: () -> Unit = {},
    expandedSelector: TopSelectorKind?,
    onExpandedSelectorChange: (TopSelectorKind?) -> Unit,
) {
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    val isConversation = state.route == AppRoute.Conversation
    val showRunDetails = isConversation && state.selectedThread != null
    val showBrowser = showRunDetails && state.capabilities.browserControlEnabled
    val showExportActions = isConversation && state.messages.isNotEmpty()
    TopAppBar(
        modifier = Modifier
            .testTag(UiTags.ChatTopBar)
            .semantics { isTraversalGroup = true },
        navigationIcon = {
            IconButton(
                onClick = if (state.route == AppRoute.Conversation) onBack else onOpenDrawer,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(UiTags.ChatNavigationButton)
                    .semantics { traversalIndex = 0f },
            ) {
                Icon(
                    if (state.route == AppRoute.Conversation) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Menu,
                    contentDescription = stringResource(if (state.route == AppRoute.Conversation) R.string.back else R.string.open_navigation),
                )
            }
        },
        actions = {
            if (showBrowser) {
                IconButton(
                    onClick = onOpenBrowser,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(UiTags.BrowserOpenButton)
                        .semantics { traversalIndex = 3f },
                ) {
                    Icon(Icons.Outlined.DesktopWindows, contentDescription = stringResource(R.string.browser_live_open))
                }
            }
            if (showRunDetails || showExportActions) {
                Box {
                    IconButton(
                        onClick = { overflowMenuExpanded = true },
                        enabled = !state.exportBusy,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(UiTags.ConversationOverflowButton)
                            .semantics { traversalIndex = if (showBrowser) 4f else 3f },
                    ) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_actions))
                    }
                    DropdownMenu(
                        expanded = overflowMenuExpanded,
                        onDismissRequest = { overflowMenuExpanded = false },
                    ) {
                        if (showRunDetails) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.run_details_open)) },
                                leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onOpenRunDetails()
                                },
                                modifier = Modifier.testTag(UiTags.RunDetailsOpenButton),
                            )
                        }
                        if (showRunDetails && showExportActions) HorizontalDivider()
                        if (showExportActions) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_markdown)) },
                                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onExport(ConversationExportFormat.Markdown)
                                },
                                enabled = !state.exportBusy,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_plain_text)) },
                                leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onExport(ConversationExportFormat.PlainText)
                                },
                                enabled = !state.exportBusy,
                            )
                        }
                    }
                }
            }
        },
        title = {
            ChatTopSelectors(
                state = state,
                expandedSelector = expandedSelector,
                onExpandedSelectorChange = onExpandedSelectorChange,
                onModelSelected = onModelSelected,
                onModeSelected = onModeSelected,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
internal fun ChatTopSelectors(
    state: AppUiState,
    expandedSelector: TopSelectorKind?,
    onExpandedSelectorChange: (TopSelectorKind?) -> Unit,
    onModelSelected: (String?) -> Unit,
    onModeSelected: (RunMode) -> Unit,
) {
    val selectedModel = state.capabilities.selectedModel(state.composer.options.modelName)
    val model = selectedModel?.displayName ?: stringResource(R.string.model)
    val availableModes = state.capabilities.availableRunModes(state.composer.options.modelName)
    Row(
        modifier = Modifier.testTag(UiTags.TopSelectors),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopSelector(
            label = model,
            modifier = Modifier
                .widthIn(max = 152.dp)
                .testTag(UiTags.ModelSelector)
                .semantics { traversalIndex = 1f },
            expanded = expandedSelector == TopSelectorKind.Model,
            onClick = {
                onExpandedSelectorChange(if (expandedSelector == TopSelectorKind.Model) null else TopSelectorKind.Model)
            },
            onDismiss = { onExpandedSelectorChange(null) },
        ) {
            Text(
                stringResource(R.string.model),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            if (state.capabilities.models.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.no_models_available)) },
                    onClick = { onExpandedSelectorChange(null) },
                    enabled = false,
                )
            } else {
                state.capabilities.models.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(option.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingIcon = {
                            if (state.composer.options.modelName == option.name) {
                                Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.option_selected))
                            }
                        },
                        onClick = {
                            onModelSelected(option.name)
                            onExpandedSelectorChange(null)
                        },
                    )
                }
            }
        }
        TopSelector(
            label = state.composer.options.mode.label(),
            modifier = Modifier
                .widthIn(max = 92.dp)
                .testTag(UiTags.ModeSelector)
                .semantics { traversalIndex = 2f },
            expanded = expandedSelector == TopSelectorKind.Mode,
            onClick = {
                onExpandedSelectorChange(if (expandedSelector == TopSelectorKind.Mode) null else TopSelectorKind.Mode)
            },
            onDismiss = { onExpandedSelectorChange(null) },
        ) {
            Text(
                stringResource(R.string.run_mode),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            availableModes.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(option.label(), style = MaterialTheme.typography.titleSmall)
                            Text(
                                option.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.capabilities.supportsReasoningEffort(state.composer.options.modelName)) {
                                Text(
                                    "${stringResource(R.string.reasoning_effort)}: ${option.reasoningLabel()} · ${option.reasoningDescription()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (state.composer.options.mode == option) {
                            Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.option_selected))
                        }
                    },
                    onClick = {
                        onModeSelected(option)
                        onExpandedSelectorChange(null)
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TopSelector(
    label: String,
    modifier: Modifier,
    expanded: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    val selectorShape = RoundedCornerShape(20.dp)
    Box {
        Surface(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = selectorShape,
            color = if (expanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (expanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.widthIn(min = 240.dp, max = 340.dp).animateContentSize(ExpressiveMotion.spatial()),
            shape = selectorShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            content = menuContent,
        )
    }
}

internal enum class TopSelectorKind {
    Model,
    Mode,
}

@Composable
private fun ChatWelcome(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.SmartToy, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.chat_welcome), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                R.string.suggestion_research,
                R.string.suggestion_plan,
                R.string.suggestion_summary,
            ).forEach { textId ->
                val suggestion = stringResource(textId)
                Surface(
                    onClick = { onSuggestion(suggestion) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        suggestion,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun TodoProgressHost(
    conversationKey: String?,
    todos: List<TodoItem>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(conversationKey) { mutableStateOf(false) }
    LaunchedEffect(todos.isEmpty()) {
        if (todos.isEmpty()) expanded = false
    }
    BackHandler(enabled = expanded) { expanded = false }

    SharedTransitionLayout(modifier = modifier.testTag(UiTags.TodoProgressHost)) {
        val progressBounds = rememberSharedContentState(key = "todo-progress-${conversationKey.orEmpty()}")
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (todos.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TODO_SUMMARY_SLOT_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !expanded,
                            enter = fadeIn(ExpressiveMotion.fastSpatial()),
                            exit = fadeOut(ExpressiveMotion.fastSpatial()),
                        ) {
                            TodoSummary(
                                todos = todos,
                                onClick = { expanded = true },
                                modifier = Modifier.sharedBounds(
                                    sharedContentState = progressBounds,
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                    enter = fadeIn(ExpressiveMotion.fastSpatial()),
                                    exit = fadeOut(ExpressiveMotion.fastSpatial()),
                                ),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag(UiTags.TodoConversationArea),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded && todos.isNotEmpty(),
                enter = fadeIn(ExpressiveMotion.fastSpatial()),
                exit = fadeOut(ExpressiveMotion.fastSpatial()),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = TODO_PROGRESS_SCRIM_ALPHA))
                            .clickable { expanded = false },
                    )
                    TodoProgressDetails(
                        todos = todos,
                        onDismiss = { expanded = false },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .sharedBounds(
                                sharedContentState = progressBounds,
                                animatedVisibilityScope = this@AnimatedVisibility,
                                enter = fadeIn(ExpressiveMotion.fastSpatial()),
                                exit = fadeOut(ExpressiveMotion.fastSpatial()),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TodoSummary(
    todos: List<TodoItem>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val completed = todos.count { it.status == "completed" }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = shape,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clip(shape)
                .testTag(UiTags.TodoProgressSummary),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.todo_progress, completed, todos.size),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { todoProgress(completed, todos.size) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                )
            }
        }
    }
}

@Composable
private fun TodoProgressDetails(
    todos: List<TodoItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val completed = todos.count { it.status == "completed" }
    val shape = RoundedCornerShape(8.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        modifier = modifier
            .widthIn(max = 900.dp)
            .heightIn(max = TODO_PROGRESS_DETAILS_MAX_HEIGHT)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape)
            .testTag(UiTags.TodoProgressDetails),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    stringResource(R.string.todo_progress, completed, todos.size),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
                }
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { todoProgress(completed, todos.size) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = TODO_PROGRESS_LIST_MAX_HEIGHT)
                    .testTag(UiTags.TodoProgressList),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(todos, key = { index, todo -> "$index:${todo.content}" }) { _, todo ->
                    TodoProgressRow(todo)
                }
            }
        }
    }
}

@Composable
private fun TodoProgressRow(todo: TodoItem) {
    val status = todo.status.lowercase()
    val completed = status == "completed"
    val inProgress = status == "in_progress"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when {
            completed -> Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            inProgress -> LoadingIndicator(Modifier.size(20.dp))
            else -> Icon(
                Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            todo.content,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (inProgress) FontWeight.SemiBold else FontWeight.Normal,
                textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = when {
                inProgress -> MaterialTheme.colorScheme.primary
                completed -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private fun todoProgress(completed: Int, total: Int): Float =
    if (total <= 0) 0f else completed.toFloat() / total.toFloat()

private val TODO_SUMMARY_SLOT_HEIGHT = 68.dp
private val TODO_PROGRESS_DETAILS_MAX_HEIGHT = 480.dp
private val TODO_PROGRESS_LIST_MAX_HEIGHT = 320.dp
private const val TODO_PROGRESS_SCRIM_ALPHA = 0.32f

@Composable
internal fun ArtifactPreviewDialog(
    preview: ArtifactPreviewState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
) {
    ArtifactSessionDialog(
        session = ArtifactSession(
            threadId = "",
            path = preview.path,
            filename = preview.filename,
            mimeType = preview.mimeType,
            totalBytes = null,
            maxDownloadBytes = 0L,
            phase = ArtifactSessionPhase.Ready,
            localPath = preview.localPath,
            text = preview.text,
            textTruncated = preview.textTruncated,
        ),
        onDismiss = onDismiss,
        onDownload = {},
        onCancel = onDismiss,
        onSave = onSave,
        onOpen = onOpen,
    )
}

@Composable
internal fun ArtifactSessionDialog(
    session: ArtifactSession,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val ready = session.phase == ArtifactSessionPhase.Ready
    val downloading = session.phase == ArtifactSessionPhase.Downloading
    val awaiting = session.phase == ArtifactSessionPhase.AwaitingConfirm
    val sizeLabel = session.totalBytes?.let { Formatter.formatFileSize(context, it) }
        ?: stringResource(R.string.artifact_size_unknown)
    val downloadedLabel = Formatter.formatFileSize(context, session.downloadedBytes)
    val markdown = session.filename.endsWith(".md", ignoreCase = true) ||
        session.filename.endsWith(".markdown", ignoreCase = true)
    val language = artifactLanguage(session.filename, session.mimeType)
    AlertDialog(
        onDismissRequest = {
            if (downloading) onCancel() else onDismiss()
        },
        title = { Text(session.filename, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (session.mimeType.isNotBlank()) {
                    Text(session.mimeType, style = MaterialTheme.typography.bodyMedium)
                }
                Text(sizeLabel, style = MaterialTheme.typography.bodyMedium)
                when {
                    awaiting -> {
                        Text(
                            stringResource(R.string.artifact_download_confirmation_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    downloading -> {
                        Text(
                            if (session.totalBytes == null) {
                                stringResource(R.string.artifact_downloaded_unknown_total, downloadedLabel)
                            } else {
                                stringResource(R.string.artifact_downloaded_of_total, downloadedLabel, sizeLabel)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (session.totalBytes == null || session.totalBytes <= 0L) {
                            LoadingIndicator(Modifier.size(24.dp))
                        } else {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = {
                                    (session.downloadedBytes.toFloat() / session.totalBytes).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    ready && session.text != null -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            SelectionContainer {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (session.textTruncated) {
                                        Text(
                                            stringResource(R.string.artifact_preview_truncated),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    when {
                                        markdown && !session.textTruncated -> MarkdownContent(session.text)
                                        language != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                language,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                session.text,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                            )
                                        }
                                        else -> Text(
                                            session.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                awaiting -> TextButton(onClick = onDownload) { Text(stringResource(R.string.download)) }
                downloading -> {}
                ready -> TextButton(onClick = onOpen, enabled = session.localPath != null) {
                    Text(stringResource(R.string.open))
                }
            }
        },
        dismissButton = {
            Row {
                if (ready) {
                    TextButton(onClick = onSave, enabled = session.localPath != null) {
                        Text(stringResource(R.string.save_copy))
                    }
                }
                TextButton(
                    onClick = if (downloading) onCancel else onDismiss,
                ) {
                    Text(stringResource(if (downloading || awaiting) R.string.cancel else R.string.close))
                }
            }
        },
    )
}

internal fun artifactLanguage(filename: String, mimeType: String): String? {
    val extension = filename.substringAfterLast('.', "").lowercase()
    return when {
        mimeType.contains("kotlin") -> "kotlin"
        mimeType.contains("python") -> "python"
        mimeType == "application/javascript" || mimeType == "text/javascript" -> "javascript"
        mimeType == "application/typescript" -> "typescript"
        mimeType == "application/json" -> "json"
        mimeType == "text/css" -> "css"
        mimeType.contains("xml") -> "xml"
        mimeType == "text/x-shellscript" -> "bash"
        mimeType == "text/x-sql" -> "sql"
        extension in setOf("kt", "kts") -> "kotlin"
        extension == "java" -> "java"
        extension == "py" -> "python"
        extension == "js" -> "javascript"
        extension == "ts" -> "typescript"
        extension == "tsx" -> "typescriptreact"
        extension == "jsx" -> "javascriptreact"
        extension == "json" -> "json"
        extension in setOf("yaml", "yml") -> "yaml"
        extension == "toml" -> "toml"
        extension == "css" -> "css"
        extension == "xml" -> "xml"
        extension == "sh" -> "bash"
        extension == "sql" -> "sql"
        extension == "go" -> "go"
        extension == "rs" -> "rust"
        extension == "rb" -> "ruby"
        extension == "php" -> "php"
        extension in setOf("c", "h") -> "c"
        extension in setOf("cpp", "hpp") -> "cpp"
        else -> null
    }
}

@Composable
internal fun MessageComposer(
    state: AppUiState,
    editorValue: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onSkillSelected: (String) -> Unit = {},
    onAttachment: () -> Unit,
    onAgent: () -> Unit,
    onQuickAction: (String, List<String>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
    onPolishInput: () -> Unit = {},
    onCancelPolish: () -> Unit = {},
    onUndoPolish: () -> Unit = {},
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    // This is explicit UI state. Route changes can occur while an input-polish request is in flight.
    val quickCapabilitiesVisible = state.showQuickCapabilities
    val awaitingHumanInput = state.run.awaitingInput
    val composerLocked = awaitingHumanInput || state.inputPolishing
    val stopInFlight = state.run.status == RunStatus.Stopping
    val stoppingDescription = stringResource(R.string.run_stopping)
    val polishEnabled = !state.run.active &&
        !awaitingHumanInput &&
        !state.composer.uploading &&
        state.composer.text.isNotBlank()
    val composerDisplayValue = if (state.inputPolishing) TextFieldValue("") else editorValue
    var composerFocused by remember { mutableStateOf(false) }
    var dismissedSkillSuggestionValue by rememberSaveable { mutableStateOf<String?>(null) }
    val inputFocusRequester = remember { FocusRequester() }
    val slashSkillQuery = leadingSlashSkillQuery(composerDisplayValue.text)
    val slashSkillSuggestions = remember(state.capabilities.skills, slashSkillQuery) {
        slashSkillQuery?.let { matchingSlashSkillSuggestions(state.capabilities.skills, it) }.orEmpty()
    }
    val showSlashSkillSuggestions = !composerLocked &&
        composerFocused &&
        slashSkillQuery != null &&
        slashSkillSuggestions.isNotEmpty() &&
        dismissedSkillSuggestionValue != composerDisplayValue.text
    val composerTopPadding by animateDpAsState(
        targetValue = if (quickCapabilitiesVisible) 4.dp else 14.dp,
        animationSpec = ExpressiveMotion.fastSpatial(),
        label = "composer-top-padding",
    )
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTags.Composer)
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 12.dp, top = composerTopPadding, end = 12.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 820.dp).fillMaxWidth()) {
                AnimatedVisibility(
                    visible = quickCapabilitiesVisible,
                    enter = expandVertically(
                        animationSpec = ExpressiveMotion.fastSpatial(),
                        expandFrom = Alignment.Bottom,
                    ) + fadeIn(animationSpec = ExpressiveMotion.fastSpatial()),
                    exit = shrinkVertically(
                        animationSpec = ExpressiveMotion.fastSpatial(),
                        shrinkTowards = Alignment.Bottom,
                    ) + fadeOut(animationSpec = ExpressiveMotion.fastSpatial()),
                    label = "composer-quick-capabilities",
                ) {
                    CapabilityRow(state, onAgent, onQuickAction)
                }
                if (state.composer.attachments.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(state.composer.attachments, key = { it.uri }) { file ->
                            AttachmentChip(
                                file = file,
                                onRemove = { onRemoveAttachment(file.uri) },
                                onRetry = { onRetryAttachment(file.uri) },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = composerDisplayValue,
                            onValueChange = { value ->
                                dismissedSkillSuggestionValue = null
                                onDraftChange(value)
                            },
                            enabled = !composerLocked,
                            placeholder = {
                                if (state.inputPolishing) {
                                    Row(
                                        modifier = Modifier.testTag(UiTags.InputPolishStatus),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        LoadingIndicator(Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.input_polishing),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                } else {
                                    Text(stringResource(R.string.message_deerflow))
                                }
                            },
                            minLines = 1,
                            maxLines = 6,
                            leadingIcon = {
                                IconButton(
                                    onClick = onAttachment,
                                    enabled = !state.composer.uploading && !composerLocked,
                                    modifier = Modifier.size(48.dp).testTag(UiTags.ComposerAttachmentButton),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_attachment))
                                }
                            },
                            trailingIcon = {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    if (state.inputPolishing) {
                                        IconButton(onClick = onCancelPolish) {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = stringResource(R.string.input_polish_cancel),
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = if (state.canUndoInputPolish) onUndoPolish else onPolishInput,
                                            enabled = state.canUndoInputPolish || polishEnabled,
                                            modifier = Modifier.testTag(UiTags.InputPolishButton),
                                        ) {
                                            Icon(
                                                imageVector = if (state.canUndoInputPolish) {
                                                    Icons.AutoMirrored.Outlined.Undo
                                                } else {
                                                    Icons.Outlined.AutoAwesome
                                                },
                                                contentDescription = stringResource(
                                                    if (state.canUndoInputPolish) {
                                                        R.string.input_polish_undo
                                                    } else {
                                                        R.string.input_polish
                                                    },
                                                ),
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(inputFocusRequester)
                                .onFocusChanged { composerFocused = it.isFocused }
                                .testTag(UiTags.ComposerInput),
                        )
                        DropdownMenu(
                            expanded = showSlashSkillSuggestions,
                            onDismissRequest = { dismissedSkillSuggestionValue = composerDisplayValue.text },
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .testTag(UiTags.SlashSkillSuggestions),
                        ) {
                            slashSkillSuggestions.forEach { skill ->
                                DropdownMenuItem(
                                    text = {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(
                                                "/${skill.name}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                            if (skill.description.isNotBlank()) {
                                                Text(
                                                    skill.description,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                                    },
                                    onClick = {
                                        val updated = replaceLeadingSlashSkillCommand(composerDisplayValue, skill.name)
                                        onDraftChange(updated)
                                        onSkillSelected(skill.name)
                                        inputFocusRequester.requestFocus()
                                    },
                                    modifier = Modifier.testTag(UiTags.SlashSkillSuggestionPrefix + skill.name),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = if (state.run.active && !stopInFlight) onStop else onSend,
                        enabled = (state.run.active && !stopInFlight) || (
                            !composerLocked &&
                                !state.composer.uploading &&
                                (state.composer.text.isNotBlank() || state.composer.attachments.isNotEmpty())
                            ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag(UiTags.SendStopButton),
                        ) {
                        when {
                            stopInFlight -> LoadingIndicator(
                                Modifier
                                    .size(24.dp)
                                    .testTag(UiTags.StopRunProgress)
                                    .semantics { contentDescription = stoppingDescription },
                            )
                            state.composer.uploading -> LoadingIndicator(Modifier.size(24.dp))
                            state.run.active -> Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.stop_run))
                            else -> Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send_message))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CapabilityRow(
    state: AppUiState,
    onAgent: () -> Unit,
    onQuickAction: (String, List<String>) -> Unit,
) {
    val actions = listOf(
        QuickActionSpec(
            label = stringResource(R.string.quick_surprise),
            prompt = stringResource(R.string.quick_surprise_prompt),
            keywords = listOf("grill-me", "brainstorm", "惊喜"),
            icon = Icons.Outlined.AutoAwesome,
        ),
        QuickActionSpec(
            label = stringResource(R.string.quick_writing),
            prompt = stringResource(R.string.quick_writing_prompt),
            keywords = listOf("write", "writer", "writing", "写作", "文案"),
            icon = Icons.Outlined.EditNote,
        ),
        QuickActionSpec(
            label = stringResource(R.string.quick_research),
            prompt = stringResource(R.string.quick_research_prompt),
            keywords = listOf("research", "report", "调研", "研究"),
            icon = Icons.Outlined.Search,
        ),
        QuickActionSpec(
            label = stringResource(R.string.quick_collect),
            prompt = stringResource(R.string.quick_collect_prompt),
            keywords = listOf("collect", "extract", "收集", "整理"),
            icon = Icons.Outlined.CollectionsBookmark,
        ),
        QuickActionSpec(
            label = stringResource(R.string.quick_learn),
            prompt = stringResource(R.string.quick_learn_prompt),
            keywords = listOf("learn", "tutor", "学习", "教学"),
            icon = Icons.Outlined.School,
        ),
        QuickActionSpec(
            label = stringResource(R.string.quick_web),
            prompt = stringResource(R.string.quick_web_prompt),
            keywords = listOf("frontend", "web", "网页", "website"),
            icon = Icons.Outlined.Language,
        ),
        QuickActionSpec(
            label = stringResource(R.string.quick_image),
            prompt = stringResource(R.string.quick_image_prompt),
            keywords = listOf("image", "图片", "generation", "视觉"),
            icon = Icons.Outlined.Image,
        ),
    )
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier.fillMaxWidth().testTag(UiTags.QuickCapabilities),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = Color.Transparent,
            toolbarContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        shape = MaterialTheme.shapes.extraLarge,
        expandedShadowElevation = 0.dp,
        collapsedShadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = onAgent,
                label = { Text(state.composer.options.agentLabel()) },
                leadingIcon = { Icon(Icons.Outlined.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            actions.forEach { action ->
                AssistChip(
                    onClick = { onQuickAction(action.prompt, action.keywords) },
                    label = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

private data class QuickActionSpec(
    val label: String,
    val prompt: String,
    val keywords: List<String>,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
internal fun AttachmentChip(file: PendingAttachment, onRemove: () -> Unit, onRetry: () -> Unit) {
    var expanded by rememberSaveable(file.uri) { mutableStateOf(false) }
    AssistChip(
        onClick = { expanded = !expanded },
        label = {
            Text(
                file.filename,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                softWrap = expanded,
            )
        },
        leadingIcon = {
            when (file.status) {
                AttachmentStatus.Uploading -> LoadingIndicator(Modifier.size(20.dp))
                AttachmentStatus.Failed -> Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.upload_failed), modifier = Modifier.size(18.dp))
                else -> Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
        trailingIcon = {
            if (file.status == AttachmentStatus.Failed) {
                IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.retry_upload), modifier = Modifier.size(16.dp))
                }
            } else {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp), enabled = file.status != AttachmentStatus.Uploading) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.remove_attachment), modifier = Modifier.size(16.dp))
                }
            }
        },
        modifier = Modifier.widthIn(max = 240.dp),
    )
}

@Composable
internal fun AttachmentSheet(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag(UiTags.AttachmentSheet)) {
        Text(stringResource(R.string.add_to_conversation), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AttachmentAction(Icons.Outlined.CameraAlt, stringResource(R.string.camera), Modifier.weight(1f), onCamera)
            AttachmentAction(Icons.Outlined.PhotoLibrary, stringResource(R.string.photos), Modifier.weight(1f), onPhotos)
            AttachmentAction(Icons.Outlined.FolderOpen, stringResource(R.string.files), Modifier.weight(1f), onFiles)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AttachmentAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.height(88.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AgentPickerSheet(state: AppUiState, viewModel: AppViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.agent),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                AgentOption("lead_agent", stringResource(R.string.lead_agent_description), state.composer.options.assistantId == "lead_agent") {
                    viewModel.selectAgent("lead_agent")
                    onDismiss()
                }
            }
            items(state.capabilities.agents.customAgentsOnly(), key = { it.name }) { agent ->
                AgentOption(agent.name, agent.description, state.composer.options.assistantId == agent.name) {
                    viewModel.selectAgent(agent.name)
                    onDismiss()
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun AgentOption(name: String, description: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(name, fontWeight = FontWeight.Medium)
                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun RunMode.label(): String = when (this) {
    RunMode.Flash -> stringResource(R.string.mode_flash)
    RunMode.Thinking -> stringResource(R.string.mode_thinking)
    RunMode.Pro -> stringResource(R.string.mode_plan)
    RunMode.Ultra -> stringResource(R.string.mode_ultra)
}

@Composable
private fun RunMode.description(): String = when (this) {
    RunMode.Flash -> stringResource(R.string.mode_flash_description)
    RunMode.Thinking -> stringResource(R.string.mode_thinking_description)
    RunMode.Pro -> stringResource(R.string.mode_plan_description)
    RunMode.Ultra -> stringResource(R.string.mode_ultra_description)
}

@Composable
private fun RunMode.reasoningLabel(): String = when (this) {
    RunMode.Flash -> stringResource(R.string.reasoning_effort_minimal)
    RunMode.Thinking -> stringResource(R.string.reasoning_effort_low)
    RunMode.Pro -> stringResource(R.string.reasoning_effort_medium)
    RunMode.Ultra -> stringResource(R.string.reasoning_effort_high)
}

@Composable
private fun RunMode.reasoningDescription(): String = when (this) {
    RunMode.Flash -> stringResource(R.string.reasoning_effort_minimal_description)
    RunMode.Thinking -> stringResource(R.string.reasoning_effort_low_description)
    RunMode.Pro -> stringResource(R.string.reasoning_effort_medium_description)
    RunMode.Ultra -> stringResource(R.string.reasoning_effort_high_description)
}

private const val MAX_SLASH_SKILL_SUGGESTIONS = 6

internal fun leadingSlashSkillQuery(value: String): String? {
    if (!value.startsWith("/")) return null
    val query = value.drop(1)
    if (query.contains("/") || query.any(Char::isWhitespace)) return null
    return query
}

internal fun matchingSlashSkillSuggestions(skills: List<SkillInfo>, query: String): List<SkillInfo> {
    val normalizedQuery = query.lowercase()
    return skills
        .withIndex()
        .filter { (_, skill) ->
            skill.enabled && (normalizedQuery.isBlank() || skill.name.lowercase().contains(normalizedQuery))
        }
        .sortedWith(
            compareByDescending<IndexedValue<SkillInfo>> {
                it.value.name.lowercase().startsWith(normalizedQuery)
            }.thenBy { it.index },
        )
        .take(MAX_SLASH_SKILL_SUGGESTIONS)
        .map { it.value }
}

internal fun replaceLeadingSlashSkillCommand(value: TextFieldValue, skillName: String): TextFieldValue {
    val normalizedName = skillName.trim().removePrefix("/")
    if (normalizedName.isBlank() || leadingSlashSkillQuery(value.text) == null) return value
    val command = "/$normalizedName "
    return TextFieldValue(
        text = command,
        selection = TextRange(command.length),
    )
}

internal fun insertSkillCommand(value: TextFieldValue, skillName: String): TextFieldValue {
    val normalizedName = skillName.trim().removePrefix("/")
    if (normalizedName.isBlank()) return value

    val selectionStart = minOf(value.selection.start, value.selection.end)
    val selectionEnd = maxOf(value.selection.start, value.selection.end)
    val command = "/$normalizedName "
    val updatedText = value.text.replaceRange(selectionStart, selectionEnd, command)
    return TextFieldValue(
        text = updatedText,
        selection = TextRange(selectionStart + command.length),
    )
}

private fun com.deerflow.mobile.data.RunOptions.agentLabel(): String =
    if (assistantId == "lead_agent") "DeerFlow" else assistantId

private fun createCameraUri(context: Context): Uri {
    val file = File.createTempFile("deerflow-camera-", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
