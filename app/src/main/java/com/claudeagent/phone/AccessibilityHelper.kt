package com.claudeagent.phone

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized accessibility / voice-first helper.
 *
 * Owns:
 *  - Android's built-in TextToSpeech engine (free, offline-capable, no
 *    Anthropic tokens spent on narration).
 *  - The accessibility-mode preference (off / on / auto).
 *  - TalkBack detection (so we can auto-enable TTS for blind users).
 *  - Verbosity setting (off / brief / detailed) for action narration.
 *  - Haptic patterns for started / done / needs-input / error.
 *
 * Singleton lifecycle: lazily initialized on first speak() call; lives
 * for the process lifetime. TTS is cheap to keep resident — ~5MB of
 * native heap — but takes ~500ms to spin up cold.
 */
object AccessibilityHelper {

    enum class Mode {
        /** Force off — no TTS, no haptics. P6 (productivity nerd) default. */
        OFF,

        /** Force on — always speak + vibrate. P1-P4 default. */
        ON,

        /** Auto-detect TalkBack at app start; mirror its state. Default. */
        AUTO,
    }

    enum class Verbosity {
        /** No action narration. Only critical confirmations. */
        OFF,

        /** Short summaries: "Sent." "Opening Instagram." */
        BRIEF,

        /** Step-by-step: "Tapping Send button. Now in WhatsApp. Message sent to Sarah." */
        DETAILED,
    }

    private const val PREFS = "accessibility_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_VERBOSITY = "verbosity"
    private const val KEY_CONFIRM_DESTRUCTIVE = "confirm_destructive"
    private const val KEY_HAPTICS = "haptics"
    private const val KEY_EASY_MODE = "easy_mode"
    private const val KEY_HIGH_CONTRAST = "high_contrast"

    private var tts: TextToSpeech? = null
    private val ttsReady = AtomicBoolean(false)
    private val pendingUtterances = mutableListOf<String>()

    /**
     * Returns true if accessibility audio output should be active for
     * this user — either explicitly turned on, or auto-mode + TalkBack
     * is running.
     */
    fun isAudioEnabled(context: Context): Boolean {
        return when (mode(context)) {
            Mode.OFF -> false
            Mode.ON -> true
            Mode.AUTO -> isScreenReaderActive(context)
        }
    }

    /**
     * Probe Android's AccessibilityManager for any enabled screen reader.
     * Covers TalkBack, Samsung's forked TalkBack, and any third-party
     * service that registers as spoken-feedback.
     */
    fun isScreenReaderActive(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        if (!am.isEnabled) return false
        val services = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN
        )
        return services.isNotEmpty()
    }

    fun mode(context: Context): Mode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, Mode.AUTO.name) ?: Mode.AUTO.name
        return runCatching { Mode.valueOf(raw) }.getOrDefault(Mode.AUTO)
    }

    fun setMode(context: Context, mode: Mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.name).apply()
    }

    fun verbosity(context: Context): Verbosity {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_VERBOSITY, Verbosity.BRIEF.name) ?: Verbosity.BRIEF.name
        return runCatching { Verbosity.valueOf(raw) }.getOrDefault(Verbosity.BRIEF)
    }

    fun setVerbosity(context: Context, v: Verbosity) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_VERBOSITY, v.name).apply()
    }

    fun confirmDestructive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONFIRM_DESTRUCTIVE, true)

    fun setConfirmDestructive(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CONFIRM_DESTRUCTIVE, on).apply()
    }

    fun hapticsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HAPTICS, true)

    fun setHapticsEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HAPTICS, on).apply()
    }

    fun isEasyMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_EASY_MODE, false)

    fun setEasyMode(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_EASY_MODE, on).apply()
    }

    fun isHighContrast(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIGH_CONTRAST, false)

    fun setHighContrast(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIGH_CONTRAST, on).apply()
    }

    /**
     * Lazy-initialize the TTS engine. Subsequent calls are no-ops.
     */
    @Synchronized
    private fun ensureTts(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault().takeIf {
                    val res = tts?.isLanguageAvailable(it)
                    res != null && res >= TextToSpeech.LANG_AVAILABLE
                } ?: Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {}
                    @Deprecated("API < 21") override fun onError(id: String?) {}
                })
                ttsReady.set(true)
                synchronized(pendingUtterances) {
                    pendingUtterances.forEach { internalSpeak(it, queue = true) }
                    pendingUtterances.clear()
                }
            }
        }
    }

    /**
     * Speak a string aloud. Respects mode + audio routing. Falls back to
     * silent if accessibility mode is off OR no TTS engine is available.
     */
    fun speak(context: Context, text: String, interrupt: Boolean = false) {
        if (!isAudioEnabled(context)) return
        if (text.isBlank()) return
        ensureTts(context)
        if (ttsReady.get()) {
            internalSpeak(text, queue = !interrupt)
        } else {
            synchronized(pendingUtterances) { pendingUtterances.add(text) }
        }
    }

    /**
     * Speak only if verbosity allows. Brief and Detailed both pass; Off doesn't.
     */
    fun narrate(context: Context, text: String) {
        if (verbosity(context) == Verbosity.OFF) return
        speak(context, text, interrupt = false)
    }

    /**
     * Speak only if verbosity is Detailed.
     */
    fun narrateDetailed(context: Context, text: String) {
        if (verbosity(context) != Verbosity.DETAILED) return
        speak(context, text, interrupt = false)
    }

    private fun internalSpeak(text: String, queue: Boolean) {
        val engine = tts ?: return
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
        engine.speak(
            text,
            if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH,
            params,
            text.hashCode().toString(),
        )
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady.set(false)
    }

    /* ---------- Theme ---------- */

    /**
     * Apply the right theme to an activity *before* setContentView. Call
     * this as the first line of onCreate. We do it at the activity layer
     * (rather than in the manifest) because the high-contrast preference
     * is runtime-toggleable.
     */
    fun applyTheme(activity: android.app.Activity) {
        if (isHighContrast(activity)) {
            activity.setTheme(R.style.Theme_ClaudePhoneAgent_HighContrast)
        }
    }

    /* ---------- Haptics ---------- */

    enum class Haptic {
        TASK_STARTED,   // Two short pulses
        TASK_DONE,      // One long pulse
        NEEDS_INPUT,    // Three quick pulses
        ERROR,          // One sharp pulse
        TICK,           // Single brief tick (action heartbeat)
    }

    fun haptic(context: Context, kind: Haptic) {
        if (!hapticsEnabled(context)) return
        val vibrator = getVibrator(context) ?: return
        val pattern = when (kind) {
            Haptic.TASK_STARTED -> longArrayOf(0, 40, 80, 40)
            Haptic.TASK_DONE -> longArrayOf(0, 180)
            Haptic.NEEDS_INPUT -> longArrayOf(0, 50, 80, 50, 80, 50)
            Haptic.ERROR -> longArrayOf(0, 250)
            Haptic.TICK -> longArrayOf(0, 20)
        }
        // Defensive: vibrate() can throw SecurityException (missing VIBRATE
        // permission) or hardware-specific exceptions. Haptics are a nice-to-have
        // — never let them crash a core action like tapping the mic.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (t: Throwable) {
            // no-op: haptics are best-effort
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /* ---------- Destructive-action detection ---------- */

    /**
     * Heuristic: is the user-visible label of this UI action one of the
     * danger words? Used by AgentLoop to decide whether to voice-confirm
     * before tapping. False positives are very cheap (one extra voice
     * prompt). False negatives are expensive (silent purchase, deleted
     * message) — so when in doubt, add the word.
     */
    private val DESTRUCTIVE_VERBS = setOf(
        "send", "post", "publish", "submit", "confirm", "buy", "purchase",
        "order", "pay", "checkout", "place order", "book", "reserve",
        "delete", "remove", "discard", "clear", "wipe", "erase",
        "unsubscribe", "deactivate", "close account", "leave group",
        "block", "report", "mute forever", "sign out", "log out",
    )

    fun isLikelyDestructive(label: String?): Boolean {
        val s = (label ?: "").lowercase().trim()
        if (s.isEmpty()) return false
        if (s in DESTRUCTIVE_VERBS) return true
        return DESTRUCTIVE_VERBS.any { verb ->
            s == verb || s.startsWith("$verb ") || s.endsWith(" $verb") ||
                s.contains(" $verb ") || s.contains(" $verb,") || s.contains(" $verb.")
        }
    }
}
