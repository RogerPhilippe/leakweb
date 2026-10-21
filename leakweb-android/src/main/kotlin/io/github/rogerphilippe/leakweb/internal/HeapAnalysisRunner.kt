/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.util.Log
import io.github.rogerphilippe.leakweb.analyzer.AnalysisProgressListener
import io.github.rogerphilippe.leakweb.model.Event
import io.github.rogerphilippe.leakweb.model.HeapAnalysis
import io.github.rogerphilippe.leakweb.model.HeapAnalysisFailure
import io.github.rogerphilippe.leakweb.model.HeapAnalysisSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Analyzes heap dumps in [scope] (a dedicated background dispatcher), stores and publishes the results. */
internal class HeapAnalysisRunner(
    private val scope: CoroutineScope,
    private val store: LeakStore,
    private val eventBus: EventBus,
    private val analyze: (HeapDump, AnalysisProgressListener) -> HeapAnalysis,
) {
    fun analyze(heapDump: HeapDump): Job =
        scope.launch {
            val analysis =
                analyze(heapDump) { step, percent -> eventBus.post(Event.AnalysisProgress(step, percent)) }
            val newLeaks = store.save(analysis)
            eventBus.post(Event.AnalysisDone(analysis.id, success = analysis is HeapAnalysisSuccess))
            newLeaks.forEach { eventBus.post(Event.NewLeak(it.signature, it.shortDescription)) }
            Log.i(LEAKWEB_TAG, analysis.logMessage(newLeakCount = newLeaks.size))
        }

    private fun HeapAnalysis.logMessage(newLeakCount: Int): String =
        when (this) {
            is HeapAnalysisSuccess -> {
                "Analysis $id done in ${durationMillis}ms: ${leaks.size} leaks ($newLeakCount new)"
            }

            is HeapAnalysisFailure -> {
                "Analysis $id failed: $errorMessage"
            }
        }
}
