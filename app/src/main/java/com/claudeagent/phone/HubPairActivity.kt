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
                    Uri.parse(BillingConfig.HUB_BASE_URL),
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

        val machineName = "Handy AI on ${android.os.Build.MODEL}"
        lifecycleScope.launch {
            val init = HubClient.initPair(machineName)
            if (init.code == null) {
                binding.statusText.text = "Couldn't get a code: ${init.error ?: "unknown error"}"
                binding.retryButton.isEnabled = true
                return@launch
            }
            UserState.setPairCode(this@HubPairActivity, init.code)
            binding.codeText.text = init.code
            binding.statusText.text =
                "Sign in at best-agent-hub-production.up.railway.app, open Machines, and approve this code."
            pollJob?.cancel()
            pollJob = launch { pollLoop(init.code) }
        }
    }

    private suspend fun pollLoop(code: String) {
        while (coroutineContext.isActive) {
            delay(3000)
            val result = HubClient.pollStatus(code)
            when (result.status) {
                "linked" -> {
                    val token = result.machineToken
                    if (token != null) {
                        UserState.setHubToken(this@HubPairActivity, token)
                        UserState.setPairCode(this@HubPairActivity, null)
                        HubConnection.start(applicationContext)
                        Toast.makeText(
                            this@HubPairActivity,
                            "Paired with best-agent hub",
                            Toast.LENGTH_SHORT,
                        ).show()
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
                    // Keep polling; may be transient.
                }
                // "pending" -> keep polling
            }
        }
    }
}
