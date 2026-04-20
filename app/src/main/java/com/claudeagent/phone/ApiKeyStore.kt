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

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: buildPrefs(context.applicationContext).also { cached = it }
        }
    }

    private fun buildPrefs(appContext: Context): SharedPreferences {
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
        } catch (t: Throwable) {
            appContext.getSharedPreferences(PREFS_NAME + "_plain", Context.MODE_PRIVATE)
        }
    }

    fun save(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API, key).apply()
    }

    fun load(context: Context): String? {
        return prefs(context).getString(KEY_API, null)?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_API).apply()
    }
}
