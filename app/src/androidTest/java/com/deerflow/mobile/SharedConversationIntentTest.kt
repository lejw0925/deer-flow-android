package com.deerflow.mobile

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.deerflow.mobile.ui.SharedConversationContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedConversationIntentTest {
    @Test
    fun consumesSharedTextAndFileOnlyOnce() {
        val file = Uri.parse("content://com.example.sender/shared/brief.pdf")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Review this brief")
            .putExtra(Intent.EXTRA_STREAM, file)

        assertEquals(
            SharedConversationContent("Review this brief", listOf(file)),
            intent.consumeSharedConversationContent(),
        )
        assertNull(intent.consumeSharedConversationContent())
    }

    @Test
    fun consumesMultipleFilesFromExtrasAndClipDataWithoutDuplicates() {
        val first = Uri.parse("content://com.example.sender/shared/first.pdf")
        val second = Uri.parse("content://com.example.sender/shared/second.pdf")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("application/pdf")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            .apply {
                clipData = ClipData.newRawUri("first", first).apply { addItem(ClipData.Item(second)) }
            }

        assertEquals(
            SharedConversationContent("", listOf(first, second)),
            intent.consumeSharedConversationContent(),
        )
    }

    @Test
    fun appIsAvailableAsATextAndFileShareTarget() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertTrue(context.resolvesToMainActivity(Intent(Intent.ACTION_SEND).setType("text/plain")))
        assertTrue(context.resolvesToMainActivity(Intent(Intent.ACTION_SEND_MULTIPLE).setType("application/pdf")))
    }

    @Suppress("DEPRECATION")
    private fun Context.resolvesToMainActivity(intent: Intent): Boolean =
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).any { resolveInfo ->
            resolveInfo.activityInfo.packageName == packageName &&
                resolveInfo.activityInfo.name == MainActivity::class.java.name
        }
}
