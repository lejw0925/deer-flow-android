package com.deerflow.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationModelsTest {
    @Test
    fun `new conversation retains its chat page while an existing thread uses the conversation page`() {
        val newFromWorkspace = AppUiState(
            serverUrl = "http://example.test",
            route = AppRoute.Conversation,
            conversationPageTarget = ConversationPageTarget.Workspace,
        )
        val newFromConversation = newFromWorkspace.copy(
            conversationPageTarget = ConversationPageTarget.Conversation,
        )

        assertEquals(AppRoute.Workspace, newFromWorkspace.workspacePageRoute())
        assertEquals(AppRoute.Conversation, newFromConversation.workspacePageRoute())
        val agents = AppUiState(serverUrl = "http://example.test", route = AppRoute.Agents)

        assertEquals(AppRoute.Agents, agents.workspacePageRoute())
    }
}
