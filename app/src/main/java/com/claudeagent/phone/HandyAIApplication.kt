package com.claudeagent.phone

import android.app.Application
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point. Runs before any activity — the only place where
 * init-that-must-not-be-skipped can reliably live.
 *
 * Responsibilities:
 *  - Init Sentry IFF BuildConfig.SENTRY_DSN is populated (otherwise stays
 *    totally silent — no network, no overhead).
 *  - Warm up ChatStore so the first-screen read doesn't block the UI.
 *  - Panel heartbeat: hit /api/phone/me once on launch to update
 *    last_seen_at server-side + detect token revocation early.
 */
class HandyAIApplication : Application() {

    // Long-lived scope for background chores that outlive any single
    // activity but die with the process. SupervisorJob so a failure in one
    // job doesn't kill siblings.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.SENTRY_DSN.isNotBlank()) {
            SentryAndroid.init(this) { opts ->
                opts.dsn = BuildConfig.SENTRY_DSN
                // Prod-sane defaults: only send errors, no perf data by
                // default (can be turned on later from the dashboard).
                opts.tracesSampleRate = 0.0
                opts.isEnableAutoSessionTracking = true
                opts.isEnableAppComponentBreadcrumbs = true
                opts.environment = if (BuildConfig.DEBUG) "debug" else "release"
                opts.release = "handy-ai@${BuildConfig.VERSION_NAME}"
                opts.setDiagnosticLevel(SentryLevel.WARNING)
                // Scrub request bodies — we never want an accessibility
                // screenshot or API key ending up in a bug report.
                opts.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
                    event.request?.data = null
                    event
                }
            }
        }

        // Touch ChatStore on the main thread — EncryptedSharedPreferences
        // reads its master key once and caches it.
        ChatStore.init(applicationContext)

        maybePanelHeartbeat()
    }

    /**
     * Fire-and-forget /api/phone/me hit so the panel UI sees this phone as
     * "just now" the next time the user looks. On 401 (Revoked) we clear
     * our local state so Settings accurately shows Not paired; on network
     * error we keep the token (transient failure shouldn't nuke state).
     */
    private fun maybePanelHeartbeat() {
        if (UserState.panelToken(this).isNullOrBlank()) return
        appScope.launch {
            when (PanelClient.panelMe(applicationContext)) {
                is PanelClient.MeResult.Revoked -> {
                    UserState.clearPanelPair(applicationContext)
                }
                // Success + Offline → no local-state change
                else -> Unit
            }
        }
    }
}
