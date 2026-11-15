package com.yoreader.app.fetch

/**
 * Tiny HTML helpers that work in plain JVM tests (unlike android.text.Html).
 * Not a sanitizer: only used for titles, previews and feed discovery.
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

    private val NON_TEXT = Regex("(?is)<(script|style|noscript|figure|svg|iframe)\\b.*?</\\1\\s*>")
    private val BLOCK_BREAK = Regex(
        "(?i)</(p|div|h[1-6]|li|blockquote|section|article|tr|pre|ul|ol|table)\\s*>" +
            "|<br\\s*/?>|<(p|h[1-6]|li|blockquote|pre)\\b[^>]*>"
    )
    private val BLANK_LINES = Regex("\\n\\s*\\n")

    /**
     * Readable paragraphs from HTML, for listening and for the cleaned plain text:
     * block elements become paragraph breaks; scripts, styles and figures (captions
     * read aloud are just noise) are dropped.
     */
    fun toParagraphs(html: String): List<String> {
        var s = NON_TEXT.replace(html, " ")
        s = BLOCK_BREAK.replace(s, "\n\n")
        s = TAG.replace(s, " ")
        s = decodeEntities(s)
        return s.split(BLANK_LINES)
            .map { WHITESPACE.replace(it, " ").trim() }
            .filter { it.isNotEmpty() }
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
