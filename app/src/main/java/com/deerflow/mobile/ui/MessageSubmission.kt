package com.deerflow.mobile.ui

import com.deerflow.mobile.data.AttachmentStatus
import com.deerflow.mobile.data.ComposerState

/** Serializes taps per editor/conversation until that prompt enters the run coordinator. */
internal class MessageSubmissionGate {
    private val lockedKeys = mutableSetOf<String>()

    @Synchronized
    fun tryAcquire(key: String): Boolean = lockedKeys.add(key)

    @Synchronized
    fun release(key: String) {
        lockedKeys.remove(key)
    }
}

internal fun restoreFailedComposer(
    composer: ComposerState,
    text: String,
    attachmentError: String?,
): ComposerState = composer.copy(
    text = text,
    uploading = false,
    attachments = composer.attachments.map { attachment ->
        if (attachmentError == null) attachment else attachment.copy(status = AttachmentStatus.Failed, error = attachmentError)
    },
)
