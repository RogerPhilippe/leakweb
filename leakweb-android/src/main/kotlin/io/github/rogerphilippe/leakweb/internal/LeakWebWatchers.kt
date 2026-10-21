/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Application
import android.os.SystemClock
import android.util.Log
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher
import io.github.rogerphilippe.leakweb.watcher.OnObjectRetainedListener
import io.github.rogerphilippe.leakweb.watcher.RetainedObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal const val LEAKWEB_TAG = "LeakWeb"

/** The object watcher and the lifecycle watchers installed on the application. */
internal class LeakWebWatchers private constructor(
    val objectWatcher: ObjectWatcher,
) {
    companion object {
        /** Delay before checking if a watched object was garbage collected. */
        private const val RETAINED_DELAY_SECONDS = 5L

        fun install(application: Application): LeakWebWatchers {
            // Checks run off the main thread because they trigger a (blocking) GC.
            val checkScheduler =
                Executors.newSingleThreadScheduledExecutor { runnable ->
                    Thread(runnable, "LeakWeb-watcher").apply { isDaemon = true }
                }
            val objectWatcher =
                ObjectWatcher(
                    clock = { SystemClock.uptimeMillis() },
                    checkRetainedExecutor = { checkScheduler.schedule(it, RETAINED_DELAY_SECONDS, TimeUnit.SECONDS) },
                )
            objectWatcher.addOnObjectRetainedListener(RetainedObjectLogger)
            application.registerActivityLifecycleCallbacks(ActivityWatcher(objectWatcher))
            installFragmentWatcherIfAvailable(application, objectWatcher)
            return LeakWebWatchers(objectWatcher)
        }
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
