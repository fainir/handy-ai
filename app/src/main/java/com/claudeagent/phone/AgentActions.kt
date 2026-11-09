package com.claudeagent.phone

import kotlinx.serialization.json.JsonObject

/** A tool call from the model, validated and ready for [AgentLoop] to execute. */
sealed interface AgentAction {
    data class Tap(val x: Float, val y: Float) : AgentAction
    data class LongPress(val x: Float, val y: Float, val durationMs: Long) : AgentAction
    data class Swipe(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val durationMs: Long,
    ) : AgentAction
    data class TypeText(val text: String) : AgentAction
    data object ClearText : AgentAction
    data class Key(val key: String) : AgentAction
    data class Wait(val ms: Long) : AgentAction
    data object ReadScreenText : AgentAction
    data class ReadTextAt(val x: Int, val y: Int, val width: Int, val height: Int) : AgentAction
    data class Finish(val success: Boolean, val summary: String) : AgentAction

    /** Unknown tool or unusable input. [message] goes back to the model as an error result. */
    data class Invalid(val message: String) : AgentAction
}

/** Pixel rectangle inside a screenshot, already clipped to its bounds. */
data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Maps the tools declared in [AgentTools] to [AgentAction]s. Pure (no Android),
 * so the mapping is covered by JVM unit tests.
 */
object AgentActions {
    val KEYS = listOf("back", "home", "recents", "ime_enter")
    const val DEFAULT_LONG_PRESS_MS = 800L
    const val DEFAULT_SWIPE_MS = 300L
    const val DEFAULT_WAIT_MS = 500L
    const val MAX_WAIT_MS = 3000L

    fun parse(name: String, input: JsonObject): AgentAction {
        // Numbers may arrive as JSON numbers or numeric strings; anything else counts as missing.
        fun num(key: String): Float? = input.stringOrNull(key)?.toFloatOrNull()?.takeIf { it.isFinite() }
        fun int(key: String): Int? = num(key)?.toInt()
        fun millis(key: String): Long? = input.stringOrNull(key)?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
        fun missing(vararg keys: String) =
            AgentAction.Invalid("$name needs numeric ${keys.joinToString(", ")}")

        return when (name) {
            "tap" -> {
                val x = num("x")
                val y = num("y")
                if (x == null || y == null) missing("x", "y") else AgentAction.Tap(x, y)
            }
            "long_press" -> {
                val x = num("x")
                val y = num("y")
                if (x == null || y == null) {
                    missing("x", "y")
                } else {
                    AgentAction.LongPress(x, y, millis("duration_ms") ?: DEFAULT_LONG_PRESS_MS)
                }
            }
            "swipe" -> {
                val x1 = num("x1")
                val y1 = num("y1")
                val x2 = num("x2")
                val y2 = num("y2")
                if (x1 == null || y1 == null || x2 == null || y2 == null) {
                    missing("x1", "y1", "x2", "y2")
                } else {
                    AgentAction.Swipe(x1, y1, x2, y2, millis("duration_ms") ?: DEFAULT_SWIPE_MS)
                }
            }
            "type_text" -> input.stringOrNull("text")?.let { AgentAction.TypeText(it) }
                ?: AgentAction.Invalid("type_text needs text")
            "clear_text" -> AgentAction.ClearText
            "key" -> {
                val key = input.stringOrNull("action").orEmpty()
                if (key in KEYS) {
                    AgentAction.Key(key)
                } else {
                    AgentAction.Invalid("Unknown key '$key'. Use one of: ${KEYS.joinToString(", ")}")
                }
            }
            "wait" -> AgentAction.Wait((millis("ms") ?: DEFAULT_WAIT_MS).coerceIn(0L, MAX_WAIT_MS))
            "read_screen_text" -> AgentAction.ReadScreenText
            "read_text_at" -> {
                val x = int("x")
                val y = int("y")
                val w = int("width")
                val h = int("height")
                if (x == null || y == null || w == null || h == null) {
                    missing("x", "y", "width", "height")
                } else {
                    AgentAction.ReadTextAt(x, y, w, h)
                }
            }
            "finish" -> AgentAction.Finish(
                success = input.stringOrNull("success")?.toBooleanStrictOrNull() ?: false,
                summary = input.stringOrNull("summary").orEmpty(),
            )
            else -> AgentAction.Invalid("Unknown tool: $name")
        }
    }

    fun toDevicePixels(action: AgentAction, xScale: Float, yScale: Float): AgentAction = when (action) {
        is AgentAction.Tap -> action.copy(x = action.x * xScale, y = action.y * yScale)
        is AgentAction.LongPress -> action.copy(x = action.x * xScale, y = action.y * yScale)
        is AgentAction.Swipe -> action.copy(
            x1 = action.x1 * xScale, y1 = action.y1 * yScale,
            x2 = action.x2 * xScale, y2 = action.y2 * yScale,
        )
        is AgentAction.ReadTextAt -> action.copy(
            x = (action.x * xScale).toInt(), y = (action.y * yScale).toInt(),
            width = (action.width * xScale).toInt(), height = (action.height * yScale).toInt(),
        )
        else -> action
    }

    /**
     * Clips a read_text_at region to the screenshot. Returns null when nothing
     * of it is on screen (or its size is not positive).
     */
    fun cropRegion(region: AgentAction.ReadTextAt, maxWidth: Int, maxHeight: Int): CropRect? {
        val left = region.x.coerceIn(0, maxWidth)
        val top = region.y.coerceIn(0, maxHeight)
        val right = (region.x.toLong() + region.width).coerceIn(0L, maxWidth.toLong()).toInt()
        val bottom = (region.y.toLong() + region.height).coerceIn(0L, maxHeight.toLong()).toInt()
        if (right <= left || bottom <= top) return null
        return CropRect(left, top, right - left, bottom - top)
    }

    /** Phone languages whose text the Latin-only on-device OCR cannot read. */
    private val NON_LATIN_LANGUAGES = setOf(
        "he", "iw", "el", "ar", "fa", "ru", "uk", "bg", "zh", "ja", "ko", "hi", "th",
    )

    private val OCR_SCRIPTS = setOf(
        Character.UnicodeScript.LATIN,
        Character.UnicodeScript.COMMON,
        Character.UnicodeScript.INHERITED,
    )

    /**
     * ML Kit's bundled recognizer reads Latin script only; Hebrew, Greek and
     * other scripts come back as junk. True when the task text or the phone's
     * language points at such a script, so the model should read the
     * screenshot itself instead of calling the OCR.
     */
    fun needsModelReading(task: String, deviceLanguage: String): Boolean {
        if (deviceLanguage.lowercase() in NON_LATIN_LANGUAGES) return true
        var i = 0
        while (i < task.length) {
            val cp = task.codePointAt(i)
            if (Character.UnicodeScript.of(cp) !in OCR_SCRIPTS) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** Detailed-verbosity narration for each step. */
    fun narration(name: String, input: JsonObject): String {
        fun s(key: String): String = input.stringOrNull(key).orEmpty()
        return when (name) {
            "tap" -> "Tapping."
            "long_press" -> "Long-pressing."
            "swipe" -> "Swiping."
            "type_text" -> {
                val text = s("text").take(60)
                if (text.isBlank()) "Typing." else "Typing: $text"
            }
            "clear_text" -> "Clearing the text field."
            "key" -> when (s("action")) {
                "back" -> "Going back."
                "home" -> "Going home."
                "recents" -> "Opening recent apps."
                "ime_enter" -> "Pressing enter."
                else -> "Pressing a key."
            }
            "wait" -> "Waiting."
            "read_screen_text" -> "Reading the screen."
            "read_text_at" -> "Reading that area."
            else -> "Working."
        }
    }
}
