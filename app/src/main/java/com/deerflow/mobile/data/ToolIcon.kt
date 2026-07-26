package com.deerflow.mobile.data

import androidx.annotation.DrawableRes
import com.deerflow.mobile.R

/** Visual categories shared by tool rows and active-run notifications. */
internal enum class ToolIconKind {
    Search,
    ViewWebPage,
    Browser,
    Files,
    ReadFile,
    WriteFile,
    ExecuteCommand,
    NeedYourHelp,
    WriteTodos,
}

/**
 * Returns null only when no tool has run yet. Unknown tool names deliberately use the same
 * generic code icon that the conversation uses for an unrecognized tool.
 */
internal fun toolIconKind(toolName: String?): ToolIconKind? {
    val normalized = toolName?.trim()?.lowercase()?.replace('-', '_')?.takeIf(String::isNotBlank)
        ?: return null
    return when {
        normalized.startsWith("browser_") -> ToolIconKind.Browser
        normalized in setOf("web_search", "image_search") -> ToolIconKind.Search
        normalized == "web_fetch" -> ToolIconKind.ViewWebPage
        normalized in setOf("present_files", "ls", "list_folder") -> ToolIconKind.Files
        normalized in setOf("read_file", "read_skill", "load_skill", "describe_skill") -> ToolIconKind.ReadFile
        normalized in setOf("write_file", "str_replace") -> ToolIconKind.WriteFile
        normalized in setOf("bash", "execute_command", "exec_command", "terminal", "shell", "python") -> ToolIconKind.ExecuteCommand
        normalized == "ask_clarification" -> ToolIconKind.NeedYourHelp
        normalized == "write_todos" -> ToolIconKind.WriteTodos
        else -> ToolIconKind.ExecuteCommand
    }
}

@DrawableRes
internal fun ToolIconKind.drawableResId(): Int = when (this) {
    ToolIconKind.Search -> R.drawable.ic_tool_search
    ToolIconKind.ViewWebPage -> R.drawable.ic_tool_view_web_page
    ToolIconKind.Browser -> R.drawable.ic_tool_browser
    ToolIconKind.Files -> R.drawable.ic_tool_files
    ToolIconKind.ReadFile -> R.drawable.ic_tool_read_file
    ToolIconKind.WriteFile -> R.drawable.ic_tool_write_file
    ToolIconKind.ExecuteCommand -> R.drawable.ic_tool_execute_command
    ToolIconKind.NeedYourHelp -> R.drawable.ic_tool_need_help
    ToolIconKind.WriteTodos -> R.drawable.ic_tool_write_todos
}
