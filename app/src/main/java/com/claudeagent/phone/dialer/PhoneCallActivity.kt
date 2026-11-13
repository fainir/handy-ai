package com.claudeagent.phone.dialer

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.claudeagent.phone.AccessibilityHelper
import com.claudeagent.phone.AgentAccessibilityService
import com.claudeagent.phone.R
import com.claudeagent.phone.databinding.ActivityPhoneCallBinding
import kotlinx.coroutines.launch

class PhoneCallActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhoneCallBinding
    private var preferredId: String? = null
    private var showKeypad = false
    private var seenCall = false
    private var lastResult = R.string.phone_ended
    private var rendered: CallControls? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AccessibilityHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityPhoneCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.setBackgroundColor(phoneBackground(R.color.phone_bg))
        binding.callName.setTextColor(phoneText(R.color.phone_text))
        binding.callStatus.setTextColor(phoneText(R.color.phone_secondary))
        binding.callDuration.setTextColor(phoneText(R.color.phone_secondary))
        binding.callToolbar.setNavigationContentDescription(R.string.phone_close)
        binding.callToolbar.setNavigationOnClickListener { finish() }
        preferredId = savedInstanceState?.getString("preferredCall")
        showKeypad = savedInstanceState?.getBoolean("keypad") ?: intent.getBooleanExtra(SHOW_KEYPAD, false)
        seenCall = savedInstanceState?.getBoolean("seenCall") ?: false
        lastResult = savedInstanceState?.getInt("result", R.string.phone_ended) ?: R.string.phone_ended
        answerFromNotification(intent)
        if (PhoneCalls.service?.primaryCall() == null && !seenCall) {
            finish()
            return
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { PhoneCalls.updates.collect { render() } }
        }
    }

    override fun onResume() {
        super.onResume()
        if (PhoneCalls.service?.primaryCall() != null) {
            AgentAccessibilityService.instance?.takeIf { it.isAgentRunning }?.stopAgent()
        }
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        preferredId = null
        showKeypad = intent.getBooleanExtra(SHOW_KEYPAD, false)
        answerFromNotification(intent)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("preferredCall", preferredId)
        outState.putBoolean("keypad", showKeypad)
        outState.putBoolean("seenCall", seenCall)
        outState.putInt("result", lastResult)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        binding.callDuration.stop()
        super.onStop()
    }

    private fun render() {
        if (isFinishing) return
        val service = PhoneCalls.service
        val call = service?.primaryCall(preferredId)
        if (service == null || call == null) {
            lastResult = PhoneCalls.lastResult
            if (rendered == null || rendered?.id != null) {
                rendered = CallControls(id = null)
                binding.callName.text = ""
                binding.callStatus.setText(lastResult)
                binding.callDuration.stop()
                binding.callDuration.isVisible = false
                binding.callControls.removeAllViews()
                buttonRow(Control(R.string.phone_close, R.color.phone_accent) { finish() })
            }
            return
        }

        seenCall = true
        val state = call.phoneState
        val active = state == Call.STATE_ACTIVE
        val held = state == Call.STATE_HOLDING
        val unlocked = !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val conferenceable = call.conferenceableCalls.firstOrNull()
        val other = service.trackedCalls().firstOrNull { it != call && !it.isRinging() }
        binding.callName.text = if (call.details.hasProperty(Call.Details.PROPERTY_CONFERENCE)) {
            getText(R.string.phone_conference)
        } else call.phoneTitle(this)
        val label = when {
            call.isRinging() -> R.string.phone_incoming
            active -> R.string.phone_active
            held -> R.string.phone_held
            state == Call.STATE_SELECT_PHONE_ACCOUNT -> R.string.phone_select_sim
            state == Call.STATE_DISCONNECTING -> R.string.phone_ending
            state == Call.STATE_CONNECTING || state == Call.STATE_NEW -> R.string.phone_connecting
            else -> R.string.phone_dialing
        }
        if (binding.callStatus.text != getText(label)) binding.callStatus.setText(label)
        updateDuration(call, active || held)

        val accounts = if (state == Call.STATE_SELECT_PHONE_ACCOUNT) phoneAccounts(call) else emptyList()
        val next = CallControls(service.callId(call), state, call.details.callCapabilities,
            service.muted, service.audioRoutes(), service.canAddCall(), conferenceable, other, showKeypad, unlocked, accounts)
        if (rendered == next) return
        rendered = next
        binding.callControls.removeAllViews()
        when {
            call.isRinging() -> buttonRow(
                Control(R.string.phone_decline_call, R.color.phone_end) { service.decline(call) },
                Control(R.string.phone_answer_call, R.color.phone_answer) { service.answer(call) },
            )
            state == Call.STATE_SELECT_PHONE_ACCOUNT -> {
                if (accounts.isEmpty()) binding.callControls.addView(phoneLabel(R.string.phone_no_sim_choice))
                addPhoneAccounts(call, accounts)
                buttonRow(Control(R.string.phone_cancel) { call.disconnect() })
            }
            state == Call.STATE_DISCONNECTING -> Unit
            else -> {
                callControls(service, call, next, active, held)
                buttonRow(Control(R.string.phone_end_call, R.color.phone_end) { call.disconnect() })
            }
        }
    }

    private fun callControls(service: HandyInCallService, call: Call, controls: CallControls, active: Boolean, held: Boolean) {
        if (!active && !held) return
        buttonRow(
            Control(if (controls.muted) R.string.phone_unmute else R.string.phone_mute) { service.setMuted(!service.muted) }
                .takeIf { call.details.can(Call.Details.CAPABILITY_MUTE) },
            Control(R.string.phone_audio) { chooseAudio(service) }.takeIf { controls.routes.size > 1 },
            Control(if (held) R.string.phone_resume else R.string.phone_hold) { if (held) call.unhold() else call.hold() }
                .takeIf { call.details.can(Call.Details.CAPABILITY_HOLD) },
        )
        buttonRow(
            Control(if (showKeypad) R.string.phone_hide_keypad else R.string.phone_keypad) {
                showKeypad = !showKeypad
                render()
            }.takeIf { active },
            Control(R.string.phone_add_call) {
                startActivity(Intent(this, PhoneActivity::class.java))
            }.takeIf { controls.canAdd && controls.unlocked },
            controls.conferenceable?.let { next -> Control(R.string.phone_merge_calls) { call.conference(next) } }
                ?: Control(R.string.phone_merge_calls) { call.mergeConference() }
                    .takeIf { call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) },
        )
        buttonRow(
            controls.other?.let { other -> Control(R.string.phone_switch_call) {
                preferredId = service.callId(other)
                if (other.phoneState == Call.STATE_HOLDING) other.unhold()
                render()
            } },
            Control(R.string.phone_draft_reply) { CallReplyDialog.show(this, service.callId(call)) }
                .takeIf { active && controls.unlocked },
        )
        if (showKeypad && active) binding.callControls.fillPhoneKeys(phoneKeys, compact = true) { service.dtmf(call, it) }
    }

    private fun answerFromNotification(intent: Intent) {
        if (intent.action != ANSWER) return
        val service = PhoneCalls.service ?: return
        val id = intent.data?.schemeSpecificPart ?: return
        service.trackedCall(id)?.let(service::answer)
    }

    private fun updateDuration(call: Call, shown: Boolean) {
        val connected = call.details.connectTimeMillis
        binding.callDuration.isVisible = shown && connected > 0
        if (binding.callDuration.isVisible) {
            val elapsed = (System.currentTimeMillis() - connected).coerceAtLeast(0)
            binding.callDuration.base = SystemClock.elapsedRealtime() - elapsed
            binding.callDuration.start()
        } else binding.callDuration.stop()
    }

    private fun chooseAudio(service: HandyInCallService) {
        val routes = service.audioRoutes()
        if (routes.isEmpty()) return
        AlertDialog.Builder(this).setTitle(R.string.phone_audio_choices)
            .setSingleChoiceItems(routes.map { it.name }.toTypedArray(), routes.indexOfFirst { it.selected }) { dialog, which ->
                dialog.dismiss()
                service.selectAudioRoute(routes[which].id) {
                    Toast.makeText(this, R.string.phone_audio_error, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.phone_cancel, null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun phoneAccounts(call: Call): List<PhoneAccountHandle> {
        val extras = call.details.extras ?: return emptyList()
        return if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelableArrayList(Call.AVAILABLE_PHONE_ACCOUNTS, PhoneAccountHandle::class.java).orEmpty()
        } else extras.getParcelableArrayList<PhoneAccountHandle>(Call.AVAILABLE_PHONE_ACCOUNTS).orEmpty()
    }

    private fun addPhoneAccounts(call: Call, accounts: List<PhoneAccountHandle>) {
        val telecom = getSystemService(TelecomManager::class.java)
        accounts.forEachIndexed { index, account ->
            val label = try { telecom.getPhoneAccount(account)?.label } catch (_: SecurityException) { null }
            binding.callControls.addView(phoneButton(R.string.phone_select_sim).apply {
                text = label?.takeIf { it.isNotBlank() } ?: getString(R.string.phone_sim_number, index + 1)
                setOnClickListener { call.phoneAccountSelected(account, false) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }

    private fun buttonRow(vararg buttons: Control?) {
        if (buttons.all { it == null }) return
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        buttons.filterNotNull().forEach { control ->
            row.addView(phoneButton(control.label).apply {
                control.fill?.let { phoneFill(it) }
                setOnClickListener { control.action() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
        }
        binding.callControls.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private data class Control(val label: Int, val fill: Int? = null, val action: () -> Unit)

    private data class CallControls(
        val id: String?,
        val state: Int = 0,
        val capabilities: Int = 0,
        val muted: Boolean = false,
        val routes: List<PhoneAudioRoute> = emptyList(),
        val canAdd: Boolean = false,
        val conferenceable: Call? = null,
        val other: Call? = null,
        val keypad: Boolean = false,
        val unlocked: Boolean = false,
        val accounts: List<PhoneAccountHandle> = emptyList(),
    )

    companion object {
        private const val SHOW_KEYPAD = "showCallKeypad"
        private const val ANSWER = "com.claudeagent.phone.dialer.ANSWER"

        internal fun answerIntent(context: Context, id: String): Intent = intent(context).apply {
            action = ANSWER
            data = Uri.fromParts("handy-call", id, null)
        }

        internal fun intent(context: Context, showDialpad: Boolean = false): Intent =
            Intent(context, PhoneCallActivity::class.java)
                .putExtra(SHOW_KEYPAD, showDialpad)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    }
}
