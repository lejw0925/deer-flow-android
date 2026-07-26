package com.deerflow.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.platform.app.InstrumentationRegistry
import com.deerflow.mobile.R
import com.deerflow.mobile.data.BrowserInput
import com.deerflow.mobile.data.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowserLiveSheetTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun liveSheetForwardsNavigationTextTabAndViewportInput() {
        val inputs = mutableListOf<BrowserInput>()
        val liveControlChanges = mutableListOf<Boolean>()
        compose.setContent {
            var liveControlEnabled by remember { mutableStateOf(true) }
            MaterialTheme {
                BrowserLiveSheet(
                    browser = BrowserUiState(
                        visible = true,
                        threadId = "thread-1",
                        url = "https://example.com",
                        tabs = listOf(
                            BrowserTab(0, "Example", "https://example.com", active = true),
                            BrowserTab(1, "Docs", "https://docs.example.com", active = false),
                        ),
                        liveControlEnabled = liveControlEnabled,
                        status = BrowserLiveStatus.Live,
                    ),
                    serverUrl = "https://deerflow.example.com",
                    onDismiss = {},
                    onLiveControlChange = { enabled ->
                        liveControlChanges += enabled
                        liveControlEnabled = enabled
                    },
                    onInput = { inputs += it },
                )
            }
        }

        compose.onNodeWithTag(UiTags.BrowserSheet).assertExists()
        compose.onNodeWithTag(UiTags.BrowserViewport).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.browser_live_back)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.browser_live_forward)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.browser_live_navigate)).performClick()
        compose.onNodeWithText("Docs").performClick()
        val viewportBounds = compose.onNodeWithTag(UiTags.BrowserViewport).fetchSemanticsNode().boundsInRoot
        assertEquals(16f / 9f, viewportBounds.width / viewportBounds.height, 0.03f)
        compose.onNodeWithContentDescription(context.getString(R.string.browser_live_keyboard)).performClick()
        compose.onNodeWithTag(UiTags.BrowserTextInput).performClick()
        compose.onNodeWithTag(UiTags.BrowserTextInput).performTextReplacement("hello")
        compose.waitForIdle()
        compose.onNodeWithTag(UiTags.BrowserTextInput).assertTextContains("hello")
        compose.onNodeWithTag(UiTags.BrowserTextSend).assertHasClickAction().performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("hello").assertDoesNotExist()
        compose.onNodeWithTag(UiTags.BrowserViewport).performTouchInput { click() }
        compose.onNodeWithContentDescription(context.getString(R.string.browser_live_stop_control)).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.browser_live_take_control)).assertHasClickAction()
        compose.onNodeWithContentDescription(context.getString(R.string.browser_live_back)).assertIsNotEnabled()
        compose.onNodeWithTag(UiTags.BrowserAddressInput).assertIsNotEnabled()

        compose.runOnIdle {
            assertEquals(listOf(false), liveControlChanges)
            assertTrue(BrowserInput.Back in inputs)
            assertTrue(BrowserInput.Forward in inputs)
            assertTrue(BrowserInput.Navigate("https://example.com") in inputs)
            assertTrue(BrowserInput.ActivateTab(1) in inputs)
            assertTrue(BrowserInput.Text("hello") in inputs)
            val click = inputs.filterIsInstance<BrowserInput.Click>().single()
            assertEquals(0.5f, click.nx, 0.05f)
            assertEquals(0.5f, click.ny, 0.05f)
        }
    }
}
