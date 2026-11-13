package com.claudeagent.phone.dialer

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import android.telecom.Call
import android.text.InputFilter
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.claudeagent.phone.AgentClient
import com.claudeagent.phone.AgentLoop
import com.claudeagent.phone.AnthropicClient
import com.claudeagent.phone.ApiKeyStore
import com.claudeagent.phone.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

internal object CallReplyDialog {
    fun show(activity: PhoneCallActivity, callId: String) = with(activity) {
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            PhoneCalls.service?.trackedCall(callId)?.phoneState != Call.STATE_ACTIVE) return@with
        val key = ApiKeyStore.load(this)?.takeIf(String::isNotBlank)
        if (key == null) {
            AlertDialog.Builder(this).setTitle(R.string.phone_draft_reply)
                .setMessage(R.string.phone_reply_key_needed).setPositiveButton(R.string.phone_close, null).show()
            return@with
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val entry = EditText(this).apply {
            hint = getString(R.string.phone_reply_prompt)
            filters = arrayOf(InputFilter.LengthFilter(1200))
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            minLines = 2
            maxLines = 5
        }
        val status = phoneLabel(R.string.phone_reply_disclosure).apply {
            setTextColor(phoneColor(R.color.text_primary))
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
            setTextIsSelectable(true)
            maxHeight = dp(148)
            movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
            isVisible = false
        }
        val copy = phoneButton(R.string.phone_reply_copy).apply { isVisible = false }
        content.addView(entry)
        content.addView(status)
        content.addView(copy)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.phone_draft_reply)
            .setMessage(R.string.phone_reply_disclosure)
            .setView(content)
            .setPositiveButton(R.string.phone_reply_generate, null)
            .setNegativeButton(R.string.phone_close, null)
            .create()
        var job: Job? = null
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
        val watch = lifecycleScope.launch {
            PhoneCalls.updates.collect {
                if (PhoneCalls.service?.trackedCall(callId)?.phoneState != Call.STATE_ACTIVE) dialog.dismiss()
            }
        }
        dialog.setOnDismissListener {
            job?.cancel()
            watch.cancel()
        }
        val send = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        send.setOnClickListener {
            val text = entry.text.toString().trim()
            if (text.isEmpty()) {
                entry.error = getString(R.string.phone_reply_prompt)
                return@setOnClickListener
            }
            if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                dialog.dismiss()
                return@setOnClickListener
            }
            send.isEnabled = false
            copy.isVisible = false
            status.setText(R.string.phone_reply_working)
            status.isVisible = true
            job = lifecycleScope.launch {
                val reply = withTimeoutOrNull(20_000) {
                    try {
                        draft(AnthropicClient(key), text)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                if (dialog.isShowing) {
                    status.text = reply ?: getText(R.string.phone_reply_failed)
                    copy.isVisible = reply != null
                    copy.setOnClickListener {
                        if (reply != null) {
                            val clip = ClipData.newPlainText(getString(R.string.phone_draft_reply), reply)
                            clip.description.extras = PersistableBundle().apply {
                                val sensitivity = if (Build.VERSION.SDK_INT >= 33) {
                                    ClipDescription.EXTRA_IS_SENSITIVE
                                } else "android.content.extra.IS_SENSITIVE"
                                putBoolean(sensitivity, true)
                            }
                            getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                            copy.announcePhone(R.string.phone_reply_copied)
                        }
                    }
                    send.isEnabled = true
                }
            }
        }
    }

    internal suspend fun draft(client: AgentClient, text: String): String? {
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive(text))
            })
        }
        val response = client.sendMessage(AgentLoop.MODEL,
            "Draft a natural phone reply in the user's language. Return only a short draft for review. Do not claim an action was taken.",
            JsonArray(emptyList()), messages, 220)
        return response.content.mapNotNull { block ->
            val value = block as? JsonObject ?: return@mapNotNull null
            (value["text"] as? JsonPrimitive)?.contentOrNull
        }.joinToString("\n").trim().takeIf(String::isNotEmpty)
    }
}
