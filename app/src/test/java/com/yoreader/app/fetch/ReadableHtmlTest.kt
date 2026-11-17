package com.yoreader.app.fetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.jsoup.Jsoup
import org.junit.Test

class ReadableHtmlTest {

    @Test
    fun marksTheElementOfEachParagraphReadAloud() {
        val r = Html.readable(
            "<h2>Título</h2><p>Uno <b>dos</b></p><figure><img src=x><figcaption>pie</figcaption></figure>" +
                "<div>Tres<br>cuatro</div><ul><li>cinco</li></ul>",
            firstIndex = 1,
        )
        assertEquals(listOf("Título", "Uno dos", "Tres", "cuatro", "cinco"), r.paragraphs)
        val doc = Jsoup.parseBodyFragment(r.html)
        assertEquals("1", doc.selectFirst("h2")!!.attr(Html.PARAGRAPH_ATTR))
        assertEquals("2", doc.selectFirst("p")!!.attr(Html.PARAGRAPH_ATTR))
        // Both lines of the div belong to it; the skipped figure stays on screen, unmarked.
        assertEquals("3 4", doc.selectFirst("div")!!.attr(Html.PARAGRAPH_ATTR))
        assertEquals("5", doc.selectFirst("li")!!.attr(Html.PARAGRAPH_ATTR))
        assertTrue(doc.selectFirst("figure img") != null)
        assertFalse(doc.selectFirst("figure")!!.hasAttr(Html.PARAGRAPH_ATTR))
    }

    @Test
    fun removesTheArticlesOwnScripts() {
        val r = Html.readable(
            "<p onclick=\"steal()\">Hola <a href=\"javascript:steal()\">x</a> <a href=\"https://a.es/\">y</a></p>" +
                "<script>steal()</script><iframe src=\"https://video.example/embed\"></iframe>"
        )
        val doc = Jsoup.parseBodyFragment(r.html)
        assertTrue(doc.select("script").isEmpty())
        assertFalse(doc.selectFirst("p")!!.hasAttr("onclick"))
        assertFalse(doc.select("a")[0].hasAttr("href"))
        assertEquals("https://a.es/", doc.select("a")[1].attr("href"))
        assertTrue(doc.selectFirst("iframe")!!.hasAttr("sandbox"))
    }
}
