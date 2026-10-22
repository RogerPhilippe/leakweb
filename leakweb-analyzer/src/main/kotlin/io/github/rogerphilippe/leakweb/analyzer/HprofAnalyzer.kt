/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.analyzer

import io.github.rogerphilippe.leakweb.model.HeapAnalysis
import io.github.rogerphilippe.leakweb.model.HeapAnalysisFailure
import io.github.rogerphilippe.leakweb.model.HeapAnalysisSuccess
import shark.AndroidObjectInspectors
import shark.AndroidReferenceMatchers
import shark.FilteringLeakingObjectFinder
import shark.FilteringLeakingObjectFinder.LeakingObjectFilter
import shark.HeapAnalyzer
import shark.HeapGraph
import shark.HprofHeapGraph.Companion.openHeapGraph
import shark.LeakingObjectFinder
import shark.ObjectInspectors
import shark.OnAnalysisProgressListener
import java.io.File
import shark.HeapAnalysisFailure as SharkHeapAnalysisFailure
import shark.HeapAnalysisSuccess as SharkHeapAnalysisSuccess

/** Receives the analysis steps, e.g. to stream them to the web UI. */
fun interface AnalysisProgressListener {
    /** [step] is the step name; [percent] goes from the first step up to 100 on the last one. */
    fun onProgress(
        step: String,
        percent: Int,
    )
}

/**
 * Finds leaks in a heap dump with Shark: objects watched with `KeyedWeakReference` and retained, plus
 * objects that are obviously dead although nobody watched them (destroyed activity, destroyed
 * fragment, detached view, stopped service...), so that a manual dump also finds them.
 */
class HprofAnalyzer internal constructor(
    private val currentTimeMillis: () -> Long,
    /** Tells which unwatched objects are dead; tests replace it since the Android ones need an Android heap. */
    private val unwatchedLeakFilters: (HeapGraph) -> List<LeakingObjectFilter>,
) {
    constructor(
        currentTimeMillis: () -> Long = System::currentTimeMillis,
    ) : this(currentTimeMillis, { graph -> graph.leakingObjectFilters() })

    /**
     * @param heapDumpUptimeMillis uptime when the heap was dumped, in the same clock as the
     * watch/retained times of the `KeyedWeakReference`s; used to report how long objects were retained.
     */
    fun analyze(
        heapDumpFile: File,
        analysisId: String,
        heapDumpUptimeMillis: Long,
        progressListener: AnalysisProgressListener = AnalysisProgressListener { _, _ -> },
    ): HeapAnalysis {
        val startMillis = currentTimeMillis()
        val sharkListener =
            OnAnalysisProgressListener { step ->
                progressListener.onProgress(step.name, (step.ordinal + 1) * PERCENT / STEP_COUNT)
            }
        val result =
            runCatching {
                // The graph-based analyze() skips the first and last steps; they are reported here.
                sharkListener.onAnalysisProgress(OnAnalysisProgressListener.Step.PARSING_HEAP_DUMP)
                heapDumpFile
                    .openHeapGraph()
                    .use { graph ->
                        HeapAnalyzer(sharkListener).analyze(
                            heapDumpFile = heapDumpFile,
                            graph = graph,
                            leakingObjectFinder = graph.leakingObjectFinder(),
                            referenceMatchers = graph.referenceMatchers(),
                            computeRetainedHeapSize = true,
                            objectInspectors =
                                graph.objectInspectors() + KeyedWeakReferenceInspector(heapDumpUptimeMillis),
                        )
                    }.also {
                        sharkListener.onAnalysisProgress(OnAnalysisProgressListener.Step.REPORTING_HEAP_ANALYSIS)
                    }
            }
        val endMillis = currentTimeMillis()
        val heapDumpSizeBytes = if (heapDumpFile.exists()) heapDumpFile.length() else 0L

        fun failure(error: Throwable) =
            HeapAnalysisFailure(
                id = analysisId,
                createdAtMillis = endMillis,
                heapDumpFileName = heapDumpFile.name,
                heapDumpSizeBytes = heapDumpSizeBytes,
                durationMillis = endMillis - startMillis,
                errorMessage = error.message ?: error.javaClass.name,
                stackTrace = error.stackTraceToString(),
            )

        return result.fold(
            onSuccess = { analysis ->
                when (analysis) {
                    is SharkHeapAnalysisSuccess -> {
                        HeapAnalysisSuccess(
                            id = analysisId,
                            createdAtMillis = endMillis,
                            heapDumpFileName = heapDumpFile.name,
                            heapDumpSizeBytes = heapDumpSizeBytes,
                            durationMillis = endMillis - startMillis,
                            leaks =
                                analysis.applicationLeaks.map { it.toModel() } +
                                    analysis.libraryLeaks.map { it.toModel() },
                        )
                    }

                    // Shark wraps the original error; report the cause, which is the useful one.
                    is SharkHeapAnalysisFailure -> {
                        failure(analysis.exception.cause ?: analysis.exception)
                    }
                }
            },
            onFailure = ::failure,
        )
    }

    private fun HeapGraph.referenceMatchers() =
        if (isAndroidHeap()) {
            AndroidReferenceMatchers.appDefaults
        } else {
            AndroidReferenceMatchers.ignoredReferencesOnly
        }

    // Finding the unwatched objects scans every object of the heap, which makes the analysis slower.
    private fun HeapGraph.leakingObjectFinder(): LeakingObjectFinder {
        val unwatchedFinder = FilteringLeakingObjectFinder(unwatchedLeakFilters(this))
        return LeakingObjectFinder { graph ->
            KeyedWeakReferenceFinder().findLeakingObjectIds(graph) + unwatchedFinder.findLeakingObjectIds(graph)
        }
    }

    private fun HeapGraph.objectInspectors() =
        if (isAndroidHeap()) {
            AndroidObjectInspectors.appDefaults
        } else {
            ObjectInspectors.jdkDefaults
        }

    private companion object {
        fun HeapGraph.leakingObjectFilters(): List<LeakingObjectFilter> =
            if (isAndroidHeap()) {
                AndroidObjectInspectors.appLeakingObjectFilters
            } else {
                ObjectInspectors.jdkLeakingObjectFilters
            }

        const val PERCENT = 100
        const val ANDROID_BUILD_CLASS_NAME = "android.os.Build"
        val STEP_COUNT = OnAnalysisProgressListener.Step.values().size

        // The Android defaults read android.os.Build from the heap, so they only work on Android heaps;
        // JVM heaps (the analyzer tests) use the JDK-only configuration.
        fun HeapGraph.isAndroidHeap(): Boolean = findClassByName(ANDROID_BUILD_CLASS_NAME) != null
    }
}
