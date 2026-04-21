package com.claudeagent.phone

import android.app.Application
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid

/**
 * Application entry point. Runs before any activity — the only place where
 * init-that-must-not-be-skipped can reliably live.
 *
 * Responsibilities:
 *  - Init Sentry IFF BuildConfig.SENTRY_DSN is populated (otherwise stays
 *    totally silent — no network, no overhead).
 *  - Warm up ChatStore so the first-screen read doesn't block the UI.
 */
class HandyAIApplication : Application() {

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
    }
}
