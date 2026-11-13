package com.claudeagent.phone.dialer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.annotation.RequiresApi
import com.claudeagent.phone.AccessibilityHelper
import com.claudeagent.phone.AgentAccessibilityService
import com.claudeagent.phone.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal val Call.phoneState: Int
    get() = if (Build.VERSION.SDK_INT >= 31) details.state else legacyState()

@Suppress("DEPRECATION")
private fun Call.legacyState(): Int = state

internal fun Call.isRinging() = phoneState == Call.STATE_RINGING || phoneState == Call.STATE_SIMULATED_RINGING

internal fun Call.isSuppressedByDnd(): Boolean = Build.VERSION.SDK_INT >= 34 &&
    details.extras?.getBoolean(Call.EXTRA_IS_SUPPRESSED_BY_DO_NOT_DISTURB) == true

internal fun Call.visibleNumber(): String? = details.takeIf {
    it.handlePresentation == TelecomManager.PRESENTATION_ALLOWED
}?.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.takeIf(String::isNotBlank)

internal fun Call.phoneTitle(context: Context): String =
    details.contactDisplayName?.takeIf { it.isNotBlank() && visibleNumber() != null }
        ?: details.callerDisplayName?.takeIf {
            it.isNotBlank() && details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED
        }
        ?: visibleNumber()
        ?: context.getString(if (details.handlePresentation == TelecomManager.PRESENTATION_RESTRICTED) {
            R.string.phone_private
        } else {
            R.string.phone_unknown
        })

internal fun Call.endedLabel(): Int = when (details.disconnectCause?.code) {
    DisconnectCause.BUSY -> R.string.phone_busy
    DisconnectCause.ERROR, DisconnectCause.RESTRICTED -> R.string.phone_call_failed
    DisconnectCause.ANSWERED_ELSEWHERE -> R.string.phone_on_other_device
    else -> R.string.phone_ended
}

/** Android invokes the service and Call.Callback on the main thread. The flow signals UI changes. */
internal object PhoneCalls {
    private val revision = MutableStateFlow(0L)
    val updates = revision.asStateFlow()
    var lastResult = R.string.phone_ended
        private set
    var service: HandyInCallService? = null
        private set

    fun attach(value: HandyInCallService) {
        service = value
        changed()
    }

    fun detach(value: HandyInCallService) {
        if (service == value) {
            service = null
            changed()
        }
    }

    fun ended(label: Int) {
        lastResult = label
        changed()
    }

    fun changed() {
        revision.value += 1
    }
}

internal data class PhoneAudioRoute(val id: String, val name: String, val selected: Boolean)

class HandyInCallService : InCallService() {
    private val handler = Handler(Looper.getMainLooper())
    private val callIds = linkedMapOf<Call, String>()
    private val notifications by lazy { CallNotifications(this) }
    private var availableEndpoints = emptyList<CallEndpoint>()
    private var currentEndpoint: CallEndpoint? = null
    private var currentDtmf: Call? = null
    private val stopTone = Runnable { stopDtmf() }
    internal var muted = false
        private set

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED) PhoneCalls.ended(call.endedLabel())
            if (state != Call.STATE_ACTIVE && currentDtmf == call) stopDtmf()
            publish()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = publish()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = publish()
        override fun onParentChanged(call: Call, parent: Call?) = publish()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        AgentAccessibilityService.instance?.takeIf { it.isAgentRunning }?.stopAgent()
        AccessibilityHelper.stopSpeaking()
        if (callIds.putIfAbsent(call, UUID.randomUUID().toString()) == null) {
            call.registerCallback(callback, handler)
        }
        PhoneCalls.attach(this)
        publish()
        if (!call.isRinging() || (!hasCallAlerts() && !call.isSuppressedByDnd() &&
                getSystemService(android.app.NotificationManager::class.java).currentInterruptionFilter ==
                android.app.NotificationManager.INTERRUPTION_FILTER_ALL)) showCall()
    }

    override fun onCallRemoved(call: Call) {
        PhoneCalls.ended(call.endedLabel())
        if (currentDtmf == call) stopDtmf()
        call.unregisterCallback(callback)
        callIds.remove(call)
        publish()
        super.onCallRemoved(call)
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        showCall(showDialpad)
    }

    override fun onCanAddCallChanged(canAddCall: Boolean) = PhoneCalls.changed()

    @Deprecated("Fallback for Android 13 and earlier")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        if (Build.VERSION.SDK_INT < 34) {
            muted = audioState.isMuted
            PhoneCalls.changed()
        }
    }

    @RequiresApi(34)
    override fun onAvailableCallEndpointsChanged(endpoints: MutableList<CallEndpoint>) {
        availableEndpoints = endpoints.toList()
        PhoneCalls.changed()
    }

    @RequiresApi(34)
    override fun onCallEndpointChanged(endpoint: CallEndpoint) {
        currentEndpoint = endpoint
        PhoneCalls.changed()
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        muted = isMuted
        PhoneCalls.changed()
    }

    override fun onUnbind(intent: Intent): Boolean {
        clearCalls()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearCalls()
        super.onDestroy()
    }

    internal fun trackedCalls(): List<Call> = callIds.keys.filter {
        it.parent == null && it.phoneState != Call.STATE_DISCONNECTED
    }

    internal fun primaryCall(preferredId: String? = null): Call? {
        val calls = trackedCalls()
        return calls.firstOrNull(Call::isRinging)
            ?: calls.firstOrNull { callIds[it] == preferredId }
            ?: calls.firstOrNull { it.phoneState != Call.STATE_HOLDING }
            ?: calls.firstOrNull()
    }

    internal fun callId(call: Call): String = checkNotNull(callIds[call])

    internal fun trackedCall(id: String): Call? = trackedCalls().firstOrNull { callIds[it] == id }

    internal fun answer(call: Call) {
        if (callIds.containsKey(call) && call.isRinging()) call.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    internal fun decline(call: Call) {
        if (callIds.containsKey(call) && call.isRinging()) call.reject(false, null)
    }

    internal fun dtmf(call: Call, key: Char) {
        if (key !in "0123456789*#" || call.phoneState != Call.STATE_ACTIVE || call !in callIds) return
        stopDtmf()
        call.playDtmfTone(key)
        currentDtmf = call
        handler.postDelayed(stopTone, 180)
    }

    @Suppress("DEPRECATION")
    internal fun audioRoutes(): List<PhoneAudioRoute> {
        if (Build.VERSION.SDK_INT >= 34) {
            return availableEndpoints.map { endpoint ->
                PhoneAudioRoute(endpoint.identifier.toString(), endpoint.endpointName.toString(), endpoint.identifier == currentEndpoint?.identifier)
            }
        }
        val state = callAudioState ?: return emptyList()
        return legacyRoutes.filter { (route, _) -> state.supportedRouteMask and route != 0 }
            .map { (route, name) -> PhoneAudioRoute(route.toString(), getString(name), state.route == route) }
    }

    @Suppress("DEPRECATION")
    internal fun selectAudioRoute(id: String, onFailure: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 34) {
            val endpoint = availableEndpoints.firstOrNull { it.identifier.toString() == id } ?: return
            requestCallEndpointChange(endpoint, mainExecutor, object : OutcomeReceiver<Void?, CallEndpointException> {
                override fun onResult(result: Void?) = Unit
                override fun onError(error: CallEndpointException) = onFailure()
            })
        } else {
            val route = id.toIntOrNull() ?: return
            val state = callAudioState ?: return
            if (state.supportedRouteMask and route != 0) setAudioRoute(route)
        }
    }

    internal fun showCall(showDialpad: Boolean = false) {
        if (trackedCalls().isEmpty()) return
        startActivity(PhoneCallActivity.intent(this, showDialpad))
    }

    private fun stopDtmf() {
        handler.removeCallbacks(stopTone)
        currentDtmf?.stopDtmfTone()
        currentDtmf = null
    }

    private fun publish() {
        PhoneCalls.changed()
        val call = primaryCall()
        if (call == null) notifications.cancel() else notifications.show(call, callId(call))
    }

    private fun clearCalls() {
        stopDtmf()
        callIds.keys.forEach { it.unregisterCallback(callback) }
        callIds.clear()
        availableEndpoints = emptyList()
        currentEndpoint = null
        muted = false
        notifications.cancel()
        PhoneCalls.detach(this)
    }

    private companion object {
        val legacyRoutes: List<Pair<Int, Int>> = listOf(
            CallAudioState.ROUTE_EARPIECE to R.string.phone_earpiece,
            CallAudioState.ROUTE_SPEAKER to R.string.phone_speaker,
            CallAudioState.ROUTE_WIRED_HEADSET to R.string.phone_wired,
            CallAudioState.ROUTE_BLUETOOTH to R.string.phone_bluetooth,
        )
    }
}
