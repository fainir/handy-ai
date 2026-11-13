package com.claudeagent.phone.dialer

import com.claudeagent.phone.AgentClient
import com.claudeagent.phone.AnthropicResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallReplyDialogTest {
    @Test fun draftSendsOnlyUserTypedTextWithNoTools() = runBlocking {
        val client = object : AgentClient {
            override suspend fun sendMessage(model: String, systemPrompt: String, tools: JsonArray,
                messages: JsonArray, maxTokens: Int): AnthropicResponse {
                assertEquals("[{\"role\":\"user\",\"content\":\"Ask for Tuesday\"}]", messages.toString())
                assertTrue(tools.isEmpty())
                return AnthropicResponse("end_turn", Json.parseToJsonElement(
                    """[{"type":"text","text":"Could we do Tuesday?"}]""",
                ) as JsonArray, null)
            }
        }
        assertEquals("Could we do Tuesday?", CallReplyDialog.draft(client, "Ask for Tuesday"))
    }
}
