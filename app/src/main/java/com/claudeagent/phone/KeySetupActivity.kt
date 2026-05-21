package com.claudeagent.phone

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Voice-first onboarding for users who can't read a 100-character
 * Anthropic key off their phone screen.
 *
 * Flow:
 *   1. onResume() -> POST /api/handy-key-setup/init -> server returns
 *      a 6-char code + a poll secret.
 *   2. Display the code visually + speak it aloud (NATO phonetic
 *      spelling so a blind user hears each character cleanly).
 *   3. The user opens gethandyai.app/setup on their laptop, types the
 *      code, pastes the API key, hits submit.
 *   4. We poll /status every 3s. On "claimed", store the key locally
 *      via ApiKeyStore, speak a success message, and finish().
 *   5. On expired/error, allow the user to retry.
 */
class KeySetupActivity : AppCompatActivity() {

    private lateinit var codeView: TextView
    private lateinit var statusView: TextView
    private lateinit var speakButton: MaterialButton
    private lateinit var cancelButton: MaterialButton

    private var code: String? = null
    private var pollSecret: String? = null
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AccessibilityHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_key_setup)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.key_setup_title)

        codeView = findViewById(R.id.codeValue)
        statusView = findViewById(R.id.setupStatus)
        speakButton = findViewById(R.id.speakCodeButton)
        cancelButton = findViewById(R.id.cancelSetupButton)

        speakButton.setOnClickListener { speakCodeAloud(interrupt = true) }
        cancelButton.setOnClickListener {
            pollJob?.cancel()
            AccessibilityHelper.stopSpeaking()
            finish()
        }

        AccessibilityHelper.speak(
            this,
            getString(R.string.key_setup_screen_announce),
            interrupt = true,
        )

        requestCode()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun requestCode() {
        statusView.text = getString(R.string.key_setup_requesting)
        AccessibilityHelper.narrate(this, getString(R.string.key_setup_requesting))
        lifecycleScope.launch {
            val r = KeySetupClient.init()
            if (r.code.isNullOrBlank() || r.pollSecret.isNullOrBlank()) {
                val msg = getString(R.string.key_setup_init_failed, r.error.orEmpty())
                statusView.text = msg
                AccessibilityHelper.speak(this@KeySetupActivity, msg, interrupt = true)
                AccessibilityHelper.haptic(this@KeySetupActivity, AccessibilityHelper.Haptic.ERROR)
                return@launch
            }
            code = r.code
            pollSecret = r.pollSecret
            codeView.text = r.code
            codeView.contentDescription = phoneticize(r.code)
            statusView.text = getString(R.string.key_setup_waiting)
            speakCodeAloud(interrupt = false)
            startPolling(r.code, r.pollSecret)
        }
    }

    private fun startPolling(code: String, pollSecret: String) {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            val started = System.currentTimeMillis()
            while (true) {
                if (System.currentTimeMillis() - started > 10 * 60 * 1000L) {
                    onExpired()
                    return@launch
                }
                delay(3_000L)
                when (val r = KeySetupClient.poll(code, pollSecret)) {
                    is KeySetupClient.PollResult.Pending -> { /* keep waiting */ }
                    is KeySetupClient.PollResult.Claimed -> {
                        onClaimed(r.apiKey)
                        return@launch
                    }
                    is KeySetupClient.PollResult.Expired -> {
                        onExpired()
                        return@launch
                    }
                    is KeySetupClient.PollResult.Error -> {
                        withContext(Dispatchers.Main) {
                            statusView.text = getString(R.string.key_setup_transient_error, r.message)
                        }
                    }
                }
            }
        }
    }

    private fun onClaimed(apiKey: String) {
        AccessibilityHelper.haptic(this, AccessibilityHelper.Haptic.TASK_DONE)
        ApiKeyStore.save(this, apiKey)
        UserState.setMode(this, Mode.BYO_KEY)
        UserState.setOnboarded(this, true)
        val msg = getString(R.string.key_setup_received)
        statusView.text = msg
        AccessibilityHelper.speak(this, msg, interrupt = true)
        lifecycleScope.launch {
            delay(1500L)
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun onExpired() {
        AccessibilityHelper.haptic(this, AccessibilityHelper.Haptic.ERROR)
        val msg = getString(R.string.key_setup_expired)
        statusView.text = msg
        AccessibilityHelper.speak(this, msg, interrupt = true)
        codeView.text = "——————"
        code = null
        pollSecret = null
        lifecycleScope.launch {
            delay(2_500L)
            requestCode()
        }
    }

    private fun speakCodeAloud(interrupt: Boolean) {
        val c = code ?: return
        val spoken = "Your code is ${c.toCharArray().joinToString(", ")}. " +
            "That's ${phoneticize(c)}."
        AccessibilityHelper.speak(this, spoken, interrupt = interrupt)
    }

    /**
     * NATO-style phonetic alphabet for character-by-character clarity.
     */
    private fun phoneticize(s: String): String {
        return s.uppercase().toCharArray().joinToString(" ") { c ->
            when (c) {
                'A' -> "Alpha"; 'B' -> "Bravo"; 'C' -> "Charlie"; 'D' -> "Delta"
                'E' -> "Echo"; 'F' -> "Foxtrot"; 'G' -> "Golf"; 'H' -> "Hotel"
                'J' -> "Juliet"; 'K' -> "Kilo"; 'L' -> "Lima"
                'M' -> "Mike"; 'N' -> "November"; 'P' -> "Papa"
                'Q' -> "Quebec"; 'R' -> "Romeo"; 'S' -> "Sierra"; 'T' -> "Tango"
                'U' -> "Uniform"; 'V' -> "Victor"; 'W' -> "Whiskey"
                'X' -> "X-ray"; 'Y' -> "Yankee"; 'Z' -> "Zulu"
                '2' -> "Two"; '3' -> "Three"; '4' -> "Four"; '5' -> "Five"
                '6' -> "Six"; '7' -> "Seven"; '8' -> "Eight"; '9' -> "Nine"
                else -> c.toString()
            }
        }
    }
}
