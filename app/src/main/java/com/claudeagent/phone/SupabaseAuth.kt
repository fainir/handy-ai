package com.claudeagent.phone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal Supabase GoTrue client. Three supported flows:
 *
 *   sendOtp(email)           → POST /auth/v1/otp
 *   verifyOtp(email, code)   → POST /auth/v1/verify  → Session
 *   signInWithGoogleIdToken  → POST /auth/v1/token?grant_type=id_token
 *   refreshSession(token)    → POST /auth/v1/token?grant_type=refresh_token
 *
 * Chose OkHttp + raw JSON over the Supabase Kotlin SDK because we already
 * depend on OkHttp for the Anthropic client, and skipping Ktor keeps the
 * APK ~1.5 MB smaller. The auth surface here is small enough that an SDK
 * would be mostly overhead.
 */
object SupabaseAuth {

    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val email: String,
        val userId: String,
        /** Seconds-from-now until the access token expires. Nullable if the
         *  server omitted it (rare but spec-allowed). */
        val expiresIn: Long?,
    )

    /**
     * All failure modes squashed into one sealed hierarchy so the UI can
     * render human-friendly copy without re-parsing strings.
     */
    sealed class AuthError(message: String) : Exception(message) {
        class NotConfigured : AuthError("Supabase not configured")
        class Network(cause: Throwable) : AuthError("Network error: ${cause.message}")
        class RateLimited(retryAfterSec: Long?) : AuthError(
            if (retryAfterSec != null) "Too many attempts — try again in ${retryAfterSec}s"
            else "Too many attempts — wait a minute and retry",
        )
        class InvalidCredentials : AuthError("Code is incorrect or expired")
        class InvalidEmail : AuthError("That email address looks invalid")
        class Server(code: Int, body: String) : AuthError(
            "Server error $code${if (body.isNotBlank()) ": ${body.take(140)}" else ""}",
        )
    }

    private val JSON_MEDIA = "application/json".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun sendOtp(email: String): Result<Unit> = runRequest {
        val body = buildJsonObject {
            put("email", JsonPrimitive(email))
            put("create_user", JsonPrimitive(true))
        }
        post("/auth/v1/otp", body)
        Unit
    }

    suspend fun verifyOtp(email: String, token: String): Result<Session> = runRequest {
        val body = buildJsonObject {
            put("type", JsonPrimitive("email"))
            put("email", JsonPrimitive(email))
            put("token", JsonPrimitive(token))
        }
        val raw = post("/auth/v1/verify", body)
        parseSession(raw, fallbackEmail = email)
    }

    /**
     * Exchange a Google ID token (JWT from Sign-In with Google) for a
     * Supabase session. Supabase must have Google provider configured with
     * the matching OAuth client ID on the server side.
     */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<Session> = runRequest {
        val body = buildJsonObject {
            put("provider", JsonPrimitive("google"))
            put("id_token", JsonPrimitive(idToken))
        }
        // grant_type=id_token tells GoTrue to verify the third-party token
        // against the configured OAuth provider instead of generating one.
        val raw = post("/auth/v1/token?grant_type=id_token", body)
        parseSession(raw)
    }

    /** Trade a refresh_token for a fresh access_token. */
    suspend fun refreshSession(refreshToken: String): Result<Session> = runRequest {
        val body = buildJsonObject { put("refresh_token", JsonPrimitive(refreshToken)) }
        val raw = post("/auth/v1/token?grant_type=refresh_token", body)
        parseSession(raw)
    }

    // ----- internals -----

    private suspend inline fun <T> runRequest(crossinline block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            if (!BillingConfig.supabaseConfigured()) {
                return@withContext Result.failure(AuthError.NotConfigured())
            }
            try {
                Result.success(block())
            } catch (e: AuthError) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(AuthError.Network(e))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private fun post(path: String, body: kotlinx.serialization.json.JsonObject): String {
        val req = Request.Builder()
            .url(BillingConfig.SUPABASE_URL.trimEnd('/') + path)
            .header("apikey", BillingConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${BillingConfig.SUPABASE_ANON_KEY}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw translateError(resp.code, raw, resp.header("Retry-After"))
            return raw
        }
    }

    /**
     * Map HTTP + body into the friendly sealed errors. GoTrue responses look
     * like `{"error":"...", "error_description":"..."}` or `{"msg":"..."}`;
     * we try both.
     */
    private fun translateError(code: Int, body: String, retryAfterHeader: String?): AuthError {
        val low = body.lowercase()
        return when {
            code == 429 -> AuthError.RateLimited(retryAfterHeader?.toLongOrNull())
            code == 400 && (low.contains("invalid") || low.contains("expired") ||
                low.contains("otp") || low.contains("token")) ->
                AuthError.InvalidCredentials()
            code == 422 -> AuthError.InvalidEmail()
            code in 500..599 -> AuthError.Server(code, body)
            else -> AuthError.Server(code, body)
        }
    }

    private fun parseSession(raw: String, fallbackEmail: String? = null): Session {
        val obj = json.parseToJsonElement(raw).jsonObject
        val access = obj["access_token"]?.jsonPrimitive?.content
            ?: throw AuthError.Server(-1, "Missing access_token")
        val refresh = obj["refresh_token"]?.jsonPrimitive?.content.orEmpty()
        val expiresIn = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull()
        val user = obj["user"]?.jsonObject
        val uid = user?.get("id")?.jsonPrimitive?.content.orEmpty()
        val email = user?.get("email")?.jsonPrimitive?.content ?: fallbackEmail.orEmpty()
        return Session(access, refresh, email, uid, expiresIn)
    }
}
