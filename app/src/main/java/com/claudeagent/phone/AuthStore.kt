package com.claudeagent.phone

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the Supabase session (email + tokens) on device, encrypted.
 *
 * Kept separate from [UserState] so the auth blob doesn't mix with the
 * subscription/billing state — easier to clear on sign-out without nuking
 * the API key or pairing setup.
 */
object AuthStore {
    private const val PREFS_NAME = "handy_auth_prefs"
    private const val KEY_EMAIL = "email"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at_epoch_ms"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: build(context.applicationContext).also { cached = it }
        }
    }

    private fun build(appContext: Context): SharedPreferences {
        return try {
            val master = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (t: Throwable) {
            // Encrypted prefs can fail on very old devices / corrupted keystore.
            // Falling back to plain prefs is safer than crashing; the token is
            // not high-value (short-lived) and the OS keystore crash is rare.
            appContext.getSharedPreferences(PREFS_NAME + "_plain", Context.MODE_PRIVATE)
        }
    }

    fun save(context: Context, session: SupabaseAuth.Session) {
        val expiresAt = session.expiresIn?.let {
            System.currentTimeMillis() + (it * 1000L)
        } ?: 0L
        prefs(context).edit()
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun email(context: Context): String? =
        prefs(context).getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }

    fun accessToken(context: Context): String? =
        prefs(context).getString(KEY_ACCESS, null)?.takeIf { it.isNotBlank() }

    fun refreshToken(context: Context): String? =
        prefs(context).getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() }

    fun userId(context: Context): String? =
        prefs(context).getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }

    fun expiresAtMs(context: Context): Long = prefs(context).getLong(KEY_EXPIRES_AT, 0L)

    /** True if we have a token AND it isn't within 60s of expiry. */
    fun isSessionFresh(context: Context): Boolean {
        if (accessToken(context) == null) return false
        val expires = expiresAtMs(context)
        if (expires == 0L) return true // server didn't tell us; assume ok
        return System.currentTimeMillis() < expires - 60_000L
    }

    fun isSignedIn(context: Context): Boolean = accessToken(context) != null

    /**
     * Ensure the stored session is fresh — refreshes via Supabase if the
     * access token is close to expiry. Returns the current access token, or
     * null if refresh failed and the user needs to sign in again.
     */
    suspend fun ensureFreshAccessToken(context: Context): String? {
        if (isSessionFresh(context)) return accessToken(context)
        val refresh = refreshToken(context) ?: return null
        val result = SupabaseAuth.refreshSession(refresh)
        return result.fold(
            onSuccess = { session ->
                save(context, session)
                session.accessToken
            },
            onFailure = { null },
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
