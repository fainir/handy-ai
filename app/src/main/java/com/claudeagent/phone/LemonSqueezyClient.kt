package com.claudeagent.phone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Lemon Squeezy license activation client. No SDK — LS exposes a public REST API
 * for license activation that works with just the license_key (no store API token needed).
 */
object LemonSqueezyClient {

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    data class ActivationResult(
        val success: Boolean,
        val instanceId: String?,
        val error: String?,
    )

    suspend fun activate(licenseKey: String, instanceName: String): ActivationResult =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("license_key", licenseKey.trim())
                    .add("instance_name", instanceName)
                    .build()
                val req = Request.Builder()
                    .url("https://api.lemonsqueezy.com/v1/licenses/activate")
                    .header("Accept", "application/json")
                    .post(body)
                    .build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val json = runCatching { JSONObject(text) }.getOrNull()
                        ?: return@withContext ActivationResult(false, null, "Bad response")
                    val activated = json.optBoolean("activated", false)
                    if (!activated) {
                        val err = json.optString("error", "License not activated")
                        return@withContext ActivationResult(false, null, err)
                    }
                    val instance = json.optJSONObject("instance")
                    val instanceId = instance?.optString("id")
                    ActivationResult(true, instanceId, null)
                }
            } catch (t: Throwable) {
                ActivationResult(false, null, t.message ?: "Network error")
            }
        }

    suspend fun validate(licenseKey: String, instanceId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("license_key", licenseKey.trim())
                    .add("instance_id", instanceId)
                    .build()
                val req = Request.Builder()
                    .url("https://api.lemonsqueezy.com/v1/licenses/validate")
                    .header("Accept", "application/json")
                    .post(body)
                    .build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val json = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext false
                    json.optBoolean("valid", false)
                }
            } catch (t: Throwable) {
                false
            }
        }
}
