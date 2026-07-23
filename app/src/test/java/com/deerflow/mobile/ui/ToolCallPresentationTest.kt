package com.deerflow.mobile.ui

import com.deerflow.mobile.data.MessageBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolCallPresentationTest {
    @Test
    fun webAndImageSearchUseLocalizedLabelKindsWithQueries() {
        assertEquals(
            ToolCallPresentation(ToolCallLabel.WebSearch("DeerFlow")),
            toolCallPresentation(MessageBlock.ToolCall("web_search", "{\"query\":\"DeerFlow\"}", "search-1")),
        )
        assertEquals(
            ToolCallPresentation(ToolCallLabel.ImageSearch("mountains")),
            toolCallPresentation(MessageBlock.ToolCall("image_search", "{\"query\":\"mountains\"}", "image-1")),
        )
    }

    @Test
    fun fileAndCommandToolsKeepTheirArgumentsSeparateFromLocalizedActions() {
        assertEquals(
            ToolCallPresentation(ToolCallLabel.ReadFile, "/mnt/work/README.md"),
            toolCallPresentation(MessageBlock.ToolCall("read_file", "{\"path\":\"/mnt/work/README.md\"}", "read-1")),
        )
        assertEquals(
            ToolCallPresentation(ToolCallLabel.ReadSkill("deep-research"), "/mnt/skills/public/deep-research/SKILL.md"),
            toolCallPresentation(MessageBlock.ToolCall("read_file", "{\"path\":\"/mnt/skills/public/deep-research/SKILL.md\"}", "skill-1")),
        )
        assertEquals(
            ToolCallPresentation(ToolCallLabel.ReadSkill("deep-research"), "/mnt/skills/public/deep-research/SKILL.md"),
            toolCallPresentation(
                MessageBlock.ToolCall(
                    "read_file",
                    "{\"description\":\"Read the requested skill file.\",\"path\":\"/mnt/skills/public/deep-research/SKILL.md\"}",
                    "skill-with-description-1",
                ),
            ),
        )
        assertEquals(
            ToolCallPresentation(ToolCallLabel.ExecuteCommand, "git status --short"),
            toolCallPresentation(MessageBlock.ToolCall("bash", "{\"command\":\"git status --short\"}", "bash-1")),
        )
    }

    @Test
    fun descriptionAndUnknownToolFollowWebFallbackOrder() {
        assertEquals(
            ToolCallPresentation(ToolCallLabel.Description("Inspect the workspace"), "pwd"),
            toolCallPresentation(MessageBlock.ToolCall("bash", "{\"description\":\"Inspect the workspace\",\"command\":\"pwd\"}", "bash-1")),
        )
        assertEquals(
            ToolCallPresentation(ToolCallLabel.UseTool("custom_tool")),
            toolCallPresentation(MessageBlock.ToolCall("custom_tool", "{}", "custom-1")),
        )
        assertEquals(
            ToolCallPresentation(ToolCallLabel.GenericTool),
            toolCallPresentation(MessageBlock.ToolCall("tool", "{}", "unknown-1")),
        )
    }
}
