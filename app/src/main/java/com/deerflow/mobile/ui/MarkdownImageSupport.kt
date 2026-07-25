package com.deerflow.mobile.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.CookieManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
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
        val loaded = withContext(Dispatchers.IO) { loadMarkdownBitmap(resolved) }
        if (loaded != null) {
            bitmapCache[resolved] = loaded
            bitmap = loaded
        } else {
            failed = true
        }
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun loadMarkdownBitmap(url: String): Bitmap? = runCatching {
    if (url.startsWith("data:image/")) {
        val comma = url.indexOf(',')
        if (comma < 0) return null
        val bytes = Base64.decode(url.substring(comma + 1), Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    val requestBuilder = Request.Builder().url(url).get()
    CookieManager.getInstance().getCookie(url)?.let { cookie ->
        requestBuilder.header("Cookie", cookie)
    }
    imageHttpClient.newCall(requestBuilder.build()).execute().use { response ->
        if (!response.isSuccessful) return null
        response.body?.byteStream()?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }
}.getOrNull()

internal fun String.isArtifactPath(): Boolean =
    startsWith("/mnt/") || startsWith("mnt/") || startsWith("sandbox:/mnt/")
