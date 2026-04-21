package com.claudeagent.phone

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Server-side trial + subscription check.
 *
 * The client stores a local [UserState.trialStartedAt] for fast UI, but the
 * source of truth is Supabase's `my_entitlement` view. A user can clear
 * local prefs all they want — the server still knows when they signed up
 * and whether they have an active subscription.
 *
 * Called before starting a task (cheap: ~100ms round-trip in eu-central-1).
 * Uses the saved access token; refreshes it via [AuthStore] if near expiry.
 */
object EntitlementClient {

    data class Entitlement(
        val isEntitled: Boolean,
        val trialDaysLeft: Int,
        val subscriptionActive: Boolean,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fetches the current entitlement. Returns null if:
     *  - Supabase isn't configured (fall back to local trial clock)
     *  - The user isn't signed in
     *  - The network call fails (callers should treat null as "don't block" —
     *    we don't want a flaky network to kill task execution for paying users)
     */
    suspend fun fetch(context: Context): Entitlement? = withContext(Dispatchers.IO) {
        if (!BillingConfig.supabaseConfigured()) return@withContext null
        val accessToken = AuthStore.ensureFreshAccessToken(context) ?: return@withContext null

        val req = Request.Builder()
            .url(BillingConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/my_entitlement?select=*&limit=1")
            .header("apikey", BillingConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string().orEmpty()
                val row = json.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject
                    ?: return@withContext null
                Entitlement(
                    isEntitled = row["is_entitled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                    trialDaysLeft = row["trial_days_left"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    subscriptionActive = row["subscription_active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                )
            }
        } catch (t: Throwable) {
            null
        }
    }
}
