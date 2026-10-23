/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.analyzer

import com.sun.management.HotSpotDiagnosticMXBean
import io.github.rogerphilippe.leakweb.model.HeapAnalysis
import io.github.rogerphilippe.leakweb.model.HeapAnalysisFailure
import io.github.rogerphilippe.leakweb.model.HeapAnalysisSuccess
import io.github.rogerphilippe.leakweb.model.LeakKind
import io.github.rogerphilippe.leakweb.model.LeakTrace
import io.github.rogerphilippe.leakweb.model.LeakingStatus
import io.github.rogerphilippe.leakweb.model.ReferenceType
import io.github.rogerphilippe.leakweb.watcher.KeyedWeakReference
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import shark.FilteringLeakingObjectFinder.LeakingObjectFilter
import shark.HeapObject.HeapInstance
import shark.ObjectInspectors
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.ref.ReferenceQueue
import java.nio.file.Files

// Static (top-level) fields: they keep the leaked objects reachable from a GC root during the dump.
private val retainedObjects = mutableListOf<Any>()
private val watchedReferences = mutableListOf<KeyedWeakReference>()

private class LeakedObject

private class WatchedButNotRetainedObject

private class CollectedObject

/** Never watched, but its state says it should be gone, like a destroyed Activity (`mDestroyed`). */
private class DeadUnwatchedObject {
    @Suppress("unused")
    val destroyed = true
}

/** Same class, alive: must not be reported. */
private class AliveUnwatchedObject {
    @Suppress("unused")
    val destroyed = false
}

/** Plays the role of Shark's Android filters (destroyed activity...), which need an Android heap. */
private val destroyedFieldFilter =
    LeakingObjectFilter { heapObject ->
        heapObject is HeapInstance &&
            heapObject.instanceClassName in UNWATCHED_CLASS_NAMES &&
            heapObject[heapObject.instanceClassName, "destroyed"]?.value?.asBoolean == true
    }

private val UNWATCHED_CLASS_NAMES = setOf(DeadUnwatchedObject::class.java.name, AliveUnwatchedObject::class.java.name)

/**
 * Creates a real leak in this JVM, dumps the heap with HotSpotDiagnosticMXBean and analyzes it.
 * The dump is done once for the whole class because it takes a few seconds.
 */
class HprofAnalyzerTest {
    @Test
    fun `retained object is reported as an application leak with LEAKING status`() {
        val trace = traceOf<LeakedObject>()
        val leak = success.leaks.single { trace in it.traces }

        assertEquals(LeakKind.APPLICATION, leak.kind)
        assertTrue(leak.signature.isNotEmpty())
        assertEquals(LeakingStatus.LEAKING, trace.leakingObject.leakingStatus)
        assertTrue(trace.leakingObject.leakingStatusReason.contains("LeakWeb was watching this because test leak"))
        assertTrue(trace.leakingObject.labels.contains("key = leaked-key"))
        assertTrue(trace.leakingObject.labels.contains("watchDurationMillis = 4000"))
        assertTrue(trace.leakingObject.labels.contains("retainedDurationMillis = 1000"))
    }

    @Test
    fun `reference path goes through the static field holding the leaked object`() {
        val path = traceOf<LeakedObject>().referencePath

        assertTrue(
            path.toString(),
            path.any { it.referenceType == ReferenceType.STATIC_FIELD && it.referenceName == "retainedObjects" },
        )
    }

    @Test
    fun `watched object that is not retained yet is not reported`() {
        assertNull(findTrace(WatchedButNotRetainedObject::class.java.name))
    }

    @Test
    fun `garbage collected object is not reported`() {
        assertNull(findTrace(CollectedObject::class.java.name))
    }

    @Test
    fun `dead object that was never watched is reported as leaking`() {
        val trace = traceOf<DeadUnwatchedObject>()

        assertEquals(LeakingStatus.LEAKING, trace.leakingObject.leakingStatus)
        assertTrue(trace.leakingObject.labels.none { it.startsWith("key = ") })
    }

    @Test
    fun `alive unwatched object is not reported`() {
        assertNull(findTrace(AliveUnwatchedObject::class.java.name))
    }

    @Test
    fun `analysis metadata comes from the dump file and the injected clock`() {
        assertEquals("analysis-1", success.id)
        assertEquals(heapDumpFile.name, success.heapDumpFileName)
        assertEquals(heapDumpFile.length(), success.heapDumpSizeBytes)
        assertEquals(END_MILLIS, success.createdAtMillis)
        assertEquals(END_MILLIS - START_MILLIS, success.durationMillis)
    }

    @Test
    fun `progress is reported up to 100 percent`() {
        assertTrue(progress.isNotEmpty())
        assertEquals(progress.map { it.second }.sorted(), progress.map { it.second })
        assertEquals("REPORTING_HEAP_ANALYSIS" to 100, progress.last())
    }

    @Test
    fun `missing heap dump file is reported as failure`() {
        val missing = File(tempDir, "missing.hprof")

        val analysis = HprofAnalyzer { 0L }.analyze(missing, "analysis-2", heapDumpUptimeMillis = 0L)

        assertTrue(analysis is HeapAnalysisFailure)
        assertEquals("missing.hprof", analysis.heapDumpFileName)
        assertEquals(0L, analysis.heapDumpSizeBytes)
    }

    private inline fun <reified T> traceOf(): LeakTrace =
        checkNotNull(findTrace(T::class.java.name)) { "No trace for ${T::class.java.name}" }

    private fun findTrace(className: String): LeakTrace? =
        success.leaks
            .flatMap { it.traces }
            .singleOrNull { it.leakingObject.className == className }

    companion object {
        private const val START_MILLIS = 10_000L
        private const val END_MILLIS = 10_300L
        private const val WATCH_UPTIME_MILLIS = 1_000L
        private const val RETAINED_UPTIME_MILLIS = 4_000L
        private const val HEAP_DUMP_UPTIME_MILLIS = 5_000L

        private lateinit var tempDir: File
        private lateinit var heapDumpFile: File
        private lateinit var success: HeapAnalysisSuccess
        private val progress = mutableListOf<Pair<String, Int>>()

        @BeforeClass
        @JvmStatic
        fun dumpAndAnalyze() {
            tempDir = Files.createTempDirectory("leakweb-analyzer").toFile()
            heapDumpFile = File(tempDir, "test.hprof")
            createLeaks()
            dumpHeap(heapDumpFile)

            val times = ArrayDeque(listOf(START_MILLIS, END_MILLIS))
            val analysis: HeapAnalysis =
                HprofAnalyzer(
                    currentTimeMillis = { times.removeFirst() },
                    unwatchedLeakFilters = { ObjectInspectors.jdkLeakingObjectFilters + destroyedFieldFilter },
                ).analyze(
                    heapDumpFile = heapDumpFile,
                    analysisId = "analysis-1",
                    heapDumpUptimeMillis = HEAP_DUMP_UPTIME_MILLIS,
                ) { step, percent -> progress += step to percent }

            check(analysis is HeapAnalysisSuccess) { (analysis as HeapAnalysisFailure).stackTrace }
            success = analysis
        }

        @AfterClass
        @JvmStatic
        fun cleanUp() {
            retainedObjects.clear()
            watchedReferences.clear()
            tempDir.deleteRecursively()
        }

        // Separate function so that no local variable on the test thread stack references the objects.
        private fun createLeaks() {
            val queue = ReferenceQueue<Any>()

            val leaked = LeakedObject()
            retainedObjects += leaked
            watchedReferences +=
                KeyedWeakReference(leaked, "leaked-key", "test leak", WATCH_UPTIME_MILLIS, queue).apply {
                    retainedUptimeMillis = RETAINED_UPTIME_MILLIS
                }

            val notRetained = WatchedButNotRetainedObject()
            retainedObjects += notRetained
            watchedReferences +=
                KeyedWeakReference(notRetained, "watched-key", "not retained", WATCH_UPTIME_MILLIS, queue)

            retainedObjects += DeadUnwatchedObject()
            retainedObjects += AliveUnwatchedObject()

            // Only weakly reachable: the GC done by the live heap dump clears it.
            watchedReferences +=
                KeyedWeakReference(CollectedObject(), "collected-key", "collected", WATCH_UPTIME_MILLIS, queue).apply {
                    retainedUptimeMillis = RETAINED_UPTIME_MILLIS
                }
        }

        private fun dumpHeap(file: File) {
            ManagementFactory
                .getPlatformMXBean(HotSpotDiagnosticMXBean::class.java)
                .dumpHeap(file.absolutePath, true)
        }
    }
}
