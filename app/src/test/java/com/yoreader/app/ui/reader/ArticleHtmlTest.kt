package com.yoreader.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleHtmlTest {

    @Test
    fun removesHeadingThatRepeatsTheTitle() {
        val body = "<a href=\"/t\">Tribunales</a><h1 class=\"x\">El PSOE lleva a Hacienda los 242.000 euros de &quot;Okdiario&quot;</h1><p>Texto</p>"
        assertEquals(
            "<a href=\"/t\">Tribunales</a><p>Texto</p>",
            ArticleHtml.withoutRepeatedTitle(body, "El PSOE lleva a Hacienda los 242.000 euros de \"Okdiario\""),
        )
    }

    @Test
    fun keepsOtherHeadings() {
        val body = "<h2>Una sección</h2><p>Texto</p>"
        assertEquals(body, ArticleHtml.withoutRepeatedTitle(body, "Otro título"))
    }
}
