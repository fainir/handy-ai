package com.claudeagent.phone.dialer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.TelecomManager
import android.text.format.DateUtils
import com.claudeagent.phone.R

internal data class PhoneDirectoryEntry(val title: String, val detail: String, val number: String?)

internal class PhoneDirectory(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun contacts(query: String): List<PhoneDirectoryEntry> {
        val uri = if (query.isBlank()) Phone.CONTENT_URI else Phone.CONTENT_FILTER_URI.buildUpon().appendPath(query).build()
        return resolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null,
            "${Phone.SORT_KEY_PRIMARY} ASC")?.use { cursor ->
            buildList {
                val seen = mutableSetOf<Pair<String, String>>()
                while (size < 60 && cursor.moveToNext()) {
                    val raw = cursor.getString(1) ?: continue
                    val number = PhoneNumbers.normalize(raw) ?: continue
                    val name = cursor.getString(0).orEmpty().ifBlank { raw }
                    if (seen.add(name to number)) add(PhoneDirectoryEntry(name, raw, number))
                }
            }
        }.orEmpty()
    }

    fun pickedNumber(uri: Uri): String? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority != "com.android.contacts") return null
        return resolver.query(uri, arrayOf(Phone.NUMBER), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.let(PhoneNumbers::normalize) else null
        }
    }

    fun recents(): List<PhoneDirectoryEntry> {
        if (!context.isDefaultPhone()) return emptyList()
        val uri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "50").build()
        val fields = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
            CallLog.Calls.DATE, CallLog.Calls.NUMBER_PRESENTATION)
        return resolver.query(uri, fields, null, null, CallLog.Calls.DEFAULT_SORT_ORDER)?.use { cursor ->
            buildList {
                while (size < 50 && cursor.moveToNext()) {
                    val presentation = cursor.getInt(4)
                    val raw = cursor.getString(0).takeIf { presentation == TelecomManager.PRESENTATION_ALLOWED }
                    val label = when (cursor.getInt(2)) {
                        CallLog.Calls.INCOMING_TYPE -> R.string.phone_recent_incoming
                        CallLog.Calls.OUTGOING_TYPE -> R.string.phone_recent_outgoing
                        CallLog.Calls.MISSED_TYPE -> R.string.phone_recent_missed
                        CallLog.Calls.REJECTED_TYPE -> R.string.phone_recent_declined
                        else -> R.string.phone_recent_other
                    }
                    val date = DateUtils.getRelativeTimeSpanString(cursor.getLong(3), System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS)
                    val title = if (raw.isNullOrBlank()) {
                        context.getString(if (presentation == TelecomManager.PRESENTATION_RESTRICTED) {
                            R.string.phone_private
                        } else R.string.phone_unknown)
                    } else cursor.getString(1).orEmpty().ifBlank { raw }
                    val details = context.getString(R.string.phone_recent_info, context.getString(label), date)
                    add(PhoneDirectoryEntry(title, details, raw?.let(PhoneNumbers::normalize)))
                }
            }
        }.orEmpty()
    }
}
