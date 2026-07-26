package com.deerflow.mobile.data

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_BROWSER_FRAME_BASE64_CHARS = 8 * 1024 * 1024
private const val MAX_BROWSER_TEXT_CHARS = 4 * 1024
private const val MAX_BROWSER_KEY_CHARS = 128
private const val MAX_BROWSER_WHEEL_DELTA = 2_000f
private val BASE64_PAYLOAD = Regex("^[A-Za-z0-9+/]*={0,2}$")

data class BrowserTab(
    val index: Int,
    val title: String,
    val url: String,
    val active: Boolean,
)

internal sealed interface BrowserLiveEvent {
    data object Opened : BrowserLiveEvent
    data class Frame(val jpegBase64: String) : BrowserLiveEvent
    data class Url(val value: String) : BrowserLiveEvent
    data class Tabs(val values: List<BrowserTab>) : BrowserLiveEvent
    data class NavigationRejected(val url: String?, val message: String?) : BrowserLiveEvent
    data class Closed(val code: Int, val reason: String) : BrowserLiveEvent
    data class Failure(val message: String) : BrowserLiveEvent
}

sealed interface BrowserInput {
    data class Click(val nx: Float, val ny: Float) : BrowserInput
    data class Move(val nx: Float, val ny: Float) : BrowserInput
    data class Down(val nx: Float, val ny: Float) : BrowserInput
    data class Up(val nx: Float, val ny: Float) : BrowserInput
    data class Wheel(val dx: Float, val dy: Float, val nx: Float? = null, val ny: Float? = null) : BrowserInput
    data class Key(val key: String) : BrowserInput
    data class Text(val text: String) : BrowserInput
    data class Navigate(val url: String) : BrowserInput
    data object Back : BrowserInput
    data object Forward : BrowserInput
    data class ActivateTab(val index: Int) : BrowserInput
}

internal interface BrowserLiveConnection {
    fun send(input: BrowserInput): Boolean
    fun close()
}

internal fun parseBrowserLiveEvent(payload: String): BrowserLiveEvent? = runCatching {
    val root = JSONObject(payload)
    when (root.optString("type")) {
        "frame" -> root.optString("data")
            .takeIf(::isBrowserFramePayload)
            ?.let(BrowserLiveEvent::Frame)
        "url" -> root.optString("url").takeIf(String::isNotBlank)?.let(BrowserLiveEvent::Url)
        "tabs" -> BrowserLiveEvent.Tabs(root.optJSONArray("tabs").toBrowserTabs())
        "nav_rejected" -> BrowserLiveEvent.NavigationRejected(
            url = root.optString("url").trim().takeIf(String::isNotBlank),
            message = root.optString("message").trim().takeIf(String::isNotBlank),
        )
        else -> null
    }
}.getOrNull()

internal fun BrowserInput.toBrowserWirePayload(): String? = when (this) {
    is BrowserInput.Click -> pointerPayload("click", nx, ny)
    is BrowserInput.Move -> pointerPayload("move", nx, ny)
    is BrowserInput.Down -> pointerPayload("down", nx, ny)
    is BrowserInput.Up -> pointerPayload("up", nx, ny)
    is BrowserInput.Wheel -> {
        val boundedDx = boundedWheel(dx)
        val boundedDy = boundedWheel(dy)
        if (boundedDx == 0f && boundedDy == 0f) {
            null
        } else {
            JSONObject().put("type", "wheel")
                .put("dx", boundedDx)
                .put("dy", boundedDy)
                .also { payload ->
                    nx?.let { payload.put("nx", normalizedPointer(it)) }
                    ny?.let { payload.put("ny", normalizedPointer(it)) }
                }
                .toString()
        }
    }
    is BrowserInput.Key -> key.trim().take(MAX_BROWSER_KEY_CHARS)
        .takeIf(String::isNotBlank)
        ?.let { JSONObject().put("type", "key").put("key", it).toString() }
    is BrowserInput.Text -> text.take(MAX_BROWSER_TEXT_CHARS)
        .takeIf(String::isNotEmpty)
        ?.let { JSONObject().put("type", "text").put("text", it).toString() }
    is BrowserInput.Navigate -> normalizeBrowserAddress(url)
        ?.let { JSONObject().put("type", "navigate").put("url", it).toString() }
    BrowserInput.Back -> JSONObject().put("type", "back").toString()
    BrowserInput.Forward -> JSONObject().put("type", "forward").toString()
    is BrowserInput.ActivateTab -> index.takeIf { it >= 0 }
        ?.let { JSONObject().put("type", "activate_tab").put("index", it).toString() }
}

internal fun browserStreamUrl(serverUrl: String, threadId: String, seedUrl: String? = null): String {
    require(threadId.isNotBlank()) { "A thread is required for Browser Live." }
    val base = URI(normalizeServerUrl(serverUrl))
    val scheme = when (base.scheme.lowercase()) {
        "http" -> "ws"
        "https" -> "wss"
        else -> error("Browser Live requires an HTTP or HTTPS server.")
    }
    val encodedThreadId = encodeBrowserSegment(threadId)
    val seed = seedUrl?.let(::normalizeBrowserAddress)
    return buildString {
        append(scheme)
        append("://")
        append(requireNotNull(base.rawAuthority))
        append("/api/threads/")
        append(encodedThreadId)
        append("/browser/stream")
        seed?.let {
            append("?seed=")
            append(encodeBrowserQuery(it))
        }
    }
}

/** CookieManager matches HTTP(S) origins, while the actual handshake uses WS(S). */
internal fun browserHandshakeCookieUrl(webSocketUrl: String): String = when {
    webSocketUrl.startsWith("ws://", ignoreCase = true) -> "http://${webSocketUrl.substringAfter("://")}"
    webSocketUrl.startsWith("wss://", ignoreCase = true) -> "https://${webSocketUrl.substringAfter("://")}"
    else -> webSocketUrl
}

internal fun normalizeBrowserAddress(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
    return uri.toASCIIString()
}

private fun JSONArray?.toBrowserTabs(): List<BrowserTab> = buildList {
    val source = this@toBrowserTabs ?: return@buildList
    for (index in 0 until source.length()) {
        val item = source.optJSONObject(index) ?: continue
        if (!item.has("index")) continue
        val tabIndex = item.optInt("index", -1)
        if (tabIndex < 0) continue
        add(
            BrowserTab(
                index = tabIndex,
                title = item.optString("title").trim(),
                url = item.optString("url").trim(),
                active = item.optBoolean("active"),
            ),
        )
    }
}

private fun pointerPayload(type: String, nx: Float, ny: Float): String = JSONObject()
    .put("type", type)
    .put("nx", normalizedPointer(nx))
    .put("ny", normalizedPointer(ny))
    .toString()

private fun normalizedPointer(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f

private fun boundedWheel(value: Float): Float =
    if (value.isFinite()) value.coerceIn(-MAX_BROWSER_WHEEL_DELTA, MAX_BROWSER_WHEEL_DELTA) else 0f

private fun isBrowserFramePayload(value: String): Boolean =
    value.isNotBlank() && value.length <= MAX_BROWSER_FRAME_BASE64_CHARS && BASE64_PAYLOAD.matches(value)

private fun encodeBrowserSegment(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun encodeBrowserQuery(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
