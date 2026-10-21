/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb

import android.app.Application
import android.util.Log
import io.github.rogerphilippe.leakweb.internal.LEAKWEB_TAG
import io.github.rogerphilippe.leakweb.internal.LeakWebRuntime
import io.github.rogerphilippe.leakweb.internal.isMainProcess

/** Entry point of LeakWeb. `leakweb-noop` has the same API with an empty implementation. */
object LeakWeb {
    // The library's only global state: what was installed in the main process.
    @Volatile
    private var runtime: LeakWebRuntime? = null

    /**
     * Watches an object that should be garbage collected soon, for example an object whose
     * lifecycle ended. If it is still in memory ~5 seconds later (after a GC), it is reported as retained.
     *
     * @param description why the object should be garbage collected, shown in the leak trace.
     */
    fun watch(
        obj: Any,
        description: String,
    ) {
        val installed = runtime
        if (installed == null) {
            Log.w(LEAKWEB_TAG, "LeakWeb.watch() ignored: LeakWeb is not installed in this process")
            return
        }
        installed.objectWatcher.expectWeaklyReachable(obj, description)
    }

    /** Installs the watchers; does nothing outside the main process or if already installed. */
    @Synchronized
    internal fun install(application: Application) {
        if (runtime != null) return
        if (!application.isMainProcess()) {
            Log.d(LEAKWEB_TAG, "Not the main process, LeakWeb is not installed")
            return
        }
        runtime = LeakWebRuntime.install(application)
        Log.i(LEAKWEB_TAG, "LeakWeb installed, watching destroyed activities and fragments")
    }
}
