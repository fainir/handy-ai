package com.claudeagent.phone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal client for the Cloudbot hub phone-pairing protocol.
 *
 * Protocol (new endpoints to add on the hub):
 *   POST {base}/api/phone/pair/init         -> { pair_code, expires_at }
 *   GET  {base}/api/phone/pair/status?code= -> { status, device_token? }
 *   WSS  {base}/api/phone/ws?token=         (bidirectional control channel)
 *
 * The paired flow from the user's perspective:
 *   1. App calls pair/init, shows a 6-char code.
 *   2. User signs in to cloudbot-ai.com, clicks "Add phone", enters the code.
 *   3. Hub marks the pair_code as claimed by that account.
 *   4. App polls pair/status, receives device_token, stores it, connects WS.
 */
object HubClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    data class PairInit(val code: String?, val error: String?)
    data class PairStatus(val status: String, val deviceToken: String?, val error: String?)

    suspend fun initPair(): PairInit = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("platform", "android")
                .put("model", android.os.Build.MODEL)
                .toString()
                .toRequestBody(JSON)
            val req = Request.Builder()
                .url("${BillingConfig.HUB_BASE_URL}/api/phone/pair/init")
                .post(body)
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext PairInit(null, "HTTP ${resp.code}")
                val json = JSONObject(text)
                PairInit(json.optString("pair_code").ifBlank { null }, null)
            }
        } catch (t: Throwable) {
            PairInit(null, t.message ?: "Network error")
        }
    }

    suspend fun pollStatus(code: String): PairStatus = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${BillingConfig.HUB_BASE_URL}/api/phone/pair/status?code=$code")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext PairStatus("error", null, "HTTP ${resp.code}")
                val json = JSONObject(text)
                PairStatus(
                    status = json.optString("status", "pending"),
                    deviceToken = json.optString("device_token").ifBlank { null },
                    error = null,
                )
            }
        } catch (t: Throwable) {
            PairStatus("error", null, t.message ?: "Network error")
        }
    }
}
