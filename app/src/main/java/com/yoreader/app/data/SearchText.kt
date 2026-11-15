package com.yoreader.app.data

import com.yoreader.app.fetch.Html
import java.text.Normalizer
import java.util.Locale

/**
 * Text folding for accent- and case-insensitive search: "Camión", "CAMION" and "camion"
 * all become "camion"; "España" becomes "espana". Stored per article in
 * [Article.searchText] and applied to the query the same way.
 */
object SearchText {

    /** Upper bound for the stored text of one article, to keep rows reasonable. */
    private const val MAX_CHARS = 100_000

    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    fun normalize(s: String): String =
        COMBINING_MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "")
            .lowercase(Locale.ROOT)

    /** Title, summary and content as one folded plain-text string. */
    fun forArticle(title: String?, summary: String?, contentHtml: String?): String =
        normalize(
            listOfNotNull(title, summary, contentHtml)
                .joinToString("\n") { Html.toPlainText(it) }
                .take(MAX_CHARS)
        )
}
