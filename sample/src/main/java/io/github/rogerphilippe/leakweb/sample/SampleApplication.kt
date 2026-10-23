/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.sample

import android.app.Application
import io.github.rogerphilippe.leakweb.LeakWeb
import io.github.rogerphilippe.leakweb.LeakWebConfig

/** Starts LeakWeb manually (see src/debug/AndroidManifest.xml); a no-op in release builds. */
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LeakWeb.start(this, LeakWebConfig(port = SAMPLE_PORT, persistToken = true))
    }

    private companion object {
        const val SAMPLE_PORT = 9090
    }
}
