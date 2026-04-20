package com.claudeagent.phone

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

object AgentTools {

    val definitions: JsonArray = buildJsonArray {
        add(tool(
            name = "tap",
            description = "Tap once at the given pixel coordinates on the phone screen.",
            properties = listOf(
                numberProp("x", "Horizontal coordinate in pixels from the left edge."),
                numberProp("y", "Vertical coordinate in pixels from the top edge."),
            ),
            required = listOf("x", "y"),
        ))
        add(tool(
            name = "long_press",
            description = "Press and hold at the given coordinates. Useful for text selection or context menus.",
            properties = listOf(
                numberProp("x", "Horizontal coordinate in pixels."),
                numberProp("y", "Vertical coordinate in pixels."),
                numberProp("duration_ms", "Hold duration in milliseconds. Default 800."),
            ),
            required = listOf("x", "y"),
        ))
        add(tool(
            name = "swipe",
            description = "Swipe from one point to another. Use to scroll (swipe up to scroll down the page) or drag.",
            properties = listOf(
                numberProp("x1", "Start X in pixels."),
                numberProp("y1", "Start Y in pixels."),
                numberProp("x2", "End X in pixels."),
                numberProp("y2", "End Y in pixels."),
                numberProp("duration_ms", "Swipe duration in milliseconds. Default 300."),
            ),
            required = listOf("x1", "y1", "x2", "y2"),
        ))
        add(tool(
            name = "type_text",
            description = "Type text into the currently focused text field. Make sure a text field is focused first by tapping on it.",
            properties = listOf(
                stringProp("text", "Text to type."),
            ),
            required = listOf("text"),
        ))
        add(tool(
            name = "clear_text",
            description = "Clear the currently focused text field.",
            properties = emptyList(),
            required = emptyList(),
        ))
        add(tool(
            name = "key",
            description = "Press a system key.",
            properties = listOf(
                enumProp(
                    "action",
                    "Which key to press.",
                    listOf("back", "home", "recents", "ime_enter"),
                ),
            ),
            required = listOf("action"),
        ))
        add(tool(
            name = "wait",
            description = "Pause briefly to let the UI settle (e.g. after navigation). Use sparingly.",
            properties = listOf(
                numberProp("ms", "Milliseconds to wait. Clamped to 3000."),
            ),
            required = listOf("ms"),
        ))
        add(tool(
            name = "finish",
            description = "Call this when the task is complete (success=true) or cannot be completed (success=false). Include a short summary of what was done.",
            properties = listOf(
                booleanProp("success", "Whether the task was accomplished."),
                stringProp("summary", "Short summary of the outcome for the user."),
            ),
            required = listOf("success", "summary"),
        ))
    }

    private fun tool(
        name: String,
        description: String,
        properties: List<Pair<String, Map<String, Any>>>,
        required: List<String>,
    ) = buildJsonObject {
        put("name", JsonPrimitive(name))
        put("description", JsonPrimitive(description))
        put("input_schema", buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {
                properties.forEach { (propName, schema) ->
                    put(propName, buildJsonObject {
                        schema.forEach { (k, v) ->
                            when (v) {
                                is String -> put(k, JsonPrimitive(v))
                                is List<*> -> put(k, buildJsonArray { v.forEach { add(JsonPrimitive(it as String)) } })
                                else -> put(k, JsonPrimitive(v.toString()))
                            }
                        }
                    })
                }
            })
            put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        })
    }

    private fun numberProp(name: String, desc: String): Pair<String, Map<String, Any>> =
        name to mapOf("type" to "number", "description" to desc)

    private fun stringProp(name: String, desc: String): Pair<String, Map<String, Any>> =
        name to mapOf("type" to "string", "description" to desc)

    private fun booleanProp(name: String, desc: String): Pair<String, Map<String, Any>> =
        name to mapOf("type" to "boolean", "description" to desc)

    private fun enumProp(name: String, desc: String, values: List<String>): Pair<String, Map<String, Any>> =
        name to mapOf("type" to "string", "description" to desc, "enum" to values)
}
