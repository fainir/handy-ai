package com.claudeagent.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RunState {
    data object Idle : RunState
    data object Running : RunState
    data class Finished(val success: Boolean, val summary: String) : RunState
    data class Error(val message: String) : RunState
    data object Stopped : RunState
}

/**
 * Lightweight agent lifecycle state. The transcript itself now lives in
 * [ChatStore]; this object only exposes run-state + status chip text.
 */
object AgentState {
    private val _state = MutableStateFlow<RunState>(RunState.Idle)
    val state: StateFlow<RunState> = _state.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    fun setState(s: RunState) { _state.value = s }
    fun setStatus(s: String) { _status.value = s }
}
