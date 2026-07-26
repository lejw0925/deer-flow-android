@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deerflow.mobile.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.deerflow.mobile.R
import com.deerflow.mobile.data.BrowserInput
import com.deerflow.mobile.data.isInlineDisplayableImageUrl
import com.deerflow.mobile.data.resolveArtifactURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_BROWSER_FRAME_BYTES = 6 * 1024 * 1024
private const val MAX_BROWSER_FRAME_PIXELS = 16 * 1024 * 1024

@Composable
internal fun BrowserLiveSheet(
    browser: BrowserUiState,
    serverUrl: String,
    onDismiss: () -> Unit,
    onLiveControlChange: (Boolean) -> Unit,
    onInput: (BrowserInput) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val addressState = remember(browser.threadId) { mutableStateOf(browser.url) }
    var address by addressState
    var textInputVisible by remember(browser.threadId) { mutableStateOf(false) }
    val textInputState = remember(browser.threadId) { mutableStateOf("") }
    val textInputFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun navigate() {
        onInput(BrowserInput.Navigate(addressState.value))
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun sendText() {
        val text = textInputState.value
        if (text.isEmpty()) return
        onInput(BrowserInput.Text(text))
        textInputState.value = ""
        textInputVisible = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    LaunchedEffect(browser.url) {
        if (browser.url.isNotBlank()) address = browser.url
    }
    LaunchedEffect(textInputVisible) {
        if (textInputVisible) textInputFocusRequester.requestFocus()
    }
    LaunchedEffect(browser.liveControlEnabled) {
        if (!browser.liveControlEnabled) {
            textInputVisible = false
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
    val previewUrl = remember(serverUrl, browser.threadId, browser.preview) {
        browser.preview?.screenshot?.takeIf(String::isNotBlank)?.let { screenshot ->
            if (isInlineDisplayableImageUrl(screenshot)) screenshot
            else browser.threadId?.let { threadId -> resolveArtifactURL(serverUrl, threadId, screenshot) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(UiTags.BrowserSheet),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = browser.preview?.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.browser_live),
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = browserStatusLabel(browser.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (browser.status) {
                        BrowserLiveStatus.Error -> MaterialTheme.colorScheme.error
                        BrowserLiveStatus.Live -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.browser_live_close))
                }
            }

            OutlinedTextField(
                value = addressState.value,
                onValueChange = { address = it },
                enabled = browser.liveControlEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTags.BrowserAddressInput),
                label = { Text(stringResource(R.string.browser_live_address)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Language, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = ::navigate, enabled = browser.liveControlEnabled) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = stringResource(R.string.browser_live_navigate),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigate() }),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onInput(BrowserInput.Back) }, enabled = browser.liveControlEnabled) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.browser_live_back))
                }
                IconButton(onClick = { onInput(BrowserInput.Forward) }, enabled = browser.liveControlEnabled) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = stringResource(R.string.browser_live_forward))
                }
                IconToggleButton(
                    checked = browser.liveControlEnabled,
                    onCheckedChange = onLiveControlChange,
                    modifier = Modifier.testTag(UiTags.BrowserLiveControl),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_live_control),
                        contentDescription = stringResource(
                            if (browser.liveControlEnabled) {
                                R.string.browser_live_stop_control
                            } else {
                                R.string.browser_live_take_control
                            },
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    enabled = browser.liveControlEnabled,
                    onClick = {
                        textInputVisible = !textInputVisible
                        if (!textInputVisible) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Keyboard, contentDescription = stringResource(R.string.browser_live_keyboard))
                }
            }

            if (browser.tabs.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(browser.tabs, key = { it.index }) { tab ->
                        AssistChip(
                            enabled = browser.liveControlEnabled,
                            onClick = { onInput(BrowserInput.ActivateTab(tab.index)) },
                            label = {
                                Text(
                                    tab.title.ifBlank { tab.url }.ifBlank { stringResource(R.string.browser_live) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = if (tab.active) {
                                { Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            AnimatedVisibility(textInputVisible && browser.liveControlEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textInputState.value,
                        onValueChange = { textInputState.value = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(textInputFocusRequester)
                            .testTag(UiTags.BrowserTextInput),
                        label = { Text(stringResource(R.string.browser_live_type_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendText() }),
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        modifier = Modifier.testTag(UiTags.BrowserTextSend),
                        onClick = ::sendText,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.browser_live_send_text))
                    }
                }
            }

            browser.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            AnimatedVisibility(!textInputVisible) {
                BrowserViewport(
                    frameBase64 = browser.frameBase64,
                    previewUrl = previewUrl,
                    status = browser.status,
                    interactive = browser.liveControlEnabled,
                    onInput = onInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(BROWSER_REMOTE_VIEWPORT_ASPECT_RATIO),
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun browserStatusLabel(status: BrowserLiveStatus): String = when (status) {
    BrowserLiveStatus.Idle -> stringResource(R.string.browser_live)
    BrowserLiveStatus.Connecting -> stringResource(R.string.browser_live_connecting)
    BrowserLiveStatus.Live -> stringResource(R.string.browser_live_connected)
    BrowserLiveStatus.Error -> stringResource(R.string.browser_live_error)
}

@Composable
private fun BrowserViewport(
    frameBase64: String?,
    previewUrl: String?,
    status: BrowserLiveStatus,
    interactive: Boolean,
    onInput: (BrowserInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    var liveBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember(previewUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(frameBase64) {
        liveBitmap = frameBase64?.let { encoded ->
            withContext(Dispatchers.Default) { decodeBrowserFrame(encoded) }
        }
    }
    LaunchedEffect(previewUrl) {
        previewBitmap = previewUrl?.let { url ->
            withContext(Dispatchers.IO) { loadCachedDisplayBitmap(url) }
        }
    }
    val bitmap = liveBitmap ?: previewBitmap
    val frameWidth = bitmap?.width?.toFloat() ?: BROWSER_REMOTE_VIEWPORT_WIDTH
    val frameHeight = bitmap?.height?.toFloat() ?: BROWSER_REMOTE_VIEWPORT_HEIGHT

    val inputModifier = if (interactive) {
        Modifier
            .pointerInput(onInput, frameWidth, frameHeight) {
                detectTapGestures { point ->
                    browserFrameBounds(
                        containerWidth = size.width.toFloat(),
                        containerHeight = size.height.toFloat(),
                        frameWidth = frameWidth,
                        frameHeight = frameHeight,
                    )?.normalizedPointAt(point.x, point.y)?.let { normalized ->
                        onInput(
                            BrowserInput.Click(
                                nx = normalized.nx,
                                ny = normalized.ny,
                            ),
                        )
                    }
                }
            }
            .pointerInput(onInput, frameWidth, frameHeight) {
                detectVerticalDragGestures { change, dragAmount ->
                    browserFrameBounds(
                        containerWidth = size.width.toFloat(),
                        containerHeight = size.height.toFloat(),
                        frameWidth = frameWidth,
                        frameHeight = frameHeight,
                    )?.normalizedPointAt(change.position.x, change.position.y)?.let { normalized ->
                        onInput(
                            BrowserInput.Wheel(
                                dx = 0f,
                                dy = -dragAmount,
                                nx = normalized.nx,
                                ny = normalized.ny,
                            ),
                        )
                    }
                    change.consume()
                }
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .testTag(UiTags.BrowserViewport)
            .then(inputModifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.browser_live_frame),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentScale = ContentScale.Fit,
            )
            status == BrowserLiveStatus.Connecting || status == BrowserLiveStatus.Live -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoadingIndicator(Modifier.size(28.dp))
                    Text(stringResource(R.string.browser_live_waiting_for_frame), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> Text(stringResource(R.string.browser_live_no_page), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun decodeBrowserFrame(encoded: String): Bitmap? = runCatching {
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    if (bytes.isEmpty() || bytes.size > MAX_BROWSER_FRAME_BYTES) {
        null
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || pixels > MAX_BROWSER_FRAME_PIXELS) {
            null
        } else {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
}.getOrNull()
