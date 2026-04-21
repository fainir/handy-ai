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
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.claudeagent.phone.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentlyRunning: Boolean = false
    private lateinit var voiceLauncher: ActivityResultLauncher<Intent>
    private val chatAdapter = ChatAdapter()
    private lateinit var sessionsAdapter: SessionsAdapter
    private lateinit var sessionsList: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!UserState.isOnboarded(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // Legacy-user upgrade: existing SUBSCRIBED users from before sign-in
        // was required still have Mode.SUBSCRIBED but no AuthStore session.
        // Once Supabase is configured, ask them to sign in. BYO-key users are
        // untouched — their mode doesn't require an account.
        if (BillingConfig.supabaseConfigured() &&
            UserState.mode(this) == Mode.SUBSCRIBED &&
            !AuthStore.isSignedIn(this)
        ) {
            UserState.setOnboarded(this, false)
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        ChatStore.init(applicationContext)

        // If this phone is already paired with the hub, keep the connection open.
        HubConnection.start(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        // Chat list
        binding.chatList.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.chatList.adapter = chatAdapter

        // Sessions drawer
        sessionsAdapter = SessionsAdapter { session ->
            ChatStore.setActive(session.id)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
        sessionsList = binding.root.findViewById(R.id.sessionsList)
        sessionsList.layoutManager = LinearLayoutManager(this)
        sessionsList.adapter = sessionsAdapter
        binding.root.findViewById<View>(R.id.newSessionButton).setOnClickListener {
            ChatStore.newSession()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Toolbar hamburger opens the drawer
        binding.toolbar.setNavigationOnClickListener {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        voiceLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                // Voice becomes a user chat message directly — same as a typed
                // message. We don't paste it into the input field because the
                // input is for composing; the chat is the conversation record.
                if (!currentlyRunning) sendTask(spoken)
            }
        }

        binding.actionButton.setOnClickListener {
            if (currentlyRunning) {
                stopTask()
            } else {
                val text = binding.taskInput.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) sendTask(text)
            }
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
                launch {
                    AgentState.status.collectLatest { status ->
                        binding.statusChip.text = status
                        binding.statusChip.visibility =
                            if (currentlyRunning) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    AgentState.state.collectLatest { s ->
                        val running = s is RunState.Running
                        currentlyRunning = running
                        updateActionButton(running)
                        binding.statusChip.visibility = if (running) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    ChatStore.activeMessages.collectLatest { messages ->
                        chatAdapter.submitList(messages) {
                            if (messages.isNotEmpty()) {
                                binding.chatList.scrollToPosition(messages.size - 1)
                            }
                        }
                        updateEmptyState(messages.isEmpty())
                    }
                }
                launch {
                    ChatStore.sessions.collectLatest { sessions ->
                        sessionsAdapter.submitList(sessions)
                    }
                }
                launch {
                    ChatStore.activeId.collectLatest { id ->
                        sessionsAdapter.activeId = id
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

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun updateActionButton(running: Boolean) {
        val hasText = !binding.taskInput.text?.toString()?.trim().isNullOrEmpty()
        val iconRes = if (running) R.drawable.ic_stop else R.drawable.ic_send
        binding.actionButton.icon = ContextCompat.getDrawable(this, iconRes)
        binding.actionButton.isEnabled = running || hasText
        binding.micButton.isEnabled = !running
    }

    private fun updateEmptyState(empty: Boolean) {
        binding.heroText.visibility = if (empty && !currentlyRunning) View.VISIBLE else View.GONE
        binding.chatList.visibility = if (empty) View.GONE else View.VISIBLE
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

    /**
     * Shared entry point: text and voice both reach this. Appends the user
     * message to the chat, clears the input, then starts the agent loop —
     * surfacing the usual missing-key / missing-accessibility dialogs if
     * something's not ready.
     */
    private fun sendTask(task: String) {
        val apiKey = ApiKeyStore.load(this)
        if (apiKey.isNullOrBlank()) {
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

        // Ensure we have an active session before the first user message, so
        // the message lands in it (and, via the title rule, names it).
        ChatStore.ensureActiveSession()
        ChatStore.append("user", task)
        binding.taskInput.text?.clear()

        // Server-side entitlement check: only for SUBSCRIBED (trial/paid)
        // users signed in against Supabase. BYO-key users bring their own
        // Anthropic credentials and are never gated.
        if (UserState.mode(this) == Mode.SUBSCRIBED && AuthStore.isSignedIn(this)) {
            lifecycleScope.launch {
                val ent = EntitlementClient.fetch(this@MainActivity)
                if (ent != null && !ent.isEntitled) {
                    ChatStore.append("status", "Trial ended. Subscribe to keep using Handy AI.")
                    showTrialEndedDialog()
                } else {
                    service.startAgent(apiKey, task)
                }
            }
        } else {
            service.startAgent(apiKey, task)
        }
    }

    private fun showTrialEndedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.trial_expired_title)
            .setMessage(R.string.trial_expired_body)
            .setPositiveButton(R.string.subscribe_now) { _, _ ->
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse(
                            BillingConfig.checkoutUrlFor(AuthStore.userId(this)),
                        ),
                    ),
                )
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
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
