package com.deerflow.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationModelsTest {
    @Test
    fun `conversation shares the workspace animation target`() {
        assertEquals(AppRoute.Workspace, AppRoute.Workspace.workspacePageRoute())
        assertEquals(AppRoute.Workspace, AppRoute.Conversation.workspacePageRoute())
        assertEquals(AppRoute.Agents, AppRoute.Agents.workspacePageRoute())
    }
}
