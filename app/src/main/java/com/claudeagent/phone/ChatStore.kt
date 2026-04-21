package com.claudeagent.phone

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Lightweight persistent chat store.
 *
 * - One "session" = one conversation the user sees in the sessions drawer.
 * - Each session holds an ordered list of [ChatMessage]s.
 * - Backed by SharedPreferences with JSON — no DB, no migrations. Fine for
 *   the thousands-of-messages scale a personal phone agent will ever hit.
 *
 * All public methods are safe to call from any thread but the store itself
 * is a process-wide singleton, so keep writes short. Observers get updates
 * via the [sessions] / [activeMessages] flows.
 */
object ChatStore {

    @Serializable
    data class Session(
        val id: String,
        var title: String,
        val createdAt: Long,
        var updatedAt: Long,
    )

    @Serializable
    data class ChatMessage(
        val id: String,
        val sessionId: String,
        // "user", "assistant", "action", "status"
        val role: String,
        val text: String,
        val timestamp: Long,
    )

    private const val PREFS = "chat_store"
    private const val KEY_SESSIONS = "sessions_v1"
    private const val KEY_ACTIVE = "active_session"
    private const val KEY_MESSAGES_PREFIX = "messages_v1_"

    /** Ring buffer per session. SharedPreferences stays under ~200KB per
     *  session even at the cap, so writes remain fast. Older messages fall
     *  off silently — we don't advertise "full history" anywhere. */
    private const val MAX_MESSAGES_PER_SESSION = 500

    /** Hard cap on how many sessions we keep. Old sessions (least recently
     *  updated) are pruned when this is exceeded. */
    private const val MAX_SESSIONS = 100

    private val json = Json { ignoreUnknownKeys = true }

    private var prefs: SharedPreferences? = null

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val activeMessages: StateFlow<List<ChatMessage>> = _activeMessages.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _sessions.value = loadSessions()
        _activeId.value = prefs?.getString(KEY_ACTIVE, null)
        _activeMessages.value = _activeId.value?.let { loadMessages(it) }.orEmpty()
    }

    private fun requirePrefs(): SharedPreferences =
        prefs ?: error("ChatStore.init(context) must be called before use")

    private fun loadSessions(): List<Session> {
        val raw = requirePrefs().getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Session>>(raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.updatedAt }
    }

    private fun saveSessions(list: List<Session>) {
        requirePrefs().edit()
            .putString(KEY_SESSIONS, json.encodeToString(list))
            .apply()
    }

    private fun loadMessages(sessionId: String): List<ChatMessage> {
        val raw = requirePrefs().getString(KEY_MESSAGES_PREFIX + sessionId, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ChatMessage>>(raw) }
            .getOrDefault(emptyList())
    }

    private fun saveMessages(sessionId: String, list: List<ChatMessage>) {
        requirePrefs().edit()
            .putString(KEY_MESSAGES_PREFIX + sessionId, json.encodeToString(list))
            .apply()
    }

    /**
     * Ensure an active session exists. If none, create one.
     * Returns the active session id.
     */
    @Synchronized
    fun ensureActiveSession(): String {
        _activeId.value?.let { return it }
        val s = Session(
            id = UUID.randomUUID().toString(),
            title = "New chat",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val list = (_sessions.value + s).sortedByDescending { it.updatedAt }
        _sessions.value = list
        saveSessions(list)
        _activeId.value = s.id
        _activeMessages.value = emptyList()
        requirePrefs().edit().putString(KEY_ACTIVE, s.id).apply()
        return s.id
    }

    /** Start a brand-new session and make it active. */
    @Synchronized
    fun newSession(): String {
        val s = Session(
            id = UUID.randomUUID().toString(),
            title = "New chat",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val all = (_sessions.value + s).sortedByDescending { it.updatedAt }
        val (kept, dropped) = all.chunked(MAX_SESSIONS).let { parts ->
            parts.first() to parts.drop(1).flatten()
        }
        _sessions.value = kept
        saveSessions(kept)
        // Drop message blobs for any session that fell off the tail.
        dropped.forEach {
            requirePrefs().edit().remove(KEY_MESSAGES_PREFIX + it.id).apply()
        }
        _activeId.value = s.id
        _activeMessages.value = emptyList()
        requirePrefs().edit().putString(KEY_ACTIVE, s.id).apply()
        return s.id
    }

    @Synchronized
    fun setActive(sessionId: String) {
        if (_activeId.value == sessionId) return
        _activeId.value = sessionId
        _activeMessages.value = loadMessages(sessionId)
        requirePrefs().edit().putString(KEY_ACTIVE, sessionId).apply()
    }

    @Synchronized
    fun append(role: String, text: String): ChatMessage {
        val sessionId = ensureActiveSession()
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            text = text,
            timestamp = System.currentTimeMillis(),
        )
        // Cap applied on append so the live flow never emits a list larger
        // than the limit (avoids a one-frame jank when the UI trims).
        val updated = (_activeMessages.value + msg).takeLast(MAX_MESSAGES_PER_SESSION)
        _activeMessages.value = updated
        saveMessages(sessionId, updated)

        // Bump session metadata (updatedAt always; title on first user msg).
        val sessions = _sessions.value.toMutableList()
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            val s = sessions[idx]
            val shouldSetTitle = role == "user" &&
                (s.title == "New chat" || s.title.isBlank())
            val newTitle = if (shouldSetTitle) text.take(48).ifBlank { "New chat" } else s.title
            sessions[idx] = s.copy(title = newTitle, updatedAt = System.currentTimeMillis())
            val sorted = sessions.sortedByDescending { it.updatedAt }
            _sessions.value = sorted
            saveSessions(sorted)
        }
        return msg
    }

    @Synchronized
    fun deleteSession(sessionId: String) {
        val remaining = _sessions.value.filter { it.id != sessionId }
        _sessions.value = remaining
        saveSessions(remaining)
        requirePrefs().edit().remove(KEY_MESSAGES_PREFIX + sessionId).apply()
        if (_activeId.value == sessionId) {
            _activeId.value = null
            _activeMessages.value = emptyList()
            requirePrefs().edit().remove(KEY_ACTIVE).apply()
        }
    }
}
