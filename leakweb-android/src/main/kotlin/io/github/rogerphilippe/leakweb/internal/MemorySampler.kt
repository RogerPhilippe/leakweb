/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.os.Debug
import io.github.rogerphilippe.leakweb.model.MemorySample
import io.github.rogerphilippe.leakweb.watcher.Clock
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher

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
 * Takes a [MemorySample] on each [sample] call (scheduled every second by the runtime), keeps the last
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

    /** The last sample, or a new one if none was taken yet. */
    fun latest(): MemorySample = synchronized(this) { samples.lastOrNull() } ?: sample()

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
