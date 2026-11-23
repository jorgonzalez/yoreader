package com.yoreader.app.clean

import com.yoreader.app.fetch.Html
import com.yoreader.app.fetch.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException

sealed interface CleanResult {
    /**
     * [html] keeps images and links for display; [text] is paragraphs separated by blank
     * lines; [title] is the page's headline when Readability found one.
     */
    data class Success(val html: String, val text: String, val title: String? = null) : CleanResult
    /** [blocked]: the site refused our download but may let a browser in. */
    data class Failure(val message: String, val blocked: Boolean = false) : CleanResult
}

/**
 * "Clean up": downloads an article's web page and keeps only the article itself
 * (no menus, ads, cookie banners or related links), using Readability4J.
 */
class ArticleCleaner(private val client: OkHttpClient) {

    suspend fun clean(url: String): CleanResult {
        if (url.isBlank()) return CleanResult.Failure("This article has no web address")
        val request = try {
            Request.Builder()
                .url(url)
                // Some news sites serve stripped or blocked pages to unknown clients.
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
                .build()
        } catch (e: IllegalArgumentException) {
            return CleanResult.Failure("Invalid web address")
        }

        // All of it off the main thread, closing included: closing an HTTP/2 response
        // can write to the network (e.g. a stream reset), which Android forbids there.
        val (finalUrl, bytes) = try {
            withContext(Dispatchers.IO) {
                client.newCall(request).await().use { r ->
                    // Cloudflare's "Verifying your connection" page: only real browsers get past it.
                    if (r.header("cf-mitigated") == "challenge") {
                        return@withContext Failed(BROWSERS_ONLY, blocked = true)
                    }
                    if (!r.isSuccessful) {
                        return@withContext Failed("The page couldn't be loaded (HTTP ${r.code})", blocked = r.code == 403)
                    }
                    val source = r.body?.source() ?: return@withContext Failed("The page is too large")
                    if (source.request(MAX_BYTES + 1)) return@withContext Failed("The page is too large")
                    Loaded(r.request.url.toString(), source.readByteArray())
                }
            }.let { if (it is Failed) return CleanResult.Failure(it.message, it.blocked) else it as Loaded }
        } catch (e: UnknownHostException) {
            return CleanResult.Failure("Server not found (offline?)")
        } catch (e: InterruptedIOException) {
            return CleanResult.Failure("Timed out")
        } catch (e: IOException) {
            return CleanResult.Failure("Network error")
        }

        return withContext(Dispatchers.Default) { extract(bytes, finalUrl) }
    }

    /**
     * Cleans a page that's already loaded (e.g. in the app's browser view), without
     * downloading it again: works for sites that only let browsers in.
     */
    suspend fun cleanLoaded(html: String, url: String): CleanResult =
        withContext(Dispatchers.Default) { extract(html, url) }

    private sealed interface Download
    private data class Loaded(val url: String, val bytes: ByteArray) : Download
    private data class Failed(val message: String, val blocked: Boolean = false) : Download

    companion object {
        private const val MAX_BYTES = 8L * 1024 * 1024
        /** Less than this much text means we probably got a teaser, paywall or error page. */
        private const val MIN_TEXT_CHARS = 250
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        private const val BROWSERS_ONLY = "This site only lets browsers in; use Open original page"

        /** A browser check page's heading, in case its markup changes. */
        private val BROWSER_CHECK_TEXT = Regex(
            "^(Performing security verification|Verifying you are human|Checking your browser)",
            RegexOption.IGNORE_CASE,
        )
        /** Longer elements are article text that may just quote those words. */
        private const val BROWSER_CHECK_MAX_CHARS = 100

        /**
         * Cloudflare's "Performing security verification" page, which a site can send with
         * HTTP 200 and no `cf-mitigated` header, or which the browser fallback sees before
         * the check passes. It has enough text to pass for an article, so look for its
         * markers: the challenge script settings or elements, or a short element (heading)
         * that starts with its wording.
         * (A mention of "challenge-platform" alone isn't one: normal pages load that script.)
         */
        fun isBrowserCheck(document: Document): Boolean {
            if (document.select("#challenge-form, #challenge-error-text, #challenge-running, #challenge-stage").isNotEmpty()) return true
            if (document.select("script").any { "_cf_chl_opt" in it.data() }) return true
            return document.select("h1, h2, h3, p, span, div").any { e ->
                val text = e.text().trim()
                text.length <= BROWSER_CHECK_MAX_CHARS && BROWSER_CHECK_TEXT.containsMatchIn(text)
            }
        }

        /** Extraction only, no network: [bytes] is the page, [url] its address. */
        fun extract(bytes: ByteArray, url: String): CleanResult = extract(url) {
            // null charset: jsoup uses the BOM / <meta charset>, defaulting to UTF-8.
            Jsoup.parse(ByteArrayInputStream(bytes), null, url)
        }

        /** Same, for a page that's already text (no charset to detect). */
        fun extract(html: String, url: String): CleanResult = extract(url) { Jsoup.parse(html, url) }

        private inline fun extract(url: String, parse: () -> Document): CleanResult {
            return try {
                val document = parse()
                if (isBrowserCheck(document)) return CleanResult.Failure(BROWSERS_ONLY, blocked = true)
                val article = Readability4JExtended(url, document).parse()
                val html = article.content
                    ?: return CleanResult.Failure("Couldn't find the article text on this page")
                val text = Html.toParagraphs(html).joinToString("\n\n")
                if (text.length < MIN_TEXT_CHARS) {
                    CleanResult.Failure("Couldn't find the article text on this page (paywall or consent page?)")
                } else {
                    CleanResult.Success(html, text, article.title?.trim()?.takeIf { it.isNotEmpty() })
                }
            } catch (e: Exception) {
                CleanResult.Failure("Couldn't read this page")
            }
        }
    }
}
