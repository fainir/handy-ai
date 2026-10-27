package com.claudeagent.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicClientTest {

    @Test
    fun badKeyGetsAnActionableMessage() {
        val body = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        assertEquals(
            "Anthropic rejected the API key (401). Check the key in Settings.",
            AnthropicClient.describeError(401, body),
        )
    }

    @Test
    fun usesTheErrorMessageFromTheResponseBody() {
        val body = """{"type":"error","error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API."}}"""
        assertEquals(
            "Anthropic API error 400: Your credit balance is too low to access the Anthropic API.",
            AnthropicClient.describeError(400, body),
        )
    }

    @Test
    fun fallsBackToTheRawBodyWhenItIsNotJson() {
        val message = AnthropicClient.describeError(502, "<html>" + "x".repeat(1000))
        assertTrue(message.startsWith("Anthropic API error 502: <html>"))
        assertTrue(message.length < 400)
        assertEquals("Anthropic API error 500: (empty response)", AnthropicClient.describeError(500, ""))
    }

    @Test
    fun overloadAndRateLimitsAreNamed() {
        assertTrue(AnthropicClient.describeError(529, "{}").startsWith("Anthropic is overloaded (529)"))
        assertTrue(AnthropicClient.describeError(429, "{}").startsWith("Rate limited by Anthropic (429)"))
    }
}
