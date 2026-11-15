package com.yoreader.app.ui.reader

import com.yoreader.app.data.Article
import com.yoreader.app.fetch.Html
import java.text.DateFormat
import java.util.Date

/**
 * Builds the page the reader's WebView shows: a header (source, date, title) and the
 * article body from the feed, styled to match the app's dark theme.
 */
internal object ArticleHtml {

    /** [cleaned]: show the cleaned-up page instead of the feed's own content. */
    fun build(article: Article, feedTitle: String, cleaned: Boolean): String {
        val body = if (cleaned && article.cleanedHtml != null) {
            article.cleanedHtml
        } else {
            article.contentHtml ?: article.summary
        }
        val date = article.publishedAt?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
        }
        val meta = listOfNotNull(feedTitle, date, if (cleaned) "Cleaned up" else null)
            .joinToString(" · ") { Html.escape(it) }
        val lang = article.language?.let { " lang=\"${Html.escape(it)}\"" }.orEmpty()

        return """
            <!DOCTYPE html>
            <html$lang>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>$CSS</style>
            </head>
            <body>
            <header>
              <p class="meta">$meta</p>
              <h1>${Html.escape(article.title)}</h1>
            </header>
            <article>
            ${body ?: "<p class=\"empty\">This feed only includes the title. Use \"Clean up\" to fetch the full article.</p>"}
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
    """.trimIndent()
}
