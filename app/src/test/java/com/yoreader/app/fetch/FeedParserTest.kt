package com.yoreader.app.fetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.time.OffsetDateTime

class FeedParserTest {

    private val parser = FeedParser { KXmlParser() }

    private fun parse(xml: String, base: String = "https://example.com/feed.xml") =
        parser.parse(xml.trimIndent().byteInputStream(), base)

    private fun millis(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun rss2() {
        val feed = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"
                 xmlns:atom="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>El Blog &amp; Más</title>
                <link>https://example.com/</link>
                <atom:link href="https://example.com/feed.xml" rel="self"/>
                <language>es-ES</language>
                <item>
                  <title>Primera entrada</title>
                  <media:title>Not this one</media:title>
                  <link>/posts/1</link>
                  <guid isPermaLink="false">post-1</guid>
                  <description><![CDATA[<p>Resumen</p>]]></description>
                  <content:encoded><![CDATA[<p>Texto <b>completo</b></p>]]></content:encoded>
                  <pubDate>Tue, 05 Mar 2024 10:30:00 +0100</pubDate>
                </item>
                <item>
                  <title>Second</title>
                  <guid>https://example.com/posts/2</guid>
                </item>
              </channel>
            </rss>
            """
        )
        assertEquals("El Blog & Más", feed.title)
        assertEquals("https://example.com/", feed.siteUrl)
        assertEquals("es", feed.language)
        assertEquals(2, feed.items.size)

        val first = feed.items[0]
        assertEquals("post-1", first.guid)
        assertEquals("Primera entrada", first.title)
        assertEquals("https://example.com/posts/1", first.link)
        assertEquals("<p>Resumen</p>", first.summary)
        assertEquals("<p>Texto <b>completo</b></p>", first.contentHtml)
        assertEquals(millis("2024-03-05T10:30:00+01:00"), first.publishedAt)

        // Permalink guid doubles as the link.
        assertEquals("https://example.com/posts/2", feed.items[1].link)
        assertNull(feed.items[1].publishedAt)
    }

    @Test
    fun atom() {
        val feed = parse(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xml:lang="en-US">
              <title type="html">News &amp;amp; Notes</title>
              <link rel="self" href="https://example.org/atom.xml"/>
              <link href="https://example.org/"/>
              <entry>
                <id>tag:example.org,2024:1</id>
                <title>Hello</title>
                <link rel="edit" href="https://example.org/edit/1"/>
                <link rel="alternate" type="text/html" href="https://example.org/hello"/>
                <updated>2024-03-06T12:00:00Z</updated>
                <published>2024-03-05T08:00:00Z</published>
                <summary>Short</summary>
                <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Hi &amp; bye</p></div></content>
              </entry>
            </feed>
            """,
            base = "https://example.org/atom.xml",
        )
        assertEquals("News & Notes", feed.title)
        assertEquals("https://example.org/", feed.siteUrl)
        assertEquals("en", feed.language)
        val e = feed.items.single()
        assertEquals("tag:example.org,2024:1", e.guid)
        assertEquals("https://example.org/hello", e.link)
        assertEquals("Short", e.summary)
        assertEquals("<div><p>Hi &amp; bye</p></div>", e.contentHtml)
        assertEquals(millis("2024-03-05T08:00:00Z"), e.publishedAt)
    }

    @Test
    fun rss1Rdf() {
        val feed = parse(
            """
            <?xml version="1.0"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns="http://purl.org/rss/1.0/" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel rdf:about="https://example.net/">
                <title>RDF Site</title>
                <link>https://example.net/</link>
                <dc:language>en</dc:language>
              </channel>
              <item rdf:about="https://example.net/a">
                <title>A</title>
                <link>https://example.net/a</link>
                <dc:date>2024-01-02T03:04:05+00:00</dc:date>
              </item>
            </rdf:RDF>
            """
        )
        assertEquals("RDF Site", feed.title)
        assertEquals("en", feed.language)
        val item = feed.items.single()
        assertEquals("https://example.net/a", item.guid)
        assertEquals(millis("2024-01-02T03:04:05Z"), item.publishedAt)
    }

    @Test
    fun untitledItemGetsTitleFromText() {
        val feed = parse(
            """
            <rss version="2.0"><channel><title>T</title>
              <item><description>&lt;p&gt;Just a short note&lt;/p&gt;</description><link>https://x.com/1</link></item>
            </channel></rss>
            """
        )
        assertEquals("Just a short note", feed.items.single().title)
    }

    @Test
    fun undeclaredHtmlEntityIsTolerated() {
        val feed = parse(
            """
            <rss version="2.0"><channel><title>A&nbsp;B</title>
              <item><title>x</title><link>https://x.com/1</link></item>
            </channel></rss>
            """
        )
        assertTrue(feed.title!!.startsWith("A"))
        assertEquals(1, feed.items.size)
    }

    @Test(expected = FeedParseException::class)
    fun htmlIsRejected() {
        parse("<html><body>nope</body></html>")
    }
}
