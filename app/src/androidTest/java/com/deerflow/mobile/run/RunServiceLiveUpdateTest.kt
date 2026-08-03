package com.deerflow.mobile.run

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.deerflow.mobile.data.RunState
import com.deerflow.mobile.data.RunStatus
import com.deerflow.mobile.data.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 36)
class RunServiceLiveUpdateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @Before
    fun grantNotificationPermission() {
        RunService.stop(context)
        notifications.cancel(2026)
        SystemClock.sleep(200)
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        runBlocking { SettingsStore(context).setNotifyOnRunCompletion(true) }
    }

    @After
    fun cleanUp() {
        RunService.stop(context)
        runBlocking { SettingsStore(context).setNotifyOnRunCompletion(true) }
    }

    @Test
    fun ongoingRunRequestsPromotedProgressNotification() {
        assumeTrue(notifications.canPostPromotedNotifications())
        RunService.start(context, "Research workspace")
        val preparing = awaitRunNotification()

        assertTrue(preparing.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(preparing.extras.getBoolean(RunService.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertTrue(preparing.flags and Notification.FLAG_PROMOTED_ONGOING != 0)
        assertEquals(0, preparing.extras.getInt(Notification.EXTRA_PROGRESS))
        assertTrue(preparing.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(Notification.ProgressStyle::class.java.name, preparing.extras.getString(Notification.EXTRA_TEMPLATE))
        assertTrue(preparing.hasPromotableCharacteristics())
        assertEquals(context.getString(com.deerflow.mobile.R.string.run_chip_preparing), preparing.shortCriticalText)
        assertEquals(expectedLiveUpdateColor(), preparing.color)
        assertNotNull(preparing.smallIcon)
        assertNotNull(preparing.actions.single().icon)

        RunService.update(
            context,
            RunProgressUpdate(
                phase = RunProgress.Working,
                completedTodos = 1,
                totalTodos = 3,
                currentTodo = "Write report",
                latestToolName = "read_file",
            ),
            "Research workspace",
        )
        val working = awaitRunNotification(expectedProgress = 33)
        assertEquals("1/3", working.shortCriticalText)
        assertEquals(
            context.getString(com.deerflow.mobile.R.string.run_current_step, "Write report"),
            working.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertNotNull(working.smallIcon)
        assertEquals(
            com.deerflow.mobile.R.drawable.ic_notification_transparent,
            working.extras.getParcelable("android.progressTrackerIcon", Icon::class.java)?.resId,
        )
    }

    @Test
    fun indeterminateRunKeepsTheProgressEndClearAndUsesTheLatestToolAsItsStatusIcon() {
        assumeTrue(notifications.canPostPromotedNotifications())
        RunService.start(context, "Research workspace")
        awaitRunNotification(isOngoing = true)

        RunService.update(
            context,
            RunProgressUpdate(
                phase = RunProgress.Working,
                latestToolName = "web_search",
            ),
            "Research workspace",
        )

        val working = awaitRunNotification(isOngoing = true)
        assertTrue(working.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(Notification.ProgressStyle::class.java.name, working.extras.getString(Notification.EXTRA_TEMPLATE))
        val endIcon = working.extras.getParcelable("android.progressEndIcon", Icon::class.java)
        assertNull(endIcon)
    }

    @Test
    fun enabledPreferenceKeepsTerminalNotification() {
        RunService.start(context, "Research workspace")
        val ongoing = awaitRunNotification(isOngoing = true)
        assertEquals(context.getColor(com.deerflow.mobile.R.color.ic_launcher_background), ongoing.color)
        assertEquals("Research workspace", ongoing.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(ongoing.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(0, ongoing.extras.getInt(Notification.EXTRA_PROGRESS))

        RunService.complete(context, "Research workspace")

        val completed = awaitRunNotification(isOngoing = false)
        assertTrue(completed.flags and Notification.FLAG_ONGOING_EVENT == 0)
        assertEquals(context.getColor(com.deerflow.mobile.R.color.ic_launcher_background), completed.color)
        assertEquals("Research workspace", completed.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(Notification.ProgressStyle::class.java.name, completed.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals(100, completed.extras.getInt(Notification.EXTRA_PROGRESS))
        assertFalse(completed.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(com.deerflow.mobile.R.drawable.ic_notification_completed, completed.smallIcon.resId)
        assertEquals(
            com.deerflow.mobile.R.drawable.ic_notification_completed,
            completed.extras.getParcelable("android.progressEndIcon", Icon::class.java)?.resId,
        )
        assertFalse(completed.flags and Notification.FLAG_PROMOTED_ONGOING != 0)
    }

    @Test
    fun staleLiveUpdateDoesNotReplaceATerminalManagedNotification() {
        val active = CoordinatedRunState(
            serverUrl = "https://example.test",
            threadId = "thread-terminal",
            title = "Terminal conversation",
            run = RunState(RunStatus.Streaming, startedAtEpochMs = 1L),
            serverMessages = emptyList(),
            revision = 10,
        )
        val terminal = active.copy(run = RunState(), revision = 11)

        RunService.synchronize(context, mapOf(active.key to active))
        awaitRunNotification(isOngoing = true)
        RunService.synchronize(context, mapOf(terminal.key to terminal))
        RunService.synchronize(context, mapOf(active.key to active))

        val completed = awaitRunNotification(isOngoing = false)
        assertEquals(100, completed.extras.getInt(Notification.EXTRA_PROGRESS))
        assertFalse(completed.flags and Notification.FLAG_PROMOTED_ONGOING != 0)
    }

    @Test
    fun disabledPreferenceRemovesNotificationAfterTheServiceStops() {
        runBlocking { SettingsStore(context).setNotifyOnRunCompletion(false) }
        RunService.start(context, "Research workspace")
        awaitRunNotification(isOngoing = true)

        RunService.complete(context, "Research workspace")
        awaitRunNotificationRemoved()
    }

    @Test
    fun dismissedLiveUpdateFallsBackToAStandardForegroundNotification() {
        assumeTrue(notifications.canPostPromotedNotifications())
        RunService.start(context, "Research workspace")
        awaitRunNotification(isOngoing = true, expectedPromoted = true)

        context.startService(
            Intent(context, RunService::class.java).setAction(RunService.ACTION_DISMISSED),
        )

        val fallback = awaitRunNotification(isOngoing = true, expectedPromoted = false)
        assertEquals("Research workspace", fallback.extras.getString(Notification.EXTRA_TITLE))
        assertFalse(fallback.extras.getBoolean(RunService.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    fun concurrentRunsUseSummaryWithoutAnAmbiguousStopAction() {
        val serverUrl = "https://example.test"
        val first = CoordinatedRunState(
            serverUrl = serverUrl,
            threadId = "thread-a",
            title = "Conversation A",
            run = RunState(RunStatus.Streaming, startedAtEpochMs = 1L),
            serverMessages = emptyList(),
        )
        val second = CoordinatedRunState(
            serverUrl = serverUrl,
            threadId = "thread-b",
            title = "Conversation B",
            run = RunState(RunStatus.Reconnecting, startedAtEpochMs = 2L),
            serverMessages = emptyList(),
        )

        RunService.synchronize(context, mapOf(first.key to first, second.key to second))

        val summary = awaitRunNotificationText(context.getString(com.deerflow.mobile.R.string.run_count_in_progress, 2))
        assertEquals(
            context.getString(com.deerflow.mobile.R.string.run_in_progress),
            summary.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertTrue(summary.actions.isNullOrEmpty())

        RunService.synchronize(context, mapOf(first.key to first))
        val focused = awaitRunNotificationTitle("Conversation A")
        assertEquals(1, focused.actions?.size)
    }

    private fun awaitRunNotification(
        expectedProgress: Int? = null,
        isOngoing: Boolean? = null,
        expectedPromoted: Boolean? = null,
    ): Notification {
        repeat(40) {
            val notification = notifications.activeNotifications
                .firstOrNull { it.id == 2026 }
                ?.notification
            val ongoing = notification?.flags?.and(Notification.FLAG_ONGOING_EVENT) != 0
            if (
                notification != null &&
                (expectedProgress == null || notification.extras.getInt(Notification.EXTRA_PROGRESS) == expectedProgress) &&
                (isOngoing == null || ongoing == isOngoing) &&
                (expectedPromoted == null || notification.extras.getBoolean(RunService.EXTRA_REQUEST_PROMOTED_ONGOING) == expectedPromoted)
            ) {
                return notification
            }
            SystemClock.sleep(100)
        }
        assertNotNull("Run notification was not posted", null)
        error("Run notification was not posted")
    }

    private fun awaitRunNotificationRemoved() {
        repeat(40) {
            if (notifications.activeNotifications.none { it.id == 2026 }) return
            SystemClock.sleep(100)
        }
        assertTrue("Run notification should have been removed", notifications.activeNotifications.none { it.id == 2026 })
    }

    private fun awaitRunNotificationText(expected: String): Notification {
        val observed = linkedSetOf<String>()
        repeat(40) {
            val notification = notifications.activeNotifications.firstOrNull { it.id == 2026 }?.notification
            if (notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() == expected) return notification
            notification?.let {
                observed += "title=${it.extras.getCharSequence(Notification.EXTRA_TITLE)} " +
                    "text=${it.extras.getCharSequence(Notification.EXTRA_TEXT)} actions=${it.actions?.size ?: 0}"
            }
            SystemClock.sleep(100)
        }
        error("Run notification did not show expected text: $expected; observed=$observed")
    }

    private fun awaitRunNotificationTitle(expected: String): Notification {
        repeat(40) {
            val notification = notifications.activeNotifications.firstOrNull { it.id == 2026 }?.notification
            if (notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() == expected) return notification
            SystemClock.sleep(100)
        }
        error("Run notification did not show expected title: $expected")
    }

    private fun expectedLiveUpdateColor(): Int =
        context.getColor(com.deerflow.mobile.R.color.ic_launcher_background)

}
