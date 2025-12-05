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
 * Bridges NewPipe's internal networking requirements to our OkHttp client.
 * Includes a CookieJar to handle YouTube's redirection loops (GDPR/Consent).
 * Optimized for high-throughput downloads.
 */
class DownloaderImpl : Downloader() {

    // Simple in-memory cookie store to handle redirects
    private val cookieStore = HashMap<String, List<Cookie>>()

    // Connection pool to reuse connections and reduce latency
    private val connectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS) // Increased timeout for large files
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .connectionPool(connectionPool)
        .retryOnConnectionFailure(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val cookies = cookieStore[url.host]
                return cookies ?: ArrayList()
            }
        })
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Expose the raw OkHttpClient for our Repository to use
     * so we share the same cookie jar and connection pool.
     */
    fun getOkHttpClient(): OkHttpClient {
        return client
    }

    override fun execute(request: NewPipeRequest): NewPipeResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend: ByteArray? = request.dataToSend()

        val requestBuilder = Request.Builder()
            .url(url)

        // Add headers from NewPipe
        headers.forEach { (key, list) ->
            list.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        // Add a User-Agent if missing (Helps prevent bot detection/redirects)
        if (headers["User-Agent"].isNullOrEmpty()) {
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        }

        // Handle POST/PUT body
        if (dataToSend != null) {
            requestBuilder.method(httpMethod, dataToSend.toRequestBody(null, 0, dataToSend.size))
        } else if (httpMethod == "POST" || httpMethod == "PUT") {
            requestBuilder.method(httpMethod, ByteArray(0).toRequestBody(null, 0, 0))
        }

        // Execute
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