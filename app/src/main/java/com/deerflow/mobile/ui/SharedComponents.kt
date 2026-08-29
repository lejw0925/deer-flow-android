package com.deerflow.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deerflow.mobile.R
import com.deerflow.mobile.ui.glass.GlassIconButton
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BrandMark(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(if (compact) 34.dp else 46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_wordmark),
                contentDescription = null,
                modifier = Modifier.size(if (compact) 34.dp else 46.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
            )
        }
        if (!compact) {
            Spacer(Modifier.width(12.dp))
            Text("DeerFlow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun StatusDot(status: String, description: String? = null) {
    val color = when (status.lowercase()) {
        "busy", "queued", "running", "pending", "enabled" -> MaterialTheme.colorScheme.tertiary
        "error", "failed", "interrupted" -> MaterialTheme.colorScheme.error
        "paused", "cancelled", "skipped" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    val semantics = if (description == null) {
        Modifier
    } else {
        Modifier.semantics { contentDescription = description }
    }
    Box(semantics.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun EmptyState(
    icon: @Composable () -> Unit,
    title: String,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/**
 * Floating screen header in the ChatScreen style: glass circular buttons and the
 * title hover over the recorded content with no full-width bar surface behind
 * them. Callers keep measuring [modifier] (onGloballyPositioned) to pad their
 * scrollable content below the header.
 */
@Composable
internal fun FloatingScreenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            GlassIconButton(onClick = onBack, modifier = backModifier) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun OfflineBanner() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.offline_cache), style = MaterialTheme.typography.labelLarge)
    }
}

private val displayTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val compactTodayFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val compactSameYearFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val compactFullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

fun String.toDisplayTime(zoneId: ZoneId = ZoneId.systemDefault()): String {
    val value = trim()
    if (value.isBlank()) return ""
    val instant = runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    return instant?.atZone(zoneId)?.format(displayTimeFormatter)
        ?: value.replace('T', ' ').substringBefore('.').removeSuffix("Z")
}

/**
 * Compact timestamp for dense lists (drawer rows): today -> HH:mm, same year ->
 * MM-dd HH:mm, older -> yyyy-MM-dd. Falls back to the raw value when unparsable.
 */
fun String.toCompactDisplayTime(zoneId: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): String {
    val value = trim()
    if (value.isBlank()) return ""
    val instant = runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: return value.replace('T', ' ').substringBefore('.').removeSuffix("Z")
    val time = instant.atZone(zoneId)
    val current = now.atZone(zoneId)
    return when {
        time.toLocalDate() == current.toLocalDate() -> time.format(compactTodayFormatter)
        time.year == current.year -> time.format(compactSameYearFormatter)
        else -> time.format(compactFullFormatter)
    }
}
