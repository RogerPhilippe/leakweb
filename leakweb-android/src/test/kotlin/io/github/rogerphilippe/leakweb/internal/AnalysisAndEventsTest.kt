/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Activity
import io.github.rogerphilippe.leakweb.model.Event
import io.github.rogerphilippe.leakweb.model.HeapAnalysis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisAndEventsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `event bus delivers posted events to collectors`() =
        runTest {
            val eventBus = EventBus()
            val received = mutableListOf<Event>()
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) { eventBus.events.collect { received += it } }

            eventBus.post(Event.HeapDumpStarted(3))

            assertEquals(listOf<Event>(Event.HeapDumpStarted(3)), received)
        }

    @Test
    fun `analysis runner stores the analysis and publishes progress, done and new leak events`() =
        runTest {
            val store = LeakStore(tempFolder.root)
            store.save(testSuccess("old", 1_000L, testLeak("known")))
            val eventBus = EventBus()
            val received = mutableListOf<Event>()
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) { eventBus.events.collect { received += it } }
            val analysis: HeapAnalysis = testSuccess("new", 2_000L, testLeak("known"), testLeak("fresh"))
            val runner =
                HeapAnalysisRunner(this, store, eventBus) { _, progressListener ->
                    progressListener.onProgress("PARSING_HEAP_DUMP", 10)
                    analysis
                }

            runner.analyze(HeapDump("new", File("new.hprof"), 0L)).join()

            assertEquals(analysis, store.analysis("new"))
            assertEquals(
                listOf(
                    Event.AnalysisProgress("PARSING_HEAP_DUMP", 10),
                    Event.AnalysisDone("new", success = true),
                    Event.NewLeak("fresh", "com.example.Leaky leaked"),
                ),
                received,
            )
        }

    @Test
    fun `failed analysis is stored and reported as not successful`() =
        runTest {
            val store = LeakStore(tempFolder.root)
            val eventBus = EventBus()
            val received = mutableListOf<Event>()
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) { eventBus.events.collect { received += it } }
            val runner = HeapAnalysisRunner(this, store, eventBus) { _, _ -> testFailure("f1", 1_000L) }

            runner.analyze(HeapDump("f1", File("f1.hprof"), 0L)).join()

            assertEquals(listOf<Event>(Event.AnalysisDone("f1", success = false)), received)
            assertEquals(listOf("f1"), store.analyses().map { it.id })
        }

    @Test
    fun `app is visible while at least one activity is started`() {
        val changes = mutableListOf<Boolean>()
        val tracker = AppVisibilityTracker { changes += it }
        val first = Activity()
        val second = Activity()

        tracker.onActivityStarted(first)
        tracker.onActivityStarted(second)
        tracker.onActivityStopped(first)
        assertTrue(tracker.isVisible)
        tracker.onActivityStopped(second)

        assertFalse(tracker.isVisible)
        assertEquals(listOf(true, false), changes)
    }
}
