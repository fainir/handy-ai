package com.claudeagent.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.claudeagent.phone.databinding.ActivityOnboardingBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private enum class Step { HOME, SIGN_IN_EMAIL, SIGN_IN_OTP, KEY, LICENSE }

    private var step: Step = Step.HOME
    private var pendingEmail: String = ""
    private val credentialManager by lazy { CredentialManager.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Free trial path now starts with sign-in. Only after auth do we
        // actually flip the user into SUBSCRIBED mode / open Lemon Squeezy.
        binding.subscribeCard.setOnClickListener { goTo(Step.SIGN_IN_EMAIL) }
        binding.byoKeyCard.setOnClickListener { goTo(Step.KEY) }

        binding.saveKeyButton.setOnClickListener { onSaveKey() }
        binding.getKeyButton.setOnClickListener {
            // Kick users straight to the API-keys page in the Anthropic
            // console. Small graceful fallback if there's somehow no browser
            // on the device (unlikely, but harmless).
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

        // Sign-in handlers.
        binding.sendCodeButton.setOnClickListener { onSendCode() }
        binding.verifyButton.setOnClickListener { onVerifyCode() }
        binding.resendCodeButton.setOnClickListener { onSendCode() }
        binding.changeEmailButton.setOnClickListener { goTo(Step.SIGN_IN_EMAIL) }
        binding.googleSignInButton.setOnClickListener { onGoogleSignIn() }
        // Show Google CTA + "or email" divider only when configured.
        val googleVisible = BillingConfig.googleSignInConfigured()
        binding.googleSignInButton.visibility = if (googleVisible) View.VISIBLE else View.GONE
        binding.signInDivider.visibility = if (googleVisible) View.VISIBLE else View.GONE

        // Kept for paid users who already have a license to paste (after trial).
        binding.activateButton.setOnClickListener { onActivateLicense() }
        binding.openCheckoutAgainButton.setOnClickListener { openCheckout() }

        // v1 ships API-key-only. The HOME / SIGN_IN_* / LICENSE steps stay
        // wired in code so we can flip them back on later (paid tier, trial)
        // without a big diff — they're just not reachable from the UI today.
        goTo(Step.KEY)
    }

    override fun onResume() {
        super.onResume()
        // If user came back from the Lemon Squeezy checkout we stashed a
        // breadcrumb, surface the license entry step to paste their key.
        if (step == Step.HOME && UserState.pairCode(this) == "checkout-opened") {
            UserState.setPairCode(this, null)
            goTo(Step.LICENSE)
        }
    }

    private fun goTo(next: Step) {
        step = next
        binding.homeGroup.visibility = if (next == Step.HOME) View.VISIBLE else View.GONE
        binding.signInEmailGroup.visibility = if (next == Step.SIGN_IN_EMAIL) View.VISIBLE else View.GONE
        binding.signInOtpGroup.visibility = if (next == Step.SIGN_IN_OTP) View.VISIBLE else View.GONE
        binding.keyEntryGroup.visibility = if (next == Step.KEY) View.VISIBLE else View.GONE
        binding.licenseEntryGroup.visibility = if (next == Step.LICENSE) View.VISIBLE else View.GONE

        if (next == Step.SIGN_IN_OTP) {
            binding.otpSubtitle.text = getString(R.string.signin_code_subtitle, pendingEmail)
            binding.otpInput.setText("")
            binding.otpInput.requestFocus()
        }
    }

    private fun openCheckout() {
        UserState.setPairCode(this, "checkout-opened")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BillingConfig.checkoutUrlFor(AuthStore.userId(this)))))
            goTo(Step.LICENSE)
        } catch (t: Throwable) {
            toast("No browser available")
        }
    }

    private fun onSaveKey() {
        val key = binding.keyInput.text?.toString()?.trim().orEmpty()
        if (!key.startsWith("sk-ant-")) {
            toast(getString(R.string.invalid_key_hint))
            return
        }
        ApiKeyStore.save(this, key)
        UserState.setMode(this, Mode.BYO_KEY)
        UserState.setOnboarded(this, true)
        finishToMain()
    }

    private fun onSendCode() {
        if (!BillingConfig.supabaseConfigured()) {
            toast(getString(R.string.signin_not_configured))
            return
        }
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast(getString(R.string.signin_error_invalid_email))
            return
        }
        pendingEmail = email
        binding.sendCodeButton.isEnabled = false
        binding.sendCodeButton.setText(R.string.signin_sending_code)
        lifecycleScope.launch {
            val result = SupabaseAuth.sendOtp(email)
            binding.sendCodeButton.isEnabled = true
            binding.sendCodeButton.setText(R.string.signin_send_code)
            result.onSuccess {
                goTo(Step.SIGN_IN_OTP)
            }.onFailure { e ->
                toast(getString(R.string.signin_error_send, e.message ?: "unknown"))
            }
        }
    }

    private fun onGoogleSignIn() {
        if (!BillingConfig.googleSignInConfigured()) {
            toast(getString(R.string.signin_google_not_configured))
            return
        }
        binding.googleSignInButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val option = GetGoogleIdOption.Builder()
                    // Filter to accounts that have signed in before = false so
                    // first-time users see all their Google accounts.
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BillingConfig.GOOGLE_WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build()

                val response = credentialManager.getCredential(
                    context = this@OnboardingActivity,
                    request = request,
                )

                val cred = response.credential
                if (cred !is CustomCredential ||
                    cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    toast(getString(R.string.signin_google_failed, "unexpected credential type"))
                    return@launch
                }

                val idToken = try {
                    GoogleIdTokenCredential.createFrom(cred.data).idToken
                } catch (e: GoogleIdTokenParsingException) {
                    toast(getString(R.string.signin_google_failed, e.message ?: "bad token"))
                    return@launch
                }

                SupabaseAuth.signInWithGoogleIdToken(idToken)
                    .onSuccess { session ->
                        AuthStore.save(this@OnboardingActivity, session)
                        UserState.setMode(this@OnboardingActivity, Mode.SUBSCRIBED)
                        UserState.setTrialStartedAt(
                            this@OnboardingActivity,
                            System.currentTimeMillis(),
                        )
                        UserState.setOnboarded(this@OnboardingActivity, true)
                        toast(getString(R.string.signed_in_as, session.email))
                        finishToMain()
                    }
                    .onFailure { e ->
                        toast(getString(R.string.signin_google_failed, e.message ?: "unknown"))
                    }
            } catch (e: GetCredentialCancellationException) {
                toast(getString(R.string.signin_google_cancelled))
            } catch (e: NoCredentialException) {
                toast(getString(R.string.signin_google_failed, "no Google accounts on this device"))
            } catch (e: GetCredentialException) {
                toast(getString(R.string.signin_google_failed, e.message ?: "unknown"))
            } finally {
                binding.googleSignInButton.isEnabled = true
            }
        }
    }

    private fun onVerifyCode() {
        val code = binding.otpInput.text?.toString()?.trim().orEmpty()
        if (code.length < 6) {
            toast(getString(R.string.signin_error_verify, "code must be 6 digits"))
            return
        }
        binding.verifyButton.isEnabled = false
        binding.verifyButton.setText(R.string.signin_verifying)
        lifecycleScope.launch {
            val result = SupabaseAuth.verifyOtp(pendingEmail, code)
            binding.verifyButton.isEnabled = true
            binding.verifyButton.setText(R.string.signin_verify)
            result.onSuccess { session ->
                AuthStore.save(this@OnboardingActivity, session)
                // Sign-in opens the 7-day free trial. Lemon Squeezy handles
                // paid conversion; until then the user is SUBSCRIBED and the
                // trial clock has started.
                UserState.setMode(this@OnboardingActivity, Mode.SUBSCRIBED)
                UserState.setTrialStartedAt(
                    this@OnboardingActivity,
                    System.currentTimeMillis(),
                )
                UserState.setOnboarded(this@OnboardingActivity, true)
                toast(getString(R.string.signed_in_as, session.email))
                finishToMain()
            }.onFailure { e ->
                toast(getString(R.string.signin_error_verify, e.message ?: "unknown"))
            }
        }
    }

    private fun onActivateLicense() {
        val key = binding.licenseInput.text?.toString()?.trim().orEmpty()
        if (key.length < 10) {
            toast(getString(R.string.activation_failed, "key too short"))
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
                toast(getString(R.string.activation_failed, result.error ?: "unknown"))
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
        // In v1 the KEY step is the only entry point — back exits the app
        // rather than dumping the user onto the hidden HOME chooser.
        when (step) {
            Step.SIGN_IN_OTP -> goTo(Step.SIGN_IN_EMAIL)
            Step.SIGN_IN_EMAIL, Step.LICENSE -> goTo(Step.HOME)
            Step.KEY, Step.HOME -> super.onBackPressed()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
