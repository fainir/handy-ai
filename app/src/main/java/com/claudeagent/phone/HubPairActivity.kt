package com.claudeagent.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.claudeagent.phone.databinding.ActivityHubPairBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class HubPairActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHubPairBinding
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHubPairBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.openHubButton.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(BillingConfig.HUB_BASE_URL + "/machines?link_phone=1"),
                ),
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
        binding.statusText.text = "Requesting pair code…"
        binding.retryButton.isEnabled = false

        lifecycleScope.launch {
            val init = HubClient.initPair()
            if (init.code == null) {
                binding.statusText.text = "Couldn't get a code: ${init.error ?: "unknown error"}"
                binding.retryButton.isEnabled = true
                return@launch
            }
            UserState.setPairCode(this@HubPairActivity, init.code)
            binding.codeText.text = init.code
            binding.statusText.text = "Enter this code on cloudbot-ai.com (Settings → Add phone)."
            pollJob?.cancel()
            pollJob = launch { pollLoop(init.code) }
        }
    }

    private suspend fun pollLoop(code: String) {
        while (coroutineContext.isActive) {
            delay(3000)
            val result = HubClient.pollStatus(code)
            when (result.status) {
                "claimed" -> {
                    val token = result.deviceToken
                    if (token != null) {
                        UserState.setHubToken(this@HubPairActivity, token)
                        UserState.setPairCode(this@HubPairActivity, null)
                        Toast.makeText(this@HubPairActivity, "Paired with Cloudbot", Toast.LENGTH_SHORT).show()
                        finish()
                        return
                    }
                }
                "expired" -> {
                    binding.statusText.text = "Code expired. Tap Retry."
                    binding.retryButton.isEnabled = true
                    return
                }
                "error" -> {
                    binding.statusText.text = "Hub not reachable: ${result.error ?: "network"}"
                    // Keep polling; maybe transient.
                }
                // "pending" -> keep polling
            }
        }
    }
}
