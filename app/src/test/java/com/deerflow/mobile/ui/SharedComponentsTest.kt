package com.deerflow.mobile.ui

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedComponentsTest {
    @Test
    fun displayTimeConvertsUtcTimestampToTheRequestedTimezone() {
        assertEquals(
            "2026-07-22 11:21:23",
            "2026-07-22T03:21:23.835590+00:00".toDisplayTime(ZoneId.of("Asia/Shanghai")),
        )
    }

    @Test
    fun displayTimeLeavesMalformedValuesReadable() {
        assertEquals("not a timestamp", "not a timestamp".toDisplayTime(ZoneId.of("Asia/Shanghai")))
    }

    @Test
    fun compactDisplayTimeShowsClockTimeForToday() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-08-27T08:00:00Z")
        assertEquals(
            "15:30",
            "2026-08-27T07:30:00Z".toCompactDisplayTime(zone, now),
        )
    }

    @Test
    fun compactDisplayTimeShowsMonthDayForSameYear() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-08-27T08:00:00Z")
        assertEquals(
            "07-22 11:21",
            "2026-07-22T03:21:23Z".toCompactDisplayTime(zone, now),
        )
    }

    @Test
    fun compactDisplayTimeShowsDateOnlyForEarlierYears() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-08-27T08:00:00Z")
        assertEquals(
            "2025-06-15",
            "2025-06-15T04:00:00Z".toCompactDisplayTime(zone, now),
        )
    }

    @Test
    fun compactDisplayTimeLeavesMalformedValuesReadable() {
        assertEquals(
            "not a timestamp",
            "not a timestamp".toCompactDisplayTime(ZoneId.of("Asia/Shanghai"), Instant.parse("2026-08-27T08:00:00Z")),
        )
    }
}
