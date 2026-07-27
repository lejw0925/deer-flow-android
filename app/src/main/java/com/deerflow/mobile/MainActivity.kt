package com.deerflow.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.deerflow.mobile.ui.AppViewModel
import com.deerflow.mobile.ui.DeerFlowApp
import com.deerflow.mobile.ui.SharedConversationContent

class MainActivity : AppCompatActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeerFlowApp(viewModel)
        }
        openDestination(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDestination(intent)
    }

    private fun openDestination(intent: Intent?) {
        intent?.consumeSharedConversationContent()?.let { sharedContent ->
            viewModel.openSharedConversation(sharedContent)
            return
        }
        when (intent?.action) {
            ACTION_NEW_CONVERSATION -> viewModel.openNewConversationShortcut()
            ACTION_OPEN_CONVERSATION -> viewModel.openRunDestination(
                serverUrl = intent.getStringExtra(EXTRA_SHORTCUT_SERVER_URL),
                threadId = intent.getStringExtra(EXTRA_SHORTCUT_THREAD_ID),
            )
            else -> viewModel.openRunDestination(
                serverUrl = intent?.getStringExtra(EXTRA_RUN_SERVER_URL),
                threadId = intent?.getStringExtra(EXTRA_RUN_THREAD_ID),
            )
        }
    }

    companion object {
        const val ACTION_NEW_CONVERSATION = "com.deerflow.mobile.action.NEW_CONVERSATION"
        const val ACTION_OPEN_CONVERSATION = "com.deerflow.mobile.action.OPEN_CONVERSATION"

        private const val EXTRA_RUN_SERVER_URL = "com.deerflow.mobile.run.SERVER_URL"
        private const val EXTRA_RUN_THREAD_ID = "com.deerflow.mobile.run.THREAD_ID"
        private const val EXTRA_SHORTCUT_SERVER_URL = "com.deerflow.mobile.shortcut.SERVER_URL"
        private const val EXTRA_SHORTCUT_THREAD_ID = "com.deerflow.mobile.shortcut.THREAD_ID"

        fun runDestinationIntent(context: Context, serverUrl: String?, threadId: String?): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_RUN_SERVER_URL, serverUrl)
                .putExtra(EXTRA_RUN_THREAD_ID, threadId)

        fun conversationShortcutIntent(context: Context, serverUrl: String, threadId: String): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_CONVERSATION)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_SHORTCUT_SERVER_URL, serverUrl)
                .putExtra(EXTRA_SHORTCUT_THREAD_ID, threadId)
    }
}

internal fun Intent.consumeSharedConversationContent(): SharedConversationContent? {
    if (action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return null

    val text = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
    val streams = buildList {
        when (action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(this@consumeSharedConversationContent, Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    ?.let(::add)
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                addAll(
                    IntentCompat.getParcelableArrayListExtra(
                        this@consumeSharedConversationContent,
                        Intent.EXTRA_STREAM,
                        android.net.Uri::class.java,
                    ).orEmpty(),
                )
            }
        }
        clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let(::add)
            }
        }
    }.distinct()
    if (text.isBlank() && streams.isEmpty()) return null

    action = null
    return SharedConversationContent(text = text, attachmentUris = streams)
}
