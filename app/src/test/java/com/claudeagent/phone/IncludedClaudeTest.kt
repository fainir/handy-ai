package com.claudeagent.phone

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicInteger

class IncludedClaudeTest {
    private val messages = Json.parseToJsonElement(
        """[{"role":"user","content":[{"type":"text","text":"Read this"},{"type":"image","source":{"data":"old-image"}}]},
            {"role":"assistant","content":[{"type":"tool_use","id":"a","name":"tap","input":{"x":1,"y":2}}]},
            {"role":"user","content":[{"type":"tool_result","tool_use_id":"a","content":[{"type":"text","text":"OK"},{"type":"image","source":{"data":"new-image"}}]}]}]""",
    ) as JsonArray

    @Test
    fun mapsIncludedScreenshotCoordinatesBackToFullResolution() {
        val tap = AgentAction.Tap(100f, 300f)
        assertEquals(AgentAction.Tap(200f, 900f), AgentActions.toDevicePixels(tap, 2f, 3f))
        val crop = AgentAction.ReadTextAt(5, 7, 10, 20)
        assertEquals(AgentAction.ReadTextAt(10, 21, 20, 60), AgentActions.toDevicePixels(crop, 2f, 3f))
        assertEquals(tap, AgentActions.toDevicePixels(tap, 1f, 1f))
    }

    @Test
    fun hostedWireKeepsOnlyLatestScreenAndStopsAtTheServerLimit() = runBlocking {
        val received = AtomicInteger()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val request = CompletableFuture<Pair<String, String>>()
        val fixture = thread(isDaemon = true) {
            server.accept().use { socket ->
                socket.soTimeout = 5_000
                val input = socket.getInputStream().bufferedReader()
                val headers = generateSequence { input.readLine()?.takeIf(String::isNotEmpty) }.toList()
                val length = headers.first { it.startsWith("Content-Length:", ignoreCase = true) }.substringAfter(':').trim().toInt()
                val body = buildString { repeat(length) { append(input.read().toChar()) } }
                request.complete(headers.joinToString("\n") to body)
                received.incrementAndGet()
                val id = Json.parseToJsonElement(body).jsonObject.stringOrNull("request_id")
                val reply = """{"ok":true,"request_id":"$id","trial_steps_remaining":0,"stop_reason":"tool_use","content":[{"type":"tool_use","id":"done","name":"finish","input":{"success":true,"summary":"Done"}}]}""".toByteArray()
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${reply.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(reply)
                    flush()
                }
            }
        }
        try {
            var exhausted = false
            val client = IncludedClient("synthetic", "http://127.0.0.1:${server.localPort}/trial", { true }) { exhausted = true }
            val result = client.sendMessage("ignored", "local-secret", JsonArray(emptyList()), messages, 8192)
            val (headers, body) = request.get(5, TimeUnit.SECONDS)
            assertFalse(body.contains("old-image"))
            assertTrue(body.contains("new-image"))
            assertFalse(body.contains("local-secret"))
            assertFalse(headers.contains("x-api-key", ignoreCase = true))
            assertTrue(exhausted)
            assertEquals("tool_use", result.stopReason)
            val refusal = runCatching {
                client.sendMessage("ignored", "local-secret", JsonArray(emptyList()), messages, 8192)
            }.exceptionOrNull()
            assertTrue(refusal?.message.orEmpty().contains("own Anthropic key"))
            assertEquals(1, received.get())
        } finally {
            server.close()
            fixture.join(1000)
        }
    }

    @Test
    fun stoppingAResponseWithAnIncompleteBodyClosesTheConnection() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val sentHeaders = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Boolean>()
        val fixture = thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = socket.getInputStream().bufferedReader()
                    val headers = generateSequence { input.readLine()?.takeIf(String::isNotEmpty) }.toList()
                    val length = headers.first { it.startsWith("Content-Length:", ignoreCase = true) }.substringAfter(':').trim().toInt()
                    repeat(length) { input.read() }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 1024\r\n\r\n{".toByteArray())
                        flush()
                    }
                    sentHeaders.complete(Unit)
                    closed.complete(input.read() == -1)
                }
            } catch (error: Exception) {
                closed.completeExceptionally(error)
            }
        }
        try {
            val client = IncludedClient("synthetic", "http://127.0.0.1:${server.localPort}/trial", { true }) {}
            val call = async { client.sendMessage("ignored", "", JsonArray(emptyList()), messages, 1) }
            withTimeout(4_000) {
                sentHeaders.await()
                delay(50)
                call.cancelAndJoin()
                assertTrue(closed.await())
            }
        } finally {
            server.close()
            fixture.join(1000)
        }
    }
}
