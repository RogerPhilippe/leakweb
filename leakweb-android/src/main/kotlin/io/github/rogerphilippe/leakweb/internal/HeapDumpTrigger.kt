/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.os.Debug
import android.util.Log
import io.github.rogerphilippe.leakweb.model.Event
import io.github.rogerphilippe.leakweb.watcher.Clock
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Writes a heap dump to [file]; throws if it fails. */
internal fun interface HeapDumper {
    fun dumpHeap(file: File)
}

/** Freezes every thread of the app while the heap is written. */
internal object AndroidHeapDumper : HeapDumper {
    override fun dumpHeap(file: File) = Debug.dumpHprofData(file.absolutePath)
}

/** Runs [command] after [delayMillis] on a background thread. */
internal fun interface DelayedExecutor {
    fun schedule(
        delayMillis: Long,
        command: Runnable,
    )
}

/** A heap dump written to disk, waiting to be analyzed. */
internal data class HeapDump(
    val id: String,
    val file: File,
    /** Uptime when the dump started, in the [ObjectWatcher] clock. */
    val uptimeMillis: Long,
)

/**
 * Dumps the heap when enough objects are retained: [visibleThreshold] (read on each check, so it can
 * change while the app runs) while the app is visible
 * (not to freeze it too often), [backgroundThreshold] in background. Waits at least
 * [waitBetweenDumpsMillis] between two dumps.
 */
@Suppress("LongParameterList")
internal class HeapDumpTrigger(
    private val objectWatcher: ObjectWatcher,
    private val clock: Clock,
    private val isAppVisible: () -> Boolean,
    private val executor: DelayedExecutor,
    private val heapDumper: HeapDumper,
    private val store: LeakStore,
    private val eventBus: EventBus,
    private val onHeapDumped: (HeapDump) -> Unit,
    private val notifyUser: (String) -> Unit = {},
    private val visibleThreshold: () -> Int = { DEFAULT_VISIBLE_THRESHOLD },
    private val backgroundThreshold: Int = 1,
    private val waitBetweenDumpsMillis: Long = DEFAULT_WAIT_BETWEEN_DUMPS_MILLIS,
    private val manualDumpDelayMillis: Long = DEFAULT_MANUAL_DUMP_DELAY_MILLIS,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val checkScheduled = AtomicBoolean(false)
    private val manualDumpPending = AtomicBoolean(false)

    @Volatile
    private var lastDumpUptimeMillis: Long? = null

    fun onObjectRetained() = scheduleCheck(0L)

    /** Going to background lowers the threshold, so it can be time to dump. */
    fun onAppVisibilityChanged() = scheduleCheck(0L)

    /**
     * Dumps the heap now, whatever the retained count; false if a requested dump did not start yet.
     * It starts [manualDumpDelayMillis] later: the dump freezes every thread, the web server included,
     * so the HTTP response and the "dump started" event must be sent first.
     */
    fun requestManualDump(): Boolean {
        if (!manualDumpPending.compareAndSet(false, true)) return false
        executor.schedule(manualDumpDelayMillis) {
            try {
                dumpHeap(objectWatcher.retainedObjectCount, "Dumping the heap on request")
            } finally {
                manualDumpPending.set(false)
            }
        }
        return true
    }

    private fun scheduleCheck(delayMillis: Long) {
        if (!checkScheduled.compareAndSet(false, true)) return
        executor.schedule(delayMillis) {
            checkScheduled.set(false)
            checkRetainedObjects()
        }
    }

    private fun checkRetainedObjects() {
        val retainedCount = objectWatcher.retainedObjectCount
        val threshold = if (isAppVisible()) visibleThreshold() else backgroundThreshold
        if (retainedCount == 0 || retainedCount < threshold) return

        val lastDump = lastDumpUptimeMillis
        val elapsedMillis = lastDump?.let { clock.uptimeMillis() - it }
        if (elapsedMillis != null && elapsedMillis < waitBetweenDumpsMillis) {
            scheduleCheck(waitBetweenDumpsMillis - elapsedMillis)
            return
        }
        dumpHeap(retainedCount, "Dumping the heap ($retainedCount retained objects)")
    }

    private fun dumpHeap(
        retainedCount: Int,
        reason: String,
    ) {
        val id = newId()
        val file = store.newHeapDumpFile(id)
        val message = "$reason, the app will freeze for a moment"
        Log.w(LEAKWEB_TAG, message)
        notifyUser("LeakWeb: $message")
        eventBus.post(Event.HeapDumpStarted(retainedCount))

        val uptimeMillis = clock.uptimeMillis()
        lastDumpUptimeMillis = uptimeMillis
        runCatching { heapDumper.dumpHeap(file) }
            .onSuccess {
                // The dump holds everything retained so far; stop watching it to not dump it again.
                objectWatcher.clearObjectsWatchedBefore(uptimeMillis)
                Log.i(LEAKWEB_TAG, "Heap dumped to ${file.absolutePath}")
                onHeapDumped(HeapDump(id, file, uptimeMillis))
            }.onFailure { error ->
                file.delete()
                Log.w(LEAKWEB_TAG, "Heap dump failed", error)
                eventBus.post(Event.HeapDumpFailed(error.message ?: error.javaClass.name))
            }
    }

    private companion object {
        const val DEFAULT_VISIBLE_THRESHOLD = 5
        const val DEFAULT_WAIT_BETWEEN_DUMPS_MILLIS = 60_000L
        const val DEFAULT_MANUAL_DUMP_DELAY_MILLIS = 300L
    }
}
