package com.claudeagent.phone

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class Mode { NONE, BYO_KEY, SUBSCRIBED }

object UserState {
    private const val PREFS_NAME = "handy_user_prefs"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_MODE = "mode"
    private const val KEY_LICENSE = "license_key"
    private const val KEY_LICENSE_INSTANCE = "license_instance_id"
    private const val KEY_TRIAL_START = "trial_started_at"
    private const val KEY_PAIR_CODE = "hub_pair_code"
    private const val KEY_HUB_TOKEN = "hub_device_token"

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
            appContext.getSharedPreferences(PREFS_NAME + "_plain", Context.MODE_PRIVATE)
        }
    }

    fun isOnboarded(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, value).apply()
    }

    fun mode(context: Context): Mode {
        val raw = prefs(context).getString(KEY_MODE, Mode.NONE.name) ?: Mode.NONE.name
        return runCatching { Mode.valueOf(raw) }.getOrDefault(Mode.NONE)
    }

    fun setMode(context: Context, mode: Mode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    fun licenseKey(context: Context): String? =
        prefs(context).getString(KEY_LICENSE, null)?.takeIf { it.isNotBlank() }

    fun setLicense(context: Context, key: String?, instanceId: String?) {
        prefs(context).edit()
            .putString(KEY_LICENSE, key)
            .putString(KEY_LICENSE_INSTANCE, instanceId)
            .apply()
    }

    fun licenseInstanceId(context: Context): String? =
        prefs(context).getString(KEY_LICENSE_INSTANCE, null)?.takeIf { it.isNotBlank() }

    fun trialStartedAt(context: Context): Long =
        prefs(context).getLong(KEY_TRIAL_START, 0L)

    fun setTrialStartedAt(context: Context, t: Long) {
        prefs(context).edit().putLong(KEY_TRIAL_START, t).apply()
    }

    fun pairCode(context: Context): String? =
        prefs(context).getString(KEY_PAIR_CODE, null)?.takeIf { it.isNotBlank() }

    fun setPairCode(context: Context, code: String?) {
        prefs(context).edit().putString(KEY_PAIR_CODE, code).apply()
    }

    fun hubToken(context: Context): String? =
        prefs(context).getString(KEY_HUB_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setHubToken(context: Context, token: String?) {
        prefs(context).edit().putString(KEY_HUB_TOKEN, token).apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
