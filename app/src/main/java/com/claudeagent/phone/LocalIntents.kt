package com.claudeagent.phone

/**
 * Requests that [MainActivity] answers on the device, without an API call:
 * "what did you do" (read back recent activity) and "what can you do"
 * (suggest tasks).
 *
 * Matching is anchored to the start of the request. A real task that merely
 * contains one of these phrases ("Search YouTube for the F1 race recap",
 * "Open Todoist and read me what tasks are due") must still reach the agent.
 */
object LocalIntents {

    // Too common inside real tasks to match as a prefix: only the whole request counts.
    private val AUDIT_EXACT = setOf("what happened", "recap", "give me a recap")
    private val AUDIT_PREFIXES = listOf(
        "what did you do",
        "what have you done",
        "read me the audit",
        "read the audit",
        "read my recent",
        "read me my recent",
    )

    private val HELP_EXACT = setOf("what can you do", "help", "help me")
    private val HELP_PREFIXES = listOf(
        "what can you do for me",
        "what tasks can you",
        "how do you work",
        "show me what you can do",
        "what are you good at",
    )

    // "Hey Handy, what can you do?" - greetings and the app's name are not part of the request.
    private val LEADING_FILLER = Regex("^(?:(?:hey|hi|hello|ok|okay|so)\\b[\\s,]*)?(?:handy(?: ai)?\\b[\\s,]*)?")

    fun isAuditLogRequest(task: String): Boolean = matches(task, AUDIT_EXACT, AUDIT_PREFIXES)

    fun isCommonTaskLibraryRequest(task: String): Boolean = matches(task, HELP_EXACT, HELP_PREFIXES)

    private fun matches(task: String, exact: Set<String>, prefixes: List<String>): Boolean {
        val q = normalize(task)
        if (q.isEmpty()) return false
        return q in exact || prefixes.any { q == it || q.startsWith("$it ") || q.startsWith("$it,") }
    }

    private fun normalize(task: String): String {
        val lower = task.lowercase().trim().trimEnd('?', '.', '!').trim()
        return lower.replaceFirst(LEADING_FILLER, "").trim()
    }
}
