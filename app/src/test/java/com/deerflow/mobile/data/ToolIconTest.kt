package com.deerflow.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolIconTest {
    @Test
    fun browserToolsUseTheDedicatedBrowserIcon() {
        listOf(
            "browser_navigate",
            "browser_snapshot",
            "browser_click",
            "browser_type",
            "browser_get_text",
            "browser_back",
            "browser_screenshot",
            "browser_close",
            "browser_future_action",
        ).forEach { toolName ->
            assertEquals(ToolIconKind.Browser, toolIconKind(toolName))
        }
    }
}
