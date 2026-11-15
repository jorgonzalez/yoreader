package com.yoreader.app.fetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class FeedDatesTest {

    private fun millis(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun rfc822Variants() {
        val expected = millis("2024-03-05T10:30:00Z")
        assertEquals(expected, FeedDates.parse("Tue, 05 Mar 2024 10:30:00 GMT"))
        assertEquals(expected, FeedDates.parse("Tue, 5 Mar 2024 10:30:00 +0000"))
        assertEquals(expected, FeedDates.parse("5 Mar 2024 10:30:00 UTC"))
        assertEquals(expected, FeedDates.parse("Tue, 05 Mar 2024 11:30 +01:00"))
        assertEquals(expected, FeedDates.parse("Tue, 05 Mar 24 05:30:00 EST"))
        assertEquals(expected, FeedDates.parse("Tuesday, 05 March 2024 10:30:00 Z"))
    }

    @Test
    fun spanishMonthNames() {
        assertEquals(millis("2024-01-15T09:00:00+01:00"), FeedDates.parse("lun, 15 ene 2024 09:00:00 +0100"))
        assertEquals(millis("2024-12-24T20:00:00Z"), FeedDates.parse("mar., 24 dic. 2024 20:00:00 GMT"))
    }

    @Test
    fun iso8601Variants() {
        assertEquals(millis("2024-03-05T10:30:00Z"), FeedDates.parse("2024-03-05T10:30:00Z"))
        assertEquals(millis("2024-03-05T10:30:00.123+02:00"), FeedDates.parse("2024-03-05T10:30:00.123+02:00"))
        assertEquals(millis("2024-03-05T10:30:00+02:00"), FeedDates.parse("2024-03-05 10:30:00+0200"))
        assertEquals(millis("2024-03-05T10:30:00Z"), FeedDates.parse("2024-03-05T10:30:00"))
        assertEquals(millis("2024-03-05T00:00:00Z"), FeedDates.parse("2024-03-05"))
    }

    @Test
    fun garbage() {
        assertNull(FeedDates.parse(null))
        assertNull(FeedDates.parse(""))
        assertNull(FeedDates.parse("yesterday"))
    }
}
