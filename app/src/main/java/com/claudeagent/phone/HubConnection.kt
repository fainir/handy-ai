package com.claudeagent.phone

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Keeps a WebSocket open to the best-agent hub so this phone appears as an online machine
 * on the user's dashboard.
 *
 * Protocol matches best-agent/hub/server.js handleMachineConnection():
 *   connect wss://<hub>/?role=machine&name=<name>&os=Android&id=<uuid>
 *   send { "type": "auth", "token": "<machineToken>" } within 5s
 *   respond to server pings
 *
 * This version is read-mostly — the phone isn't wired to execute shell commands from
 * the hub. It just keeps the connection alive so the user can see the device online.
 */
object HubConnection {

    private const val TAG = "HubConnection"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var ws: WebSocket? = null
    @Volatile private var loopJob: Job? = null
    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        val token = UserState.hubToken(context) ?: return
        val machineId = UserState.machineId(context) ?: UUID.randomUUID().toString().also {
            UserState.setMachineId(context, it)
        }
        val machineName = "Handy AI on ${android.os.Build.MODEL}"
        started = true
        loopJob = scope.launch { connectLoop(token, machineId, machineName) }
    }

    fun stop() {
        started = false
        loopJob?.cancel()
        loopJob = null
        try { ws?.close(1000, "client stopping") } catch (_: Throwable) {}
        ws = null
    }

    private suspend fun connectLoop(token: String, machineId: String, machineName: String) {
        var backoff = 2000L
        while (started) {
            val ok = tryConnect(token, machineId, machineName)
            if (!started) return
            // Exponential backoff capped at 60s, reset on successful auth.
            backoff = if (ok) 2000L else (backoff * 2).coerceAtMost(60_000L)
            delay(backoff)
        }
    }

    private suspend fun tryConnect(token: String, machineId: String, machineName: String): Boolean {
        val base = BillingConfig.HUB_BASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        val encodedName = java.net.URLEncoder.encode(machineName, "UTF-8")
        val url = "$base/?role=machine&name=$encodedName&os=Android&id=$machineId"

        val req = Request.Builder().url(url).build()
        val result = java.util.concurrent.CompletableFuture<Boolean>()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WS open → sending auth")
                val authMsg = JSONObject()
                    .put("type", "auth")
                    .put("token", token)
                    .toString()
                webSocket.send(authMsg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Server may send command/ping messages. We respond to pings
                // and otherwise acknowledge but don't execute (phone can't run shell).
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    when (type) {
                        "ping" -> webSocket.send(JSONObject().put("type", "pong").toString())
                        "auth_ok" -> {
                            Log.i(TAG, "Auth confirmed as machine ${json.optString("machineId")}")
                            result.complete(true)
                        }
                        "exec", "run_command", "cmd" -> {
                            val id = json.optString("id")
                            val response = JSONObject()
                                .put("type", "exec_result")
                                .put("id", id)
                                .put("success", false)
                                .put("error", "Handy AI doesn't execute shell commands.")
                                .toString()
                            webSocket.send(response)
                        }
                        else -> Log.d(TAG, "Unhandled server message: $type")
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Malformed server message: $t")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WS closed: $code $reason")
                result.complete(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WS failure: ${t.message}  code=${response?.code}")
                result.complete(false)
            }
        }

        ws = http.newWebSocket(req, listener)
        return try {
            // Wait for either auth_ok or a failure/close, with a 30s upper bound.
            // (The connection can keep running after this returns true.)
            kotlinx.coroutines.withTimeoutOrNull(30_000) {
                while (!result.isDone) delay(200)
                result.get()
            } ?: false
        } catch (t: Throwable) {
            false
        }
    }
}
