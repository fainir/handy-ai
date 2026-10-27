package com.claudeagent.phone

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/** One `tool_use` block from a model response. */
data class ToolCall(val id: String, val name: String, val input: JsonObject)

/**
 * A model response split into what the agent loop needs. [replay] is the
 * assistant turn to send back on the next request: non-blank text and
 * well-formed tool calls only. Thinking blocks are left out on purpose:
 * [AgentProtocol.trimScreenshotHistory] edits earlier turns, which
 * invalidates replayed thinking on Claude 5.x models.
 */
data class AssistantTurn(
    val texts: List<String>,
    val toolCalls: List<ToolCall>,
    val replay: JsonArray,
)

/** String value of a primitive field; null when absent, JSON null, or an object/array. */
internal fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/**
 * Pure helpers for the Messages API wire format used by [AgentLoop]. Nothing
 * here touches Android, so it is covered by JVM unit tests.
 */
object AgentProtocol {

    /** Never throws: blocks that are not objects, or tool calls without an id or name, are skipped. */
    fun parseAssistantTurn(content: JsonArray): AssistantTurn {
        val texts = mutableListOf<String>()
        val calls = mutableListOf<ToolCall>()
        val replay = mutableListOf<JsonElement>()
        for (element in content) {
            val block = element as? JsonObject ?: continue
            when (block.stringOrNull("type")) {
                "text" -> {
                    val text = block.stringOrNull("text").orEmpty().trim()
                    if (text.isNotEmpty()) {
                        texts += text
                        replay += block
                    }
                }
                "tool_use" -> {
                    val id = block.stringOrNull("id") ?: continue
                    val name = block.stringOrNull("name") ?: continue
                    calls += ToolCall(id, name, block["input"] as? JsonObject ?: JsonObject(emptyMap()))
                    replay += block
                }
            }
        }
        return AssistantTurn(texts, calls, JsonArray(replay))
    }

    /** Chat status line for a response that contained no tool call. */
    fun noActionStatus(stopReason: String?): String = when (stopReason) {
        "refusal" -> "Claude declined this task."
        "max_tokens" -> "Claude ran out of output tokens before choosing an action. Try again, or split the task into smaller steps."
        else -> "Model stopped without calling a tool."
    }

    /**
     * Keeps the screenshots of the last [keepRecent] tool-result turns and
     * replaces older ones with a text marker, so each request carries a
     * bounded number of images. Turns trimmed on an earlier step are left
     * byte-for-byte unchanged, which keeps the prompt-cache prefix stable.
     */
    fun trimScreenshotHistory(messages: List<JsonObject>, keepRecent: Int): List<JsonObject> {
        val withScreenshots = messages.indices.filter { i ->
            val m = messages[i]
            m.stringOrNull("role") == "user" && contentOf(m).any { isToolResult(it) && hasImage(it) }
        }
        if (withScreenshots.size <= keepRecent) return messages
        val toTrim = withScreenshots.dropLast(keepRecent).toSet()
        return messages.mapIndexed { i, m -> if (i in toTrim) withoutScreenshots(m) else m }
    }

    private fun contentOf(message: JsonObject): List<JsonObject> =
        (message["content"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun isToolResult(block: JsonObject) = block.stringOrNull("type") == "tool_result"

    private fun hasImage(toolResult: JsonObject): Boolean =
        (toolResult["content"] as? JsonArray).orEmpty().any { (it as? JsonObject)?.stringOrNull("type") == "image" }

    private fun withoutScreenshots(message: JsonObject): JsonObject {
        val content = (message["content"] as? JsonArray).orEmpty().map { element ->
            val tr = element as? JsonObject
            if (tr == null || !isToolResult(tr) || !hasImage(tr)) return@map element
            val summaryText = (tr["content"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.stringOrNull("type") == "text" }
                ?.stringOrNull("text")
                ?: "[action completed]"
            buildJsonObject {
                put("type", JsonPrimitive("tool_result"))
                put("tool_use_id", JsonPrimitive(tr.stringOrNull("tool_use_id").orEmpty()))
                put("is_error", JsonPrimitive(tr.stringOrNull("is_error")?.toBooleanStrictOrNull() ?: false))
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive("$summaryText\n[earlier screenshot omitted]"))
                    })
                })
            }
        }
        return buildJsonObject {
            put("role", JsonPrimitive("user"))
            put("content", JsonArray(content))
        }
    }

    fun systemPrompt(width: Int, height: Int, aliases: String, maxSteps: Int): String {
        val aliasBlock = if (aliases.isBlank()) "" else """

Known name aliases for THIS user (resolve these to the listed entity when they appear in the task):
$aliases

When the user says one of these names, treat it as the resolved entity. If the alias resolution is ambiguous on the current screen, ask the user a one-sentence disambiguation question via finish(success=false, summary="Did you mean ...?") rather than guessing.
"""
        return """
You are a mobile phone operator agent. You control a real Android phone on behalf of the user to complete tasks they describe in natural language.$aliasBlock

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
- A small floating circular accessibility button (the Handy AI "H" icon) may appear in the screenshots near an edge. Ignore it; it is the system shortcut for this app, not part of the app you are operating on.
- If an app is in a first-run/setup flow (welcome screens, permission dialogs), dismiss or complete them first, then continue the task.
- If you encounter two or more plausible targets for an ambiguous user reference (e.g. "Send mom a message" but there are three "Mom" contacts, or "Open Bank" but the user has Chase Bank, BoA, and Bank of Hawaii installed), stop and disambiguate. Call finish(success=false, summary="I see three apps named Bank - Chase, BoA, or Hawaii. Which one?") - the host will read the question aloud and the user will re-issue the task. Do NOT pick one arbitrarily.
- For tasks like "read me the screen", "read me the menu", "what does this notification say", or "read me this article", call read_screen_text instead of describing the screen yourself. The OCR result is read aloud verbatim to the user - fast and zero token cost.

You have at most $maxSteps steps total.
""".trimIndent()
    }
}
