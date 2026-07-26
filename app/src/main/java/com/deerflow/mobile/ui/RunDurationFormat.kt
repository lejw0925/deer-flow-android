package com.deerflow.mobile.ui

data class RunDurationLabels(
    val lessThanSecond: String,
    val hours: (Int) -> String,
    val minutes: (Int) -> String,
    val seconds: (Int) -> String,
    val separator: String,
)

/** Mirrors web formatRunDuration: floor non-negative seconds; skip zero units. */
fun formatRunDuration(value: Int, labels: RunDurationLabels): String? {
    if (value < 0) return null
    if (value == 0) return labels.lessThanSecond
    val hours = value / 3600
    val minutes = (value % 3600) / 60
    val seconds = value % 60
    val parts = buildList {
        if (hours > 0) add(labels.hours(hours))
        if (minutes > 0) add(labels.minutes(minutes))
        if (seconds > 0) add(labels.seconds(seconds))
    }
    return parts.joinToString(labels.separator).ifEmpty { null }
}
