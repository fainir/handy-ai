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
 * Client for the best-agent-hub API.
 *
 * Protocol (uses existing machine-pairing endpoints):
 *   POST {base}/api/link-code                 -> { code }
 *   GET  {base}/api/link-status/{code}        -> { status, machineToken?, machineName?, machineOs? }
 *   WSS  {base}/?role=machine&token=... (kept alive by HubConnection)
 *
 * Flow from the user's perspective:
 *   1. App calls /api/link-code, shows the 6-char code.
 *   2. User signs in to the hub in a browser, clicks "Approve" on that code.
 *   3. Hub returns status="linked" + machineToken to the polling app.
 *   4. App stores machineToken, connects the WebSocket.
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
    data class PairStatus(
        val status: String,
        val machineToken: String?,
        val machineName: String?,
        val error: String?,
    )

    suspend fun initPair(machineName: String): PairInit = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("machineName", machineName)
                .put("machineOs", "Android")
                .toString()
                .toRequestBody(JSON)
            val req = Request.Builder()
                .url("${BillingConfig.HUB_BASE_URL}/api/link-code")
                .post(body)
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext PairInit(null, "HTTP ${resp.code}")
                val json = JSONObject(text)
                PairInit(json.optString("code").ifBlank { null }, null)
            }
        } catch (t: Throwable) {
            PairInit(null, t.message ?: "Network error")
        }
    }

    suspend fun pollStatus(code: String): PairStatus = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${BillingConfig.HUB_BASE_URL}/api/link-status/$code")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful && resp.code != 404) {
                    return@withContext PairStatus("error", null, null, "HTTP ${resp.code}")
                }
                val json = JSONObject(text)
                PairStatus(
                    status = json.optString("status", "pending"),
                    machineToken = json.optString("machineToken").ifBlank { null },
                    machineName = json.optString("machineName").ifBlank { null },
                    error = null,
                )
            }
        } catch (t: Throwable) {
            PairStatus("error", null, null, t.message ?: "Network error")
        }
    }
}
