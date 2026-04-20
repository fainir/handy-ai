package com.claudeagent.phone

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

class AgentLoop(
    private val client: AnthropicClient,
    private val service: AgentAccessibilityService,
) {
    private val messages = mutableListOf<JsonObject>()

    suspend fun run(task: String) {
        AgentState.clearLog()
        AgentState.setState(RunState.Running)
        AgentState.setStatus("Preparing...")
        log("Task: $task")

        val (w, h) = service.screenSize()
        log("Screen: ${w}x${h}")

        val firstBitmap = captureOrFail() ?: return fail("Could not capture initial screenshot")

        messages.add(buildJsonObject {
            put("role", JsonPrimitive("user"))
            put("content", buildJsonArray {
                add(textBlock("Task: $task"))
                add(imageBlock(firstBitmap))
            })
        })
        firstBitmap.recycle()

        val systemPrompt = systemPrompt(w, h)

        for (step in 1..MAX_STEPS) {
            if (!coroutineContext.isActive) return stopped()
            AgentState.setStatus("Step $step: thinking...")

            val response = try {
                client.sendMessage(
                    model = MODEL,
                    systemPrompt = systemPrompt,
                    tools = AgentTools.definitions,
                    messages = JsonArray(messages.toList()),
                    maxTokens = 1536,
                )
            } catch (ce: CancellationException) {
                return stopped()
            } catch (t: Throwable) {
                return fail("API call failed: ${t.message}")
            }

            val assistantContent = response.content
            messages.add(buildJsonObject {
                put("role", JsonPrimitive("assistant"))
                put("content", assistantContent)
            })

            val textBlocks = assistantContent.filter {
                it.jsonObject["type"]?.jsonPrimitive?.content == "text"
            }
            textBlocks.forEach {
                val t = it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty().trim()
                if (t.isNotEmpty()) log("💭 $t")
            }

            val toolUses = assistantContent.filter {
                it.jsonObject["type"]?.jsonPrimitive?.content == "tool_use"
            }

            if (toolUses.isEmpty()) {
                log("⚠ Model stopped without calling a tool.")
                AgentState.setState(RunState.Finished(success = false, summary = "Model returned no action."))
                AgentState.setStatus("Stopped: no action from model")
                return
            }

            val toolResults = mutableListOf<JsonObject>()
            var finished: RunState.Finished? = null

            for (block in toolUses) {
                val obj = block.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: continue
                val name = obj["name"]?.jsonPrimitive?.content ?: continue
                val input = obj["input"]?.jsonObject ?: buildJsonObject { }

                log("▶ $name(${input})")
                AgentState.setStatus("Step $step: $name")

                if (name == "finish") {
                    val success = input["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val summary = input["summary"]?.jsonPrimitive?.content.orEmpty()
                    finished = RunState.Finished(success, summary)
                    toolResults.add(toolResult(id, "Acknowledged."))
                    break
                }

                val (ok, message) = try {
                    executeAction(name, input)
                } catch (ce: CancellationException) {
                    return stopped()
                } catch (t: Throwable) {
                    false to "Error: ${t.message}"
                }

                // Let UI settle before screenshot.
                delay(POST_ACTION_DELAY_MS)

                val newShot = captureOrFail()
                if (newShot == null) {
                    toolResults.add(toolResult(id, "$message\n[screenshot unavailable]"))
                    continue
                }

                toolResults.add(buildJsonObject {
                    put("type", JsonPrimitive("tool_result"))
                    put("tool_use_id", JsonPrimitive(id))
                    put("is_error", JsonPrimitive(!ok))
                    put("content", buildJsonArray {
                        add(textBlock(message))
                        add(imageBlock(newShot))
                    })
                })
                newShot.recycle()
            }

            trimScreenshotHistory()

            messages.add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonArray(toolResults))
            })

            if (finished != null) {
                AgentState.setState(finished)
                AgentState.setStatus(if (finished.success) "Done: ${finished.summary}" else "Stopped: ${finished.summary}")
                log(if (finished.success) "✓ ${finished.summary}" else "✗ ${finished.summary}")
                return
            }
        }

        AgentState.setState(RunState.Finished(false, "Reached step limit"))
        AgentState.setStatus("Stopped: step limit reached")
        log("⚠ Reached max step limit ($MAX_STEPS)")
    }

    private suspend fun executeAction(name: String, input: JsonObject): Pair<Boolean, String> {
        fun num(key: String, default: Float? = null): Float {
            val v = input[key]?.jsonPrimitive?.content
            return v?.toFloatOrNull() ?: default ?: 0f
        }
        fun str(key: String): String = input[key]?.jsonPrimitive?.content.orEmpty()

        return when (name) {
            "tap" -> {
                val ok = service.tap(num("x"), num("y"))
                ok to if (ok) "Tapped (${num("x").toInt()}, ${num("y").toInt()})" else "Tap failed"
            }
            "long_press" -> {
                val duration = input["duration_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 800L
                val ok = service.longPress(num("x"), num("y"), duration)
                ok to if (ok) "Long-pressed (${num("x").toInt()}, ${num("y").toInt()})" else "Long press failed"
            }
            "swipe" -> {
                val duration = input["duration_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 300L
                val ok = service.swipe(num("x1"), num("y1"), num("x2"), num("y2"), duration)
                ok to if (ok) "Swiped (${num("x1").toInt()},${num("y1").toInt()}) → (${num("x2").toInt()},${num("y2").toInt()})"
                        else "Swipe failed"
            }
            "type_text" -> {
                val ok = service.typeText(str("text"))
                ok to if (ok) "Typed ${str("text").length} chars" else "No focused text field to type into"
            }
            "clear_text" -> {
                val ok = service.clearText()
                ok to if (ok) "Cleared text field" else "No focused text field"
            }
            "key" -> {
                val action = str("action")
                val ok = when (action) {
                    "back" -> service.back()
                    "home" -> service.home()
                    "recents" -> service.recents()
                    "ime_enter" -> service.pressImeEnter()
                    else -> false
                }
                ok to if (ok) "Pressed $action" else "Key '$action' failed"
            }
            "wait" -> {
                val ms = (input["ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 500L).coerceIn(0L, 3000L)
                delay(ms)
                true to "Waited ${ms}ms"
            }
            else -> false to "Unknown tool: $name"
        }
    }

    private fun trimScreenshotHistory() {
        // Keep only the last KEEP_RECENT tool_result screenshots; replace older images with a marker.
        val toolResultUserMsgs = messages.withIndex().filter { (_, m) ->
            m["role"]?.jsonPrimitive?.content == "user" &&
            (m["content"] as? JsonArray)?.any {
                it.jsonObject["type"]?.jsonPrimitive?.content == "tool_result"
            } == true
        }
        if (toolResultUserMsgs.size <= KEEP_RECENT_SCREENSHOTS) return
        val toTrim = toolResultUserMsgs.dropLast(KEEP_RECENT_SCREENSHOTS)
        toTrim.forEach { (index, msg) ->
            val trimmedContent = (msg["content"] as JsonArray).map { trElement ->
                val tr = trElement.jsonObject
                if (tr["type"]?.jsonPrimitive?.content != "tool_result") return@map trElement
                val id = tr["tool_use_id"]?.jsonPrimitive?.content.orEmpty()
                val isError = tr["is_error"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                val summaryText = (tr["content"] as? JsonArray)
                    ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                    ?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: "[action completed]"
                buildJsonObject {
                    put("type", JsonPrimitive("tool_result"))
                    put("tool_use_id", JsonPrimitive(id))
                    put("is_error", JsonPrimitive(isError))
                    put("content", buildJsonArray {
                        add(textBlock("$summaryText\n[earlier screenshot omitted]"))
                    })
                }
            }
            messages[index] = buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonArray(trimmedContent))
            }
        }
    }

    private suspend fun captureOrFail(): Bitmap? {
        repeat(3) { attempt ->
            val bmp = service.captureScreenshot()
            if (bmp != null) return bmp
            delay(300L * (attempt + 1))
        }
        return null
    }

    private fun fail(msg: String) {
        log("✗ $msg")
        AgentState.setState(RunState.Error(msg))
        AgentState.setStatus("Error: $msg")
    }

    private fun stopped() {
        log("■ Stopped by user")
        AgentState.setState(RunState.Stopped)
        AgentState.setStatus("Stopped")
    }

    private fun log(line: String) {
        AgentState.appendLog(line)
    }

    private fun textBlock(text: String): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("text"))
        put("text", JsonPrimitive(text))
    }

    private fun imageBlock(bitmap: Bitmap): JsonObject {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return buildJsonObject {
            put("type", JsonPrimitive("image"))
            put("source", buildJsonObject {
                put("type", JsonPrimitive("base64"))
                put("media_type", JsonPrimitive("image/jpeg"))
                put("data", JsonPrimitive(b64))
            })
        }
    }

    private fun toolResult(toolUseId: String, text: String): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("tool_result"))
        put("tool_use_id", JsonPrimitive(toolUseId))
        put("content", buildJsonArray { add(textBlock(text)) })
    }

    private fun systemPrompt(width: Int, height: Int): String = """
You are a mobile phone operator agent. You control a real Android phone on behalf of the user to complete tasks they describe in natural language.

At each step you receive the current screenshot of the phone. Decide the single best next action and call exactly one tool. After you call a tool you will receive a new screenshot showing the result.

IMPORTANT - Coordinate system:
The screenshot you receive is the full phone display at ${width} x ${height} pixels. Every coordinate you emit MUST be in this same pixel space. The top-left of the screenshot is (0, 0); the bottom-right is (${width - 1}, ${height - 1}). When you identify a UI element in the screenshot, emit its coordinates as they appear in the screenshot pixel grid - do not normalize, do not use image thumbnail coordinates.

How to work:
- Look carefully at the current screenshot before each action. Briefly (one short sentence) describe what you see and what you'll do, then call the tool.
- Aim for the center of the button/element you want to tap.
- To type into a text field you must first tap it so it is focused, then call type_text.
- After typing, either tap a visible submit/search button or call key with action "ime_enter".
- To scroll, use swipe. Swiping up (y2 < y1) scrolls content down; swiping down scrolls content up.
- Use key "home" to return to the launcher and key "back" to go back.
- If two consecutive taps at the same location have no visible effect, stop repeating - zoom your attention, re-measure the target's center, and try a clearly different coordinate.
- When the task is complete, or you decide it cannot be completed, call finish with success and a short summary. Do not keep acting after calling finish.
- Do not enter passwords, credit card numbers, or perform destructive actions (delete, uninstall, purchase, send money) without an explicit request from the user for that specific action.
- Prefer fewer, higher-quality actions over many small ones. Wait only if the UI is still loading.
- A small floating circular accessibility button may appear in the screenshots (usually near an edge). Ignore it; it is a system overlay, not part of any app, and tapping it opens accessibility settings.
- If an app is in a first-run/setup flow (welcome screens, permission dialogs), dismiss or complete them first, then continue the task.

You have at most ${MAX_STEPS} steps total.
""".trimIndent()

    companion object {
        private const val MODEL = "claude-opus-4-7"
        private const val MAX_STEPS = 40
        private const val KEEP_RECENT_SCREENSHOTS = 3
        private const val POST_ACTION_DELAY_MS = 700L
        private const val JPEG_QUALITY = 75
    }
}
