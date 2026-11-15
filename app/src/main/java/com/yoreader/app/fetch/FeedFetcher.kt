package com.yoreader.app.fetch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

sealed interface FetchResult {
    /** [url] is where the feed really lives: differs from the request if it was discovered from a web page. */
    data class Success(
        val feed: ParsedFeed,
        val url: String,
        val etag: String?,
        val lastModified: String?,
    ) : FetchResult

    data object NotModified : FetchResult

    data class Failure(val message: String) : FetchResult
}

/** Downloads and parses one feed. Never throws except for cancellation. */
class FeedFetcher(
    private val client: OkHttpClient,
    private val parser: FeedParser,
) {

    suspend fun fetch(url: String, etag: String? = null, lastModified: String? = null): FetchResult =
        fetch(url, etag, lastModified, allowDiscovery = true)

    private suspend fun fetch(
        url: String,
        etag: String?,
        lastModified: String?,
        allowDiscovery: Boolean,
    ): FetchResult {
        val request = Request.Builder()
            .url(url)
            .header("Accept", ACCEPT)
            .apply {
                etag?.let { header("If-None-Match", it) }
                lastModified?.let { header("If-Modified-Since", it) }
            }
            .build()

        val (code, finalUrl, headers, body) = try {
            client.newCall(request).await().use { r ->
                val bytes = if (r.isSuccessful) withContext(Dispatchers.IO) { readLimited(r) } else null
                Quad(r.code, r.request.url.toString(), r.headers, bytes)
            }
        } catch (e: IllegalArgumentException) {
            return FetchResult.Failure("Invalid address")
        } catch (e: UnknownHostException) {
            return FetchResult.Failure("Server not found (offline?)")
        } catch (e: SSLException) {
            return FetchResult.Failure("Secure connection failed")
        } catch (e: InterruptedIOException) {
            return FetchResult.Failure("Timed out") // covers SocketTimeoutException and call timeouts
        } catch (e: TooLargeException) {
            return FetchResult.Failure("Feed is too large")
        } catch (e: IOException) {
            return FetchResult.Failure(e.message?.let { "Network error: $it" } ?: "Network error")
        }

        if (code == 304) return FetchResult.NotModified
        if (body == null) return FetchResult.Failure(httpMessage(code))

        if (looksLikeHtml(body)) {
            if (!allowDiscovery) return FetchResult.Failure("Advertised feed is a web page")
            val href = Html.findFeedLinks(String(body, Charsets.UTF_8)).firstOrNull()
                ?: return FetchResult.Failure("This page doesn't have an RSS or Atom feed")
            val feedUrl = try {
                URI(finalUrl).resolve(href).toString()
            } catch (e: Exception) {
                return FetchResult.Failure("Invalid feed link on page")
            }
            return fetch(feedUrl, etag = null, lastModified = null, allowDiscovery = false)
        }

        return try {
            val feed = withContext(Dispatchers.Default) {
                parser.parse(ByteArrayInputStream(body), finalUrl)
            }
            FetchResult.Success(feed, finalUrl, headers["ETag"], headers["Last-Modified"])
        } catch (e: FeedParseException) {
            FetchResult.Failure(e.message ?: "Couldn't read the feed")
        } catch (e: IOException) {
            FetchResult.Failure("Couldn't read the feed")
        } catch (e: RuntimeException) {
            // Parsers can throw unchecked exceptions on badly broken input.
            FetchResult.Failure("Couldn't read the feed")
        }
    }

    private data class Quad(val code: Int, val url: String, val headers: okhttp3.Headers, val body: ByteArray?)

    private class TooLargeException : IOException()

    private fun readLimited(r: Response): ByteArray {
        val body = r.body ?: return ByteArray(0)
        if (body.contentLength() > MAX_BYTES) throw TooLargeException()
        val source = body.source()
        if (source.request(MAX_BYTES + 1)) throw TooLargeException()
        return source.readByteArray()
    }

    private fun looksLikeHtml(body: ByteArray): Boolean {
        val head = String(body, 0, minOf(body.size, 2048), Charsets.ISO_8859_1).lowercase()
        val feedMarker = listOf("<rss", "<feed", "<rdf:rdf").any { it in head }
        return !feedMarker && ("<html" in head || "<!doctype html" in head)
    }

    private fun httpMessage(code: Int) = when (code) {
        401, 403 -> "Access denied (HTTP $code)"
        404, 410 -> "Feed not found (HTTP $code)"
        429 -> "Server asked us to slow down (HTTP 429)"
        in 500..599 -> "Server error (HTTP $code)"
        else -> "HTTP error $code"
    }

    companion object {
        private const val MAX_BYTES = 10L * 1024 * 1024
        private const val ACCEPT =
            "application/rss+xml, application/atom+xml, application/rdf+xml;q=0.9, " +
                "application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.5, */*;q=0.1"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            // Application interceptor: runs before OkHttp adds its own default User-Agent,
            // so requests that set one (e.g. page fetches for clean-up) keep theirs.
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.header("User-Agent") != null) {
                    chain.proceed(request)
                } else {
                    chain.proceed(request.newBuilder().header("User-Agent", USER_AGENT).build())
                }
            }
            .build()

        private const val USER_AGENT = "yoreader/0.1 (Android RSS reader)"
    }
}
