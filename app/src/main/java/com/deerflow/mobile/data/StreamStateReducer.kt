package com.deerflow.mobile.data

/** Merge a token event. Assistant text and reasoning are deltas; structured blocks are keyed. */
internal fun mergeStreamChunk(messages: List<ChatMessage>, chunk: ChatMessage): List<ChatMessage> =
    upsertMessage(messages, chunk.copy(isStreaming = chunk.role == MessageRole.Assistant), appendText = true)

/** Apply one LangGraph reducer write without treating it as a full state snapshot. */
internal fun mergeStreamPatch(messages: List<ChatMessage>, patch: StreamPatch): List<ChatMessage> {
    var merged = messages
    patch.messages.forEach { operation ->
        merged = when (operation) {
            is StreamMessageOperation.Upsert -> upsertMessage(merged, operation.message, appendText = false)
            is StreamMessageOperation.Remove -> merged.filterNot { it.id == operation.id }
            StreamMessageOperation.RemoveAll -> emptyList()
        }
    }
    return merged
}

/** Kept for full thread-state callers; unlike stream patches it never deletes missing messages. */
internal fun mergeStreamSnapshot(current: List<ChatMessage>, snapshot: ThreadSnapshot): List<ChatMessage> =
    if (snapshot.hasMessages) {
        snapshot.messages.fold(current) { merged, message -> upsertMessage(merged, message, appendText = false) }
    } else {
        current
    }

internal fun projectVisibleMessages(
    serverMessages: List<ChatMessage>,
    pendingUserMessage: ChatMessage?,
    pendingUserIndex: Int?,
): List<ChatMessage> {
    val unconfirmedPending = pendingUserMessage?.takeUnless { pending ->
        serverMessages.any { server -> confirmsPendingUserMessage(pending, server) }
    }
    val withPending = serverMessages.toMutableList().apply {
        unconfirmedPending?.let { message ->
            add((pendingUserIndex ?: size).coerceIn(0, size), message)
        }
    }
    val visible = mutableListOf<ChatMessage>()
    val indexByKey = mutableMapOf<String, Int>()
    withPending.forEach { message ->
        val key = message.displayIdentity()
        val index = indexByKey[key]
        if (index == null) {
            indexByKey[key] = visible.size
            visible += message
        } else {
            visible[index] = mergeMessage(visible[index], message, appendText = false)
        }
    }
    return visible
}

/** The Gateway may replace a client UUID with a run-scoped human-message ID. */
internal fun confirmsPendingUserMessage(pending: ChatMessage, server: ChatMessage): Boolean =
    server.id == pending.id || (
        pending.role == MessageRole.User &&
            server.role == MessageRole.User &&
            pending.text.isNotBlank() &&
            server.text == pending.text
        )

private fun upsertMessage(messages: List<ChatMessage>, incoming: ChatMessage, appendText: Boolean): List<ChatMessage> {
    val index = messages.indexOfLast { it.id == incoming.id }
        .takeIf { it >= 0 }
        ?: messages.indexOfLast { existing -> existing.isGatewayUserEchoOf(incoming) }
    if (index < 0) return messages + incoming
    return messages.toMutableList().also { existing ->
        val previous = existing[index]
        val canonical = when {
            incoming.id.endsWith(GATEWAY_USER_SUFFIX) -> incoming
            previous.id.endsWith(GATEWAY_USER_SUFFIX) -> previous
            else -> incoming
        }
        val other = if (canonical === incoming) previous else incoming
        existing[index] = mergeMessage(other, canonical, appendText)
    }
}

private fun ChatMessage.isGatewayUserEchoOf(incoming: ChatMessage): Boolean {
    if (
        role != MessageRole.User ||
        incoming.role != MessageRole.User ||
        text.isBlank() ||
        text != incoming.text
    ) return false
    return id.removeSuffix(GATEWAY_USER_SUFFIX) == incoming.id.removeSuffix(GATEWAY_USER_SUFFIX) &&
        (id.endsWith(GATEWAY_USER_SUFFIX) || incoming.id.endsWith(GATEWAY_USER_SUFFIX))
}

private const val GATEWAY_USER_SUFFIX = "__user"

private fun mergeMessage(existing: ChatMessage, incoming: ChatMessage, appendText: Boolean): ChatMessage {
    val text = mergeMessageText(existing.text, incoming.text, appendText && incoming.role == MessageRole.Assistant)
    val streaming = when {
        incoming.role != MessageRole.Assistant -> false
        appendText -> true
        else -> existing.isStreaming
    }
    val textMerged = incoming.withText(text, streaming = streaming)
    return textMerged.copy(
        blocks = textMerged.blocks.filter(MessageBlock::isTextBlock) +
            mergeStructuredBlocks(existing.blocks, textMerged.blocks, appendText),
        attachments = (existing.attachments + incoming.attachments).distinctBy { it.path ?: it.filename },
        hiddenFromUi = existing.hiddenFromUi || incoming.hiddenFromUi,
        tokenUsage = incoming.tokenUsage ?: existing.tokenUsage,
    )
}

private fun mergeMessageText(existing: String, incoming: String, appendDelta: Boolean): String = when {
    incoming.isEmpty() -> existing
    existing.isEmpty() -> incoming
    incoming == existing -> existing
    incoming.startsWith(existing) -> incoming
    existing.startsWith(incoming) -> existing
    !appendDelta && incoming.length < existing.length -> existing
    appendDelta -> existing + incoming
    else -> incoming
}

private fun mergeStructuredBlocks(
    existing: List<MessageBlock>,
    incoming: List<MessageBlock>,
    appendReasoning: Boolean,
): List<MessageBlock> {
    val result = existing.filterNot(MessageBlock::isTextBlock).toMutableList()
    incoming.forEach { block ->
        if (block.isTextBlock()) return@forEach
        val index = result.indexOfFirst { it.structuredKey() == block.structuredKey() }
        if (index >= 0) {
            result[index] = mergeStructuredBlock(result[index], block, appendReasoning)
        } else {
            result += block
        }
    }
    return result
}

private fun MessageBlock.isTextBlock(): Boolean =
    this is MessageBlock.Markdown || this is MessageBlock.Code || this is MessageBlock.Quote

private fun mergeStructuredBlock(
    previous: MessageBlock,
    incoming: MessageBlock,
    appendReasoning: Boolean,
): MessageBlock = when {
    previous is MessageBlock.Reasoning && incoming is MessageBlock.Reasoning ->
        MessageBlock.Reasoning(mergeMessageText(previous.text, incoming.text, appendReasoning))
    previous is MessageBlock.ToolCall && incoming is MessageBlock.ToolCall -> previous.copy(
        name = incoming.name.takeUnless { it.isBlank() || it == "tool" } ?: previous.name,
        detail = mergeToolCallDetail(previous.detail, incoming.detail),
    )
    previous is MessageBlock.ToolResult && incoming is MessageBlock.ToolResult -> previous.copy(
        name = incoming.name.ifBlank { previous.name },
        detail = mergeMessageText(previous.detail, incoming.detail, appendDelta = false),
        failed = incoming.failed,
    )
    previous is MessageBlock.Subtask && incoming is MessageBlock.Subtask -> mergeSubtaskBlock(previous, incoming)
    else -> incoming
}

private fun mergeToolCallDetail(existing: String, incoming: String): String {
    if (existing.isBlank() || incoming.isBlank()) return incoming.ifBlank { existing }
    val current = existing.trimEnd()
    // A LangChain tool_call_chunk often ends midway through its JSON argument.
    if (current.startsWith('{') && !current.endsWith('}')) return existing + incoming
    return mergeMessageText(existing, incoming, appendDelta = false)
}

private fun mergeSubtaskBlock(previous: MessageBlock.Subtask, incoming: MessageBlock.Subtask): MessageBlock.Subtask =
    previous.copy(
        subagentType = incoming.subagentType.ifBlank { previous.subagentType },
        description = incoming.description.takeUnless { it == "Subtask" || it.isBlank() } ?: previous.description,
        prompt = incoming.prompt.ifBlank { previous.prompt },
        status = if (incoming.status != MessageBlock.SubtaskStatus.InProgress) incoming.status else previous.status,
        result = incoming.result ?: previous.result,
        error = incoming.error ?: previous.error,
        modelName = incoming.modelName ?: previous.modelName,
    )

private fun MessageBlock.structuredKey(): String = when (this) {
    is MessageBlock.ToolCall -> "call:${id.ifBlank { "$name:$detail" }}"
    is MessageBlock.ToolResult -> "result:${callId.ifBlank { name }}"
    is MessageBlock.HumanInput -> "input:${request.requestId}"
    is MessageBlock.Approval -> "input:${request.requestId}"
    is MessageBlock.HumanInputResponseBlock -> "response:${response.requestId}"
    is MessageBlock.Subtask -> "subtask:${callId.ifBlank { description }}"
    is MessageBlock.Reasoning -> "reasoning"
    else -> toString()
}

private fun ChatMessage.displayIdentity(): String {
    if (role != MessageRole.Tool) return "message:$id"
    val callId = blocks.filterIsInstance<MessageBlock.ToolResult>().firstOrNull()?.callId.orEmpty()
    return if (callId.isBlank()) "message:$id" else "tool:$callId"
}
