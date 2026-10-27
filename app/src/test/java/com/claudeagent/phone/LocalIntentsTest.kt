package com.claudeagent.phone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIntentsTest {

    @Test
    fun recognisesQuestionsAboutRecentActivity() {
        for (q in listOf(
            "What did you do?",
            "what did you do just now",
            "What have you done",
            "Read me the audit log",
            "read my recent activity",
            "Recap",
            "What happened?",
            "Hey Handy, what did you do?",
        )) {
            assertTrue(q, LocalIntents.isAuditLogRequest(q))
        }
    }

    @Test
    fun realTasksThatMentionThosePhrasesStillReachTheAgent() {
        // These used to be swallowed by a substring match and answered with the audit log.
        for (q in listOf(
            "Search YouTube for the F1 race recap",
            "Google what happened in the news today",
            "Text Mom: what did you do this weekend?",
            "Open Chrome and look up what happened to the Titanic",
        )) {
            assertFalse(q, LocalIntents.isAuditLogRequest(q))
        }
    }

    @Test
    fun recognisesWhatCanYouDo() {
        for (q in listOf(
            "What can you do?",
            "help",
            "Help me",
            "what can you do for me",
            "What tasks can you do",
            "How do you work?",
            "show me what you can do",
            "Hi, what are you good at?",
        )) {
            assertTrue(q, LocalIntents.isCommonTaskLibraryRequest(q))
        }
    }

    @Test
    fun realTasksThatMentionHelpPhrasesStillReachTheAgent() {
        for (q in listOf(
            "Open Todoist and read me what tasks are due today",
            "Ask Dana on WhatsApp how do you work out the rent split",
            "Help me send a message to Sarah",
            "history of the help desk",
            "",
        )) {
            assertFalse(q, LocalIntents.isCommonTaskLibraryRequest(q))
        }
    }
}
