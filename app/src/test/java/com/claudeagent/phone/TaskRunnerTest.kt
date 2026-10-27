package com.claudeagent.phone

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class TaskRunnerTest {

    private val parent = SupervisorJob()
    private val scope = CoroutineScope(parent + Dispatchers.Default)
    private val crashes: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())
    private val runner = TaskRunner(scope) { crashes += it }

    private suspend fun awaitAllTasks() = withTimeout(5_000) {
        parent.children.toList().forEach { it.join() }
    }

    @Test
    fun stopIsNotReportedAsACrash() = runBlocking {
        // Stop during a plain suspension point (the agent's post-action delay, a
        // screenshot) used to surface as "Crash: StandaloneCoroutine was cancelled"
        // and flip the UI to an error state.
        val started = CompletableDeferred<Unit>()
        runner.start {
            started.complete(Unit)
            delay(60_000)
        }
        started.await()
        assertTrue(runner.isRunning)

        runner.stop()
        awaitAllTasks()

        assertFalse(runner.isRunning)
        assertEquals(emptyList<Throwable>(), crashes.toList())
    }

    @Test
    fun realFailuresAreReported() = runBlocking {
        runner.start { error("boom") }
        awaitAllTasks()

        assertEquals(listOf("boom"), crashes.map { it.message })
    }

    @Test
    fun aNewTaskStartsOnlyAfterThePreviousOneHasUnwound() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val firstStarted = CompletableDeferred<Unit>()
        runner.start {
            try {
                firstStarted.complete(Unit)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    delay(100)
                    events += "first cleaned up"
                }
            }
        }
        firstStarted.await()

        runner.start { events += "second started" }
        awaitAllTasks()

        assertEquals(listOf("first cleaned up", "second started"), events.toList())
        assertEquals(emptyList<Throwable>(), crashes.toList())
    }
}
