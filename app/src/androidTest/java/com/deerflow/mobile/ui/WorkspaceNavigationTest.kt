package com.deerflow.mobile.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.deerflow.mobile.R
import com.deerflow.mobile.data.DeerFlowUser
import com.deerflow.mobile.data.ThreadSummary
import com.deerflow.mobile.data.TodoItem
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkspaceNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun drawerPullToRefreshRequestsTheLatestThreads() {
        var refreshRequests = 0
        compose.setContent {
            MaterialTheme {
                WorkspaceDrawer(
                    state = AppUiState(
                        serverUrl = "http://10.0.2.2:2027",
                        threads = listOf(testThread()),
                    ),
                    onNewChat = {},
                    onOpenThread = {},
                    onRenameThread = { _, _ -> },
                    onDeleteThread = {},
                    onPinThread = {},
                    onDestination = {},
                    onRefreshThreads = { refreshRequests += 1 },
                )
            }
        }

        compose.onNodeWithTag(UiTags.RecentConversationRefresh).performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, refreshRequests) }
    }

    @Test
    fun drawerAgentsDestinationUsesChildRoute() {
        var destination: DrawerDestination? = null
        compose.setContent {
            MaterialTheme {
                WorkspaceDrawer(
                    state = AppUiState(serverUrl = "http://10.0.2.2:2027"),
                    onNewChat = {},
                    onOpenThread = {},
                    onRenameThread = { _, _ -> },
                    onDeleteThread = {},
                    onPinThread = {},
                    onDestination = { destination = it },
                )
            }
        }

        compose.onNodeWithText("Agents").performClick()
        compose.runOnIdle { assertEquals(DrawerDestination.Agents, destination) }
    }

    @Test
    fun drawerHasOneWorkingNewChatActionAndSearchStartsUnfocused() {
        var newChatClicks = 0
        compose.setContent {
            MaterialTheme {
                WorkspaceDrawer(
                    state = AppUiState(serverUrl = "http://10.0.2.2:2027"),
                    onNewChat = { newChatClicks += 1 },
                    onOpenThread = {},
                    onRenameThread = { _, _ -> },
                    onDeleteThread = {},
                    onPinThread = {},
                    onDestination = {},
                )
            }
        }

        compose.onNodeWithTag(UiTags.ConversationSearch).assertIsNotFocused()
        compose.onNodeWithTag(UiTags.NewChatButton).performClick()
        compose.runOnIdle { assertEquals(1, newChatClicks) }
    }

    @Test
    fun drawerPinsSearchAndNewChatTogetherAtTheBottom() {
        compose.setContent {
            MaterialTheme {
                WorkspaceDrawer(
                    state = AppUiState(
                        serverUrl = "http://10.0.2.2:2027",
                        user = DeerFlowUser("user-1", "user@example.com", "member", needsSetup = false),
                        threads = (0 until 24).map { index ->
                            ThreadSummary(
                                id = "thread-$index",
                                title = "Conversation $index",
                                status = "idle",
                                updatedAt = "2026-07-20T10:00:00Z",
                            )
                        },
                    ),
                    onNewChat = {},
                    onOpenThread = {},
                    onRenameThread = { _, _ -> },
                    onDeleteThread = {},
                    onPinThread = {},
                    onDestination = {},
                )
            }
        }

        compose.onNodeWithTag(UiTags.RecentConversationScroll).assertExists()
        val search = compose.onNodeWithTag(UiTags.ConversationSearch).fetchSemanticsNode().boundsInRoot
        val newChat = compose.onNodeWithTag(UiTags.NewChatButton).fetchSemanticsNode().boundsInRoot
        val actionBar = compose.onNodeWithTag(UiTags.ConversationActionsBar).fetchSemanticsNode().boundsInRoot
        val profile = compose.onNodeWithContentDescription(context.getString(R.string.tab_profile))
        val profileTop = profile.fetchSemanticsNode().boundsInRoot.top
        val identityTop = compose.onNodeWithTag(UiTags.DrawerIdentityHeader).fetchSemanticsNode().boundsInRoot.top

        assertTrue(search.right <= newChat.left)
        assertEquals(search.bottom, newChat.bottom, 2f)
        assertTrue(newChat.bottom <= actionBar.bottom)
        compose.onAllNodesWithText("user@example.com").assertCountEquals(1)

        repeat(4) {
            compose.onNodeWithTag(UiTags.RecentConversationScroll).performTouchInput { swipeUp() }
        }
        compose.onNodeWithText("Conversation 23").assertIsDisplayed()

        assertEquals(profileTop, profile.fetchSemanticsNode().boundsInRoot.top, 2f)
        assertEquals(identityTop, compose.onNodeWithTag(UiTags.DrawerIdentityHeader).fetchSemanticsNode().boundsInRoot.top, 2f)
        assertEquals(search.bottom, compose.onNodeWithTag(UiTags.ConversationSearch).fetchSemanticsNode().boundsInRoot.bottom, 2f)
        assertEquals(newChat.bottom, compose.onNodeWithTag(UiTags.NewChatButton).fetchSemanticsNode().boundsInRoot.bottom, 2f)
        recordDrawerScreenshot()
    }

    @Test
    fun drawerHeaderProfileIconOpensProfileWithoutADuplicateAction() {
        var profileOpens = 0
        compose.setContent {
            MaterialTheme {
                WorkspaceDrawer(
                    state = AppUiState(serverUrl = "http://10.0.2.2:2027"),
                    onNewChat = {},
                    onOpenThread = {},
                    onRenameThread = { _, _ -> },
                    onDeleteThread = {},
                    onPinThread = {},
                    onDestination = {},
                    onOpenProfile = { profileOpens += 1 },
                )
            }
        }

        compose
            .onAllNodesWithContentDescription(context.getString(R.string.tab_profile))
            .assertCountEquals(1)
        compose.onNodeWithContentDescription(context.getString(R.string.tab_profile)).performClick()
        compose.runOnIdle { assertEquals(1, profileOpens) }
    }

    @Test
    fun drawerShowsCoordinatorActivityInsteadOfStaleThreadStatus() {
        val thread = testThread()
        compose.setContent {
            MaterialTheme {
                WorkspaceDrawer(
                    state = AppUiState(
                        serverUrl = "http://10.0.2.2:2027",
                        threads = listOf(thread),
                        activeRunThreadIds = setOf(thread.id),
                    ),
                    onNewChat = {},
                    onOpenThread = {},
                    onRenameThread = { _, _ -> },
                    onDeleteThread = {},
                    onPinThread = {},
                    onDestination = {},
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.status_description, "running")).assertExists()
    }

    @Test
    fun chatTopBarShowsMenuInWorkspaceAndBackInsideConversation() {
        var openDrawer = 0
        var goBack = 0
        val route = mutableStateOf(AppRoute.Workspace)
        compose.setContent {
            MaterialTheme {
                ChatTopBar(
                    state = AppUiState(serverUrl = "http://10.0.2.2:2027", route = route.value),
                    onOpenDrawer = { openDrawer += 1 },
                    onBack = { goBack += 1 },
                    onModelSelected = {},
                    onModeSelected = {},
                    onExport = {},
                    expandedSelector = null,
                    onExpandedSelectorChange = {},
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.open_navigation)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, openDrawer)
            assertEquals(0, goBack)
            route.value = AppRoute.Conversation
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.open_navigation)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, openDrawer)
            assertEquals(1, goBack)
        }
    }

    @Test
    fun conversationLongPressPinsSelectedThread() {
        val thread = testThread()
        var pinned: ThreadSummary? = null
        setDrawer(thread, onPin = { pinned = it })

        openThreadMenu(thread)
        compose.onNodeWithTag(UiTags.ThreadPinAction).performClick()

        compose.runOnIdle { assertEquals(thread, pinned) }
    }

    @Test
    fun conversationLongPressRenamesSelectedThread() {
        val thread = testThread()
        var renamed: Pair<ThreadSummary, String>? = null
        setDrawer(thread, onRename = { selected, title -> renamed = selected to title })

        openThreadMenu(thread)
        compose.onNodeWithTag(UiTags.ThreadRenameAction).performClick()
        compose.onNodeWithTag(UiTags.ThreadRenameTitle).performTextReplacement("Updated title")
        compose.waitForIdle()
        compose.onNodeWithTag(UiTags.ThreadRenameSave).assertIsEnabled().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { renamed != null }

        compose.runOnIdle { assertEquals(thread to "Updated title", renamed) }
    }

    @Test
    fun conversationLongPressDeletesOnlyAfterConfirmation() {
        val thread = testThread()
        var deleted: ThreadSummary? = null
        setDrawer(thread, onDelete = { deleted = it })

        openThreadMenu(thread)
        compose.onNodeWithTag(UiTags.ThreadDeleteAction).performClick()
        compose.runOnIdle { assertEquals(null, deleted) }
        compose.onNodeWithTag(UiTags.ThreadDeleteConfirm).performClick()

        compose.runOnIdle { assertEquals(thread, deleted) }
    }

    @Test
    fun todoProgressUsesAnOverlayWithoutMovingTheConversationArea() {
        compose.setContent {
            MaterialTheme {
                TodoProgressHost(
                    conversationKey = "thread-1",
                    todos = listOf(
                        TodoItem("Inspect code", "completed"),
                        TodoItem("Add tests", "in_progress"),
                    ),
                    modifier = Modifier.height(520.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Text(
                            text = "Conversation stays in place",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("Todo progress: 1/2 complete").assertExists()
        compose.onAllNodesWithText("Add tests").assertCountEquals(0)
        recordTodoProgressScreenshot("collapsed")
        val conversationBoundsBefore = compose.onNodeWithTag(UiTags.TodoConversationArea).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Todo progress: 1/2 complete").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(UiTags.TodoProgressDetails).assertIsDisplayed()
        compose.onNodeWithText("Add tests").assertIsDisplayed()
        val todoStatus = compose.onNodeWithTag(UiTags.TodoStatusPrefix + "Add tests").fetchSemanticsNode().boundsInRoot
        val todoText = compose.onNodeWithText("Add tests").fetchSemanticsNode().boundsInRoot
        assertEquals(todoText.center.y, todoStatus.center.y, 2f)
        recordTodoProgressScreenshot("expanded")
        val conversationBoundsAfter = compose.onNodeWithTag(UiTags.TodoConversationArea).fetchSemanticsNode().boundsInRoot
        assertEquals(conversationBoundsBefore.top, conversationBoundsAfter.top, 0.5f)
        assertEquals(conversationBoundsBefore.bottom, conversationBoundsAfter.bottom, 0.5f)

        compose.onNodeWithContentDescription("Close").performClick()
        compose.onAllNodesWithTag(UiTags.TodoProgressDetails).assertCountEquals(0)
    }

    private fun recordTodoProgressScreenshot(state: String) {
        if (!InstrumentationRegistry.getArguments().getString(RECORD_TODO_PROGRESS_SCREENSHOTS).equals("true", ignoreCase = true)) return
        val image = compose.onNodeWithTag(UiTags.TodoProgressHost).captureToImage().asAndroidBitmap()
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "visual-baselines",
        ).apply { mkdirs() }
        FileOutputStream(File(directory, "todo-progress-$state.png")).use { stream ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private fun recordDrawerScreenshot() {
        if (!InstrumentationRegistry.getArguments().getString(RECORD_DRAWER_SCREENSHOTS).equals("true", ignoreCase = true)) return
        val image = compose.onNodeWithTag(UiTags.WorkspaceDrawer).captureToImage().asAndroidBitmap()
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "visual-baselines",
        ).apply { mkdirs() }
        FileOutputStream(File(directory, "drawer-bottom-actions.png")).use { stream ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    @Test
    fun codeArtifactPreviewShowsDetectedLanguage() {
        compose.setContent {
            MaterialTheme {
                ArtifactPreviewDialog(
                    preview = ArtifactPreviewState(
                        path = "mnt/user-data/outputs/Main.kt",
                        filename = "Main.kt",
                        mimeType = "text/plain",
                        text = "fun main() = println(42)",
                        localPath = "/tmp/Main.kt",
                    ),
                    onDismiss = {},
                    onSave = {},
                    onOpen = {},
                )
            }
        }

        compose.onNodeWithText("kotlin").assertExists()
        compose.onNodeWithText("fun main() = println(42)").assertExists()
    }

    private fun setDrawer(
        thread: ThreadSummary,
        onRename: (ThreadSummary, String) -> Unit = { _, _ -> },
        onDelete: (ThreadSummary) -> Unit = {},
        onPin: (ThreadSummary) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                WorkspaceDrawer(
                    state = AppUiState(
                        serverUrl = "http://10.0.2.2:2027",
                        threads = listOf(thread),
                    ),
                    onNewChat = {},
                    onOpenThread = {},
                    onRenameThread = onRename,
                    onDeleteThread = onDelete,
                    onPinThread = onPin,
                    onDestination = {},
                )
            }
        }
    }

    private fun openThreadMenu(thread: ThreadSummary) {
        compose.onNodeWithTag(UiTags.ThreadRowPrefix + thread.id).performTouchInput { longClick() }
    }

    private fun testThread() = ThreadSummary(
        id = "thread-1",
        title = "Original title",
        status = "idle",
        updatedAt = "2026-07-20T10:00:00Z",
    )

    private companion object {
        const val RECORD_DRAWER_SCREENSHOTS = "deerflow.record_drawer_screenshots"
        const val RECORD_TODO_PROGRESS_SCREENSHOTS = "deerflow.record_todo_progress_screenshots"
    }
}
