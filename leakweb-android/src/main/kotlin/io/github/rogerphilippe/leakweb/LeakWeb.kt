/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb

import android.app.Application
import android.util.Log
import io.github.rogerphilippe.leakweb.internal.LEAKWEB_TAG
import io.github.rogerphilippe.leakweb.internal.LeakWebRuntime
import io.github.rogerphilippe.leakweb.internal.RunningServer
import io.github.rogerphilippe.leakweb.internal.isMainProcess
import io.github.rogerphilippe.leakweb.internal.serverSettings
import java.io.IOException

/**
 * Entry point of LeakWeb. `leakweb-noop` has the same API with an empty implementation.
 *
 * LeakWeb starts by itself with the default [LeakWebConfig]. To start it manually (other port,
 * fixed token...), remove the automatic start from the app manifest and call [start]; see the README.
 */
object LeakWeb {
    // The library's only global state: what was started in the main process.
    @Volatile
    private var runtime: LeakWebRuntime? = null

    @Volatile
    private var server: RunningServer? = null

    /** The running server, or [LeakWebSession.NOT_RUNNING]. */
    val session: LeakWebSession
        get() = server?.session ?: LeakWebSession.NOT_RUNNING

    /**
     * Starts watching for leaks and the web server. Calling it again applies the watcher settings of
     * [config] ([LeakWebConfig.watchers], [LeakWebConfig.retainedThreshold]) and restarts the server only
     * if one of its settings changed; otherwise it returns the current session.
     *
     * Does nothing outside the main process. Never throws: if no port can be bound, it logs the error
     * and returns [LeakWebSession.NOT_RUNNING].
     */
    @Synchronized
    fun start(
        application: Application,
        config: LeakWebConfig = LeakWebConfig(),
    ): LeakWebSession {
        if (!application.isMainProcess()) {
            Log.d(LEAKWEB_TAG, "Not the main process, LeakWeb is not started")
            return LeakWebSession.NOT_RUNNING
        }
        val installed =
            runtime?.also { it.applyConfig(config) }
                ?: LeakWebRuntime.install(application, config).also { runtime = it }
        Log.i(LEAKWEB_TAG, "LeakWeb watching: ${config.watchers.joinToString().ifEmpty { "nothing" }}")
        server?.let { running ->
            if (running.config.serverSettings() == config.serverSettings()) return running.session
            running.stop()
            server = null
        }
        return try {
            RunningServer.start(application, installed, config).also { server = it }.session
        } catch (error: IOException) {
            Log.e(LEAKWEB_TAG, "LeakWeb server not started: no free port from ${config.port}", error)
            LeakWebSession.NOT_RUNNING
        }
    }

    /** Stops the web server. Leaks are still detected and stored; [start] serves them again. */
    @Synchronized
    fun stop() {
        server?.stop()
        server = null
    }

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
            Log.w(LEAKWEB_TAG, "LeakWeb.watch() ignored: LeakWeb was not started in this process")
            return
        }
        installed.objectWatcher.expectWeaklyReachable(obj, description)
    }
}
