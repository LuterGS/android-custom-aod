package dev.lutergs.sgaod.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class NotificationTimeTest {
    @Test fun relativeAgeUsesWholeMinutes() {
        assertEquals(0L, NotificationTime.minutesAgo(1_000, 60_999))
        assertEquals(1L, NotificationTime.minutesAgo(1_000, 61_000))
        assertEquals(5L, NotificationTime.minutesAgo(1_000, 301_000))
    }
    @Test fun futureAndMissingTimestampsNeverShowNegativeAge() {
        assertEquals(0L, NotificationTime.minutesAgo(100_000, 1_000))
        assertNull(NotificationTime.minutesAgo(0, 100_000))
        assertNull(NotificationTime.minutesAgo(-1, 100_000))
        assertEquals("—", NotificationTime.absolute(0, ZoneId.of("UTC")))
    }
    @Test fun absoluteTimeUsesTheCurrentZoneAndLeadingZero() {
        val posted = Instant.parse("2026-09-09T18:15:00Z").toEpochMilli()
        assertEquals("03:15", NotificationTime.absolute(posted, ZoneId.of("Asia/Seoul")))
        assertEquals("18:15", NotificationTime.absolute(posted, ZoneId.of("UTC")))
    }
}
