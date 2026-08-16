package com.deerflow.mobile.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.deerflow.mobile.data.ComposerState
import com.deerflow.mobile.data.RunOptions
import com.deerflow.mobile.data.SkillInfo
import com.deerflow.mobile.data.ThreadSummary
import com.deerflow.mobile.data.WorkspaceCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatComposerTest {
    @Test
    fun `skill command is inserted at the cursor with trailing space`() {
        val result = insertSkillCommand(
            value = TextFieldValue("hello world", selection = TextRange(6)),
            skillName = "animate",
        )

        assertEquals("hello /animate world", result.text)
        assertEquals(TextRange(15), result.selection)
    }

    @Test
    fun `skill command replaces the selected text`() {
        val result = insertSkillCommand(
            value = TextFieldValue("use old now", selection = TextRange(4, 7)),
            skillName = "/animate",
        )

        assertEquals("use /animate  now", result.text)
        assertEquals(TextRange(13), result.selection)
    }

    @Test
    fun `slash skill query only matches an unfinished leading command`() {
        assertEquals("", leadingSlashSkillQuery("/"))
        assertEquals("research", leadingSlashSkillQuery("/research"))
        assertEquals(null, leadingSlashSkillQuery("research /deep"))
        assertEquals(null, leadingSlashSkillQuery("/deep research"))
        assertEquals(null, leadingSlashSkillQuery("/deep/research"))
    }

    @Test
    fun `slash skill suggestions keep enabled prefix matches first`() {
        val suggestions = matchingSlashSkillSuggestions(
            skills = listOf(
                SkillInfo("z-research", "Secondary research", "research", enabled = true),
                SkillInfo("research", "Research reports", "research", enabled = true),
                SkillInfo("report", "Write reports", "writing", enabled = true),
                SkillInfo("disabled-research", "Unavailable", "research", enabled = false),
            ),
            query = "re",
        )

        assertEquals(listOf("research", "report", "z-research"), suggestions.map { it.name })
    }

    @Test
    fun `slash skill suggestions are capped at six entries`() {
        val suggestions = matchingSlashSkillSuggestions(
            skills = (1..7).map { index ->
                SkillInfo("skill-$index", "Skill $index", "other", enabled = true)
            },
            query = "",
        )

        assertEquals((1..6).map { "skill-$it" }, suggestions.map { it.name })
    }

    @Test
    fun `slash skill selection replaces the typed leading command`() {
        val selected = replaceLeadingSlashSkillCommand(TextFieldValue("/rese"), "deep-research")

        assertEquals("/deep-research ", selected.text)
        assertEquals(TextRange(selected.text.length), selected.selection)
    }

    @Test
    fun `slash skill suggestions stay above the composer when there is room`() {
        val position = SlashSkillSuggestionPositionProvider(8).calculatePosition(
            anchorBounds = IntRect(left = 16, top = 500, right = 416, bottom = 560),
            windowSize = IntSize(width = 432, height = 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(width = 400, height = 200),
        )

        assertEquals(16, position.x)
        assertEquals(292, position.y)
    }

    @Test
    fun `thread load result only applies to the still selected conversation`() {
        assertTrue(isCurrentThreadLoad("thread-2", "thread-2"))
        assertFalse(isCurrentThreadLoad("thread-2", "thread-1"))
        assertFalse(isCurrentThreadLoad(null, "thread-1"))
    }

    @Test
    fun `new draft restore only applies to the untouched matching session`() {
        val sessionKey = "new-draft-session"
        val initial = AppUiState(serverUrl = "http://example.test", draftSessionKey = sessionKey)

        assertTrue(isCurrentNewDraftLoad(initial, sessionKey))
        assertFalse(isCurrentNewDraftLoad(initial.copy(draftSessionKey = "other-session"), sessionKey))
        assertFalse(isCurrentNewDraftLoad(initial.copy(composer = ComposerState(text = "typing")), sessionKey))
        assertFalse(
            isCurrentNewDraftLoad(
                initial.copy(
                    selectedThread = ThreadSummary("thread-1", "Thread", "idle", "2026-07-25T00:00:00Z"),
                ),
                sessionKey,
            ),
        )
    }

    @Test
    fun `submission result only applies to its original conversation editor`() {
        val thread = ThreadSummary("thread-1", "Thread", "idle", "2026-07-25T00:00:00Z")
        val original = AppUiState(
            serverUrl = "http://example.test",
            selectedThread = thread,
            draftStorageKey = thread.id,
            draftSessionKey = "editor-1",
        )

        assertTrue(isCurrentConversationSession(original, original.serverUrl, thread.id, "editor-1"))
        assertFalse(
            isCurrentConversationSession(
                original.copy(selectedThread = thread.copy(id = "thread-2"), draftSessionKey = "editor-2"),
                original.serverUrl,
                thread.id,
                "editor-1",
            ),
        )
        assertFalse(isCurrentConversationSession(original, "http://other.test", thread.id, "editor-1"))
    }

    @Test
    fun `input polish result only applies to the untouched original editor`() {
        val thread = ThreadSummary("thread-1", "Thread", "idle", "2026-07-25T00:00:00Z")
        val original = AppUiState(
            serverUrl = "http://example.test",
            selectedThread = thread,
            draftSessionKey = "editor-1",
            composer = ComposerState(text = "rough draft"),
        )

        assertTrue(isCurrentInputPolish(original, original.serverUrl, thread.id, "editor-1", "rough draft"))
        assertFalse(
            isCurrentInputPolish(
                original.copy(composer = original.composer.copy(text = "edited draft")),
                original.serverUrl,
                thread.id,
                "editor-1",
                "rough draft",
            ),
        )
        assertFalse(isCurrentInputPolish(original, original.serverUrl, thread.id, "editor-2", "rough draft"))
    }

    @Test
    fun `model availability errors are promoted without catching unrelated failures`() {
        assertTrue(isModelUnavailableError("The configured LLM provider is temporarily unavailable after multiple retries."))
        assertTrue(isModelUnavailableError("Model 'research' not found in config"))
        assertTrue(isModelUnavailableError("模型服务提供商当前不可用"))
        assertFalse(isModelUnavailableError("The run timed out."))
        assertFalse(isModelUnavailableError("Artifact delivery incomplete: no produced output artifact was presented"))
        assertFalse(isModelUnavailableError("The model provider stopped this response for safety."))
        assertFalse(isModelUnavailableError(null))
    }

    @Test
    fun `model fallback assistant message promotes an opaque run error`() {
        val fallback = "The configured LLM provider is temporarily unavailable after multiple retries."
        val messages = listOf(
            com.deerflow.mobile.data.ChatMessage("user-1", com.deerflow.mobile.data.MessageRole.User, "Do the work"),
            com.deerflow.mobile.data.ChatMessage("assistant-1", com.deerflow.mobile.data.MessageRole.Assistant, fallback),
        )

        assertEquals(fallback, modelUnavailableMessage("Connection error.", messages))
    }

    @Test
    fun `historical model fallback does not promote a later unrelated failure`() {
        val messages = listOf(
            com.deerflow.mobile.data.ChatMessage("user-1", com.deerflow.mobile.data.MessageRole.User, "First request"),
            com.deerflow.mobile.data.ChatMessage(
                "assistant-1",
                com.deerflow.mobile.data.MessageRole.Assistant,
                "The configured LLM provider is temporarily unavailable after multiple retries.",
            ),
            com.deerflow.mobile.data.ChatMessage("user-2", com.deerflow.mobile.data.MessageRole.User, "Second request"),
        )

        assertEquals(null, modelUnavailableMessage("The run timed out.", messages))
    }

    @Test
    fun `completed answer does not promote an earlier model fallback`() {
        val messages = listOf(
            com.deerflow.mobile.data.ChatMessage("user-1", com.deerflow.mobile.data.MessageRole.User, "Do the work"),
            com.deerflow.mobile.data.ChatMessage(
                "assistant-1",
                com.deerflow.mobile.data.MessageRole.Assistant,
                "The configured LLM provider is temporarily unavailable after multiple retries.",
            ),
            com.deerflow.mobile.data.ChatMessage(
                "assistant-2",
                com.deerflow.mobile.data.MessageRole.Assistant,
                "The deliverable is complete.",
            ),
        )

        assertEquals(
            null,
            modelUnavailableMessage(
                "Artifact delivery incomplete: no produced output artifact was presented",
                messages,
            ),
        )
    }

    @Test
    fun `polish undo is only available while the rewritten draft is untouched`() {
        val undo = InputPolishUndo("rough", "Clear instruction")
        assertTrue(
            AppUiState(
                serverUrl = "http://example.test",
                composer = ComposerState(text = undo.rewrittenText),
                inputPolishUndo = undo,
            ).canUndoInputPolish,
        )
        assertFalse(
            AppUiState(
                serverUrl = "http://example.test",
                composer = ComposerState(text = "Edited instruction"),
                inputPolishUndo = undo,
            ).canUndoInputPolish,
        )
    }

    @Test
    fun `quick action replaces non-empty draft and adds matching enabled skill`() {
        val updated = applyQuickActionToComposer(
            composer = ComposerState(
                text = "Existing draft",
                options = RunOptions(enabledSkills = setOf("already-enabled")),
            ),
            capabilities = WorkspaceCapabilities(
                skills = listOf(
                    SkillInfo("deep-research", "Research reports", "research", enabled = true),
                ),
            ),
            prompt = "Research [topic]",
            skillKeywords = listOf("research"),
        )

        assertEquals("Research [topic]", updated.text)
        assertEquals(setOf("already-enabled", "deep-research"), updated.options.enabledSkills)
    }

    @Test
    fun `quick action ignores matching disabled skill`() {
        val updated = applyQuickActionToComposer(
            composer = ComposerState(text = "Existing draft"),
            capabilities = WorkspaceCapabilities(
                skills = listOf(
                    SkillInfo("deep-research", "Research reports", "research", enabled = false),
                ),
            ),
            prompt = "Research [topic]",
            skillKeywords = listOf("research"),
        )

        assertEquals("Research [topic]", updated.text)
        assertTrue(updated.options.enabledSkills.isEmpty())
    }
}
