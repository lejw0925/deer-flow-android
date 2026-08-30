package com.deerflow.mobile.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.CookieManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deerflow.mobile.data.isInlineDisplayableImageUrl
import com.deerflow.mobile.data.resolveMessageImageURL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class MarkdownImageContext(
    val serverUrl: String = "",
    val threadId: String = "",
    val artifactPaths: List<String> = emptyList(),
    val onOpenArtifact: (String) -> Unit = {},
)

val LocalMarkdownImageContext = staticCompositionLocalOf { MarkdownImageContext() }

@Composable
fun ProvideMarkdownImageContext(
    context: MarkdownImageContext,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMarkdownImageContext provides context, content = content)
}

/**
 * Bounded LRU: chat messages can embed many photos and nothing evicted here
 * before, so a long session grew without limit (full-resolution decodes).
 * ~48 full-screen ARGB_8888 bitmaps ≈ 96MB ceiling on the density-scaled
 * dimension cap below; decode bounds are also sampled down to it.
 */
private const val MAX_IMAGE_DIMENSION = 1600
private val bitmapCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
        size > 48
}
private val bitmapCacheLock = Any()

private fun cacheBitmap(url: String, bitmap: Bitmap): Bitmap = synchronized(bitmapCacheLock) {
    bitmapCache[url] = bitmap
    bitmap
}

private fun cachedBitmap(url: String): Bitmap? = synchronized(bitmapCacheLock) { bitmapCache[url] }

/** Two-pass decode: bounds first, then downsample so max(w, h) fits [MAX_IMAGE_DIMENSION]. */
private fun decodeSampled(bytes: ByteArray?, streamFactory: (() -> java.io.InputStream)?): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    if (bytes != null) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    } else {
        streamFactory!!().use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_DIMENSION) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return if (bytes != null) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } else {
        streamFactory!!().use { BitmapFactory.decodeStream(it, null, options) }
    }
}
private val imageHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder().build()
}

@Composable
internal fun MarkdownMessageImage(
    destination: String?,
    title: String?,
    onArtifact: (String) -> Unit,
) {
    val imageContext = LocalMarkdownImageContext.current
    val src = destination.orEmpty()
    if (src.isBlank()) return
    val resolved = remember(src, imageContext.serverUrl, imageContext.threadId, imageContext.artifactPaths) {
        if (imageContext.serverUrl.isBlank() || imageContext.threadId.isBlank()) src
        else resolveMessageImageURL(src, imageContext.serverUrl, imageContext.threadId, imageContext.artifactPaths)
    }
    val label = markdownImageLabel(title, destination)
    if (!isInlineDisplayableImageUrl(resolved)) {
        TextButton(
            onClick = { if (src.isArtifactPath()) onArtifact(src) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label.ifBlank { src }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        return
    }

    var bitmap by remember(resolved) { mutableStateOf(bitmapCache[resolved]) }
    var failed by remember(resolved) { mutableStateOf(false) }
    LaunchedEffect(resolved) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { loadCachedDisplayBitmap(resolved) }
        if (loaded != null) bitmap = loaded else failed = true
    }

    when {
        bitmap != null -> {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = label.ifBlank { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 420.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(12.dp),
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (src.isArtifactPath()) onArtifact(src)
                        else imageContext.onOpenArtifact(src)
                    },
            )
        }
        failed -> {
            TextButton(onClick = { if (src.isArtifactPath()) onArtifact(src) }) {
                Text(label.ifBlank { resolved }, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        else -> {
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(16f / 10f)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp),
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Shared by Markdown and Browser Live so both use the authenticated WebView cookie jar. */
internal fun loadCachedDisplayBitmap(url: String): Bitmap? {
    cachedBitmap(url)?.let { return it }
    val loaded = loadMarkdownBitmap(url) ?: return null
    return cacheBitmap(url, loaded)
}

private fun loadMarkdownBitmap(url: String): Bitmap? = runCatching {
    if (url.startsWith("data:image/")) {
        val comma = url.indexOf(',')
        if (comma < 0) return null
        val bytes = Base64.decode(url.substring(comma + 1), Base64.DEFAULT)
        return decodeSampled(bytes, null)
    }
    val requestBuilder = Request.Builder().url(url).get()
    CookieManager.getInstance().getCookie(url)?.let { cookie ->
        requestBuilder.header("Cookie", cookie)
    }
    imageHttpClient.newCall(requestBuilder.build()).execute().use { response ->
        if (!response.isSuccessful) return null
        val body = response.body ?: return null
        // Two passes need two streams: buffer the bytes once, then decode twice.
        val bytes = body.byteStream().use { it.readBytes() }
        decodeSampled(bytes, null)
    }
}.getOrNull()

internal fun String.isArtifactPath(): Boolean =
    startsWith("/mnt/") || startsWith("mnt/") || startsWith("sandbox:/mnt/")
