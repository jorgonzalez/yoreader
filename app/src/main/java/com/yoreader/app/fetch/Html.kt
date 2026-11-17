package com.yoreader.app.fetch

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeFilter
import org.jsoup.select.NodeTraversor

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
     * anything the page hides. They stay on screen; reading skips them. */
    private const val SKIPPED =
        "script, style, noscript, template, figure, svg, iframe, video, audio, button, " +
            "[hidden], [aria-hidden=true], [style~=(?i)display\\s*:\\s*none], " +
            "[style~=(?i)visibility\\s*:\\s*hidden]"

    /** Attribute marking, on screen, the element holding each paragraph read aloud. */
    const val PARAGRAPH_ATTR = "data-yr"

    /**
     * HTML ready for the reader together with the paragraphs read aloud from it.
     * [html] is the input with the scripts and event handlers it carried removed, and
     * with each paragraph's element marked `data-yr="<n>"` (several numbers, space
     * separated, when one element holds several paragraphs), numbering from the
     * `firstIndex` given to [readable]. So the reader can show which paragraph is
     * being read.
     */
    data class Readable(val html: String, val paragraphs: List<String>)

    /**
     * Readable paragraphs from HTML, for listening and for the cleaned plain text:
     * block elements become paragraph breaks; what [SKIPPED] matches is left out.
     * Uses a real HTML parser: pages like CNN's put whole HTML snippets inside
     * attributes (`data-description="<p>…</p>"`), which must never be read out.
     */
    fun toParagraphs(html: String): List<String> = readable(html).paragraphs

    /** See [Readable]; paragraphs are numbered from [firstIndex]. */
    fun readable(html: String, firstIndex: Int = 0): Readable {
        val body = Jsoup.parseBodyFragment(html).body()
        removeScripts(body)
        val skipped = body.select(SKIPPED).toHashSet()
        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        // The element on screen that holds the paragraph being collected.
        var holder: Element? = null

        fun endParagraph() {
            val text = WHITESPACE.replace(current, " ").trim()
            if (text.isNotEmpty()) {
                val index = (firstIndex + paragraphs.size).toString()
                holder?.let { h ->
                    val existing = h.attr(PARAGRAPH_ATTR)
                    h.attr(PARAGRAPH_ATTR, if (existing.isEmpty()) index else "$existing $index")
                }
                paragraphs += text
            }
            current.setLength(0)
            holder = null
        }

        NodeTraversor.filter(object : NodeFilter {
            override fun head(node: Node, depth: Int): NodeFilter.FilterResult {
                if (node is Element && node in skipped) return NodeFilter.FilterResult.SKIP_ENTIRELY
                when {
                    node is TextNode -> {
                        val text = node.wholeText
                        if (holder == null && text.isNotBlank()) holder = blockAround(node, body)
                        current.append(text)
                    }
                    node is Element && (node.isBlock || node.normalName() == "br") -> endParagraph()
                }
                return NodeFilter.FilterResult.CONTINUE
            }

            override fun tail(node: Node, depth: Int): NodeFilter.FilterResult {
                if (node is Element && node.isBlock) endParagraph()
                return NodeFilter.FilterResult.CONTINUE
            }
        }, body)
        endParagraph()
        return Readable(body.html(), paragraphs)
    }

    /** The innermost block element containing [node] (null if only [root] does). */
    private fun blockAround(node: Node, root: Element): Element? {
        var p = node.parent()
        while (p != null && p !== root) {
            if (p is Element && p.isBlock) return p
            p = p.parent()
        }
        return null
    }

    /**
     * The reader runs a little JavaScript of its own (to follow the reading), so the
     * article must not bring any: scripts, inline event handlers and javascript: links
     * go, and embedded frames are sandboxed (no scripts in them either).
     */
    private fun removeScripts(root: Element) {
        root.select("script").remove()
        for (el in root.allElements) {
            val names = el.attributes().asList().map { it.key }
            for (name in names) {
                val value = el.attr(name).trim()
                if (name.startsWith("on", ignoreCase = true) ||
                    (value.startsWith("javascript:", ignoreCase = true) &&
                        name.lowercase() in setOf("href", "src", "action", "formaction", "xlink:href"))
                ) {
                    el.removeAttr(name)
                }
            }
        }
        root.select("iframe").attr("sandbox", "")
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
