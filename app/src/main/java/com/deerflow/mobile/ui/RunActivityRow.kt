package com.deerflow.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.delay

internal object UiTagsRunActivity {
    const val RunActivity = "run-activity"
}

@Composable
fun RunActivityRow(
    startedAtEpochMs: Long?,
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(UiTagsRunActivity.RunActivity)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
}
