package com.claudeagent.phone

import android.content.Context
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

internal object IncludedClaude {
    private const val URL = "https://betterloopai.com/api/claude/v1/complete"
    private const val CONSENT = "screen_gateway_consent_v1"
    private const val EXHAUSTED = "trial_exhausted"
    private fun prefs(context: Context) = context.getSharedPreferences("included_claude", Context.MODE_PRIVATE)

    fun consented(context: Context): Boolean = prefs(context).getBoolean(CONSENT, false)
    fun exhausted(context: Context): Boolean = prefs(context).getBoolean(EXHAUSTED, false)

    fun revoke(context: Context) {
        prefs(context).edit().remove(CONSENT).apply()
    }

    fun confirm(activity: AppCompatActivity, confirmed: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.included_claude_title)
            .setMessage(R.string.included_claude_disclosure)
            .setNegativeButton(R.string.not_now, null)
            .setPositiveButton(R.string.included_claude_accept) { _, _ ->
                prefs(activity).edit().putBoolean(CONSENT, true).apply()
                confirmed()
            }
            .show()
    }

    fun client(context: Context): AgentClient? {
        ApiKeyStore.load(context)?.takeIf { it.isNotBlank() }?.let { return AnthropicClient(it) }
        if (!consented(context) || exhausted(context)) return null
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: return null
        val actor = MessageDigest.getInstance("SHA-256")
            .digest("handyai:included:v1:$androidId".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val app = context.applicationContext
        return IncludedClient(actor, URL, { consented(app) }) {
            prefs(app).edit().putBoolean(EXHAUSTED, true).apply()
        }
    }
}

internal class IncludedClient(
    private val actor: String,
    private val url: String,
    private val canSend: () -> Boolean,
    private val onExhausted: () -> Unit,
) : AgentClient {
    override val included = true
    private var exhausted = false

    private fun markExhausted() {
        exhausted = true
        onExhausted()
    }

    override suspend fun sendMessage(
        model: String,
        systemPrompt: String,
        tools: JsonArray,
        messages: JsonArray,
        maxTokens: Int,
    ): AnthropicResponse = withContext(Dispatchers.IO) {
        if (exhausted) throw IncludedError(explain("trial_exhausted"))
        if (!canSend()) throw IncludedError("Included Claude is off. Enable it in Settings or add your own key.")
        val id = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("version", JsonPrimitive(1))
            put("app", JsonPrimitive("handyai"))
            put("feature", JsonPrimitive("assist"))
            put("actor", JsonPrimitive(actor))
            put("request_id", JsonPrimitive(id))
            put("input", buildJsonObject { put("messages", currentScreenOnly(messages)) })
        }.toString()
        if (payload.toByteArray(Charsets.UTF_8).size > 768 * 1024) throw IOException(
            "This screen or task is too large for the trial. Add your own key in Settings to try it.",
        )
        val request = Request.Builder().url(url)
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        // The gateway may already have reserved or dispatched this step when
        // a socket fails. Do not retry or switch credentials automatically.
        try {
            val (successful, body) = readResponse(http.newCall(request))
            val code = (body?.get("error") as? JsonObject)?.stringOrNull("code")
            if (!successful) {
                if (code == "trial_exhausted" && body?.stringOrNull("request_id") == id) markExhausted()
                throw IncludedError(explain(code))
            }
            if (body?.stringOrNull("request_id") != id || body.stringOrNull("ok") != "true") throw IOException()
            if (body.stringOrNull("trial_steps_remaining") == "0") markExhausted()
            if (!canSend()) throw IncludedError("Included Claude was turned off. The returned action was discarded.")
            AnthropicResponse(
                stopReason = body.stringOrNull("stop_reason"),
                content = body["content"]?.jsonArray ?: throw IOException(),
                usage = body["usage"] as? JsonObject,
            )
        } catch (error: IOException) {
            throw IncludedError("This step could not be confirmed. Check the phone before starting a new task.")
        }
    }

    // Keep the cancellable continuation alive until OkHttp finishes reading
    // the bounded body. A Stop press after headers must still close the socket.
    private suspend fun readResponse(call: Call): Pair<Boolean, JsonObject?> =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    val parsed = response.use {
                        val body = runCatching {
                            val source = it.body?.source() ?: throw IOException()
                            if (source.request(128 * 1024 + 1L)) throw IOException()
                            Json.parseToJsonElement(source.readUtf8()).jsonObject
                        }.getOrNull()
                        it.isSuccessful to body
                    }
                    if (continuation.isActive) continuation.resume(parsed)
                }
            })
        }

    private fun explain(code: String?): String = when (code) {
        "trial_exhausted" -> "Your included Claude steps are used. Add your own Anthropic key in Settings to continue."
        "profile_disabled", "budget_paused", "quota_exhausted", "unavailable" ->
            "Included Claude is temporarily unavailable. Try again later or add your own key in Settings."
        "invalid_request" -> "This screen or task could not be sent with the trial. Try a simpler task or add your own key in Settings."
        "invalid_result" -> "Claude did not return a usable action. Check the phone before starting a new task."
        else -> "This step could not be confirmed. Check the phone before starting a new task."
    }

    companion object {
        private val http = OkHttpClient.Builder()
            .callTimeout(85, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        internal fun currentScreenOnly(messages: JsonArray): JsonArray =
            JsonArray(messages.mapIndexed { index, message ->
                val turn = message.jsonObject
                if (index == messages.lastIndex) turn else withoutImages(turn)
            })

        private fun withoutImages(turn: JsonObject): JsonObject {
            val content = turn["content"]?.jsonArray ?: return turn
            val filtered = content.mapNotNull { value ->
                val block = value.jsonObject
                when (block.stringOrNull("type")) {
                    "image" -> null
                    "tool_result" -> withoutImages(block)
                    else -> block
                }
            }
            return JsonObject(turn + ("content" to JsonArray(filtered)))
        }
    }
}

private class IncludedError(message: String) : RuntimeException(message)
