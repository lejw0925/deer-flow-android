@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deerflow.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deerflow.mobile.R
import com.deerflow.mobile.data.RunDetails
import com.deerflow.mobile.data.RunEventRecord
import com.deerflow.mobile.data.WorkspaceChangeFile
import com.deerflow.mobile.data.WorkspaceChanges
import com.deerflow.mobile.ui.glass.GlassModalBottomSheet

@Composable
fun RunDetailsSheet(
    state: AppUiState,
    onDismiss: () -> Unit,
    onSelectRun: (String) -> Unit,
    onReload: () -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(UiTags.RunDetailsSheet),
    ) {
        RunDetailsSheetContent(
            runs = state.conversationRuns,
            selectedRunId = state.selectedRunDetailsId,
            events = state.runEvents,
            workspaceChanges = state.workspaceChanges,
            loading = state.loadingRunDetails,
            error = state.runDetailsError,
            onSelectRun = onSelectRun,
            onReload = onReload,
        )
    }
}

@Composable
internal fun RunDetailsSheetContent(
    runs: List<RunDetails>,
    selectedRunId: String?,
    events: List<RunEventRecord>,
    workspaceChanges: WorkspaceChanges?,
    loading: Boolean,
    error: String?,
    onSelectRun: (String) -> Unit,
    onReload: () -> Unit,
) {
    val selected = runs.firstOrNull { it.runId == selectedRunId }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .testTag(UiTags.RunDetailsSheet),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.run_details_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onReload,
                enabled = !loading,
                modifier = Modifier.size(48.dp).testTag(UiTags.RunDetailsRefresh),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.run_details_reload))
            }
        }
        if (runs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(runs, key = RunDetails::runId) { run ->
                    FilterChip(
                        selected = run.runId == selectedRunId,
                        onClick = { onSelectRun(run.runId) },
                        label = {
                            Text(
                                run.runId.takeLast(8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.testTag(UiTags.RunDetailsRunPrefix + run.runId),
                    )
                }
            }
        }
        when {
            loading && selected == null -> Text(
                stringResource(R.string.loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            )

            runs.isEmpty() && !loading -> Text(
                stringResource(R.string.run_details_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            )

            selected != null -> RunAuditContent(
                selected = selected,
                events = events,
                workspaceChanges = workspaceChanges,
                loading = loading,
                error = error,
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RunAuditContent(
    selected: RunDetails,
    events: List<RunEventRecord>,
    workspaceChanges: WorkspaceChanges?,
    loading: Boolean,
    error: String?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            RunSummary(selected)
        }
        error?.let { problem ->
            item {
                Text(
                    problem,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        item {
            HorizontalDivider()
            SectionTitle(R.string.run_details_events)
        }
        when {
            loading -> item {
                Text(
                    stringResource(R.string.loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            events.isEmpty() -> item {
                Text(
                    stringResource(R.string.run_details_no_events),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            else -> items(events, key = { event -> event.sequence ?: "${event.eventType}-${event.createdAt}" }) { event ->
                RunEventRow(event)
            }
        }
        item {
            HorizontalDivider()
            SectionTitle(R.string.run_details_workspace_changes)
        }
        workspaceChanges?.let { changes ->
            item { WorkspaceChangeSummaryRow(changes) }
            if (changes.available && changes.files.isNotEmpty()) {
                items(changes.files, key = WorkspaceChangeFile::path) { file -> WorkspaceChangeRow(file) }
            }
        } ?: item {
            Text(
                stringResource(R.string.run_details_no_workspace_changes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun RunSummary(run: RunDetails) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(run.runId, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            run.status.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
        )
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryMetric(R.string.run_details_tokens, formatTokenCount(run.totalTokens.toLong()))
            SummaryMetric(R.string.run_details_llm_calls, run.llmCallCount.toString())
            SummaryMetric(R.string.run_details_messages, run.messageCount.toString())
        }
        if (run.leadAgentTokens + run.subagentTokens + run.middlewareTokens > 0) {
            Text(
                stringResource(
                    R.string.run_details_token_breakdown,
                    formatTokenCount(run.leadAgentTokens.toLong()),
                    formatTokenCount(run.subagentTokens.toLong()),
                    formatTokenCount(run.middlewareTokens.toLong()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        run.stopReason?.takeIf(String::isNotBlank)?.let { reason ->
            Text(
                stringResource(R.string.run_details_stop_reason, reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SummaryMetric(labelRes: Int, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(titleRes: Int) {
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RunEventRow(event: RunEventRecord) {
    ListItem(
        headlineContent = {
            Text(event.eventType.ifBlank { event.category.ifBlank { "event" } })
        },
        supportingContent = {
            Column {
                event.createdAt?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                event.taskId?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                event.content.takeIf(String::isNotBlank)?.let { content ->
                    Text(content, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        trailingContent = {
            event.sequence?.let { sequence ->
                Text(sequence.toString(), style = MaterialTheme.typography.labelSmall)
            }
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
private fun WorkspaceChangeSummaryRow(changes: WorkspaceChanges) {
    if (!changes.available || changes.files.isEmpty()) {
        Text(
            stringResource(R.string.run_details_no_workspace_changes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        return
    }
    Text(
        stringResource(
            R.string.run_details_change_summary,
            changes.summary.created,
            changes.summary.modified,
            changes.summary.deleted,
            changes.summary.additions,
            changes.summary.deletions,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun WorkspaceChangeRow(file: WorkspaceChangeFile) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                file.path.ifBlank { file.root },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                file.status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.run_details_additions_deletions, file.additions, file.deletions),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        file.diffUnavailableReason?.takeIf(String::isNotBlank)?.let { reason ->
            Text(
                stringResource(R.string.run_details_diff_unavailable, reason),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        file.diff.takeIf(String::isNotBlank)?.let { diff ->
            SelectionContainer {
                Text(
                    diff,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
