package com.yoreader.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class OpmlTest {

    private fun parse(xml: String) = Opml.parse(xml.trimIndent().byteInputStream(), KXmlParser())

    @Test
    fun roundTripKeepsGroupsAndUngroupedFeeds() {
        val eldiario = Feed(id = 1, title = "elDiario.es", url = "https://www.eldiario.es/rss/", siteUrl = "https://www.eldiario.es/")
        val xkcd = Feed(id = 2, title = "xkcd & friends", url = "https://xkcd.com/atom.xml")
        val loose = Feed(id = 3, title = "Loose", url = "https://example.com/feed")
        val groups = listOf(
            GroupWithFeeds(FeedGroup(id = 10, name = "News"), listOf(eldiario)),
            GroupWithFeeds(FeedGroup(id = 11, name = "Tech \"&\" fun"), listOf(xkcd, eldiario)),
        )

        val xml = Opml.write(groups, listOf(eldiario, xkcd, loose))
        val feeds = Opml.parse(xml.byteInputStream(), KXmlParser()).associateBy { it.url }

        assertEquals(3, feeds.size)
        assertEquals(setOf("News", "Tech \"&\" fun"), feeds.getValue(eldiario.url).groups)
        assertEquals("https://www.eldiario.es/", feeds.getValue(eldiario.url).siteUrl)
        assertEquals("xkcd & friends", feeds.getValue(xkcd.url).title)
        assertEquals(setOf("Tech \"&\" fun"), feeds.getValue(xkcd.url).groups)
        assertTrue(feeds.getValue(loose.url).groups.isEmpty())
    }

    @Test
    fun readsTypicalExportFromOtherReaders() {
        val feeds = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <head><title>Feedly export</title></head>
              <body>
                <outline text="Ciencia" title="Ciencia">
                  <outline type="rss" text="Naukas" xmlUrl="https://naukas.com/feed/" htmlUrl="https://naukas.com"/>
                </outline>
                <outline text="Sin título" xmlurl="http://old.example.org/rss.xml"/>
              </body>
            </opml>
            """
        )
        assertEquals(2, feeds.size)
        assertEquals("Naukas", feeds[0].title)
        assertEquals(setOf("Ciencia"), feeds[0].groups)
        // Lower-case attribute names (seen in the wild) are accepted too.
        assertEquals("http://old.example.org/rss.xml", feeds[1].url)
        assertTrue(feeds[1].groups.isEmpty())
    }

    @Test(expected = OpmlException::class)
    fun rejectsOtherXml() {
        parse("<rss><channel><title>x</title></channel></rss>")
    }
}
