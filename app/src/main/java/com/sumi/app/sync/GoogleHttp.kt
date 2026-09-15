package com.sumi.app.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Plain HTTPS calls to Google's REST APIs, carrying one access token.
 *
 * Google's Java client libraries would do the same in several megabytes and need
 * extra shrinker rules; Sumi makes a handful of JSON calls, which Android can
 * already do with HttpURLConnection and org.json.
 */
class GoogleHttp(private val accessToken: String) {

    /** Google answered, but not with success. [code] is the HTTP status. */
    class HttpError(val code: Int, val reason: String) : IOException("HTTP $code: $reason")

    suspend fun get(url: String): JSONObject = send("GET", url, null)

    suspend fun post(url: String, body: JSONObject): JSONObject = send("POST", url, body)

    private suspend fun send(method: String, url: String, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }

                val code = connection.responseCode
                val ok = code in 200..299
                val text = (if (ok) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    .orEmpty()

                if (!ok) throw HttpError(code, reasonFrom(text))
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } finally {
                connection.disconnect()
            }
        }

    /** Google's errors are JSON with a human message inside; fall back to the raw text. */
    private fun reasonFrom(text: String): String =
        runCatching { JSONObject(text).getJSONObject("error").getString("message") }
            .getOrElse { text.take(200) }

    companion object {
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
