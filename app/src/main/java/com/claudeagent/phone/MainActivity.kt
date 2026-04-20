package com.claudeagent.phone

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.claudeagent.phone.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentlyRunning: Boolean = false
    private lateinit var voiceLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!UserState.isOnboarded(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        voiceLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                val existing = binding.taskInput.text?.toString().orEmpty()
                val joined = if (existing.isBlank()) spoken else "$existing $spoken"
                binding.taskInput.setText(joined)
                binding.taskInput.setSelection(binding.taskInput.text?.length ?: 0)
            }
        }

        binding.actionButton.setOnClickListener {
            if (currentlyRunning) stopTask() else attemptStartTask()
        }
        binding.micButton.setOnClickListener { startVoiceInput() }
        binding.taskInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!currentlyRunning) updateActionButton(false)
            }
        })
        binding.addKeyButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.grantAccessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { AgentState.status.collectLatest { binding.statusText.text = it } }
                launch {
                    AgentState.log.collectLatest { lines ->
                        binding.logView.text = lines.joinToString("\n")
                    }
                }
                launch {
                    AgentState.state.collectLatest { s ->
                        val running = s is RunState.Running
                        currentlyRunning = running
                        updateActionButton(running)
                        updateRunningViews(s)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Hide the old setup bar — onboarding + settings now own those actions.
        binding.setupActions.visibility = View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateActionButton(running: Boolean) {
        val hasText = !binding.taskInput.text?.toString()?.trim().isNullOrEmpty()
        val iconRes = if (running) R.drawable.ic_stop else R.drawable.ic_send
        binding.actionButton.icon = ContextCompat.getDrawable(this, iconRes)
        binding.actionButton.isEnabled = running || hasText
        binding.micButton.isEnabled = !running
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
            toast(getString(R.string.voice_unavailable))
        }
    }

    private fun updateRunningViews(state: RunState) {
        val active = state !is RunState.Idle
        binding.heroText.visibility = if (active) View.GONE else View.VISIBLE
        binding.statusText.visibility = if (active) View.VISIBLE else View.GONE
        binding.logView.visibility = if (active) View.VISIBLE else View.GONE
    }

    private fun attemptStartTask() {
        val task = binding.taskInput.text?.toString()?.trim().orEmpty()
        if (task.isBlank()) {
            toast("Describe the task")
            return
        }

        val apiKey = ApiKeyStore.load(this)
        if (apiKey.isNullOrBlank()) {
            // Subscribed mode: future versions will fetch an ephemeral key from the hub.
            // For now we direct the user to Settings to paste their key.
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.edit_api_key))
                .setMessage("Add your Anthropic API key in Settings to run tasks.")
                .setPositiveButton(getString(R.string.edit_api_key)) { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton(getString(R.string.not_now), null)
                .show()
            return
        }

        if (!isAccessibilityEnabled()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.needs_accessibility_title)
                .setMessage(R.string.needs_accessibility_body)
                .setPositiveButton(R.string.open_accessibility) { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton(R.string.not_now, null)
                .show()
            return
        }

        val service = AgentAccessibilityService.instance
        if (service == null) {
            toast("Accessibility service not running yet. Re-toggle it in settings.")
            return
        }

        service.startAgent(apiKey, task)
        toast("Starting — keep the phone awake and unlocked")
    }

    private fun stopTask() {
        AgentAccessibilityService.instance?.stopAgent()
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (AgentAccessibilityService.instance != null) return true
        val expectedId = "${packageName}/${AgentAccessibilityService::class.java.name}"
        val setting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(setting) }
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedId, ignoreCase = true)) return true
        }
        return false
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
