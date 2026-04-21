package com.claudeagent.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.claudeagent.phone.databinding.ActivitySettingsBinding
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.saveKeyButton.setOnClickListener {
            val key = binding.apiKeyInput.text?.toString()?.trim().orEmpty()
            if (!key.startsWith("sk-ant-")) {
                toast("Key should start with sk-ant-")
                return@setOnClickListener
            }
            ApiKeyStore.save(this, key)
            binding.apiKeyInput.text?.clear()
            refreshApiKeyStatus()
            toast("API key saved")
        }

        binding.clearKeyButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_key)
                .setMessage("Remove the saved Anthropic key from this device?")
                .setPositiveButton(R.string.clear_key) { _, _ ->
                    ApiKeyStore.clear(this)
                    refreshApiKeyStatus()
                }
                .setNegativeButton(R.string.not_now, null)
                .show()
        }

        binding.grantAccessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.manageSubscriptionButton.setOnClickListener {
            val url = BillingConfig.checkoutUrlFor(AuthStore.userId(this))
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        binding.switchModeButton.setOnClickListener { onSwitchMode() }

        binding.pairHubButton.setOnClickListener { onPairHub() }

        binding.signOutButton.setOnClickListener { onSignOut() }
    }

    override fun onResume() {
        super.onResume()
        refreshApiKeyStatus()
        refreshAccessibilityStatus()
        refreshPlanStatus()
        refreshHubStatus()
        refreshAccountStatus()
    }

    private fun refreshAccountStatus() {
        // The Account section is only meaningful if trial sign-in is wired up.
        // Hiding it on BYO-key-only builds keeps the settings screen honest.
        val show = BillingConfig.supabaseConfigured() || AuthStore.isSignedIn(this)
        binding.accountSection.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        val email = AuthStore.email(this)
        binding.accountStatus.text = if (email.isNullOrBlank()) {
            getString(R.string.account_not_signed_in)
        } else {
            getString(R.string.signed_in_as, email)
        }
        binding.signOutButton.isEnabled = !email.isNullOrBlank()
    }

    private fun onSignOut() {
        AlertDialog.Builder(this)
            .setTitle(R.string.signout_confirm)
            .setMessage(R.string.signout_body)
            .setPositiveButton(R.string.signout) { _, _ ->
                val accessToken = AuthStore.accessToken(this)
                // Fire-and-forget server-side revocation — we don't wait for
                // it before clearing local state so a flaky network can't
                // strand the user in a "half signed-out" state.
                if (!accessToken.isNullOrBlank()) {
                    lifecycleScope.launch { SupabaseAuth.signOut(accessToken) }
                }
                AuthStore.clear(this)
                // Keep the API key + other settings in place. Next launch
                // will route the user back to onboarding's sign-in step.
                UserState.setOnboarded(this, false)
                val intent = Intent(this, OnboardingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    private fun refreshApiKeyStatus() {
        val saved = ApiKeyStore.load(this)
        binding.apiKeyStatus.text = if (saved.isNullOrBlank()) {
            getString(R.string.api_key_not_saved)
        } else {
            getString(R.string.api_key_saved_mask, maskKey(saved))
        }
    }

    private fun refreshAccessibilityStatus() {
        val granted = isAccessibilityEnabled()
        binding.accessibilityStatus.setText(
            if (granted) R.string.accessibility_status_on else R.string.accessibility_status_off,
        )
    }

    private fun refreshPlanStatus() {
        val mode = UserState.mode(this)
        binding.planStatus.text = when (mode) {
            Mode.SUBSCRIBED -> {
                val start = UserState.trialStartedAt(this)
                val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - start)
                val left = (7 - days).coerceAtLeast(0)
                if (left > 0) getString(R.string.plan_trial, left.toInt())
                else getString(R.string.plan_subscribed)
            }
            Mode.BYO_KEY -> getString(R.string.plan_byo_key)
            Mode.NONE -> getString(R.string.plan_byo_key)
        }
        binding.switchModeButton.setText(
            if (mode == Mode.SUBSCRIBED) R.string.switch_to_byo_key else R.string.switch_to_subscription,
        )
    }

    private fun refreshHubStatus() {
        val token = UserState.hubToken(this)
        if (token.isNullOrBlank()) {
            binding.hubStatus.setText(R.string.hub_not_paired)
            binding.pairHubButton.setText(R.string.hub_pair_button)
        } else {
            val short = token.take(6)
            binding.hubStatus.text = getString(R.string.hub_paired, short)
            binding.pairHubButton.setText(R.string.hub_unpair)
        }
    }

    private fun onSwitchMode() {
        val current = UserState.mode(this)
        val target = if (current == Mode.SUBSCRIBED) Mode.BYO_KEY else Mode.SUBSCRIBED
        UserState.setMode(this, target)
        if (target == Mode.SUBSCRIBED && UserState.licenseKey(this).isNullOrBlank()) {
            val url = BillingConfig.checkoutUrlFor(AuthStore.userId(this))
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        refreshPlanStatus()
    }

    private fun onPairHub() {
        val token = UserState.hubToken(this)
        if (!token.isNullOrBlank()) {
            UserState.setHubToken(this, null)
            UserState.setMachineId(this, null)
            UserState.setPairCode(this, null)
            HubConnection.stop()
            refreshHubStatus()
            return
        }
        startActivity(Intent(this, HubPairActivity::class.java))
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

    private fun maskKey(key: String): String {
        if (key.length <= 12) return "••••"
        return key.take(8) + "..." + key.takeLast(4)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
