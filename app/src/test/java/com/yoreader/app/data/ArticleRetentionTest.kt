package com.yoreader.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleRetentionTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun cutoffIsThatManyDaysAgo() {
        val now = 1_000 * day
        assertEquals(now - 90 * day, ArticleRetention.cutoff(90, now))
        assertEquals(now - 30 * day, ArticleRetention.cutoff(30, now))
    }

    @Test
    fun neverMeansNoCutoff() {
        assertNull(ArticleRetention.cutoff(0, 1_000 * day))
    }

    @Test
    fun defaultIsOffered() {
        assertTrue(ArticleRetention.DEFAULT_DAYS in ArticleRetention.CHOICES)
    }
}
