package com.claudeagent.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.claudeagent.phone.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private enum class Step { HOME, KEY, LICENSE }

    private var step: Step = Step.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.subscribeCard.setOnClickListener { openCheckout() }
        binding.openCheckoutAgainButton.setOnClickListener { openCheckout() }
        binding.byoKeyCard.setOnClickListener { goTo(Step.KEY) }

        binding.saveKeyButton.setOnClickListener { onSaveKey() }
        binding.activateButton.setOnClickListener { onActivateLicense() }

        goTo(Step.HOME)
    }

    override fun onResume() {
        super.onResume()
        // If user completed purchase in browser and came back, assume they have a license
        // and need to enter it. Only do this if we don't already have a license saved.
        if (step == Step.HOME && UserState.pairCode(this) == "checkout-opened") {
            UserState.setPairCode(this, null)
            goTo(Step.LICENSE)
        }
    }

    private fun goTo(next: Step) {
        step = next
        binding.homeGroup.visibility = if (next == Step.HOME) View.VISIBLE else View.GONE
        binding.keyEntryGroup.visibility = if (next == Step.KEY) View.VISIBLE else View.GONE
        binding.licenseEntryGroup.visibility = if (next == Step.LICENSE) View.VISIBLE else View.GONE
    }

    private fun openCheckout() {
        UserState.setPairCode(this, "checkout-opened")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BillingConfig.LEMONSQUEEZY_CHECKOUT_URL)))
            goTo(Step.LICENSE)
        } catch (t: Throwable) {
            Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onSaveKey() {
        val key = binding.keyInput.text?.toString()?.trim().orEmpty()
        if (!key.startsWith("sk-ant-")) {
            Toast.makeText(this, R.string.invalid_key_hint, Toast.LENGTH_LONG).show()
            return
        }
        ApiKeyStore.save(this, key)
        UserState.setMode(this, Mode.BYO_KEY)
        UserState.setOnboarded(this, true)
        finishToMain()
    }

    private fun onActivateLicense() {
        val key = binding.licenseInput.text?.toString()?.trim().orEmpty()
        if (key.length < 10) {
            Toast.makeText(this, getString(R.string.activation_failed, "key too short"), Toast.LENGTH_SHORT).show()
            return
        }
        binding.activateButton.isEnabled = false
        binding.activateButton.text = getString(R.string.activating)
        val instanceName = "android-${android.os.Build.MODEL}"
        lifecycleScope.launch {
            val result = LemonSqueezyClient.activate(key, instanceName)
            binding.activateButton.isEnabled = true
            binding.activateButton.setText(R.string.activate_license)
            if (result.success) {
                UserState.setLicense(this@OnboardingActivity, key, result.instanceId)
                UserState.setMode(this@OnboardingActivity, Mode.SUBSCRIBED)
                UserState.setTrialStartedAt(this@OnboardingActivity, System.currentTimeMillis())
                UserState.setOnboarded(this@OnboardingActivity, true)
                finishToMain()
            } else {
                Toast.makeText(
                    this@OnboardingActivity,
                    getString(R.string.activation_failed, result.error ?: "unknown"),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun finishToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    override fun onBackPressed() {
        if (step != Step.HOME) goTo(Step.HOME) else super.onBackPressed()
    }
}
