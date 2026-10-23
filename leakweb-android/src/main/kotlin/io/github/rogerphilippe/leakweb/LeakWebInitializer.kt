/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb

import android.app.Application
import android.content.Context
import androidx.startup.Initializer

/** Starts LeakWeb automatically when the app starts (androidx.startup). */
class LeakWebInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        LeakWeb.start(context.applicationContext as Application)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
