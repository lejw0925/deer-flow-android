package com.deerflow.mobile.ui

import com.deerflow.mobile.data.MessageBlock
import org.json.JSONObject

internal data class ToolCallPresentation(
    val label: ToolCallLabel,
    val detail: String? = null,
)

internal sealed interface ToolCallLabel {
    data class WebSearch(val query: String?) : ToolCallLabel
    data class ImageSearch(val query: String?) : ToolCallLabel
    data object ViewWebPage : ToolCallLabel
    data class BrowserNavigate(val url: String?) : ToolCallLabel
    data object BrowserSnapshot : ToolCallLabel
    data object BrowserClick : ToolCallLabel
    data object BrowserType : ToolCallLabel
    data object BrowserGetText : ToolCallLabel
    data object BrowserBack : ToolCallLabel
    data object BrowserScreenshot : ToolCallLabel
    data object BrowserClose : ToolCallLabel
    data object PresentFiles : ToolCallLabel
    data object ListFolder : ToolCallLabel
    data object ReadFile : ToolCallLabel
    data class ReadSkill(val name: String?) : ToolCallLabel
    data object WriteFile : ToolCallLabel
    data object ExecuteCommand : ToolCallLabel
    data object NeedYourHelp : ToolCallLabel
    data object WriteTodos : ToolCallLabel
    data object GenericTool : ToolCallLabel
    data class Description(val value: String) : ToolCallLabel
    data class UseTool(val name: String) : ToolCallLabel
}

internal fun toolCallPresentation(call: MessageBlock.ToolCall): ToolCallPresentation {
    val args = runCatching { JSONObject(call.detail) }.getOrNull()
    val description = args?.stringArgument("description")
    return when (call.name) {
        "web_search" -> ToolCallPresentation(ToolCallLabel.WebSearch(args?.stringArgument("query")))
        "image_search" -> ToolCallPresentation(ToolCallLabel.ImageSearch(args?.stringArgument("query")))
        "web_fetch" -> ToolCallPresentation(ToolCallLabel.ViewWebPage, args?.stringArgument("url"))
        "browser_navigate" -> ToolCallPresentation(ToolCallLabel.BrowserNavigate(args?.stringArgument("url")))
        "browser_snapshot" -> ToolCallPresentation(ToolCallLabel.BrowserSnapshot)
        "browser_click" -> ToolCallPresentation(ToolCallLabel.BrowserClick)
        "browser_type" -> ToolCallPresentation(ToolCallLabel.BrowserType)
        "browser_get_text" -> ToolCallPresentation(ToolCallLabel.BrowserGetText)
        "browser_back" -> ToolCallPresentation(ToolCallLabel.BrowserBack)
        "browser_screenshot" -> ToolCallPresentation(ToolCallLabel.BrowserScreenshot)
        "browser_close" -> ToolCallPresentation(ToolCallLabel.BrowserClose)
        "present_files" -> ToolCallPresentation(ToolCallLabel.PresentFiles)
        "ls" -> ToolCallPresentation(
            label = description?.let(ToolCallLabel::Description) ?: ToolCallLabel.ListFolder,
            detail = args?.stringArgument("path"),
        )
        "read_file" -> {
            val path = args?.stringArgument("path")
            ToolCallPresentation(
                label = path?.takeIf(::isSkillFile)?.let { ToolCallLabel.ReadSkill(it.skillName()) }
                    ?: description?.let(ToolCallLabel::Description)
                    ?: ToolCallLabel.ReadFile,
                detail = path,
            )
        }
        "read_skill", "load_skill", "describe_skill" -> ToolCallPresentation(
            label = description?.let(ToolCallLabel::Description)
                ?: ToolCallLabel.ReadSkill(args?.stringArgument("name") ?: args?.stringArgument("skill")),
        )
        "write_file", "str_replace" -> ToolCallPresentation(
            label = description?.let(ToolCallLabel::Description) ?: ToolCallLabel.WriteFile,
            detail = args?.stringArgument("path"),
        )
        "bash" -> ToolCallPresentation(
            label = description?.let(ToolCallLabel::Description) ?: ToolCallLabel.ExecuteCommand,
            detail = args?.stringArgument("command"),
        )
        "ask_clarification" -> ToolCallPresentation(ToolCallLabel.NeedYourHelp)
        "write_todos" -> ToolCallPresentation(ToolCallLabel.WriteTodos)
        "", "tool", "unknown" -> ToolCallPresentation(description?.let(ToolCallLabel::Description) ?: ToolCallLabel.GenericTool)
        else -> ToolCallPresentation(description?.let(ToolCallLabel::Description) ?: ToolCallLabel.UseTool(call.name))
    }
}

private fun JSONObject.stringArgument(name: String): String? =
    optString(name).takeIf(String::isNotBlank)

private fun isSkillFile(path: String): Boolean = path.endsWith("/SKILL.md")

private fun String.skillName(): String? = substringBeforeLast("/SKILL.md")
    .substringAfterLast('/')
    .takeIf(String::isNotBlank)
