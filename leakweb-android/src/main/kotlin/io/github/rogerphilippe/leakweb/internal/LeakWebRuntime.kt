/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import io.github.rogerphilippe.leakweb.analyzer.HprofAnalyzer
import io.github.rogerphilippe.leakweb.model.Event
import io.github.rogerphilippe.leakweb.watcher.Clock
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher
import io.github.rogerphilippe.leakweb.watcher.OnObjectRetainedListener
import io.github.rogerphilippe.leakweb.watcher.RetainedObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal const val LEAKWEB_TAG = "LeakWeb"

/** Everything LeakWeb runs in the app process: watchers, heap dumps, analysis, store and events. */
internal class LeakWebRuntime private constructor(
    val objectWatcher: ObjectWatcher,
    val store: LeakStore,
    val eventBus: EventBus,
) {
    companion object {
        /** Delay before checking if a watched object was garbage collected. */
        private const val RETAINED_DELAY_SECONDS = 5L

        fun install(application: Application): LeakWebRuntime {
            val clock = Clock { SystemClock.uptimeMillis() }
            // Checks run off the main thread because they trigger a (blocking) GC.
            val watcherThread = singleThreadScheduler("LeakWeb-watcher")
            val objectWatcher =
                ObjectWatcher(
                    clock = clock,
                    checkRetainedExecutor = { watcherThread.schedule(it, RETAINED_DELAY_SECONDS, TimeUnit.SECONDS) },
                )
            val store = LeakStore(File(application.filesDir, "leakweb"))
            val eventBus = EventBus()
            val analysisScope =
                CoroutineScope(
                    SupervisorJob() + Executors.newSingleThreadExecutor(::analysisThread).asCoroutineDispatcher(),
                )
            val analysisRunner =
                HeapAnalysisRunner(analysisScope, store, eventBus) { heapDump, progressListener ->
                    HprofAnalyzer().analyze(heapDump.file, heapDump.id, heapDump.uptimeMillis, progressListener)
                }

            lateinit var visibilityTracker: AppVisibilityTracker
            val dumpThread = singleThreadScheduler("LeakWeb-heap-dump")
            val mainHandler = Handler(Looper.getMainLooper())
            val heapDumpTrigger =
                HeapDumpTrigger(
                    objectWatcher = objectWatcher,
                    clock = clock,
                    isAppVisible = { visibilityTracker.isVisible },
                    executor = {
                        delayMillis,
                        command,
                        ->
                        dumpThread.schedule(command, delayMillis, TimeUnit.MILLISECONDS)
                    },
                    heapDumper = AndroidHeapDumper,
                    store = store,
                    eventBus = eventBus,
                    onHeapDumped = { analysisRunner.analyze(it) },
                    notifyUser = { message ->
                        mainHandler.post { Toast.makeText(application, message, Toast.LENGTH_LONG).show() }
                    },
                )
            visibilityTracker = AppVisibilityTracker { heapDumpTrigger.onAppVisibilityChanged() }

            objectWatcher.addOnObjectRetainedListener(RetainedObjectLogger)
            objectWatcher.addOnObjectRetainedListener { retainedObject, retainedObjectCount ->
                eventBus.post(
                    Event.ObjectRetained(
                        key = retainedObject.key,
                        description = retainedObject.description,
                        className = retainedObject.className,
                        retainedObjectCount = retainedObjectCount,
                    ),
                )
                heapDumpTrigger.onObjectRetained()
            }
            application.registerActivityLifecycleCallbacks(visibilityTracker)
            application.registerActivityLifecycleCallbacks(ActivityWatcher(objectWatcher))
            installFragmentWatcherIfAvailable(application, objectWatcher)
            return LeakWebRuntime(objectWatcher, store, eventBus)
        }

        private fun singleThreadScheduler(name: String): ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, name).apply { isDaemon = true } }

        private fun analysisThread(runnable: Runnable) = Thread(runnable, "LeakWeb-analysis").apply { isDaemon = true }
    }
}

/** Logs each retained object to logcat. */
internal object RetainedObjectLogger : OnObjectRetainedListener {
    override fun onObjectRetained(
        retainedObject: RetainedObject,
        retainedObjectCount: Int,
    ) {
        Log.i(LEAKWEB_TAG, retainedObjectMessage(retainedObject, retainedObjectCount))
    }

    fun retainedObjectMessage(
        retainedObject: RetainedObject,
        retainedObjectCount: Int,
    ): String {
        val watchedMillis = retainedObject.retainedUptimeMillis - retainedObject.watchUptimeMillis
        return "Retained object: ${retainedObject.className} (${retainedObject.description}), " +
            "still in memory ${watchedMillis}ms after being watched. Retained objects: $retainedObjectCount"
    }
}
