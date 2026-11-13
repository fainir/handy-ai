package com.claudeagent.phone.dialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.Call
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.claudeagent.phone.R

internal class CallNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(call: Call, id: String) {
        val ringing = call.isRinging()
        val suppressed = ringing && (call.isSuppressedByDnd() ||
            (!context.hasPhonePermission(android.Manifest.permission.READ_CONTACTS) &&
                manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL))
        val channel = if (ringing && !suppressed) INCOMING_CHANNEL else ONGOING_CHANNEL
        if (!manager.areNotificationsEnabled() || (ringing && !context.hasCallAlerts())) {
            cancel()
            return
        }
        manager.createNotificationChannels(listOf(
            NotificationChannel(INCOMING_CHANNEL, context.getString(R.string.phone_notification_incoming),
                NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(ONGOING_CHANNEL, context.getString(R.string.phone_notification_ongoing),
                NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) },
        ))
        val open = PendingIntent.getActivity(context, 0, PhoneCallActivity.intent(context), PendingIntent.FLAG_IMMUTABLE)
        val person = Person.Builder().setName(call.phoneTitle(context)).build()
        val endAction = if (ringing) CallNotificationActions.DECLINE else CallNotificationActions.END
        val end = CallNotificationActions.intent(context, id, endAction)
        val style = if (ringing) {
            NotificationCompat.CallStyle.forIncomingCall(
                person, end, PendingIntent.getActivity(context, 0, PhoneCallActivity.answerIntent(context, id),
                    PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            NotificationCompat.CallStyle.forOngoingCall(person, end)
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_phone)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(if (ringing && !suppressed) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(style)
        if (ringing && !suppressed) notification.setFullScreenIntent(open, true)
        if (suppressed) notification.setSilent(true)
        val interrupting = ringing && !suppressed
        manager.cancel(if (interrupting) ONGOING_ID else INCOMING_ID)
        manager.notify(if (interrupting) INCOMING_ID else ONGOING_ID, notification.build())
    }

    fun cancel() {
        manager.cancel(INCOMING_ID)
        manager.cancel(ONGOING_ID)
    }

    companion object {
        const val INCOMING_CHANNEL = "carrier_incoming_calls"
        private const val ONGOING_CHANNEL = "carrier_active_calls"
        private const val INCOMING_ID = 601
        private const val ONGOING_ID = 602
    }
}

class CallNotificationActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = PhoneCalls.service ?: return
        val id = intent.data?.schemeSpecificPart ?: return
        val call = service.trackedCall(id) ?: return
        when (intent.action) {
            DECLINE -> service.decline(call)
            END -> if (!call.isRinging()) call.disconnect()
        }
    }

    companion object {
        internal const val DECLINE = "com.claudeagent.phone.dialer.DECLINE"
        internal const val END = "com.claudeagent.phone.dialer.END"

        internal fun intent(context: Context, id: String, action: String): PendingIntent =
            PendingIntent.getBroadcast(context, 0, Intent(context, CallNotificationActions::class.java).apply {
                this.action = action
                data = Uri.fromParts("handy-call", id, null)
            }, PendingIntent.FLAG_IMMUTABLE)
    }
}
