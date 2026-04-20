package com.claudeagent.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface RunState {
    data object Idle : RunState
    data object Running : RunState
    data class Finished(val success: Boolean, val summary: String) : RunState
    data class Error(val message: String) : RunState
    data object Stopped : RunState
}

object AgentState {
    private val _state = MutableStateFlow<RunState>(RunState.Idle)
    val state: StateFlow<RunState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    fun setState(s: RunState) { _state.value = s }
    fun setStatus(s: String) { _status.value = s }

    fun appendLog(line: String) {
        _log.update { (it + line).takeLast(MAX_LOG_LINES) }
    }

    fun clearLog() { _log.value = emptyList() }

    private const val MAX_LOG_LINES = 200
}
