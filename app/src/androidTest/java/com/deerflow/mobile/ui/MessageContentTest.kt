package com.deerflow.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.deerflow.mobile.R
import com.deerflow.mobile.data.ChatMessage
import com.deerflow.mobile.data.ChatMessageGroup
import com.deerflow.mobile.data.HumanInputOption
import com.deerflow.mobile.data.HumanInputRequest
import com.deerflow.mobile.data.MessageBlock
import com.deerflow.mobile.data.MessageRole
import com.deerflow.mobile.data.AttachmentStatus
import com.deerflow.mobile.data.BrowserViewSnapshot
import com.deerflow.mobile.data.PendingAttachment
import com.deerflow.mobile.data.TokenUsage
import com.deerflow.mobile.data.groupChatMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MessageContentTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun processingGroupCollapsesPreviousStepsAndKeepsLatestToolVisible() {
        val group = ChatMessageGroup.Processing(
            key = "processing",
            messages = listOf(
                ChatMessage(
                    id = "ai-1",
                    role = MessageRole.Assistant,
                    text = "",
                    blocks = listOf(
                        MessageBlock.Reasoning("Plan the search"),
                        MessageBlock.ToolCall("web_search", "{\"query\":\"old query\"}", "call-1"),
                        MessageBlock.Reasoning("Review the result"),
                        MessageBlock.ToolCall("bash", "{\"description\":\"Inspect renderer\"}", "call-2"),
                    ),
                ),
            ),
        )
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(group, runActive = true, onHumanInput = { _, _, _ -> })
            }
        }

        compose.onNodeWithText("3 more steps").assertExists()
        compose.onNodeWithText("Inspect renderer").assertExists()
        compose.onNodeWithText("Plan the search").assertDoesNotExist()
        compose.onNodeWithText("Search on the web for “old query”").assertDoesNotExist()

        compose.onNodeWithText("3 more steps").performClick()

        compose.onNodeWithText("Plan the search").assertExists()
        compose.onNodeWithText("Search on the web for “old query”").assertExists()
    }

    @Test
    fun webSearchToolShowsLinkedSourceResults() {
        setProcessingToolResult(
            MessageBlock.ToolCall("web_search", "{\"query\":\"Android\"}", "search-1"),
            MessageBlock.ToolResult(
                "search-1",
                "web_search",
                """{"results":[{"title":"Android Developers","url":"https://developer.android.com","content":"Official Android documentation"}]}""",
            ),
        )

        compose.onNodeWithText("Search on the web for “Android”").assertExists()
        compose.onNodeWithText("Android Developers").assertDoesNotExist()
    }

    @Test
    fun imageSearchToolShowsResultTilesWithoutRequestingRemoteImages() {
        setProcessingToolResult(
            MessageBlock.ToolCall("image_search", "{\"query\":\"mountains\"}", "image-1"),
            MessageBlock.ToolResult(
                "image-1",
                "image_search",
                """{"results":[{"title":"Mountain reference","source_url":"https://source.example/mountain"}]}""",
            ),
        )

        compose.onNodeWithText("Search for related images for “mountains”").assertExists()
        compose.onNodeWithText("Mountain reference").assertDoesNotExist()
    }

    @Test
    fun directoryToolShowsEntriesInsteadOfRawOutput() {
        setProcessingToolResult(
            MessageBlock.ToolCall("ls", "{\"path\":\"/mnt/work\"}", "list-1"),
            MessageBlock.ToolResult("list-1", "ls", "src/\nREADME.md"),
        )

        compose.onNodeWithText("List folder").assertExists()
        compose.onNodeWithText("/mnt/work").assertExists()
        compose.onNodeWithText("src/").assertDoesNotExist()
    }

    @Test
    fun shellToolShowsCommandAndDedicatedOutput() {
        setProcessingToolResult(
            MessageBlock.ToolCall("bash", "{\"command\":\"echo hello\"}", "shell-1"),
            MessageBlock.ToolResult("shell-1", "bash", "hello"),
        )

        compose.onNodeWithText("Execute command").assertExists()
        compose.onNodeWithText("echo hello").assertExists()
        compose.onNodeWithText("hello").assertDoesNotExist()
    }

    @Test
    fun browserToolPreviewOpensTheMatchingLiveBrowserSnapshot() {
        val browserView = BrowserViewSnapshot(
            screenshot = "/mnt/user-data/browser/step.jpg",
            url = "https://example.com",
            title = "Example",
        )
        var opened: BrowserViewSnapshot? = null
        val group = ChatMessageGroup.Processing(
            key = "browser-processing",
            messages = listOf(
                ChatMessage(
                    id = "browser-call",
                    role = MessageRole.Assistant,
                    text = "",
                    blocks = listOf(MessageBlock.ToolCall("browser_navigate", "{\"url\":\"https://example.com\"}", "browser-1")),
                ),
                ChatMessage(
                    id = "browser-result",
                    role = MessageRole.Tool,
                    text = "Opened page",
                    blocks = listOf(MessageBlock.ToolResult("browser-1", "browser_navigate", "Opened page", browserView = browserView)),
                ),
            ),
        )
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = group,
                    runActive = false,
                    onHumanInput = { _, _, _ -> },
                    onBrowser = { opened = it },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.tool_open_browser)).performClick()

        compose.runOnIdle { assertEquals(browserView, opened) }
    }

    @Test
    fun humanInputChoiceSubmitsStructuredOption() {
        val request = HumanInputRequest(
            source = "ask_clarification",
            requestId = "request-1",
            toolCallId = "call-1",
            title = null,
            question = "Which format should I use?",
            context = null,
            inputMode = "choice_with_other",
            options = listOf(HumanInputOption("option-0", "Markdown", "Markdown")),
        )
        var submitted: Triple<String, String, String?>? = null
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = ChatMessageGroup.HumanInput(request, response = null, isLatestOpen = true),
                    runActive = false,
                    onHumanInput = { valueRequest, value, optionId ->
                        submitted = Triple(valueRequest.requestId, value, optionId)
                    },
                )
            }
        }

        compose.onNodeWithText("Needs your help").assertExists()
        compose.onNodeWithText("Markdown").performClick()
        compose.runOnIdle { assertEquals(Triple("request-1", "Markdown", "option-0"), submitted) }
    }

    @Test
    fun humanInputFreeTextSubmitsTypedAnswer() {
        val request = HumanInputRequest(
            source = "ask_clarification",
            requestId = "request-free-text",
            toolCallId = "call-free-text",
            title = null,
            question = "What should I continue with?",
            context = null,
            inputMode = "free_text",
            options = emptyList(),
        )
        var submitted: Triple<String, String, String?>? = null
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = ChatMessageGroup.HumanInput(request, response = null, isLatestOpen = true),
                    runActive = false,
                    onHumanInput = { valueRequest, value, optionId ->
                        submitted = Triple(valueRequest.requestId, value, optionId)
                    },
                )
            }
        }

        compose.onNodeWithTag(UiTags.HumanInputText).performTextInput("Continue the session")
        compose.onNodeWithTag(UiTags.HumanInputSubmit).performClick()

        compose.runOnIdle {
            assertEquals(Triple("request-free-text", "Continue the session", null), submitted)
        }
    }

    @Test
    fun approvalRequestShowsDistinctApprovalCardAndSubmitsChoice() {
        val request = HumanInputRequest(
            source = "ask_clarification",
            requestId = "approval-1",
            toolCallId = "call-approval",
            title = null,
            clarificationType = "risk_confirmation",
            question = "Delete the selected files?",
            context = null,
            inputMode = "single_choice",
            options = listOf(HumanInputOption("approve", "Approve", "yes")),
        )
        var submitted: Triple<String, String, String?>? = null
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = ChatMessageGroup.Approval(request, response = null, isLatestOpen = true),
                    runActive = false,
                    onHumanInput = { valueRequest, value, optionId ->
                        submitted = Triple(valueRequest.requestId, value, optionId)
                    },
                )
            }
        }

        compose.onNodeWithText("Approval required").assertExists()
        compose.onNodeWithText("Approve").performClick()
        compose.runOnIdle { assertEquals(Triple("approval-1", "yes", "approve"), submitted) }
    }

    @Test
    fun userMessageDoesNotExposeEditAndResendAction() {
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = ChatMessageGroup.Message(ChatMessage("human-1", MessageRole.User, "Original prompt")),
                    runActive = false,
                    onHumanInput = { _, _, _ -> },
                )
            }
        }

        compose.onNodeWithText("Original prompt").assertExists()
        assertEquals(0, compose.onAllNodesWithContentDescription("Edit and resend").fetchSemanticsNodes().size)
    }

    @Test
    fun assistantActionsInvokeCopyAndBranchCallbacks() {
        var copied = ""
        var branched = ""
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = ChatMessageGroup.Message(ChatMessage("ai-1", MessageRole.Assistant, "Answer")),
                    runActive = false,
                    onHumanInput = { _, _, _ -> },
                    onCopy = { copied = it },
                    onBranch = { branched = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Copy").performClick()
        compose.onNodeWithContentDescription("Branch conversation").performClick()
        compose.runOnIdle {
            assertEquals("ai-1", copied)
            assertEquals("ai-1", branched)
        }
        assertEquals(0, compose.onAllNodesWithContentDescription("Regenerate response").fetchSemanticsNodes().size)
    }

    @Test
    fun assistantMessageShowsCopyActionAndTokenUsage() {
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = ChatMessageGroup.Message(
                        ChatMessage(
                            id = "ai-usage",
                            role = MessageRole.Assistant,
                            text = "Completed response",
                            tokenUsage = TokenUsage(12, 34, 46),
                        ),
                    ),
                    runActive = false,
                    onHumanInput = { _, _, _ -> },
                )
            }
        }

        compose.onNodeWithContentDescription("Copy").assertExists()
        compose.onNodeWithText("Tokens · In 12 · Out 34 · Total 46").assertExists()
    }

    @Test
    fun finalReplyKeepsThoughtCollapsedWhileMarkdownRemainsVisible() {
        val tool = ChatMessage(
            id = "ai-tool",
            role = MessageRole.Assistant,
            text = "",
            blocks = listOf(MessageBlock.ToolCall("web_search", "{}", "search-1")),
        )
        val final = ChatMessage(
            id = "ai-reasoning",
            role = MessageRole.Assistant,
            text = "Final answer",
            blocks = listOf(
                MessageBlock.Markdown("Final answer"),
                MessageBlock.Reasoning("Private rationale"),
            ),
        )
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    groupChatMessages(listOf(tool, final)).forEach { group ->
                        ChatMessageGroupItem(
                            group = group,
                            runActive = false,
                            onHumanInput = { _, _, _ -> },
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("Final answer").assertExists()
        compose.onNodeWithText("Private rationale").assertDoesNotExist()
        val cardBounds = compose.onNodeWithTag(UiTags.ProcessingCard).fetchSemanticsNode().boundsInRoot
        val reasoningBounds = compose.onNodeWithText("Reasoning").fetchSemanticsNode().boundsInRoot
        assertTrue(reasoningBounds.top >= cardBounds.top && reasoningBounds.bottom <= cardBounds.bottom)
        compose.onNodeWithText("Reasoning").performClick()
        compose.onNodeWithText("Private rationale").assertExists()
    }

    @Test
    fun finalTableReplyPlacesTheReasoningControlAboveTheTable() {
        val final = ChatMessage(
            id = "ai-table",
            role = MessageRole.Assistant,
            text = "| Column 1 | Column 2 |\n| --- | --- |\n| One | Two |",
            blocks = listOf(
                MessageBlock.Markdown("| Column 1 | Column 2 |\n| --- | --- |\n| One | Two |"),
                MessageBlock.Reasoning("Explain the table"),
            ),
        )
        val group = groupChatMessages(listOf(final)).single()

        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = group,
                    runActive = false,
                    onHumanInput = { _, _, _ -> },
                )
            }
        }

        val reasoningBounds = compose.onNodeWithText("Reasoning", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tableBounds = compose.onNodeWithText("Column 1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Reasoning bounds $reasoningBounds should be above table bounds $tableBounds",
            reasoningBounds.bottom <= tableBounds.top,
        )
        compose.onNodeWithText("Reasoning").performClick()
        compose.onNodeWithText("Explain the table").assertExists()
    }

    @Test
    fun processingGroupShowsThinkingIndicatorBeforeAnyToolCall() {
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = ChatMessageGroup.Processing(
                        key = "thinking",
                        messages = listOf(ChatMessage("ai-thinking", MessageRole.Assistant, "")),
                    ),
                    runActive = true,
                    onHumanInput = { _, _, _ -> },
                )
            }
        }

        compose.onNodeWithText("Thinking").assertExists()
    }

    @Test
    fun markdownTableAndArtifactLinkRenderThroughNativeHandlers() {
        var opened = ""
        compose.setContent {
            MaterialTheme {
                MarkdownContent(
                    "| Name | Value |\n| --- | --- |\n| Deer | Flow |\n\n[Download report](/mnt/user-data/outputs/report.md)",
                    onArtifact = { opened = it },
                )
            }
        }

        compose.onNodeWithText("Name").assertExists()
        compose.onNodeWithText("Flow").assertExists()
        compose.onNodeWithText("Download report").performClick()
        compose.runOnIdle { assertEquals("/mnt/user-data/outputs/report.md", opened) }
    }

    @Test
    fun citationTapBringsItsMatchingSourceIntoView() {
        val sourceTag = UiTags.CitationSourcePrefix + "1"
        val supportingText = List(24) { index ->
            "Supporting detail $index keeps the source below the initial viewport."
        }.joinToString(separator = "\n\n")
        val markdown = listOf(
            "[citation: API](https://example.com/api)",
            supportingText,
            "## Sources:\n- [citation: Docs](https://example.com/docs)\n- [citation: API](https://example.com/api)",
        ).joinToString(separator = "\n\n")
        compose.setContent {
            MaterialTheme {
                LazyColumn(Modifier.height(180.dp)) {
                    item {
                        MarkdownContent(markdown)
                    }
                }
            }
        }

        compose.onNodeWithTag(UiTags.CitationInline, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(sourceTag, useUnmergedTree = true).assertIsNotDisplayed()

        compose.onNodeWithTag(UiTags.CitationInline, useUnmergedTree = true).performTouchInput { click(center) }
        compose.waitForIdle()

        compose.onNodeWithTag(sourceTag, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun failedAttachmentOffersRetryAction() {
        var retried = ""
        compose.setContent {
            MaterialTheme {
                AttachmentChip(
                    file = PendingAttachment(
                        uri = "content://failed",
                        filename = "report.pdf",
                        mimeType = "application/pdf",
                        size = 42,
                        status = AttachmentStatus.Failed,
                        error = "network",
                    ),
                    onRemove = {},
                    onRetry = { retried = "content://failed" },
                )
            }
        }

        compose.onNodeWithContentDescription("Retry upload").performClick()
        compose.runOnIdle { assertEquals("content://failed", retried) }
    }

    @Test
    fun subtaskBlockShowsStructuredLifecycleState() {
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = ChatMessageGroup.Processing(
                        key = "processing-task",
                        messages = listOf(
                            ChatMessage(
                                id = "task-1",
                                role = MessageRole.Assistant,
                                text = "",
                                blocks = listOf(
                                    MessageBlock.Subtask(
                                        callId = "task-1",
                                        subagentType = "researcher",
                                        description = "Check the docs",
                                        prompt = "Read the API docs",
                                        status = MessageBlock.SubtaskStatus.Completed,
                                        result = "Done",
                                    ),
                                ),
                            ),
                        ),
                    ),
                    runActive = false,
                    onHumanInput = { _, _, _ -> },
                )
            }
        }

        compose.onNodeWithText("Check the docs").assertExists()
        compose.onNodeWithText("Subtask completed").assertExists()
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.onNodeWithText("Check the docs").performClick()
        compose.onNodeWithText("Done").assertExists()
    }

    @Test
    fun presentFilesMessageRendersInsideTheFinalConversationReply() {
        var opened = ""
        val tool = ChatMessage(
            id = "ai-tool",
            role = MessageRole.Assistant,
            text = "",
            blocks = listOf(MessageBlock.ToolCall("bash", "{}", "call-1")),
        )
        val message = ChatMessage(
            id = "ai-artifact",
            role = MessageRole.Assistant,
            text = "",
            blocks = listOf(
                MessageBlock.ToolCall("present_files", "{\"filepaths\":[\"mnt/user-data/outputs/report.md\"]}", "files-1"),
                MessageBlock.Artifact("report.md", "mnt/user-data/outputs/report.md"),
            ),
        )
        val result = ChatMessage(
            id = "files-result",
            role = MessageRole.Tool,
            text = "Successfully presented files",
            blocks = listOf(MessageBlock.ToolResult("files-1", "present_files", "Successfully presented files")),
        )
        val final = ChatMessage("ai-final", MessageRole.Assistant, "The report is ready.")
        val group = groupChatMessages(listOf(tool, message, result, final)).last()
        assertTrue(group is ChatMessageGroup.Message)
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = group,
                    runActive = false,
                    onHumanInput = { _, _, _ -> },
                    onArtifact = { opened = it },
                )
            }
        }

        compose.onNodeWithText("The report is ready.").assertExists()
        compose.onNodeWithText("report.md").performClick()
        compose.runOnIdle { assertEquals("mnt/user-data/outputs/report.md", opened) }
    }

    @Test
    fun multiplePresentedFilesShareOneHorizontallyScrollableRow() {
        var opened = ""
        val artifacts = listOf(
            MessageBlock.Artifact("first-long-report-name.pdf", "/mnt/user-data/outputs/first.pdf"),
            MessageBlock.Artifact("second-long-report-name.pdf", "/mnt/user-data/outputs/second.pdf"),
            MessageBlock.Artifact("third-long-report-name.pdf", "/mnt/user-data/outputs/third.pdf"),
        )
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = ChatMessageGroup.Message(
                        message = ChatMessage("ai-final", MessageRole.Assistant, "The files are ready."),
                        trailingArtifacts = artifacts,
                    ),
                    runActive = false,
                    onHumanInput = { _, _, _ -> },
                    onArtifact = { opened = it },
                )
            }
        }

        compose.onNodeWithTag(UiTags.PresentedArtifactRow).assertIsDisplayed()
        compose.onNodeWithTag(UiTags.PresentedArtifactPrefix + "0").assertIsDisplayed()
        repeat(3) {
            compose.onNodeWithTag(UiTags.PresentedArtifactRow).performTouchInput { swipeLeft() }
            compose.waitForIdle()
        }
        compose.onNodeWithTag(UiTags.PresentedArtifactPrefix + "2").assertIsDisplayed().performClick()

        compose.runOnIdle { assertEquals("/mnt/user-data/outputs/third.pdf", opened) }
    }

    private fun setProcessingToolResult(call: MessageBlock.ToolCall, result: MessageBlock.ToolResult) {
        compose.setContent {
            MaterialTheme {
                ChatMessageGroupItem(
                    group = ChatMessageGroup.Processing(
                        key = "tool-${call.id}",
                        messages = listOf(
                            ChatMessage(
                                id = "assistant-${call.id}",
                                role = MessageRole.Assistant,
                                text = "",
                                blocks = listOf(call, result),
                            ),
                        ),
                    ),
                    runActive = false,
                    onHumanInput = { _, _, _ -> },
                )
            }
        }
    }
}
