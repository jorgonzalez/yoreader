package com.yoreader.app.fetch

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/**
 * Small HTML helpers that work in plain JVM tests (unlike android.text.Html).
 * Not a sanitizer: used for titles, previews, feed discovery and text to read aloud.
 */
object Html {

    private val TAG = Regex("<[^>]*>")
    private val ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")
    private val WHITESPACE = Regex("\\s+")

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "ndash" to "–", "mdash" to "—", "hellip" to "…",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
        "laquo" to "«", "raquo" to "»", "iexcl" to "¡", "iquest" to "¿",
        "aacute" to "á", "eacute" to "é", "iacute" to "í", "oacute" to "ó", "uacute" to "ú",
        "Aacute" to "Á", "Eacute" to "É", "Iacute" to "Í", "Oacute" to "Ó", "Uacute" to "Ú",
        "ntilde" to "ñ", "Ntilde" to "Ñ", "uuml" to "ü", "Uuml" to "Ü",
        "copy" to "©", "reg" to "®", "trade" to "™", "euro" to "€", "middot" to "·",
    )

    fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let(::codePoint)
            e.startsWith("#") -> e.drop(1).toIntOrNull()?.let(::codePoint)
            else -> NAMED[e]
        } ?: m.value
    }

    private fun codePoint(cp: Int): String? =
        if (Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else null

    /** Strips tags, decodes entities (twice, for double-escaped feeds) and collapses spaces. */
    fun toPlainText(html: String): String {
        var s = decodeEntities(html)
        s = TAG.replace(s, " ")
        s = decodeEntities(s)
        return WHITESPACE.replace(s, " ").trim()
    }

    /** Not read aloud: code, media, figures (captions read aloud are just noise), and
     * anything the page hides. */
    private const val SKIPPED =
        "script, style, noscript, template, figure, svg, iframe, video, audio, button, " +
            "[hidden], [aria-hidden=true], [style~=(?i)display\\s*:\\s*none], " +
            "[style~=(?i)visibility\\s*:\\s*hidden]"

    /**
     * Readable paragraphs from HTML, for listening and for the cleaned plain text:
     * block elements become paragraph breaks; what [SKIPPED] matches is dropped.
     * Uses a real HTML parser: pages like CNN's put whole HTML snippets inside
     * attributes (`data-description="<p>…</p>"`), which must never be read out.
     */
    fun toParagraphs(html: String): List<String> {
        val body = Jsoup.parseBodyFragment(html).body()
        body.select(SKIPPED).remove()
        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        fun endParagraph() {
            val text = WHITESPACE.replace(current, " ").trim()
            if (text.isNotEmpty()) paragraphs += text
            current.setLength(0)
        }
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                when {
                    node is TextNode -> current.append(node.wholeText)
                    node is Element && (node.isBlock || node.normalName() == "br") -> endParagraph()
                }
            }

            override fun tail(node: Node, depth: Int) {
                if (node is Element && node.isBlock) endParagraph()
            }
        }, body)
        endParagraph()
        return paragraphs
    }

    fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }

    private val LINK_TAG = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("""([a-zA-Z-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
    private val FEED_TYPES = setOf("application/rss+xml", "application/atom+xml", "application/rdf+xml")

    /**
     * Feed URLs advertised by an HTML page via
     * `<link rel="alternate" type="application/rss+xml" href="...">`, in page order.
     * Hrefs are returned as written (possibly relative).
     */
    fun findFeedLinks(html: String): List<String> =
        LINK_TAG.findAll(html).mapNotNull { tag ->
            val attrs = ATTR.findAll(tag.value).associate { a ->
                a.groupValues[1].lowercase() to decodeEntities(
                    a.groupValues[2].ifEmpty { a.groupValues[3] }.ifEmpty { a.groupValues[4] }
                )
            }
            val rel = attrs["rel"]?.lowercase()?.split(' ').orEmpty()
            val type = attrs["type"]?.lowercase()?.substringBefore(';')?.trim()
            attrs["href"]?.takeIf { "alternate" in rel && type in FEED_TYPES && it.isNotBlank() }
        }.toList()
}
