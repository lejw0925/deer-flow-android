package com.deerflow.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.deerflow.mobile.data.GatewayRunStatus
import com.deerflow.mobile.data.RunDetails
import com.deerflow.mobile.data.RunEventRecord
import com.deerflow.mobile.data.WorkspaceChangeFile
import com.deerflow.mobile.data.WorkspaceChangeSummary
import com.deerflow.mobile.data.WorkspaceChanges
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RunDetailsSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun auditPanelShowsGatewayEventsWorkspaceChangesAndRunSelection() {
        var selectedRun = ""
        val first = run("run-1", GatewayRunStatus.Success)
        val second = run("run-2", GatewayRunStatus.Error)
        compose.setContent {
            MaterialTheme {
                RunDetailsSheetContent(
                    runs = listOf(first, second),
                    selectedRunId = first.runId,
                    events = listOf(
                        RunEventRecord(7, "subagent.step", "subagent", "{\"tool\":\"web_search\"}", "2026-07-26T10:00:00Z", "task-1"),
                    ),
                    workspaceChanges = WorkspaceChanges(
                        available = true,
                        version = 1,
                        summary = WorkspaceChangeSummary(1, 1, 0, 0, 5, 1, false),
                        files = listOf(
                            WorkspaceChangeFile(
                                path = "outputs/report.md",
                                root = "workspace",
                                status = "modified",
                                binary = false,
                                sensitive = false,
                                sizeBefore = 1,
                                sizeAfter = 2,
                                diff = "+report",
                                diffTruncated = false,
                                diffUnavailableReason = null,
                                additions = 5,
                                deletions = 1,
                            ),
                        ),
                    ),
                    loading = false,
                    error = null,
                    onSelectRun = { selectedRun = it },
                    onReload = {},
                )
            }
        }

        compose.onNodeWithText("subagent.step").assertExists()
        compose.onNodeWithText("outputs/report.md").assertExists()
        compose.onNodeWithTag(UiTags.RunDetailsRunPrefix + second.runId).performClick()

        compose.runOnIdle { assertEquals(second.runId, selectedRun) }
    }

    private fun run(id: String, status: GatewayRunStatus) = RunDetails(
        runId = id,
        threadId = "thread-1",
        assistantId = "lead_agent",
        status = status,
        createdAt = "2026-07-26T10:00:00Z",
        updatedAt = "2026-07-26T10:01:00Z",
        totalInputTokens = 10,
        totalOutputTokens = 20,
        totalTokens = 30,
        llmCallCount = 1,
        leadAgentTokens = 30,
        subagentTokens = 0,
        middlewareTokens = 0,
        messageCount = 2,
        stopReason = null,
    )
}
