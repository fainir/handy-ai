package com.claudeagent.phone

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Runs [HubConnection.tryConnect] against a local WebSocket server. No network. */
class HubConnectionTest {

    private val server = MockWebServer()
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Server side of one connection: answers the auth message with [reply], or closes if null. */
    private fun hub(reply: String?, onAuth: (WebSocket) -> Unit = {}) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!text.contains("\"auth\"")) return
            if (reply == null) {
                webSocket.close(4001, "bad token")
            } else {
                webSocket.send(reply)
                onAuth(webSocket)
            }
        }
    }

    @Test
    fun holdsOneConnectionWhileAuthenticated() = runBlocking {
        // tryConnect used to return as soon as auth succeeded; the connect loop then
        // opened another socket 2s later, and another, while the first stayed open.
        val serverSide = CompletableDeferred<WebSocket>()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                hub("""{"type":"auth_ok","machineId":"m1"}""") { serverSide.complete(it) },
            ),
        )

        val result = async(Dispatchers.IO) { HubConnection.tryConnect(baseUrl, "token", "id-1", "Handy AI on Test") }
        val socket = withTimeout(5_000) { serverSide.await() }

        delay(500)
        assertFalse("tryConnect returned while the connection was still open", result.isCompleted)

        socket.close(1000, "server restart")
        assertTrue(withTimeout(5_000) { result.await() })
        assertEquals(1, server.requestCount)
        val handshake = server.takeRequest()
        assertTrue(handshake.path!!.startsWith("/?role=machine&name=Handy+AI+on+Test&os=Android&id=id-1"))
    }

    @Test
    fun rejectedAuthReturnsFalse() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(hub(reply = null)))

        val ok = withTimeout(5_000) { HubConnection.tryConnect(baseUrl, "bad", "id-1", "Handy AI on Test") }

        assertFalse(ok)
    }
}
