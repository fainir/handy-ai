package com.claudeagent.phone

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs at most one agent task at a time on [scope].
 *
 * - Starting a task cancels the previous one and waits for it to unwind
 *   first, so the old run's "Stopped" bookkeeping cannot land on top of the
 *   new run's state.
 * - Cancellation is how Stop works, so it is not a crash: only other
 *   exceptions reach [onCrash].
 */
class TaskRunner(
    private val scope: CoroutineScope,
    private val onCrash: (Throwable) -> Unit,
) {
    @Volatile
    private var current: Job? = null

    val isRunning: Boolean
        get() = current?.isActive == true

    @Synchronized
    fun start(task: suspend () -> Unit) {
        val previous = current
        previous?.cancel()
        current = scope.launch {
            previous?.join()
            try {
                task()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                onCrash(t)
            }
        }
    }

    fun stop() {
        current?.cancel()
    }
}
