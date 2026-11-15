package com.yoreader.app.fetch

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTest {

    @Test
    fun findsAdvertisedFeeds() {
        val page = """
            <html><head>
              <link rel="stylesheet" href="/style.css">
              <link rel="alternate" type="application/rss+xml" title="RSS" href="/feed/">
              <LINK REL='alternate' TYPE='application/atom+xml' HREF='https://e.com/atom?a=1&amp;b=2'>
              <link rel="alternate" hreflang="es" href="/es/">
            </head></html>
        """
        assertEquals(listOf("/feed/", "https://e.com/atom?a=1&b=2"), Html.findFeedLinks(page))
    }

    @Test
    fun plainText() {
        assertEquals("AT&T “quoted” — ñ", Html.toPlainText("AT&amp;amp;T &ldquo;quoted&rdquo; &#8212; &ntilde;"))
        assertEquals("a b", Html.toPlainText("<p>a</p>\n<p>b</p>"))
    }
}
