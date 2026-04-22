package com.claudeagent.phone

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentlyRunning: Boolean = false
    private lateinit var voiceLauncher: ActivityResultLauncher<Intent>
    private val chatAdapter = ChatAdapter()
    private lateinit var sessionsAdapter: SessionsAdapter
    private lateinit var sessionsList: RecyclerView

    // When sendTask bounces the user off to Settings (missing key or
    // accessibility), we stash the pending task here so onResume() can fire
    // it automatically the moment they come back with the permission granted.
    // Kept in-memory only — MainActivity survives the settings bounce, so
    // this is enough without pulling in SharedPreferences.
    private var pendingTask: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // v1: the chat IS the home screen. No onboarding gate. If the user
        // hasn't added a key yet, the empty state shows a setup card that
        // takes them to the key-entry screen. We keep UserState.isOnboarded
        // wired up (saving a key still flips it true) for when we bring back
        // the Subscribe / trial gates later.

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

        // Inline API-key bar: shown at the bottom (in place of the chat
        // input) until a key is saved. Tapping save validates the key, stores
        // it encrypted, and immediately swaps the chat input in.
        val keyBarInput = binding.root.findViewById<EditText>(R.id.keyBarInput)
        binding.root.findViewById<View>(R.id.keyBarSaveButton).setOnClickListener {
            val key = keyBarInput.text?.toString()?.trim().orEmpty()
            if (!key.startsWith("sk-ant-")) {
                toast(getString(R.string.invalid_key_hint))
                return@setOnClickListener
            }
            ApiKeyStore.save(this, key)
            UserState.setMode(this, Mode.BYO_KEY)
            UserState.setOnboarded(this, true)
            keyBarInput.text?.clear()
            updateEmptyState(ChatStore.activeMessages.value.isEmpty())
            binding.taskInput.requestFocus()
        }
        binding.root.findViewById<View>(R.id.getKeyLink).setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.anthropic_keys_url)),
                    ),
                )
            } catch (t: Throwable) {
                toast("No browser available")
            }
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
                    // Merge the chat message list with the agent run state so
                    // we can append a synthetic "typing" row at the tail
                    // whenever the agent is working. The row is NOT persisted
                    // — it's injected only into what the adapter sees.
                    combine(ChatStore.activeMessages, AgentState.state) { msgs, s ->
                        msgs to (s is RunState.Running)
                    }.collectLatest { (messages, running) ->
                        val displayList = if (running) {
                            messages + ChatStore.ChatMessage(
                                id = "__typing__",
                                sessionId = messages.lastOrNull()?.sessionId.orEmpty(),
                                role = "typing",
                                text = "",
                                timestamp = 0L,
                            )
                        } else messages
                        chatAdapter.submitList(displayList) {
                            if (displayList.isNotEmpty()) {
                                binding.chatList.scrollToPosition(displayList.size - 1)
                            }
                        }
                        // Hero visibility tracks REAL messages only — the
                        // typing row shouldn't hide the empty-state hero
                        // (though in practice typing means we have at least
                        // the user's message already).
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
        // Re-evaluate the empty-state setup card: if the user just came back
        // from the key-entry screen with a fresh key pasted in, the "Add your
        // Anthropic API key" prompt should disappear on its own.
        updateEmptyState(ChatStore.activeMessages.value.isEmpty())
        resumePendingIfReady()
    }

    /**
     * If the user hit send, was bounced to grant the API key or accessibility,
     * and has now come back with everything ready — fire the task without
     * making them tap send a second time.
     *
     * Silent: if they came back without granting, we do NOT re-pop the dialog.
     * They'll see the dialog when they explicitly hit send again. Prevents
     * a nag-loop every time the app is brought forward.
     */
    private fun resumePendingIfReady() {
        val queued = pendingTask ?: return
        if (currentlyRunning) return
        val apiKey = ApiKeyStore.load(this).orEmpty()
        if (apiKey.isBlank()) return
        if (!isAccessibilityEnabled()) return
        val service = AgentAccessibilityService.instance ?: return
        pendingTask = null
        runAgent(apiKey, queued, service)
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
        val showHero = empty && !currentlyRunning
        binding.heroText.visibility = if (showHero) View.VISIBLE else View.GONE
        binding.chatList.visibility = if (empty) View.GONE else View.VISIBLE

        // First-run split: no key → the key-entry bar appears ABOVE the chat
        // input, and the chat input itself is disabled (visible but grayed
        // out) so the layout doesn't jump around when the key is saved. Once
        // a key is saved the key-bar goes away and the example prompts show.
        val hasKey = !ApiKeyStore.load(this).isNullOrBlank()
        binding.root.findViewById<View>(R.id.keySetupBar).visibility =
            if (hasKey) View.GONE else View.VISIBLE
        binding.root.findViewById<View>(R.id.heroExamples).visibility =
            if (showHero && hasKey) View.VISIBLE else View.GONE

        // Dim + disable the chat input until a key exists.
        binding.taskInput.isEnabled = hasKey
        binding.micButton.isEnabled = hasKey
        binding.actionButton.isEnabled = hasKey
        val dim = if (hasKey) 1.0f else 0.45f
        binding.inputContainer.alpha = dim
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
     * Shared entry point: text and voice both reach this. Records the user's
     * message in chat IMMEDIATELY (so it survives the permission bounce),
     * clears the input, then tries to run. If a prerequisite is missing
     * (API key, accessibility, service not live yet), the task is stashed in
     * [pendingTask] and [onResume]/[resumePendingIfReady] will auto-fire it
     * as soon as the user returns with the permission granted.
     */
    private fun sendTask(task: String) {
        // Ensure we have an active session before the first user message, so
        // the message lands in it (and, via the title rule, names it).
        ChatStore.ensureActiveSession()
        ChatStore.append("user", task)
        binding.taskInput.text?.clear()

        val apiKey = ApiKeyStore.load(this)
        if (apiKey.isNullOrBlank()) {
            // Should be unreachable — the chat input isn't visible until a
            // key is saved, so sendTask() only runs after the key bar has
            // been used. Defensive toast just in case something routes here.
            pendingTask = task
            toast(getString(R.string.api_key_bar_hint))
            return
        }

        if (!isAccessibilityEnabled()) {
            pendingTask = task
            AlertDialog.Builder(this)
                .setTitle(R.string.needs_accessibility_title)
                .setMessage(R.string.needs_accessibility_body)
                .setPositiveButton(R.string.open_accessibility) { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton(R.string.not_now) { _, _ ->
                    pendingTask = null
                }
                .show()
            return
        }

        val service = AgentAccessibilityService.instance
        if (service == null) {
            pendingTask = task
            toast("Accessibility service not running yet. Re-toggle it in settings.")
            return
        }

        pendingTask = null
        runAgent(apiKey, task, service)
    }

    /**
     * Starts the agent loop. Extracted so [resumePendingIfReady] can replay a
     * queued task without going through [sendTask] (which would double-append
     * the user message to the chat).
     */
    private fun runAgent(apiKey: String, task: String, service: AgentAccessibilityService) {
        // Flip the run state synchronously so the "Thinking…" row and the
        // stop button appear the instant the user hits send — without
        // waiting for the coroutine in AgentLoop.run to reach its own
        // setState(Running). Idempotent; AgentLoop's own call is a no-op.
        AgentState.setState(RunState.Running)
        AgentState.setStatus("Preparing…")

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
