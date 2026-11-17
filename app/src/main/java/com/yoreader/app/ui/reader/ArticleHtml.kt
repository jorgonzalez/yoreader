package com.yoreader.app.ui.reader

import com.yoreader.app.data.Article
import com.yoreader.app.data.SearchText
import com.yoreader.app.fetch.Html
import java.text.DateFormat
import java.util.Date

/**
 * Builds the page the reader's WebView shows: a header (source, date, title) and the
 * article body from the feed, styled to match the app's dark theme.
 *
 * The page and the text read aloud come from the same [readable] call, so the reader
 * can highlight the paragraph being read: paragraph 0 is the title, then the body's.
 */
internal object ArticleHtml {

    /**
     * The article body as shown ([cleaned]: the cleaned-up page, if there is one) and
     * the paragraphs read aloud from it, numbered from 1 (0 is the title). Null when
     * the feed has nothing but the title.
     */
    fun readable(article: Article, cleaned: Boolean): Html.Readable? {
        val body = if (cleaned && article.cleanedHtml != null) {
            article.cleanedHtml
        } else {
            article.contentHtml ?: article.summary
        }
        return body?.let { readable(it, article.title) }
    }

    /** Same, for any body HTML with this [title] (e.g. a cleaned web page). */
    fun readable(bodyHtml: String, title: String): Html.Readable =
        Html.readable(withoutRepeatedTitle(bodyHtml, title), firstIndex = 1)

    /** [cleaned]: show the cleaned-up page instead of the feed's own content. */
    fun build(article: Article, feedTitle: String, cleaned: Boolean): String {
        val body = readable(article, cleaned)?.html
        val date = article.publishedAt?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
        }
        val meta = listOfNotNull(feedTitle, date, if (cleaned) "Cleaned up" else null)
        return page(
            title = article.title,
            meta = meta,
            body = body
                ?: "<p class=\"empty\">This feed only includes the title. Use \"Clean up\" to fetch the full article.</p>",
            language = article.language,
        )
    }

    /** A cleaned web page opened from a link (not stored as an article). */
    fun webPage(title: String, host: String, bodyHtml: String, language: String?): String =
        page(title, listOf(host, "Cleaned up"), readable(bodyHtml, title).html, language)

    private val HEADING = Regex("(?is)<h([1-3])\\b[^>]*>(.*?)</h\\1\\s*>")

    /**
     * Cleaned pages often repeat the headline as their first heading; we already show
     * the title in our header, so an early heading with the same text is removed.
     */
    internal fun withoutRepeatedTitle(bodyHtml: String, title: String): String {
        val wanted = SearchText.normalize(Html.toPlainText(title))
        val match = HEADING.find(bodyHtml) ?: return bodyHtml
        if (match.range.first > 3_000) return bodyHtml
        val heading = SearchText.normalize(Html.toPlainText(match.groupValues[2]))
        return if (heading == wanted) bodyHtml.removeRange(match.range) else bodyHtml
    }

    /** [body]: already prepared by [readable] (or our own placeholder). */
    private fun page(title: String, meta: List<String>, body: String, language: String?): String {
        val metaLine = meta.joinToString(" · ") { Html.escape(it) }
        val lang = language?.let { " lang=\"${Html.escape(it)}\"" }.orEmpty()

        return """
            <!DOCTYPE html>
            <html$lang>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>$CSS</style>
            <script>$SCRIPT</script>
            </head>
            <body>
            <header>
              <p class="meta">$metaLine</p>
              <h1 ${Html.PARAGRAPH_ATTR}="0">${Html.escape(title)}</h1>
            </header>
            <article>
            $body
            </article>
            </body>
            </html>
        """.trimIndent()
    }

    // Colors mirror ui/theme/Color.kt. Feed HTML often hard-codes black text or white
    // backgrounds, which would be unreadable on a dark page, so those are overridden.
    private val CSS = """
        html { -webkit-text-size-adjust: 100%; }
        body {
          margin: 0; padding: 8px 18px 48px;
          background: #0E0F12; color: #E6E7EA;
          font-family: sans-serif; font-size: 18px; line-height: 1.6;
          overflow-wrap: break-word;
        }
        article * { color: inherit !important; background-color: transparent !important; }
        a, article a { color: #7CB7FF !important; }
        .meta { color: #9AA0AB; font-size: 14px; margin: 8px 0 4px; }
        h1 { font-size: 26px; line-height: 1.25; margin: 0 0 20px; }
        h2, h3, h4 { line-height: 1.3; }
        img, video, iframe, svg { max-width: 100%; height: auto; border-radius: 6px; }
        figure { margin: 16px 0; }
        figcaption { color: #9AA0AB !important; font-size: 14px; }
        blockquote { margin: 16px 0; padding-left: 14px; border-left: 3px solid #3A3F4A; color: #9AA0AB !important; }
        pre, code { font-family: monospace; font-size: 15px; }
        pre { overflow-x: auto; padding: 12px; background-color: #1F222A !important; border-radius: 6px; }
        table { display: block; overflow-x: auto; border-collapse: collapse; }
        td, th { border: 1px solid #3A3F4A; padding: 4px 8px; }
        hr { border: none; border-top: 1px solid #3A3F4A; }
        .empty { color: #9AA0AB; font-style: italic; }
        /* The paragraph being read aloud. */
        .yr-reading, article .yr-reading {
          background-color: rgba(124, 183, 255, 0.13) !important;
          box-shadow: 0 0 0 6px rgba(124, 183, 255, 0.13);
          border-radius: 4px;
          transition: background-color 0.3s;
        }
    """.trimIndent()

    /**
     * Called by the app (WebView.evaluateJavascript) as reading moves on:
     * yrRead(paragraph, progress 0..1, follow). Highlights that paragraph's element
     * (-1: none) and, with follow, scrolls smoothly to keep the text being read in
     * view: a short paragraph as a whole, a long one at the line being read.
     */
    const val READ_FUNCTION = "yrRead"

    private val SCRIPT = """
        var yrEl = null;
        function yrFind(i) {
          for (var k = i; k >= 0; k--) {
            var el = document.querySelector('[${Html.PARAGRAPH_ATTR}~="' + k + '"]');
            if (el) return el;
          }
          return null;
        }
        function $READ_FUNCTION(i, progress, follow) {
          var el = i >= 0 ? yrFind(i) : null;
          if (el !== yrEl) {
            if (yrEl) yrEl.classList.remove('yr-reading');
            if (el) el.classList.add('yr-reading');
            yrEl = el;
          }
          if (!el || !follow) return;
          var r = el.getBoundingClientRect(), h = window.innerHeight, y;
          if (r.height < h * 0.6) {
            if (r.top >= h * 0.08 && r.bottom <= h * 0.85) return;
            y = r.top - h * 0.2;
          } else {
            var line = r.top + r.height * progress;
            if (line >= h * 0.15 && line <= h * 0.7) return;
            y = line - h * 0.3;
          }
          window.scrollTo({ top: Math.max(0, window.scrollY + y), behavior: 'smooth' });
        }
    """.trimIndent()
}
