package com.yoreader.app.ui

import com.yoreader.app.ui.reader.SharedLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLinkTest {
    @Test
    fun aLinkAlone() {
        assertEquals("https://example.com/a?b=1#c", SharedLink.findUrl("https://example.com/a?b=1#c"))
    }

    @Test
    fun aHeadlineThenTheLink() {
        assertEquals(
            "https://historia.example.es/a/godwin_27098",
            SharedLink.findUrl("William Godwin, filósofo\nhttps://historia.example.es/a/godwin_27098"),
        )
    }

    @Test
    fun punctuationAfterTheLinkIsNotPartOfIt() {
        assertEquals("http://example.com/x", SharedLink.findUrl("Read this (http://example.com/x)."))
    }

    @Test
    fun theFirstLinkWins() {
        assertEquals("https://one.example/", SharedLink.findUrl("https://one.example/ via https://two.example/"))
    }

    @Test
    fun noLink() {
        assertNull(SharedLink.findUrl("just some words"))
        assertNull(SharedLink.findUrl(null))
        assertNull(SharedLink.findUrl("ftp://example.com/file"))
    }
}
