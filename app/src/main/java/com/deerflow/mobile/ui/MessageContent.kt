@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deerflow.mobile.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deerflow.mobile.R
import com.deerflow.mobile.data.ChatMessage
import com.deerflow.mobile.data.ChatMessageGroup
import com.deerflow.mobile.data.BrowserViewSnapshot
import com.deerflow.mobile.data.HumanInputRequest
import com.deerflow.mobile.data.MessageBlock
import com.deerflow.mobile.data.MessageRole
import com.deerflow.mobile.data.TokenUsage
import com.deerflow.mobile.data.ToolIconKind
import com.deerflow.mobile.data.drawableResId
import com.deerflow.mobile.data.isInlineDisplayableImageUrl
import com.deerflow.mobile.data.resolveMessageImageURL
import com.deerflow.mobile.data.toolIconKind
import com.deerflow.mobile.ui.theme.ExpressiveMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChatMessageGroupItem(
    group: ChatMessageGroup,
    runActive: Boolean,
    actionBusy: Boolean = false,
    onHumanInput: (HumanInputRequest, String, String?) -> Unit,
    onCopy: (String) -> Unit = {},
    onBranch: (String) -> Unit = {},
    onArtifact: (String) -> Unit = {},
    onBrowser: (BrowserViewSnapshot) -> Unit = {},
    processingStepsExpanded: Boolean? = null,
    onProcessingStepsExpandedChange: (String, Boolean) -> Unit = { _, _ -> },
) {
    when (group) {
        is ChatMessageGroup.Message -> MessageItem(
            message = group.message,
            showReasoning = group.showReasoning,
            trailingArtifacts = group.trailingArtifacts,
            actionsEnabled = !runActive && !actionBusy,
            onCopy = onCopy,
            onBranch = onBranch,
            onArtifact = onArtifact,
        )
        is ChatMessageGroup.Processing -> ProcessingMessageGroup(
            group = group,
            runActive = runActive,
            onArtifact = onArtifact,
            onBrowser = onBrowser,
            expanded = processingStepsExpanded,
            onExpandedChange = onProcessingStepsExpandedChange,
        )
        is ChatMessageGroup.HumanInput -> HumanInputCard(
            request = group.request,
            response = group.response,
            isLatestOpen = group.isLatestOpen,
            runActive = runActive,
            onSubmit = onHumanInput,
        )
        is ChatMessageGroup.Approval -> HumanInputCard(
            request = group.request,
            response = group.response,
            isLatestOpen = group.isLatestOpen,
            runActive = runActive,
            onSubmit = onHumanInput,
            approval = true,
        )
    }
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    showReasoning: Boolean,
    trailingArtifacts: List<MessageBlock.Artifact>,
    actionsEnabled: Boolean,
    onCopy: (String) -> Unit,
    onBranch: (String) -> Unit,
    onArtifact: (String) -> Unit,
) {
    val user = message.role == MessageRole.User
    val clipboard = LocalClipboardManager.current
    val reasoning = message.blocks.filterIsInstance<MessageBlock.Reasoning>().lastOrNull()?.takeIf { showReasoning }
    val bubbleShape = MaterialTheme.shapes.medium
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            color = when (message.role) {
                MessageRole.User -> MaterialTheme.colorScheme.primaryContainer
                MessageRole.Assistant -> Color.Transparent
                MessageRole.Tool, MessageRole.System -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            shape = bubbleShape,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .animateContentSize(ExpressiveMotion.spatial()),
        ) {
            Column(
            // The assistant reply aligns horizontally with the thinking block's card
            // edge (list padding only), so reply and thinking text share one margin.
            Modifier.padding(if (user) PaddingValues(14.dp) else PaddingValues(vertical = 8.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
                if (!user) {
                    Text(
                        if (message.role == MessageRole.Assistant) "DeerFlow" else stringResource(R.string.system),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (user) {
                    SelectionContainer {
                        Text(
                            message.text,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.4.sp),
                        )
                    }
                } else {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (message.role == MessageRole.Assistant) {
                                reasoning?.let { FinalReasoningDisclosure(it.text) }
                            }
                            message.blocks
                                .filterNot {
                                    it is MessageBlock.Reasoning ||
                                        it is MessageBlock.ToolCall ||
                                        it is MessageBlock.ToolResult
                                }
                                .forEach { MessageBlockView(it, onArtifact, streaming = message.isStreaming) }
                        }
                    }
                    PresentedArtifactRow(trailingArtifacts, onArtifact)
                }
                MessageAttachments(message)
                if (message.role == MessageRole.Assistant) {
                    message.tokenUsage?.let { usage -> TokenUsageLabel(usage) }
                }
                if (message.isStreaming) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
                if (message.id.isNotBlank() && message.role == MessageRole.Assistant) {
                    Row(
                        modifier = Modifier.align(Alignment.Start),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        MessageActionButton(
                            icon = Icons.Outlined.ContentCopy,
                            label = stringResource(R.string.copy),
                            enabled = message.text.isNotBlank(),
                            onClick = {
                                clipboard.setText(AnnotatedString(message.text))
                                onCopy(message.id)
                            },
                        )
                        MessageActionButton(
                            icon = Icons.AutoMirrored.Outlined.CallSplit,
                            label = stringResource(R.string.branch_conversation),
                            enabled = actionsEnabled,
                            onClick = { onBranch(message.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenUsageLabel(usage: TokenUsage) {
    Text(
        stringResource(
            R.string.token_usage,
            formatTokenCount(usage.inputTokens),
            formatTokenCount(usage.outputTokens),
            formatTokenCount(usage.totalTokens),
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun formatTokenCount(value: Long): String = when {
    value >= 1_000_000 -> compactTokenCount(value, 1_000_000, "m")
    value >= 1_000 -> compactTokenCount(value, 1_000, "k")
    else -> value.toString()
}

private fun compactTokenCount(value: Long, divisor: Long, suffix: String): String {
    val scaled = value.toDouble() / divisor
    val rounded = (scaled * 10).toInt() / 10.0
    val text = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    return text + suffix
}

@Composable
private fun MessageActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ProcessingMessageGroup(
    group: ChatMessageGroup.Processing,
    runActive: Boolean,
    onArtifact: (String) -> Unit,
    onBrowser: (BrowserViewSnapshot) -> Unit,
    expanded: Boolean?,
    onExpandedChange: (String, Boolean) -> Unit,
) {
    val steps = remember(group.messages) { processingSteps(group.messages) }
    val lastToolIndex = steps.indexOfLast { it is ProcessingStep.Tool }
    val aboveLastTool = steps.take(lastToolIndex.coerceAtLeast(0))
    val collapsibleAboveLastTool = aboveLastTool.filterNot { it is ProcessingStep.AssistantText }
    val lastTool = steps.getOrNull(lastToolIndex) as? ProcessingStep.Tool
    val stepReasoning = if (lastToolIndex >= 0) {
        steps.drop(lastToolIndex + 1).filterIsInstance<ProcessingStep.Reasoning>().lastOrNull()
    } else {
        steps.filterIsInstance<ProcessingStep.Reasoning>().lastOrNull()
    }
    val finalReasoning = group.trailingReasoning?.text ?: stepReasoning?.text
    var savedShowPreviousSteps by rememberSaveable(group.key) { mutableStateOf(false) }
    val showPreviousSteps = expanded ?: savedShowPreviousSteps

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        // Matches the user message bubble corner radius.
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .testTag(UiTags.ProcessingCard)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (collapsibleAboveLastTool.isNotEmpty()) {
                ProcessingStepsToggle(
                    count = collapsibleAboveLastTool.size,
                    expanded = showPreviousSteps,
                    onClick = {
                        val next = !showPreviousSteps
                        if (expanded == null) savedShowPreviousSteps = next
                        onExpandedChange(group.key, next)
                    },
                )
            }
            // Keep the disclosure reflow atomic. Animating each historical step separately
            // briefly remeasures the outer Column and makes the gap below this control flicker.
            aboveLastTool
                .filter { showPreviousSteps || it is ProcessingStep.AssistantText }
                .forEach { step ->
                    ProcessingStepView(step = step, runActive = false, onArtifact = onArtifact, onBrowser = onBrowser)
                }
            lastTool?.let {
                ProcessingStepView(step = it, runActive = runActive, onArtifact = onArtifact, onBrowser = onBrowser)
            }
            steps.drop(lastToolIndex + 1)
                .filterNot { it == stepReasoning }
                .forEach {
                    ProcessingStepView(
                        step = it,
                        runActive = false,
                        onArtifact = onArtifact,
                        onBrowser = onBrowser,
                        streaming = runActive,
                    )
                }
            finalReasoning?.let { FinalReasoningDisclosure(it) }
            if (runActive && (steps.isEmpty() || lastTool?.result != null)) {
                ThinkingIndicator()
            }
        }
    }
}

@Composable
private fun ProcessingStepsToggle(count: Int, expanded: Boolean, onClick: () -> Unit) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = ExpressiveMotion.fastSpatial(),
        label = "processing-steps-arrow",
    )
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.Psychology,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(if (expanded) R.string.fewer_tool_steps else R.string.more_tool_steps, count),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
            )
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(arrowRotation),
            )
        }
    }
}

private sealed interface ProcessingStep {
    val key: String

    data class AssistantText(val block: MessageBlock, override val key: String) : ProcessingStep
    data class Reasoning(val text: String, override val key: String) : ProcessingStep
    data class Tool(
        val call: MessageBlock.ToolCall,
        val result: MessageBlock.ToolResult?,
        override val key: String,
    ) : ProcessingStep

    data class Subtask(val value: MessageBlock.Subtask, override val key: String) : ProcessingStep
}

private fun processingSteps(messages: List<ChatMessage>): List<ProcessingStep> {
    val results = messages
        .flatMap { it.blocks }
        .filterIsInstance<MessageBlock.ToolResult>()
        .associateBy { it.callId }

    return buildList {
        messages.forEachIndexed { messageIndex, message ->
            if (message.role != MessageRole.Assistant) return@forEachIndexed
            val containsToolCall = message.blocks.any { it is MessageBlock.ToolCall }
            message.blocks.forEachIndexed { blockIndex, block ->
                val key = "${message.id.ifBlank { messageIndex.toString() }}:$blockIndex"
                when (block) {
                    is MessageBlock.Markdown, is MessageBlock.Code, is MessageBlock.Quote -> {
                        if (containsToolCall) add(ProcessingStep.AssistantText(block, key))
                    }
                    is MessageBlock.Reasoning -> add(ProcessingStep.Reasoning(block.text, key))
                    is MessageBlock.ToolCall -> {
                        if (block.name !in setOf("task", "present_files")) {
                            add(ProcessingStep.Tool(block, results[block.id], key))
                        }
                    }
                    is MessageBlock.Subtask -> add(ProcessingStep.Subtask(block, key))
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ProcessingStepView(
    step: ProcessingStep,
    runActive: Boolean,
    onArtifact: (String) -> Unit,
    onBrowser: (BrowserViewSnapshot) -> Unit,
    streaming: Boolean = false,
) {
    when (step) {
        is ProcessingStep.AssistantText -> MessageBlockView(step.block, onArtifact, streaming = streaming)
        is ProcessingStep.Reasoning -> MarkdownContent(
            step.text,
            Modifier.padding(start = 28.dp),
            onArtifact,
            streaming = streaming,
        )
        is ProcessingStep.Tool -> ToolCallSummary(step.call, step.result, active = runActive, onBrowser = onBrowser)
        is ProcessingStep.Subtask -> SubtaskStep(step.value)
    }
}

@Composable
private fun SubtaskStep(subtask: MessageBlock.Subtask) {
    var expanded by rememberSaveable(subtask.callId) { mutableStateOf(false) }
    val displaySteps = remember(subtask.steps, subtask.status) {
        com.deerflow.mobile.data.subtaskStepsForDisplay(subtask.steps, subtask.status)
    }
    val latestStep = displaySteps.lastOrNull()
    val progressHint = when {
        latestStep == null -> null
        latestStep.kind == "tool" -> latestStep.toolName?.takeIf { it.isNotBlank() }
            ?: latestStep.text.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull()
        latestStep.toolCalls.isNotEmpty() -> latestStep.toolCalls.first()
        latestStep.text.isNotBlank() -> latestStep.text.lineSequence().firstOrNull()?.take(80)
        else -> null
    }
    val statusText = when (subtask.status) {
        MessageBlock.SubtaskStatus.InProgress -> progressHint
            ?.let { stringResource(R.string.subtask_running_step, it) }
            ?: stringResource(R.string.subtask_running)
        MessageBlock.SubtaskStatus.Completed -> stringResource(R.string.subtask_completed)
        MessageBlock.SubtaskStatus.Failed -> stringResource(R.string.subtask_failed)
    }
    Surface(
        onClick = { expanded = !expanded },
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(subtask.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = if (subtask.status == MessageBlock.SubtaskStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when (subtask.status) {
                    MessageBlock.SubtaskStatus.InProgress -> LoadingIndicator(Modifier.size(20.dp))
                    MessageBlock.SubtaskStatus.Completed -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    MessageBlock.SubtaskStatus.Failed -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.rotate(if (expanded) 180f else 0f))
            }
            if (expanded) {
                subtask.subagentType.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                subtask.prompt.takeIf { it.isNotBlank() }?.let { MarkdownContent(it) }
                if (displaySteps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        displaySteps.takeLast(12).forEach { step ->
                            val label = when {
                                step.kind == "tool" -> step.toolName?.takeIf { it.isNotBlank() } ?: step.text
                                step.toolCalls.isNotEmpty() -> step.toolCalls.joinToString(", ")
                                else -> step.text
                            }.ifBlank { "…" }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 4.dp),
                            ) {
                                Icon(
                                    if (step.kind == "tool" || step.toolCalls.isNotEmpty()) Icons.Outlined.Code else Icons.Outlined.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                subtask.result?.takeIf { it.isNotBlank() }?.let { MarkdownContent(it) }
                subtask.error?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ToolCallSummary(
    call: MessageBlock.ToolCall,
    result: MessageBlock.ToolResult?,
    active: Boolean,
    onBrowser: (BrowserViewSnapshot) -> Unit,
) {
    val failed = result?.failed == true
    val presentation = remember(call.name, call.detail) { toolCallPresentation(call) }
    val label = localizedToolCallLabel(presentation.label)

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(toolCallIconRes(call.name)),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                presentation.detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                failed -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                active -> LoadingIndicator(Modifier.size(20.dp))
                else -> Unit
            }
        }
        result?.browserView?.let { BrowserToolPreview(it, onBrowser) }
    }
}

@Composable
private fun BrowserToolPreview(browserView: BrowserViewSnapshot, onBrowser: (BrowserViewSnapshot) -> Unit) {
    val imageContext = LocalMarkdownImageContext.current
    val resolvedScreenshot = remember(browserView, imageContext.serverUrl, imageContext.threadId, imageContext.artifactPaths) {
        if (imageContext.serverUrl.isBlank() || imageContext.threadId.isBlank()) {
            browserView.screenshot
        } else {
            resolveMessageImageURL(
                browserView.screenshot,
                imageContext.serverUrl,
                imageContext.threadId,
                imageContext.artifactPaths,
            )
        }
    }
    var bitmap by remember(resolvedScreenshot) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(resolvedScreenshot) {
        bitmap = if (isInlineDisplayableImageUrl(resolvedScreenshot)) {
            withContext(Dispatchers.IO) { loadCachedDisplayBitmap(resolvedScreenshot) }
        } else {
            null
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, top = 2.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onBrowser(browserView) },
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = stringResource(R.string.browser_live_frame),
                modifier = Modifier.fillMaxWidth().height(148.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tool_open_browser), style = MaterialTheme.typography.labelLarge)
                Text(
                    browserView.title.ifBlank { browserView.url },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.tool_open_browser), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FinalReasoningDisclosure(text: String) {
    var expanded by rememberSaveable(text.hashCode()) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.reasoning), modifier = Modifier.padding(start = 8.dp, end = 8.dp))
            Box(Modifier.weight(1f))
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
        if (expanded) MarkdownContent(text, Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoadingIndicator(Modifier.size(22.dp))
        Text(
            stringResource(R.string.thinking),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HumanInputCard(
    request: HumanInputRequest,
    response: com.deerflow.mobile.data.HumanInputResponse?,
    isLatestOpen: Boolean,
    runActive: Boolean,
    onSubmit: (HumanInputRequest, String, String?) -> Unit,
    approval: Boolean = false,
) {
    var answer by rememberSaveable(request.requestId) { mutableStateOf("") }
    var textInputFocused by remember { mutableStateOf(false) }
    val submitRequester = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val pending = runActive && isLatestOpen && response == null
    val enabled = isLatestOpen && response == null && !runActive

    LaunchedEffect(textInputFocused, imeBottom) {
        if (textInputFocused && imeBottom > 0) submitRequester.bringIntoView()
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (approval) Icons.Outlined.ErrorOutline else Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null,
                tint = if (approval) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        request.title ?: stringResource(if (approval) R.string.approval_required else R.string.need_your_help),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    when {
                        response != null -> Text(stringResource(R.string.answered), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        pending -> LoadingIndicator(Modifier.size(20.dp))
                    }
                }
                request.context?.takeIf { it.isNotBlank() }?.let { MarkdownContent(it) }
                MarkdownContent(request.question)
                request.options.forEach { option ->
                    OutlinedButton(
                        onClick = { onSubmit(request, option.value, option.id) },
                        enabled = enabled,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(option.label, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (request.inputMode == "free_text" || request.inputMode == "choice_with_other") {
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        enabled = enabled,
                        placeholder = { Text(stringResource(R.string.human_input_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                            .onFocusChanged { textInputFocused = it.isFocused }
                            .testTag(UiTags.HumanInputText),
                    )
                    Button(
                        onClick = { onSubmit(request, answer, null) },
                        enabled = enabled && answer.isNotBlank(),
                        modifier = Modifier
                            .align(Alignment.End)
                            .bringIntoViewRequester(submitRequester)
                            .testTag(UiTags.HumanInputSubmit),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.submit_answer), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                response?.let {
                    Text(stringResource(R.string.answered_value, it.value), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MessageBlockView(
    block: MessageBlock,
    onArtifact: (String) -> Unit = {},
    streaming: Boolean = false,
) {
    when (block) {
        is MessageBlock.Markdown -> MarkdownContent(block.text, onArtifact = onArtifact, streaming = streaming)
        is MessageBlock.Code -> StreamingReveal(animate = streaming) { CodeDetail(block.code, block.language) }
        is MessageBlock.Quote -> Row(
            // IntrinsicSize.Min lets the accent bar stretch to the full quote height;
            // a fixed 48.dp bar broke visually on multi-line quotes.
            Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
            MarkdownContent(block.text, Modifier.weight(1f), onArtifact, streaming)
        }
        is MessageBlock.Reasoning -> FinalReasoningDisclosure(block.text)
        is MessageBlock.ToolCall -> Unit
        is MessageBlock.ToolResult -> Unit
        is MessageBlock.Subtask -> SubtaskStep(block)
        is MessageBlock.HumanInput -> Unit
        is MessageBlock.Approval -> Unit
        is MessageBlock.HumanInputResponseBlock -> Unit
        is MessageBlock.Todo -> StreamingReveal(animate = streaming) {
            val completed = block.status == "completed"
            val inProgress = block.status == "in_progress"
            Text(
                block.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (inProgress) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = when {
                    inProgress -> MaterialTheme.colorScheme.primary
                    completed -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        is MessageBlock.Artifact -> FileAttachmentChip(
            filename = block.title,
            onClick = { onArtifact(block.path) },
            leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp)) },
            expandable = false,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        is MessageBlock.Error -> Text(block.message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun CodeDetail(code: String, language: String? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            language?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            Text(code, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

@Composable
private fun PresentedArtifactRow(
    artifacts: List<MessageBlock.Artifact>,
    onArtifact: (String) -> Unit,
) {
    if (artifacts.isEmpty()) return
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTags.PresentedArtifactRow),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(
            items = artifacts,
            key = { index, artifact -> "${artifact.path}:$index" },
        ) { index, artifact ->
            FileAttachmentChip(
                filename = artifact.title,
                leadingIcon = {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.testTag(UiTags.PresentedArtifactPrefix + index),
                expandable = false,
                onClick = { onArtifact(artifact.path) },
            )
        }
    }
}

@Composable
private fun MessageAttachments(message: ChatMessage) {
    if (message.attachments.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(message.attachments, key = { it.path ?: it.filename }) { file ->
            FileAttachmentChip(
                filename = file.filename,
                leadingIcon = {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

@Composable
internal fun FileAttachmentChip(
    filename: String,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    expandable: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable(filename) { mutableStateOf(false) }
    val showFullName = expandable && expanded
    AssistChip(
        onClick = {
            when {
                expandable -> expanded = !expanded
                onClick != null -> onClick()
            }
        },
        label = {
            Text(
                filename,
                maxLines = if (showFullName) Int.MAX_VALUE else 1,
                overflow = if (showFullName) TextOverflow.Visible else TextOverflow.Ellipsis,
                softWrap = showFullName,
            )
        },
        leadingIcon = leadingIcon,
        modifier = if (showFullName) modifier else modifier.widthIn(max = 220.dp),
    )
}

@Composable
private fun localizedToolCallLabel(label: ToolCallLabel): String = when (label) {
    is ToolCallLabel.WebSearch -> label.query?.let { stringResource(R.string.tool_search_on_web_for, it) }
        ?: stringResource(R.string.tool_search_related_info)
    is ToolCallLabel.ImageSearch -> label.query?.let { stringResource(R.string.tool_search_related_images_for, it) }
        ?: stringResource(R.string.tool_search_related_images)
    ToolCallLabel.ViewWebPage -> stringResource(R.string.tool_view_web_page)
    is ToolCallLabel.BrowserNavigate -> label.url?.let { stringResource(R.string.tool_browser_navigate_url, it) }
        ?: stringResource(R.string.tool_browser_navigate)
    ToolCallLabel.BrowserSnapshot -> stringResource(R.string.tool_browser_snapshot)
    ToolCallLabel.BrowserClick -> stringResource(R.string.tool_browser_click)
    ToolCallLabel.BrowserType -> stringResource(R.string.tool_browser_type)
    ToolCallLabel.BrowserGetText -> stringResource(R.string.tool_browser_get_text)
    ToolCallLabel.BrowserBack -> stringResource(R.string.tool_browser_back)
    ToolCallLabel.BrowserScreenshot -> stringResource(R.string.tool_browser_screenshot)
    ToolCallLabel.BrowserClose -> stringResource(R.string.tool_browser_close)
    ToolCallLabel.PresentFiles -> stringResource(R.string.tool_present_files)
    ToolCallLabel.ListFolder -> stringResource(R.string.tool_list_folder)
    ToolCallLabel.ReadFile -> stringResource(R.string.tool_read_file)
    is ToolCallLabel.ReadSkill -> label.name?.let { stringResource(R.string.tool_read_named_skill, it) }
        ?: stringResource(R.string.tool_read_skill)
    ToolCallLabel.WriteFile -> stringResource(R.string.tool_write_file)
    ToolCallLabel.ExecuteCommand -> stringResource(R.string.tool_execute_command)
    ToolCallLabel.NeedYourHelp -> stringResource(R.string.need_your_help)
    ToolCallLabel.WriteTodos -> stringResource(R.string.tool_update_todos)
    ToolCallLabel.GenericTool -> stringResource(R.string.tool_generic)
    is ToolCallLabel.Description -> label.value
    is ToolCallLabel.UseTool -> stringResource(R.string.tool_use, label.name)
}

private fun toolCallIconRes(toolName: String): Int =
    (toolIconKind(toolName) ?: ToolIconKind.ExecuteCommand).drawableResId()
