package com.claudeagent.phone

import android.content.Context
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
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

class AgentLoop(
    private val client: AnthropicClient,
    private val service: AgentAccessibilityService,
) {
    private val messages = mutableListOf<JsonObject>()

    // Tracks the last user-facing text the model emitted, so we can voice-warn
    // before destructive actions ("I'll tap Send" → speak this aloud and add
    // a confirmation delay before the actual tap fires).
    private var lastAssistantText: String = ""

    private val ctx: Context get() = service.applicationContext

    suspend fun run(task: String) {
        try {
            runSteps(task)
        } catch (ce: CancellationException) {
            // Stop can also land while we are suspended outside the explicit
            // checks below (the post-action delay, a screenshot). Report it the
            // same way, then let the cancellation finish the coroutine.
            stopped()
            throw ce
        }
    }

    private suspend fun runSteps(task: String) {
        AgentState.setState(RunState.Running)
        AgentState.setStatus("Preparing...")

        // Audible + haptic start.
        AccessibilityHelper.haptic(ctx, AccessibilityHelper.Haptic.TASK_STARTED)
        AccessibilityHelper.narrate(ctx, "Starting: ${task.take(120)}")

        // The user message itself is appended by the UI before this loop
        // starts, so we don't echo it here. We just quietly note the screen
        // size as a status line - handy in logs, invisible as a bubble.
        val (w, h) = service.screenSize()
        appendStatus("Screen: ${w}x${h}")

        val firstBitmap = captureOrFail() ?: return fail("Could not capture initial screenshot")

        messages.add(buildJsonObject {
            put("role", JsonPrimitive("user"))
            put("content", buildJsonArray {
                add(textBlock("Task: $task"))
                add(imageBlock(firstBitmap))
            })
        })
        firstBitmap.recycle()

        val systemPrompt = AgentProtocol.systemPrompt(w, h, NameAliases.format(ctx), MAX_STEPS)

        for (step in 1..MAX_STEPS) {
            if (!coroutineContext.isActive) return stopped()
            AgentState.setStatus("Step $step: thinking...")

            val response = try {
                client.sendMessage(
                    model = MODEL,
                    systemPrompt = systemPrompt,
                    tools = AgentTools.definitions,
                    messages = JsonArray(messages.toList()),
                    // Opus 5.5 always thinks, and thinking shares this budget with the action.
                    maxTokens = 8192,
                )
            } catch (ce: CancellationException) {
                return stopped()
            } catch (t: Throwable) {
                return fail("API call failed: ${t.message}")
            }

            val turn = AgentProtocol.parseAssistantTurn(response.content)
            messages.add(buildJsonObject {
                put("role", JsonPrimitive("assistant"))
                put("content", turn.replay)
            })

            turn.texts.forEach { t ->
                appendAssistant(t)
                lastAssistantText = t
                // Read every assistant reply aloud - primary value
                // prop for blind users.
                AccessibilityHelper.speak(ctx, t, interrupt = false)
            }

            if (turn.toolCalls.isEmpty()) {
                appendStatus(AgentProtocol.noActionStatus(response.stopReason))
                AgentState.setState(RunState.Finished(success = false, summary = "Model returned no action."))
                AgentState.setStatus("Stopped: no action from model")
                return
            }

            val toolResults = mutableListOf<JsonObject>()
            var finished: RunState.Finished? = null

            for (call in turn.toolCalls) {
                appendAction("${call.name}(${call.input})")
                AgentState.setStatus("Step $step: ${call.name}")

                val action = AgentActions.parse(call.name, call.input)
                if (action is AgentAction.Finish) {
                    finished = RunState.Finished(action.success, action.summary)
                    toolResults.add(toolResult(call.id, "Acknowledged."))
                    break
                }

                // Voice-confirm destructive actions. Heuristic: if the
                // model's most recent text mentions Send / Buy / Delete /
                // etc., voice-warn and delay 1.5s so a blind user can
                // hit Stop if it's wrong.
                if (action !is AgentAction.Invalid &&
                    AccessibilityHelper.isAudioEnabled(ctx) &&
                    AccessibilityHelper.confirmDestructive(ctx) &&
                    AccessibilityHelper.isLikelyDestructive(lastAssistantText)) {
                    AccessibilityHelper.haptic(ctx, AccessibilityHelper.Haptic.NEEDS_INPUT)
                    AccessibilityHelper.speak(
                        ctx,
                        "Heads up. About to $lastAssistantText. Tap Stop to cancel.",
                        interrupt = true,
                    )
                    delay(DESTRUCTIVE_CONFIRM_DELAY_MS)
                    if (!coroutineContext.isActive) return stopped()
                }

                // Detailed-verbosity per-action narration.
                AccessibilityHelper.narrateDetailed(ctx, AgentActions.narration(call.name, call.input))

                val (ok, message) = try {
                    executeAction(action)
                } catch (ce: CancellationException) {
                    return stopped()
                } catch (t: Throwable) {
                    false to "Error: ${t.message}"
                }

                // Let UI settle before screenshot.
                delay(POST_ACTION_DELAY_MS)

                val newShot = captureOrFail()
                if (newShot == null) {
                    toolResults.add(toolResult(call.id, "$message\n[screenshot unavailable]"))
                    continue
                }

                toolResults.add(buildJsonObject {
                    put("type", JsonPrimitive("tool_result"))
                    put("tool_use_id", JsonPrimitive(call.id))
                    put("is_error", JsonPrimitive(!ok))
                    put("content", buildJsonArray {
                        add(textBlock(message))
                        add(imageBlock(newShot))
                    })
                })
                newShot.recycle()
            }

            val trimmed = AgentProtocol.trimScreenshotHistory(messages, KEEP_RECENT_SCREENSHOTS)
            messages.clear()
            messages.addAll(trimmed)

            messages.add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonArray(toolResults))
            })

            if (finished != null) {
                AgentState.setState(finished)
                AgentState.setStatus(if (finished.success) "Done: ${finished.summary}" else "Stopped: ${finished.summary}")
                if (finished.summary.isNotBlank()) {
                    appendAssistant(finished.summary)
                    AccessibilityHelper.speak(ctx, finished.summary, interrupt = false)
                }
                AccessibilityHelper.haptic(
                    ctx,
                    if (finished.success) AccessibilityHelper.Haptic.TASK_DONE
                    else AccessibilityHelper.Haptic.ERROR,
                )
                return
            }
        }

        AgentState.setState(RunState.Finished(false, "Reached step limit"))
        AgentState.setStatus("Stopped: step limit reached")
        appendStatus("Reached max step limit ($MAX_STEPS)")
        AccessibilityHelper.speak(ctx, "Reached step limit. Stopping.", interrupt = false)
        AccessibilityHelper.haptic(ctx, AccessibilityHelper.Haptic.ERROR)
    }

    private suspend fun executeAction(action: AgentAction): Pair<Boolean, String> = when (action) {
        is AgentAction.Tap -> {
            val ok = service.tap(action.x, action.y)
            ok to if (ok) "Tapped (${action.x.toInt()}, ${action.y.toInt()})" else "Tap failed"
        }
        is AgentAction.LongPress -> {
            val ok = service.longPress(action.x, action.y, action.durationMs)
            ok to if (ok) "Long-pressed (${action.x.toInt()}, ${action.y.toInt()})" else "Long press failed"
        }
        is AgentAction.Swipe -> {
            val ok = service.swipe(action.x1, action.y1, action.x2, action.y2, action.durationMs)
            ok to if (ok) {
                "Swiped (${action.x1.toInt()},${action.y1.toInt()}) → (${action.x2.toInt()},${action.y2.toInt()})"
            } else {
                "Swipe failed"
            }
        }
        is AgentAction.TypeText -> {
            val ok = service.typeText(action.text)
            ok to if (ok) "Typed ${action.text.length} chars" else "No focused text field to type into"
        }
        AgentAction.ClearText -> {
            val ok = service.clearText()
            ok to if (ok) "Cleared text field" else "No focused text field"
        }
        is AgentAction.Key -> {
            val ok = when (action.key) {
                "back" -> service.back()
                "home" -> service.home()
                "recents" -> service.recents()
                "ime_enter" -> service.pressImeEnter()
                else -> false
            }
            ok to if (ok) "Pressed ${action.key}" else "Key '${action.key}' failed"
        }
        is AgentAction.Wait -> {
            delay(action.ms)
            true to "Waited ${action.ms}ms"
        }
        AgentAction.ReadScreenText -> ocrAction(region = null)
        is AgentAction.ReadTextAt -> ocrAction(region = action)
        is AgentAction.Finish -> true to "Acknowledged."
        is AgentAction.Invalid -> false to action.message
    }

    /**
     * Backing implementation for the read_screen_text / read_text_at
     * tools. Captures a screenshot, runs MlKit OCR (locally, no tokens
     * spent), speaks the result aloud, returns verbatim text.
     */
    private suspend fun ocrAction(region: AgentAction.ReadTextAt?): Pair<Boolean, String> {
        val shot = captureOrFail() ?: return false to "Could not capture screen"
        val shotWidth = shot.width
        val shotHeight = shot.height
        val crop = region?.let { AgentActions.cropRegion(it, shotWidth, shotHeight) }
        if (region != null && crop == null) {
            shot.recycle()
            return false to "Region (${region.x}, ${region.y}, ${region.width}x${region.height}) " +
                "is outside the ${shotWidth}x${shotHeight} screen"
        }
        val text = try {
            ScreenOcr.recognize(shot, crop)
        } finally {
            shot.recycle()
        }
        if (text.isBlank()) {
            return true to "No readable text found."
        }
        AccessibilityHelper.speak(ctx, text, interrupt = false)
        val label = if (crop == null) "Screen text" else
            "Text at (${crop.left}, ${crop.top}, ${crop.width}x${crop.height})"
        return true to "$label:\n$text"
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
        appendStatus("Error: $msg")
        AgentState.setState(RunState.Error(msg))
        AgentState.setStatus("Error: $msg")
        AccessibilityHelper.speak(ctx, "Error. $msg", interrupt = true)
        AccessibilityHelper.haptic(ctx, AccessibilityHelper.Haptic.ERROR)
    }

    private fun stopped() {
        appendStatus("Stopped by user")
        AgentState.setState(RunState.Stopped)
        AgentState.setStatus("Stopped")
        AccessibilityHelper.stopSpeaking()
        AccessibilityHelper.speak(ctx, "Stopped.", interrupt = true)
    }

    private fun appendAssistant(text: String) { ChatStore.append("assistant", text) }
    private fun appendAction(text: String) { ChatStore.append("action", text) }
    private fun appendStatus(text: String) { ChatStore.append("status", text) }

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

    companion object {
        // The one place the model is chosen.
        private const val MODEL = "claude-opus-5-5"
        private const val MAX_STEPS = 40
        private const val KEEP_RECENT_SCREENSHOTS = 3
        private const val POST_ACTION_DELAY_MS = 700L
        private const val JPEG_QUALITY = 75

        // Pause inserted before destructive actions so a voiced warning
        // is fully heard and the user can hit Stop.
        private const val DESTRUCTIVE_CONFIRM_DELAY_MS = 1500L
    }
}
