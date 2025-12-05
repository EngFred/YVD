package com.engfred.yvd.data.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool

/**
 * Custom Network Bridge for NewPipe Extractor.
 *
 * This class adapts the NewPipe library's internal networking requests to use
 * our configured [OkHttpClient].
 *
 * Key Optimization Features:
 * 1. **Connection Pooling:** Reuses TCP connections to reduce latency (handshake overhead).
 * 2. **Cookie Persistence:** Handles YouTube's GDPR/Consent redirections and prevents infinite loops.
 * 3. **High Timeouts:** Configured to handle unstable mobile networks without premature failure.
 */
class DownloaderImpl : Downloader() {

    // In-memory storage for cookies to handle session redirects
    private val cookieStore = HashMap<String, List<Cookie>>()

    /**
     * Connection Pool: Keeps up to 5 idle connections alive for 5 minutes.
     * This drastically improves speed when downloading multiple chunks in parallel.
     */
    private val connectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .connectionPool(connectionPool)
        .retryOnConnectionFailure(true) // Resilience against dropped packets
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: ArrayList()
            }
        })
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Provides access to the underlying OkHttpClient.
     * Used by the Repository to ensure the actual file downloads share the same
     * connection pool and cookies as the metadata extraction.
     */
    fun getOkHttpClient(): OkHttpClient {
        return client
    }

    /**
     * Executes a request initiated by the NewPipe Extractor library.
     * Maps NewPipe's [NewPipeRequest] to OkHttp's [Request] and back.
     */
    override fun execute(request: NewPipeRequest): NewPipeResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend: ByteArray? = request.dataToSend()

        val requestBuilder = Request.Builder().url(url)

        // Transfer Headers
        headers.forEach { (key, list) ->
            list.forEach { value -> requestBuilder.addHeader(key, value) }
        }

        // Injecting User-Agent if missing to prevent bot detection
        if (headers["User-Agent"].isNullOrEmpty()) {
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        }

        // Handle Request Body (POST/PUT)
        if (dataToSend != null) {
            requestBuilder.method(httpMethod, dataToSend.toRequestBody(null, 0, dataToSend.size))
        } else if (httpMethod == "POST" || httpMethod == "PUT") {
            requestBuilder.method(httpMethod, ByteArray(0).toRequestBody(null, 0, 0))
        }

        // Execute via OkHttp
        val response = client.newCall(requestBuilder.build()).execute()

        return NewPipeResponse(
            response.code,
            response.message,
            response.headers.toMultimap(),
            response.body?.string() ?: "",
            response.request.url.toString()
        )
    }
}