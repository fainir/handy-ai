package com.claudeagent.phone

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AgentAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentJob: Job? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* noop */ }
    override fun onInterrupt() { /* noop */ }

    private val accessibilityButtonCallback =
        object : AccessibilityButtonController.AccessibilityButtonCallback() {
            // The floating accessibility-shortcut button on the edge of the
            // screen (declared via flagRequestAccessibilityButton) opens the
            // Handy AI UI from any app, so the user can start a new task
            // without hunting for the icon.
            override fun onClicked(controller: AccessibilityButtonController) {
                val launch = Intent(this@AgentAccessibilityService, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                }
                startActivity(launch)
            }
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Safe to call repeatedly — ChatStore.init is idempotent. Done here so
        // any path that starts the agent (incl. future hub-triggered runs)
        // can append to the store without needing a Context handy.
        ChatStore.init(applicationContext)
        accessibilityButtonController.registerAccessibilityButtonCallback(accessibilityButtonCallback)
    }

    override fun onDestroy() {
        currentJob?.cancel()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun startAgent(apiKey: String, task: String) {
        currentJob?.cancel()
        currentJob = scope.launch {
            val client = AnthropicClient(apiKey)
            val loop = AgentLoop(client, this@AgentAccessibilityService)
            try {
                loop.run(task)
            } catch (t: Throwable) {
                ChatStore.append("status", "Crash: ${t.message}")
                AgentState.setState(RunState.Error(t.message.orEmpty()))
                AgentState.setStatus("Error: ${t.message}")
            }
        }
    }

    fun stopAgent() {
        currentJob?.cancel()
        currentJob = null
        // Flip the agent state synchronously so the UI's stop→send swap
        // happens immediately, without waiting for the coroutine to wake up
        // from whatever suspension it's in (HTTP read, gesture, etc.).
        // The AgentLoop's own stopped() path will still run and append the
        // "Stopped by user" chat row once cancellation actually propagates.
        AgentState.setState(RunState.Stopped)
        AgentState.setStatus("Stopped")
    }

    val isAgentRunning: Boolean
        get() = currentJob?.isActive == true

    fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = wm.currentWindowMetrics.bounds
        return bounds.width() to bounds.height()
    }

    suspend fun captureScreenshot(): Bitmap? = suspendCancellableCoroutine { cont ->
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val hw = try {
                            Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        } catch (t: Throwable) {
                            null
                        }
                        val sw = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle()
                        try { result.hardwareBuffer.close() } catch (_: Throwable) {}
                        cont.safeResume(sw)
                    }

                    override fun onFailure(errorCode: Int) {
                        cont.safeResume(null)
                    }
                }
            )
        } catch (t: Throwable) {
            cont.safeResume(null)
        }
    }

    suspend fun tap(x: Float, y: Float, durationMs: Long = 100L): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y + 1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatchGestureSuspend(GestureDescription.Builder().addStroke(stroke).build())
    }

    suspend fun longPress(x: Float, y: Float, durationMs: Long = 800L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatchGestureSuspend(GestureDescription.Builder().addStroke(stroke).build())
    }

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300L): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L))
        return dispatchGestureSuspend(GestureDescription.Builder().addStroke(stroke).build())
    }

    fun typeText(text: String): Boolean {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun clearText(): Boolean {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun pressImeEnter(): Boolean {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    private suspend fun dispatchGestureSuspend(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { cont.safeResume(true) }
                override fun onCancelled(g: GestureDescription?) { cont.safeResume(false) }
            }, null)
            if (!ok) cont.safeResume(false)
        }

    private fun <T> CancellableContinuation<T>.safeResume(value: T) {
        if (isActive) resume(value)
    }

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set
    }
}
