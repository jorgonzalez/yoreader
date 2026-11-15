package com.yoreader.app.fetch

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Lenient date parsing for feeds. RSS uses RFC 822 dates and Atom uses RFC 3339, but
 * real feeds mix them up, drop seconds, use time zone names or localized month names.
 * Returns epoch millis, or null if the text can't be understood.
 */
object FeedDates {

    fun parse(text: String?): Long? {
        val s = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return parseRfc822(s) ?: parseIso8601(s)
    }

    // "Mon, 2 Jan 2006 15:04:05 -0700", day name and seconds optional.
    private val RFC822 = Regex(
        """^(?:[^\d,\s]+,?\s*)?(\d{1,2})[\s-]+([^\d\s.-]+)\.?[\s-]+(\d{2,4})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(.*)$"""
    )

    /** English and Spanish month abbreviations ("set" is also used for septiembre). */
    private val MONTHS = mapOf(
        "jan" to 1, "ene" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "abr" to 4,
        "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "ago" to 8,
        "sep" to 9, "set" to 9, "oct" to 10, "nov" to 11, "dec" to 12, "dic" to 12,
    )

    private val ZONES = mapOf(
        "UT" to 0, "UTC" to 0, "GMT" to 0, "Z" to 0,
        "EST" to -5, "EDT" to -4, "CST" to -6, "CDT" to -5,
        "MST" to -7, "MDT" to -6, "PST" to -8, "PDT" to -7,
        "CET" to 1, "CEST" to 2,
    )

    private fun parseRfc822(s: String): Long? {
        val m = RFC822.matchEntire(s) ?: return null
        val (dayS, monS, yearS, hourS, minS, secS, zoneS) = m.destructured
        val month = MONTHS[monS.take(3).lowercase()] ?: return null
        var year = yearS.toInt()
        if (yearS.length == 2) year += if (year < 70) 2000 else 1900
        val offset = parseZone(zoneS.trim()) ?: return null
        return try {
            LocalDateTime.of(year, month, dayS.toInt(), hourS.toInt(), minS.toInt(), secS.toIntOrNull() ?: 0)
                .toInstant(offset)
                .toEpochMilli()
        } catch (e: java.time.DateTimeException) {
            null
        }
    }

    private val NUMERIC_ZONE = Regex("""^([+-])(\d{2}):?(\d{2})$""")

    private fun parseZone(z: String): ZoneOffset? {
        if (z.isEmpty()) return ZoneOffset.UTC
        NUMERIC_ZONE.matchEntire(z)?.let {
            val (sign, h, mm) = it.destructured
            val secs = h.toInt() * 3600 + mm.toInt() * 60
            return try {
                ZoneOffset.ofTotalSeconds(if (sign == "-") -secs else secs)
            } catch (e: java.time.DateTimeException) {
                null
            }
        }
        // Text zones; anything unknown (military letters, "Europe/Madrid") falls back to UTC.
        val name = z.substringBefore(' ').uppercase()
        return ZoneOffset.ofHours(ZONES[name] ?: 0)
    }

    private fun parseIso8601(input: String): Long? {
        // Accept "2024-01-02 10:00:00" and "+0100" offsets, which strict ISO rejects.
        val s = input.replace(' ', 'T').replace(Regex("""([+-]\d{2})(\d{2})$"""), "$1:$2")
        return try {
            OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (e: DateTimeParseException) {
                try {
                    LocalDate.parse(s.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                } catch (e: DateTimeParseException) {
                    null
                }
            }
        }
    }
}
