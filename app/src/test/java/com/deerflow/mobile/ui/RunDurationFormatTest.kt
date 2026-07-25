package com.deerflow.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunDurationFormatTest {
    private val labels = RunDurationLabels(
        lessThanSecond = "<1s",
        hours = { h -> "${h}h" },
        minutes = { m -> "${m}m" },
        seconds = { s -> "${s}s" },
        separator = " ",
    )

    @Test
    fun zeroIsLessThanSecond() {
        assertEquals("<1s", formatRunDuration(0, labels))
    }

    @Test
    fun negativeOrInvalidIsNull() {
        assertNull(formatRunDuration(-1, labels))
    }

    @Test
    fun formatsHoursMinutesSeconds() {
        assertEquals("1h 2m 3s", formatRunDuration(3723, labels))
    }

    @Test
    fun omitsZeroUnits() {
        assertEquals("2m", formatRunDuration(120, labels))
        assertEquals("45s", formatRunDuration(45, labels))
    }
}
