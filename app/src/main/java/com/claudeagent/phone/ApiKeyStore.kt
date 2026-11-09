package com.claudeagent.phone

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object ApiKeyStore {
    private const val PREFS_NAME = "claude_agent_prefs"
    private const val KEY_API = "anthropic_api_key"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences? {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: buildPrefs(context.applicationContext).also { cached = it }
        }
    }

    private fun buildPrefs(appContext: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun legacy(context: Context) =
        context.getSharedPreferences(PREFS_NAME + "_plain", Context.MODE_PRIVATE)

    fun save(context: Context, key: String): Boolean {
        val secure = prefs(context) ?: return false
        return runCatching {
            secure.edit().putString(KEY_API, key).commit().also { saved ->
                if (saved) {
                    legacy(context).edit().remove(KEY_API).apply()
                    IncludedClaude.revoke(context)
                }
            }
        }.getOrDefault(false)
    }

    fun load(context: Context): String? {
        val existing = runCatching { prefs(context)?.getString(KEY_API, null) }.getOrNull()
        if (!existing.isNullOrBlank()) return existing
        // Keep the key from an older install usable while Android repairs
        // its Keystore. A working Keystore migrates that value once.
        val old = legacy(context).getString(KEY_API, null)?.takeIf { it.isNotBlank() } ?: return null
        save(context, old)
        return old
    }

    fun clear(context: Context) {
        prefs(context)?.edit()?.remove(KEY_API)?.apply()
        legacy(context).edit().remove(KEY_API).apply()
    }
}
