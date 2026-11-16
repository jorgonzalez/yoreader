package com.yoreader.app.data

import com.yoreader.app.fetch.Html
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream

/** One feed read from an OPML file, with the names of the folders it was listed in. */
data class OpmlFeed(
    val url: String,
    val title: String?,
    val siteUrl: String?,
    val groups: Set<String>,
)

class OpmlException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * OPML, the usual import/export format of feed readers. Groups are written as folders;
 * a feed in several groups appears in each folder, and feeds in no group at the top.
 */
object Opml {

    fun write(groups: List<GroupWithFeeds>, feeds: List<Feed>, title: String = "yoreader sources"): String {
        val inAGroup = groups.flatMap { g -> g.feeds.map { it.id } }.toSet()
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<opml version=\"2.0\">\n")
            append("  <head>\n    <title>").append(Html.escape(title)).append("</title>\n  </head>\n")
            append("  <body>\n")
            for (g in groups) {
                val name = Html.escape(g.group.name)
                append("    <outline text=\"").append(name).append("\" title=\"").append(name).append("\">\n")
                for (f in g.feeds.sortedBy { it.title.lowercase() }) append(feedLine(f, "      "))
                append("    </outline>\n")
            }
            for (f in feeds.filter { it.id !in inAGroup }.sortedBy { it.title.lowercase() }) {
                append(feedLine(f, "    "))
            }
            append("  </body>\n</opml>\n")
        }
    }

    private fun feedLine(f: Feed, indent: String): String {
        val title = Html.escape(f.title)
        val site = f.siteUrl?.let { " htmlUrl=\"${Html.escape(it)}\"" }.orEmpty()
        return "$indent<outline type=\"rss\" text=\"$title\" title=\"$title\" " +
            "xmlUrl=\"${Html.escape(f.url)}\"$site/>\n"
    }

    /**
     * Reads every outline with an xmlUrl. Folder names come from the enclosing outlines
     * (the innermost one); the same feed in several folders is merged into one entry.
     */
    fun parse(input: InputStream, parser: XmlPullParser): List<OpmlFeed> {
        val byUrl = LinkedHashMap<String, OpmlFeed>()
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
            parser.setInput(input, null)
            // Names of the folder outlines we're inside (null for outlines that aren't folders).
            val folders = ArrayDeque<String?>()
            var sawOpml = false
            while (true) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                        "opml" -> sawOpml = true
                        "outline" -> {
                            val xmlUrl = attr(parser, "xmlUrl")
                            val text = attr(parser, "title") ?: attr(parser, "text")
                            if (xmlUrl != null) {
                                val folder = folders.lastOrNull { it != null }
                                val previous = byUrl[xmlUrl]
                                byUrl[xmlUrl] = OpmlFeed(
                                    url = xmlUrl,
                                    title = previous?.title ?: text?.let(Html::toPlainText)?.takeIf { it.isNotEmpty() },
                                    siteUrl = previous?.siteUrl ?: attr(parser, "htmlUrl"),
                                    groups = previous?.groups.orEmpty() + listOfNotNull(folder),
                                )
                                folders.addLast(null)
                            } else {
                                folders.addLast(text?.let(Html::toPlainText)?.takeIf { it.isNotEmpty() })
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name.equals("outline", ignoreCase = true)) {
                        folders.removeLastOrNull()
                    }
                    XmlPullParser.END_DOCUMENT -> break
                }
            }
            if (!sawOpml) throw OpmlException("This isn't an OPML file")
        } catch (e: XmlPullParserException) {
            throw OpmlException("The file isn't valid OPML", e)
        }
        return byUrl.values.toList()
    }

    private fun attr(p: XmlPullParser, name: String): String? {
        for (i in 0 until p.attributeCount) {
            if (p.getAttributeName(i).equals(name, ignoreCase = true)) {
                return p.getAttributeValue(i)?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }
}
