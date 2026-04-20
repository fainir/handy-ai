package com.claudeagent.phone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AnthropicResponse(
    val stopReason: String?,
    val content: JsonArray,
    val usage: JsonObject?,
)

class AnthropicClient(private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun sendMessage(
        model: String,
        systemPrompt: String,
        tools: JsonArray,
        messages: JsonArray,
        maxTokens: Int = 2048,
    ): AnthropicResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("max_tokens", JsonPrimitive(maxTokens))
            put("system", buildJsonArray {
                add(buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(systemPrompt))
                    put("cache_control", buildJsonObject { put("type", JsonPrimitive("ephemeral")) })
                })
            })
            put("tools", tools)
            put("messages", messages)
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                http.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.code in RETRYABLE_STATUS) {
                        lastError = RuntimeException("API ${resp.code}: ${text.take(200)}")
                        return@use
                    }
                    if (!resp.isSuccessful) {
                        throw RuntimeException("Anthropic API error ${resp.code}: ${text.take(500)}")
                    }
                    val obj = json.parseToJsonElement(text).jsonObject
                    return@withContext AnthropicResponse(
                        stopReason = obj["stop_reason"]?.jsonPrimitive?.content,
                        content = obj["content"]?.jsonArray ?: JsonArray(emptyList()),
                        usage = obj["usage"]?.jsonObject,
                    )
                }
            } catch (io: IOException) {
                lastError = io
            }
            if (attempt < MAX_ATTEMPTS - 1) delay(BACKOFF_MS[attempt])
        }
        throw lastError ?: RuntimeException("Anthropic request failed")
    }

    companion object {
        private const val MAX_ATTEMPTS = 4
        private val BACKOFF_MS = longArrayOf(1_500L, 4_000L, 10_000L)
        private val RETRYABLE_STATUS = setOf(408, 429, 500, 502, 503, 504, 529)
    }
}
