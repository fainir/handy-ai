package com.claudeagent.phone

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**

 * Client for the cloudbot-panel phone-pairing API under `/api/phone/`.
 *
 * Separate from [HubClient]: the hub is the machine-remote-control
 * channel (best-agent-hub), while the panel is the user dashboard at
 * cloudbot-ai.com. A phone can be paired with both or either.
 *
 * Protocol:
 *   POST /api/phone/pair/init            -> { code, expiresInSeconds }
 *   GET  /api/phone/pair/status/{code}   -> { status, phoneToken?, phoneId?, name? }
 *   GET  /api/phone/me                   -> { id, name, phoneOs, createdAt, lastSeenAt? }
 *                                           (Bearer <phoneToken>)
 *
 * Flow from the user's perspective:
 *   1. App calls /pair/init, shows the 6-char code.
 *   2. User signs in to cloudbot-ai.com and enters the code.
 *   3. Panel calls /pair/claim (JWT-auth), which flips the status to
 *      "claimed" and stashes a one-time phoneToken on the row.
 *   4. Phone polls /pair/status/{code}. Once claimed, receives phoneToken
 *      + phoneId. Server nulls the token immediately (single-use
 *      delivery).
 *   5. App stores phoneToken in [UserState.setPanelToken] and uses it
 *      as the Bearer on all subsequent /api/phone calls.
 */
object PanelClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    data class PairInit(val code: String?, val expiresInSeconds: Int, val error: String?)

    data class PairStatus(
        val status: String, // pending | claimed | expired | consumed | error
        val phoneToken: String?,
        val phoneId: String?,
        val name: String?,
        val error: String?,
    )

    data class Me(
        val id: String,
        val name: String,
        val phoneOs: String,
        val createdAt: String,
        val lastSeenAt: String?,
    )

    /**
     * Phone requests a new 6-char pairing code.
     *
     * @param name display name shown to the user in the panel (e.g. "Pixel 10")
     * @param phoneOs OS label, typically "Android"
     */
    suspend fun initPanelPair(name: String, phoneOs: String = "Android"): PairInit =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("name", name)
                    .put("phoneOs", phoneOs)
                    .toString()
                    .toRequestBody(JSON)
                val req = Request.Builder()
                    .url("${BillingConfig.PANEL_BASE_URL}/api/phone/pair/init")
                    .post(body)
                    .build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext PairInit(null, 0, "HTTP ${resp.code}")
                    }
                    val json = JSONObject(text)
                    PairInit(
                        code = json.optString("code").ifBlank { null },
                        expiresInSeconds = json.optInt("expiresInSeconds", 600),
                        error = null,
                    )
                }
            } catch (t: Throwable) {
                PairInit(null, 0, t.message ?: "Network error")
            }
        }

    /**
     * Poll for claim status. 404 is treated as "expired" so the caller can
     * surface a single error path (server reaps expired rows, so a
     * once-valid code turning 404 is indistinguishable from never-existed
     * and we lean toward the friendlier interpretation).
     */
    suspend fun pollPanelStatus(code: String): PairStatus = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${BillingConfig.PANEL_BASE_URL}/api/phone/pair/status/$code")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.code == 404) {
                    return@withContext PairStatus("expired", null, null, null, null)
                }
                if (!resp.isSuccessful) {
                    return@withContext PairStatus("error", null, null, null, "HTTP ${resp.code}")
                }
                val json = JSONObject(text)
                PairStatus(
                    status = json.optString("status", "pending"),
                    phoneToken = json.optString("phoneToken").ifBlank { null },
                    phoneId = json.optString("phoneId").ifBlank { null },
                    name = json.optString("name").ifBlank { null },
                    error = null,
                )
            }
        } catch (t: Throwable) {
            PairStatus("error", null, null, null, t.message ?: "Network error")
        }
    }

    /**
     * Heartbeat: verify a stored phone token is still valid and learn our
     * own panel-side record. Returns null on any failure (caller treats
     * null as "re-pair required").
     */
    suspend fun panelMe(context: Context): Me? = withContext(Dispatchers.IO) {
        val token = UserState.panelToken(context) ?: return@withContext null
        try {
            val req = Request.Builder()
                .url("${BillingConfig.PANEL_BASE_URL}/api/phone/me")
                .get()
                .header("Authorization", "Bearer $token")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JSONObject(resp.body?.string().orEmpty())
                Me(
                    id = json.optString("id"),
                    name = json.optString("name"),
                    phoneOs = json.optString("phoneOs"),
                    createdAt = json.optString("createdAt"),
                    lastSeenAt = json.optString("lastSeenAt").ifBlank { null },
                )
            }
        } catch (t: Throwable) {
            null
        }
    }

    /** True if a panel bearer token is stored locally. */
    fun isPaired(context: Context): Boolean =
        !UserState.panelToken(context).isNullOrBlank()
}
