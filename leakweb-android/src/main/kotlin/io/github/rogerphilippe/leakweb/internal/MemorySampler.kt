/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.internal

import android.os.Debug
import io.github.rogerphilippe.leakweb.model.MemorySample
import io.github.rogerphilippe.leakweb.watcher.Clock
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher
import java.io.File
import java.util.concurrent.Future

/** Reads the memory counters of the process. */
internal interface MemoryReader {
    /** A sample without [MemorySample.pssKb], which [readPssKb] reads. */
    fun read(): MemorySample

    fun readPssKb(): Long
}

internal class AndroidMemoryReader(
    private val objectWatcher: ObjectWatcher,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : MemoryReader {
    override fun read(): MemorySample {
        val runtime = Runtime.getRuntime()
        return MemorySample(
            timestampMillis = currentTimeMillis(),
            javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            javaHeapMaxBytes = runtime.maxMemory(),
            nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize(),
            watchedObjectCount = objectWatcher.watchedObjectCount,
            retainedObjectCount = objectWatcher.retainedObjectCount,
        )
    }

    // Walks the memory maps of the process, much slower than the other counters.
    override fun readPssKb(): Long = Debug.getPss()
}

/**
 * Takes a [MemorySample] on each [sample] call (every second while [LiveMemory] is on), keeps the last
 * [historySize] ones for the chart of the web UI and passes each one to [onSample].
 * PSS is read only every [pssIntervalMillis]; the samples in between repeat the last value.
 */
internal class MemorySampler(
    private val clock: Clock,
    private val reader: MemoryReader,
    private val onSample: (MemorySample) -> Unit,
    private val pssIntervalMillis: Long = DEFAULT_PSS_INTERVAL_MILLIS,
    private val historySize: Int = DEFAULT_HISTORY_SIZE,
) {
    private val samples = ArrayDeque<MemorySample>()
    private var lastPssKb: Long? = null
    private var lastPssUptimeMillis: Long? = null

    /** Oldest first. */
    val history: List<MemorySample>
        @Synchronized get() = samples.toList()

    /** Reads the memory now, PSS included, without adding it to the history. */
    fun snapshot(): MemorySample = reader.read().copy(pssKb = reader.readPssKb())

    /** Forgets the history and reads PSS again on the next sample. */
    @Synchronized
    fun clear() {
        samples.clear()
        lastPssKb = null
        lastPssUptimeMillis = null
    }

    fun sample(): MemorySample {
        val sample =
            synchronized(this) {
                val now = clock.uptimeMillis()
                val lastPss = lastPssUptimeMillis
                if (lastPss == null || now - lastPss >= pssIntervalMillis) {
                    lastPssKb = reader.readPssKb()
                    lastPssUptimeMillis = now
                }
                reader.read().copy(pssKb = lastPssKb).also { sample ->
                    samples.addLast(sample)
                    if (samples.size > historySize) samples.removeFirst()
                }
            }
        onSample(sample)
        return sample
    }

    companion object {
        const val SAMPLE_INTERVAL_MILLIS = 1_000L
        private const val DEFAULT_PSS_INTERVAL_MILLIS = 10_000L

        // Five minutes at one sample per second.
        private const val DEFAULT_HISTORY_SIZE = 300
    }
}

/** Runs [command] every [intervalMillis] on a background thread until the returned future is cancelled. */
internal fun interface RepeatingExecutor {
    fun scheduleRepeating(
        intervalMillis: Long,
        command: Runnable,
    ): Future<*>
}

/**
 * Live memory sampling, off by default. Only the web UI turns it on or off, and it stays so until
 * turned again, even across app restarts ([stateFile] exists while it is on). Turning it on clears
 * the history; while it is off nothing is sampled at all.
 */
internal class LiveMemory(
    val sampler: MemorySampler,
    private val stateFile: File,
    private val executor: RepeatingExecutor,
    private val onChanged: (enabled: Boolean) -> Unit,
) {
    private var sampling: Future<*>? = null

    val isEnabled: Boolean
        @Synchronized get() = sampling != null

    /** Turns sampling back on if it was on when the app last stopped. */
    fun restore() {
        if (stateFile.isFile) setEnabled(true)
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (enabled == isEnabled) return
        if (enabled) {
            sampler.clear()
            // An exception would cancel the next runs.
            sampling =
                executor.scheduleRepeating(MemorySampler.SAMPLE_INTERVAL_MILLIS) { runCatching { sampler.sample() } }
        } else {
            sampling?.cancel(false)
            sampling = null
        }
        saveState(enabled)
        onChanged(enabled)
    }

    private fun saveState(enabled: Boolean) {
        runCatching {
            if (enabled) {
                stateFile.parentFile?.mkdirs()
                stateFile.writeText("on")
            } else {
                stateFile.delete()
            }
        }
    }
}
