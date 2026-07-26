package com.deerflow.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deerflow.mobile.R
import com.deerflow.mobile.data.RunNoticeKind
import com.deerflow.mobile.data.StreamUpdate
import kotlinx.coroutines.delay

internal object UiTagsRunActivity {
    const val RunActivity = "run-activity"
}

@Composable
fun RunActivityRow(
    startedAtEpochMs: Long?,
    notice: StreamUpdate.RunNotice? = null,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember(startedAtEpochMs) { mutableIntStateOf(0) }
    LaunchedEffect(startedAtEpochMs) {
        if (startedAtEpochMs == null) {
            elapsedSeconds = 0
            return@LaunchedEffect
        }
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAtEpochMs) / 1000L)
                .toInt()
                .coerceAtLeast(0)
            delay(1000)
        }
    }
    val lessThanSecond = stringResource(R.string.run_duration_less_than_second)
    val hoursTemplate = stringResource(R.string.run_duration_hours)
    val minutesTemplate = stringResource(R.string.run_duration_minutes)
    val secondsTemplate = stringResource(R.string.run_duration_seconds)
    val labels = remember(lessThanSecond, hoursTemplate, minutesTemplate, secondsTemplate) {
        RunDurationLabels(
            lessThanSecond = lessThanSecond,
            hours = { hoursTemplate.format(it) },
            minutes = { minutesTemplate.format(it) },
            seconds = { secondsTemplate.format(it) },
            separator = " ",
        )
    }
    val formatted = formatRunDuration(elapsedSeconds, labels)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(UiTagsRunActivity.RunActivity)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LoadingIndicator(modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.run_activity_working),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (formatted != null) {
                Text(
                    stringResource(R.string.run_activity_elapsed, formatted),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        notice?.let { currentNotice ->
            val retry = currentNotice.kind == RunNoticeKind.LlmRetry
            val summary = if (retry) {
                stringResource(
                    R.string.run_notice_llm_retry,
                    currentNotice.attempt ?: 0,
                    currentNotice.maxAttempts ?: 0,
                )
            } else {
                stringResource(R.string.run_notice_safety_termination)
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (retry) Icons.Outlined.Refresh else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (retry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Column {
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    currentNotice.message.takeIf { it.isNotBlank() }?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
