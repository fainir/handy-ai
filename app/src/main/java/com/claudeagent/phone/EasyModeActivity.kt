package com.claudeagent.phone

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Easy Mode: strip the entire app down to one huge mic button.
 *
 * Designed for personas P3 (motor disability) and P4 (cognitive
 * decline) where the chat UI, hamburger menu, examples row, and
 * settings menu are all overwhelming. One affordance: tap mic, speak,
 * agent runs, result is spoken back.
 */
class EasyModeActivity : AppCompatActivity() {

    private lateinit var micButton: MaterialButton
    private lateinit var statusView: TextView
    private lateinit var voiceLauncher: ActivityResultLauncher<Intent>

    private var currentlyRunning: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AccessibilityHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_easy_mode)
        supportActionBar?.hide()

        ChatStore.init(applicationContext)

        micButton = findViewById(R.id.easyModeMicButton)
        statusView = findViewById(R.id.easyModeStatus)
        val exitButton: MaterialButton = findViewById(R.id.easyModeExitButton)

        voiceLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty() && !currentlyRunning) {
                sendTask(spoken)
            }
        }

        micButton.setOnClickListener {
            if (currentlyRunning) {
                AccessibilityHelper.stopSpeaking()
                AccessibilityHelper.haptic(this, AccessibilityHelper.Haptic.TICK)
                AgentAccessibilityService.instance?.stopAgent()
            } else {
                AccessibilityHelper.haptic(this, AccessibilityHelper.Haptic.TICK)
                AccessibilityHelper.speak(this, getString(R.string.easy_mode_listening), interrupt = true)
                startVoiceInput()
            }
        }

        exitButton.setOnClickListener {
            AccessibilityHelper.setEasyMode(this, false)
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }

        AccessibilityHelper.setMode(this, AccessibilityHelper.Mode.ON)
        AccessibilityHelper.speak(this, getString(R.string.easy_mode_announce), interrupt = true)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AgentState.state.collectLatest { s ->
                        val running = s is RunState.Running
                        currentlyRunning = running
                        updateMicLabel(running)
                    }
                }
                launch {
                    AgentState.status.collectLatest { st ->
                        if (currentlyRunning && st.isNotBlank()) {
                            statusView.text = st
                        } else if (!currentlyRunning) {
                            statusView.text = getString(R.string.easy_mode_idle)
                        }
                    }
                }
            }
        }
    }

    private fun updateMicLabel(running: Boolean) {
        if (running) {
            micButton.icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_stop)
            micButton.contentDescription = getString(R.string.easy_mode_stop_desc)
        } else {
            micButton.icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_mic)
            micButton.contentDescription = getString(R.string.easy_mode_mic_desc)
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            AccessibilityHelper.speak(
                this,
                getString(R.string.voice_unavailable),
                interrupt = true,
            )
        }
    }

    private fun sendTask(task: String) {
        ChatStore.ensureActiveSession()
        ChatStore.append("user", task)

        val apiKey = ApiKeyStore.load(this)
        if (apiKey.isNullOrBlank()) {
            AccessibilityHelper.speak(this, getString(R.string.easy_mode_no_key), interrupt = true)
            statusView.text = getString(R.string.easy_mode_no_key)
            return
        }

        if (!isAccessibilityEnabled()) {
            AccessibilityHelper.speak(this, getString(R.string.easy_mode_no_accessibility), interrupt = true)
            statusView.text = getString(R.string.easy_mode_no_accessibility)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        val service = AgentAccessibilityService.instance
        if (service == null) {
            AccessibilityHelper.speak(this, getString(R.string.easy_mode_service_off), interrupt = true)
            return
        }

        AgentState.setState(RunState.Running)
        AgentState.setStatus("Working on it…")
        service.startAgent(apiKey, task)
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (AgentAccessibilityService.instance != null) return true
        val expectedId = "${packageName}/${AgentAccessibilityService::class.java.name}"
        val setting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = android.text.TextUtils.SimpleStringSplitter(':').apply { setString(setting) }
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedId, ignoreCase = true)) return true
        }
        return false
    }
}
