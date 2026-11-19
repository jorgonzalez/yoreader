package com.yoreader.app.clean

import com.yoreader.app.fetch.Html
import com.yoreader.app.fetch.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
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
    data class Failure(val message: String) : CleanResult
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
                        return@withContext Failed("This site only lets browsers in; use Open original page")
                    }
                    if (!r.isSuccessful) return@withContext Failed("The page couldn't be loaded (HTTP ${r.code})")
                    val source = r.body?.source() ?: return@withContext Failed("The page is too large")
                    if (source.request(MAX_BYTES + 1)) return@withContext Failed("The page is too large")
                    Loaded(r.request.url.toString(), source.readByteArray())
                }
            }.let { if (it is Failed) return CleanResult.Failure(it.message) else it as Loaded }
        } catch (e: UnknownHostException) {
            return CleanResult.Failure("Server not found (offline?)")
        } catch (e: InterruptedIOException) {
            return CleanResult.Failure("Timed out")
        } catch (e: IOException) {
            return CleanResult.Failure("Network error")
        }

        return withContext(Dispatchers.Default) { extract(bytes, finalUrl) }
    }

    private sealed interface Download
    private data class Loaded(val url: String, val bytes: ByteArray) : Download
    private data class Failed(val message: String) : Download

    companion object {
        private const val MAX_BYTES = 8L * 1024 * 1024
        /** Less than this much text means we probably got a teaser, paywall or error page. */
        private const val MIN_TEXT_CHARS = 250
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        /** Extraction only, no network: [bytes] is the page, [url] its address. */
        fun extract(bytes: ByteArray, url: String): CleanResult {
            return try {
                // null charset: jsoup uses the BOM / <meta charset>, defaulting to UTF-8.
                val document = Jsoup.parse(ByteArrayInputStream(bytes), null, url)
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
