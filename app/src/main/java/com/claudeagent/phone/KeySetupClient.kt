package com.claudeagent.phone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for the paired-laptop API-key setup flow at
 * `cloudbot-ai.com/api/handy-key-setup/`.
 *
 * A blind user can't realistically paste a 100-char API key on their
 * phone screen. Instead, the phone asks the panel for a short pair
 * code, speaks it aloud, and the user enters the key on their laptop
 * at `gethandyai.app/setup` where desktop screen readers handle long
 * strings well.
 *
 *   POST /api/handy-key-setup/init                            -> { code, pollSecret, expiresInSeconds }
 *   GET  /api/handy-key-setup/status/{code}?secret=<pollSecret> -> { status, apiKey? }
 *   POST /api/handy-key-setup/claim   (laptop posts the key)
 */
object KeySetupClient {

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    data class InitResult(
        val code: String?,
        val pollSecret: String?,
        val expiresInSeconds: Int,
        val error: String?,
    )

    sealed class PollResult {
        object Pending : PollResult()
        data class Claimed(val apiKey: String) : PollResult()
        object Expired : PollResult()
        data class Error(val message: String) : PollResult()
    }

    suspend fun init(): InitResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${BillingConfig.PANEL_BASE_URL}/api/handy-key-setup/init")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext InitResult(null, null, 0, "HTTP ${resp.code}: $body")
                }
                val json = JSONObject(body)
                InitResult(
                    code = json.optString("code").ifBlank { null },
                    pollSecret = json.optString("pollSecret").ifBlank { null },
                    expiresInSeconds = json.optInt("expiresInSeconds", 600),
                    error = null,
                )
            }
        } catch (t: Throwable) {
            InitResult(null, null, 0, t.message ?: "network error")
        }
    }

    suspend fun poll(code: String, pollSecret: String): PollResult = withContext(Dispatchers.IO) {
        try {
            // The poll secret goes in a header, not the query string. In a URL it would be copied into
            // server access logs, any intermediate proxy's logs and Referer headers - and this secret is
            // what collects a live Anthropic key. Server-side counterpart reads x-poll-secret.
            val url = "${BillingConfig.PANEL_BASE_URL}/api/handy-key-setup/status/$code"
            val req = Request.Builder()
                .url(url)
                .header("x-poll-secret", pollSecret)
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                when (resp.code) {
                    200 -> {
                        val json = JSONObject(body)
                        val status = json.optString("status")
                        if (status == "claimed") {
                            val key = json.optString("apiKey", "")
                            if (key.isBlank()) PollResult.Error("server returned no key") else PollResult.Claimed(key)
                        } else {
                            PollResult.Pending
                        }
                    }
                    401 -> PollResult.Expired
                    else -> PollResult.Error("HTTP ${resp.code}: $body")
                }
            }
        } catch (t: Throwable) {
            PollResult.Error(t.message ?: "network error")
        }
    }
}
