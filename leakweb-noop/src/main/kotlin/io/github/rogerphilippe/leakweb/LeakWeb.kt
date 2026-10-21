/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb

import android.app.Application

/** No-op version of LeakWeb for release builds: same API as `leakweb-android`, does nothing. */
@Suppress("UNUSED_PARAMETER", "UnusedParameter")
object LeakWeb {
    val session: LeakWebSession
        get() = LeakWebSession.NOT_RUNNING

    fun start(
        application: Application,
        config: LeakWebConfig = LeakWebConfig(),
    ): LeakWebSession = LeakWebSession.NOT_RUNNING

    fun stop() = Unit

    fun watch(
        obj: Any,
        description: String,
    ) = Unit
}
