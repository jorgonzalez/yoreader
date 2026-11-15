package com.yoreader.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test
    fun foldsAccentsAndCase() {
        assertEquals("camion", SearchText.normalize("Camión"))
        assertEquals("camion", SearchText.normalize("CAMIÓN"))
        assertEquals("espana", SearchText.normalize("España"))
        assertEquals("pinguino cafe", SearchText.normalize("pingüino CAFÉ"))
        assertEquals("francais", SearchText.normalize("Français"))
    }

    @Test
    fun articleTextIsPlainAndFolded() {
        val text = SearchText.forArticle(
            title = "Él camión",
            summary = "<p class=\"lead\">Resumen &amp; más</p>",
            contentHtml = null,
        )
        assertTrue("camion" in text)
        assertTrue("resumen & mas" in text)
        // Markup is stripped, so searching "class" or "lead" doesn't match tags.
        assertTrue("class" !in text && "lead" !in text)
    }
}
