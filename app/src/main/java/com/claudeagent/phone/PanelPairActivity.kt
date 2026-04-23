package com.claudeagent.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.claudeagent.phone.databinding.ActivityPanelPairBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Pair this phone with the CloudBot panel (cloudbot-ai.com).
 *
 * Mirrors [HubPairActivity] but talks to [PanelClient] / the new
 * `/api/phone/` endpoints. The two live side-by-side — this screen is
 * purely for binding the phone to a user dashboard account.
 */
class PanelPairActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPanelPairBinding
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPanelPairBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.openPanelButton.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(BillingConfig.PANEL_BASE_URL + "/phones")),
            )
        }
        binding.retryButton.setOnClickListener { startPairing() }

        startPairing()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun startPairing() {
        binding.codeText.text = "…"
        binding.statusText.setText(R.string.panel_pair_requesting)
        binding.retryButton.isEnabled = false

        val deviceName = "Handy AI on ${android.os.Build.MODEL}"
        lifecycleScope.launch {
            val init = PanelClient.initPanelPair(deviceName)
            if (init.code == null || init.pollSecret == null) {
                binding.statusText.text = getString(
                    R.string.panel_pair_code_fail,
                    init.error ?: "unknown error",
                )
                binding.retryButton.isEnabled = true
                return@launch
            }
            UserState.setPanelPairCode(this@PanelPairActivity, init.code)
            binding.codeText.text = init.code
            binding.statusText.setText(R.string.panel_pair_instructions)
            pollJob?.cancel()
            // Cap the poll loop at the server's TTL. If the user
            // backgrounds the app and returns 30min later, the existing
            // code is dead anyway — better to stop polling than to
            // hammer /pair/status with guaranteed-404s.
            val deadlineMs = System.currentTimeMillis() + init.expiresInSeconds * 1000L
            pollJob = launch { pollLoop(init.code, init.pollSecret, deadlineMs) }
        }
    }

    private suspend fun pollLoop(code: String, pollSecret: String, deadlineMs: Long) {
        while (coroutineContext.isActive) {
            // 3s cadence matches HubPairActivity + gives the server breathing
            // room; the code lives for 10min so there's no urgency in the poll.
            delay(3000)
            if (System.currentTimeMillis() > deadlineMs) {
                binding.statusText.setText(R.string.panel_pair_code_expired)
                binding.retryButton.isEnabled = true
                return
            }
            val result = PanelClient.pollPanelStatus(code, pollSecret)
            when (result.status) {
                "claimed" -> {
                    val token = result.phoneToken
                    if (token != null) {
                        UserState.setPanelToken(this@PanelPairActivity, token)
                        UserState.setPanelPhoneId(this@PanelPairActivity, result.phoneId)
                        UserState.setPanelPairCode(this@PanelPairActivity, null)
                        Toast.makeText(
                            this@PanelPairActivity,
                            R.string.panel_pair_success,
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                        return
                    }
                    // claimed without token → token already consumed (shouldn't
                    // normally happen on first poll); surface as expired.
                    binding.statusText.setText(R.string.panel_pair_code_expired)
                    binding.retryButton.isEnabled = true
                    return
                }
                "expired", "consumed" -> {
                    binding.statusText.setText(R.string.panel_pair_code_expired)
                    binding.retryButton.isEnabled = true
                    return
                }
                "error" -> {
                    binding.statusText.text = getString(
                        R.string.panel_pair_error,
                        result.error ?: "network",
                    )
                    // Transient — keep polling.
                }
                // "pending" -> keep polling silently
            }
        }
    }
}
