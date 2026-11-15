package com.yoreader.app.fetch

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream
import java.net.URI

data class ParsedFeed(
    val title: String?,
    val siteUrl: String?,
    /** Two-letter language code from the feed, e.g. "es" or "en". */
    val language: String?,
    val items: List<ParsedItem>,
)

data class ParsedItem(
    val guid: String,
    val title: String,
    val link: String,
    val summary: String?,
    val contentHtml: String?,
    val publishedAt: Long?,
)

class FeedParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses RSS 2.0, RSS 1.0 (RDF) and Atom 1.0.
 *
 * [newParser] supplies the pull parser: `android.util.Xml.newPullParser()` in the app,
 * a kxml2 parser in JVM unit tests.
 */
class FeedParser(private val newParser: () -> XmlPullParser) {

    /** [baseUrl] is the feed's own URL, used to resolve relative links. */
    fun parse(input: InputStream, baseUrl: String): ParsedFeed {
        val p = newParser()
        try {
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            // Tolerates HTML entities like &nbsp; that many feeds use without declaring.
            runCatching { p.setFeature(FEATURE_RELAXED, true) }
            p.setInput(input, null) // null: detect the encoding from the BOM / XML declaration
            p.nextTag()
            return when (p.name.lowercase()) {
                "rss" -> parseRss(p, baseUrl)
                "rdf" -> parseRdf(p, baseUrl)
                "feed" -> parseAtom(p, baseUrl)
                else -> throw FeedParseException("Not an RSS or Atom feed (root element <${p.name}>)")
            }
        } catch (e: XmlPullParserException) {
            throw FeedParseException("The feed isn't valid XML", e)
        }
    }

    // ---------------------------------------------------------------- RSS 2.0

    private fun parseRss(p: XmlPullParser, base: String): ParsedFeed {
        var result: ParsedFeed? = null
        forEachChild(p) {
            if (p.name == "channel") result = parseRssChannel(p, base) else skip(p)
        }
        return result ?: throw FeedParseException("RSS feed has no <channel>")
    }

    private fun parseRssChannel(p: XmlPullParser, base: String): ParsedFeed {
        var title: String? = null
        var link: String? = null
        var language: String? = null
        val items = mutableListOf<ParsedItem>()
        forEachChild(p) {
            when {
                !isRssElement(p) -> skip(p) // e.g. <atom:link rel="self">, not the site link
                p.name == "title" -> title = readText(p)
                p.name == "link" -> link = readText(p)
                p.name == "language" -> language = readText(p)
                p.name == "item" -> readRssItem(p, base)?.let(items::add)
                else -> skip(p)
            }
        }
        return ParsedFeed(cleanTitle(title), resolve(base, link), normalizeLanguage(language), items)
    }

    /** Shared by RSS 2.0 and RSS 1.0 items, which use the same element names. */
    private fun readRssItem(p: XmlPullParser, base: String): ParsedItem? {
        val rdfAbout = p.getAttributeValue(RDF_NS, "about")
        var title: String? = null
        var link: String? = null
        var guid: String? = null
        var guidIsLink = false
        var description: String? = null
        var content: String? = null
        var date: String? = null
        forEachChild(p) {
            when {
                p.namespace == CONTENT_NS && p.name == "encoded" -> content = readText(p)
                p.namespace == DC_NS && p.name == "date" -> date = date ?: readText(p)
                !isRssElement(p) -> skip(p) // media:title, itunes:summary, atom:link...
                p.name == "title" -> title = readText(p)
                p.name == "link" -> link = readText(p)
                p.name == "guid" -> {
                    guidIsLink = p.getAttributeValue(null, "isPermaLink") != "false"
                    guid = readText(p)
                }
                p.name == "description" -> description = readText(p)
                p.name == "pubDate" -> date = readText(p)
                else -> skip(p)
            }
        }
        // Some feeds only put the URL in a permalink <guid>.
        val itemLink = resolve(base, link ?: guid?.takeIf { guidIsLink } ?: rdfAbout)
        return buildItem(
            guid = guid ?: rdfAbout,
            title = title,
            link = itemLink,
            summary = description,
            contentHtml = content,
            date = date,
        )
    }

    // ---------------------------------------------------------------- RSS 1.0

    private fun parseRdf(p: XmlPullParser, base: String): ParsedFeed {
        var title: String? = null
        var link: String? = null
        var language: String? = null
        val items = mutableListOf<ParsedItem>()
        forEachChild(p) {
            when (p.name) {
                "channel" -> forEachChild(p) {
                    when {
                        p.name == "title" -> title = readText(p)
                        p.name == "link" -> link = readText(p)
                        p.namespace == DC_NS && p.name == "language" -> language = readText(p)
                        else -> skip(p)
                    }
                }
                "item" -> readRssItem(p, base)?.let(items::add)
                else -> skip(p)
            }
        }
        return ParsedFeed(cleanTitle(title), resolve(base, link), normalizeLanguage(language), items)
    }

    // ---------------------------------------------------------------- Atom

    private fun parseAtom(p: XmlPullParser, base: String): ParsedFeed {
        val language = p.getAttributeValue(XML_NS, "lang")
        var title: String? = null
        var link: String? = null
        val items = mutableListOf<ParsedItem>()
        forEachChild(p) {
            when (p.name) {
                "title" -> title = readAtomText(p)
                "link" -> readAtomLink(p)?.let { link = link ?: it }
                "entry" -> readAtomEntry(p, base)?.let(items::add)
                else -> skip(p)
            }
        }
        return ParsedFeed(cleanTitle(title), resolve(base, link), normalizeLanguage(language), items)
    }

    private fun readAtomEntry(p: XmlPullParser, base: String): ParsedItem? {
        var id: String? = null
        var title: String? = null
        var link: String? = null
        var summary: String? = null
        var content: String? = null
        var published: String? = null
        var updated: String? = null
        forEachChild(p) {
            when (p.name) {
                "id" -> id = readText(p)
                "title" -> title = readAtomText(p)
                "link" -> readAtomLink(p)?.let { link = link ?: it }
                "summary" -> summary = readAtomText(p)
                "content" -> content = readAtomText(p)
                "published" -> published = readText(p)
                "updated" -> updated = readText(p)
                else -> skip(p)
            }
        }
        return buildItem(
            guid = id,
            title = title,
            link = resolve(base, link),
            summary = summary,
            contentHtml = content,
            date = published ?: updated,
        )
    }

    /** Returns the href of a rel="alternate" link (the default rel), else null. */
    private fun readAtomLink(p: XmlPullParser): String? {
        val rel = p.getAttributeValue(null, "rel") ?: "alternate"
        val href = p.getAttributeValue(null, "href")
        skip(p)
        return href?.takeIf { rel == "alternate" }
    }

    /** Atom text constructs: type="text" | "html" | "xhtml". Returns HTML for xhtml. */
    private fun readAtomText(p: XmlPullParser): String? =
        if (p.getAttributeValue(null, "type") == "xhtml") readInnerXml(p) else readText(p)

    // ---------------------------------------------------------------- helpers

    private fun buildItem(
        guid: String?,
        title: String?,
        link: String?,
        summary: String?,
        contentHtml: String?,
        date: String?,
    ): ParsedItem? {
        val cleanTitle = cleanTitle(title)
        if (link == null && cleanTitle == null) return null
        val publishedAt = FeedDates.parse(date)
        val id = guid?.trim()?.takeIf { it.isNotEmpty() }
            ?: link
            ?: "${cleanTitle}|${publishedAt ?: ""}"
        return ParsedItem(
            guid = id,
            // Untitled posts (common on microblogs) get a title from their text.
            title = cleanTitle ?: Html.toPlainText(summary ?: contentHtml.orEmpty()).take(80).ifBlank { link!! },
            link = link ?: "",
            summary = summary?.trim()?.takeIf { it.isNotEmpty() },
            contentHtml = contentHtml?.trim()?.takeIf { it.isNotEmpty() },
            publishedAt = publishedAt,
        )
    }

    /** Runs [block] with the parser on each direct child start tag of the current element. */
    private inline fun forEachChild(p: XmlPullParser, block: () -> Unit) {
        val depth = p.depth
        while (true) {
            val event = p.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && p.depth == depth) return
            if (event == XmlPullParser.START_TAG && p.depth == depth + 1) block()
        }
    }

    /** Text content of the current element, ignoring any child tags. Ends on its END_TAG. */
    private fun readText(p: XmlPullParser): String? {
        val sb = StringBuilder()
        val depth = p.depth
        while (true) {
            when (p.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> sb.append(p.text)
                XmlPullParser.END_TAG -> if (p.depth == depth) break
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString().trim().takeIf { it.isNotEmpty() }
    }

    /** Re-serializes the children of the current element as markup (for Atom xhtml). */
    private fun readInnerXml(p: XmlPullParser): String? {
        val sb = StringBuilder()
        val depth = p.depth
        while (true) {
            when (p.next()) {
                XmlPullParser.START_TAG -> {
                    sb.append('<').append(p.name)
                    for (i in 0 until p.attributeCount) {
                        sb.append(' ').append(p.getAttributeName(i)).append("=\"")
                            .append(Html.escape(p.getAttributeValue(i))).append('"')
                    }
                    sb.append('>')
                }
                XmlPullParser.END_TAG -> {
                    if (p.depth == depth) break
                    sb.append("</").append(p.name).append('>')
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF ->
                    sb.append(Html.escape(p.text))
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString().trim().takeIf { it.isNotEmpty() }
    }

    /** Plain RSS 2.0 elements have no namespace; RSS 1.0 uses its own default one. */
    private fun isRssElement(p: XmlPullParser): Boolean =
        p.namespace.isNullOrEmpty() || p.namespace == RSS1_NS

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** Titles sometimes carry markup or double-escaped entities ("AT&amp;amp;T"). */
    private fun cleanTitle(raw: String?): String? =
        raw?.let { Html.toPlainText(it) }?.takeIf { it.isNotEmpty() }

    private fun resolve(base: String, href: String?): String? {
        val h = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            URI(base).resolve(URI(h.replace(" ", "%20"))).toString()
        } catch (e: Exception) {
            h
        }
    }

    private fun normalizeLanguage(raw: String?): String? =
        raw?.trim()?.lowercase()?.take(2)?.takeIf { it.length == 2 && it.all(Char::isLetter) }

    companion object {
        private const val FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"
        private const val ATOM_NS = "http://www.w3.org/2005/Atom"
        private const val CONTENT_NS = "http://purl.org/rss/1.0/modules/content/"
        private const val DC_NS = "http://purl.org/dc/elements/1.1/"
        private const val RSS1_NS = "http://purl.org/rss/1.0/"
        private const val RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
        private const val XML_NS = "http://www.w3.org/XML/1998/namespace"
    }
}
