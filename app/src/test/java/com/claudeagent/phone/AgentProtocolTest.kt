package com.claudeagent.phone

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProtocolTest {

    private fun array(json: String): JsonArray = Json.parseToJsonElement(json).jsonArray
    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun parsesTextAndToolCallsAndDropsThinking() {
        val turn = AgentProtocol.parseAssistantTurn(
            array(
                """
                [
                  {"type":"thinking","thinking":"","signature":"abc"},
                  {"type":"text","text":"  I'll open the camera.  "},
                  {"type":"tool_use","id":"toolu_1","name":"tap","input":{"x":336,"y":2035}}
                ]
                """,
            ),
        )
        assertEquals(listOf("I'll open the camera."), turn.texts)
        assertEquals(listOf(ToolCall("toolu_1", "tap", obj("""{"x":336,"y":2035}"""))), turn.toolCalls)
        assertEquals(listOf("text", "tool_use"), turn.replay.map { it.jsonObject.stringOrNull("type") })
    }

    @Test
    fun malformedBlocksAreSkippedInsteadOfCrashingTheLoop() {
        val turn = AgentProtocol.parseAssistantTurn(
            array(
                """
                [
                  "not an object",
                  42,
                  {"type":"text","text":"   "},
                  {"type":"tool_use","name":"tap","input":{"x":1,"y":1}},
                  {"type":"tool_use","id":"toolu_2","input":{}},
                  {"type":"tool_use","id":"toolu_3","name":"clear_text","input":"oops"},
                  {"type":["weird"]}
                ]
                """,
            ),
        )
        assertTrue(turn.texts.isEmpty())
        // Calls without an id or name cannot be answered with a tool_result, so they are not
        // replayed either (a dangling tool_use makes the next request fail).
        assertEquals(listOf(ToolCall("toolu_3", "clear_text", JsonObject(emptyMap()))), turn.toolCalls)
        assertEquals(1, turn.replay.size)
    }

    @Test
    fun noActionStatusExplainsWhy() {
        assertEquals("Claude declined this task.", AgentProtocol.noActionStatus("refusal"))
        assertTrue(AgentProtocol.noActionStatus("max_tokens").contains("ran out of output tokens"))
        assertEquals("Model stopped without calling a tool.", AgentProtocol.noActionStatus("end_turn"))
        assertEquals("Model stopped without calling a tool.", AgentProtocol.noActionStatus(null))
    }

    private val firstUserMessage = obj(
        """
        {"role":"user","content":[
          {"type":"text","text":"Task: take a picture"},
          {"type":"image","source":{"type":"base64","media_type":"image/jpeg","data":"AAAA"}}
        ]}
        """,
    )

    private fun assistant(id: String) = obj(
        """{"role":"assistant","content":[{"type":"tool_use","id":"$id","name":"tap","input":{"x":1,"y":2}}]}""",
    )

    private fun toolResult(id: String, text: String) = obj(
        """
        {"role":"user","content":[{"type":"tool_result","tool_use_id":"$id","is_error":false,"content":[
          {"type":"text","text":"$text"},
          {"type":"image","source":{"type":"base64","media_type":"image/jpeg","data":"BBBB"}}
        ]}]}
        """,
    )

    private fun conversation(steps: Int): List<JsonObject> = buildList {
        add(firstUserMessage)
        for (i in 1..steps) {
            add(assistant("t$i"))
            add(toolResult("t$i", "Tapped $i"))
        }
    }

    private fun imageCount(messages: List<JsonObject>): Int =
        Json.encodeToString(JsonArray.serializer(), JsonArray(messages)).split("\"type\":\"image\"").size - 1

    @Test
    fun keepsOnlyTheMostRecentScreenshots() {
        val trimmed = AgentProtocol.trimScreenshotHistory(conversation(5), keepRecent = 3)

        // The task screenshot is never trimmed; of the five tool results only the last three keep theirs.
        assertEquals(1 + 3, imageCount(trimmed))
        assertSame(firstUserMessage, trimmed[0])
        val oldest = trimmed[2]["content"]!!.jsonArray[0].jsonObject
        assertEquals(
            obj(
                """
                {"type":"tool_result","tool_use_id":"t1","is_error":false,
                 "content":[{"type":"text","text":"Tapped 1\n[earlier screenshot omitted]"}]}
                """,
            ),
            oldest,
        )
    }

    @Test
    fun underTheLimitNothingChanges() {
        val messages = conversation(3)
        assertSame(messages, AgentProtocol.trimScreenshotHistory(messages, keepRecent = 3))
    }

    @Test
    fun alreadyTrimmedTurnsStayByteIdentical() {
        // The loop trims on every step. Re-trimming used to append another
        // "[earlier screenshot omitted]" to every old turn each step, growing the
        // prompt and changing the oldest turn every request (which defeats prompt caching).
        var messages = conversation(4)
        messages = AgentProtocol.trimScreenshotHistory(messages, keepRecent = 3)
        val afterFirstTrim = messages[2]
        messages = messages + assistant("t5") + toolResult("t5", "Tapped 5")
        messages = AgentProtocol.trimScreenshotHistory(messages, keepRecent = 3)
        messages = AgentProtocol.trimScreenshotHistory(messages, keepRecent = 3)

        assertEquals(afterFirstTrim, messages[2])
        val text = messages[2]["content"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject.stringOrNull("text")
        assertEquals("Tapped 1\n[earlier screenshot omitted]", text)
        assertEquals(1 + 3, imageCount(messages))
    }

    @Test
    fun systemPromptStatesStepBudgetAndCoordinateSpace() {
        val prompt = AgentProtocol.systemPrompt(width = 1080, height = 2424, aliases = "", maxSteps = 40)
        assertTrue(prompt.contains("You have at most 40 steps total."))
        assertTrue(prompt.contains("full phone display at 1080 x 2424 pixels"))
        assertTrue(prompt.contains("the bottom-right is (1079, 2423)"))
        assertFalse(prompt.contains("Known name aliases"))

        val withAliases = AgentProtocol.systemPrompt(1080, 2424, "- \"mom\" → contact Sarah", 40)
        assertTrue(withAliases.contains("Known name aliases for THIS user"))
        assertTrue(withAliases.contains("- \"mom\" → contact Sarah"))
    }
}
