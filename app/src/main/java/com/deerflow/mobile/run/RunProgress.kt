package com.deerflow.mobile.run

import com.deerflow.mobile.data.TodoItem
import com.deerflow.mobile.data.ToolIconKind
import com.deerflow.mobile.data.toolIconKind

/** Stable milestones used by the notification and the foreground run UI. */
enum class RunProgress(val percent: Int) {
    Preparing(8),
    Uploading(18),
    Connecting(28),
    Working(62),
    Responding(78),
    Reconnecting(38),
    Finalizing(92),
    Completed(100),
}

data class RunProgressUpdate(
    val phase: RunProgress,
    val completedTodos: Int = 0,
    val totalTodos: Int = 0,
    val currentTodo: String? = null,
    val latestToolName: String? = null,
) {
    val indeterminate: Boolean get() = totalTodos == 0
    val percent: Int
        get() = when {
            phase == RunProgress.Completed -> 100
            indeterminate -> phase.percent
            else -> (completedTodos * 100 / totalTodos).coerceAtMost(99)
        }
    val todoChip: String? get() = if (indeterminate) null else "$completedTodos/$totalTodos"
}

/** A completed notification must show its final value rather than keep animating. */
internal fun RunProgressUpdate.usesIndeterminateNotificationProgress(ongoing: Boolean): Boolean =
    ongoing && phase != RunProgress.Completed && indeterminate

internal sealed interface RunNotificationIcon {
    data object Thinking : RunNotificationIcon
    data class Tool(val icon: ToolIconKind) : RunNotificationIcon
    data object Upload : RunNotificationIcon
    data object Reconnect : RunNotificationIcon
    data object Completed : RunNotificationIcon
}

internal fun RunProgressUpdate.notificationIcon(): RunNotificationIcon = when (phase) {
    RunProgress.Uploading -> RunNotificationIcon.Upload
    RunProgress.Reconnecting -> RunNotificationIcon.Reconnect
    RunProgress.Finalizing, RunProgress.Completed -> RunNotificationIcon.Completed
    RunProgress.Working, RunProgress.Responding -> toolIconKind(latestToolName)
        ?.let(RunNotificationIcon::Tool)
        ?: RunNotificationIcon.Thinking
    RunProgress.Preparing, RunProgress.Connecting -> RunNotificationIcon.Thinking
}

/** The only foreground-notification details that may trigger an in-run update. */
internal data class RunNotificationProjection(
    val phase: RunProgress,
    val percent: Int,
    val todoChip: String?,
    val currentTodo: String?,
    val latestToolName: String?,
)

internal fun RunProgressUpdate.notificationProjection(): RunNotificationProjection = RunNotificationProjection(
    phase = phase,
    percent = percent,
    todoChip = todoChip,
    currentTodo = currentTodo,
    latestToolName = latestToolName,
)

internal fun shouldPublishOngoingNotification(
    previous: RunNotificationProjection?,
    next: RunNotificationProjection,
    lastPublishedAtMs: Long,
    nowMs: Long,
    force: Boolean,
): Boolean = force || previous != next && nowMs - lastPublishedAtMs >= NOTIFICATION_UPDATE_INTERVAL_MS

fun runProgressUpdate(
    phase: RunProgress,
    todos: List<TodoItem>,
    latestToolName: String? = null,
): RunProgressUpdate {
    val currentTodo = todos.firstOrNull { it.status.lowercase() in ACTIVE_TODO_STATUSES }
        ?.content
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(MAX_TODO_LABEL_LENGTH)
        ?.takeIf { it.isNotBlank() }
    return RunProgressUpdate(
        phase = phase,
        completedTodos = todos.count { it.status == "completed" },
        totalTodos = todos.size,
        currentTodo = currentTodo,
        latestToolName = latestToolName?.takeIf { it.isNotBlank() },
    )
}

private val ACTIVE_TODO_STATUSES = setOf("in_progress", "running", "active")
private const val MAX_TODO_LABEL_LENGTH = 72
internal const val NOTIFICATION_UPDATE_INTERVAL_MS = 1_000L
