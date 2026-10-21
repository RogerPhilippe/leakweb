/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Activity

/** The app is visible while at least one activity is started. Callbacks run on the main thread. */
internal class AppVisibilityTracker(
    private val onVisibilityChanged: (visible: Boolean) -> Unit,
) : SimpleActivityLifecycleCallbacks() {
    private var startedActivityCount = 0

    @Volatile
    var isVisible: Boolean = false
        private set

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
        updateVisibility()
    }

    override fun onActivityStopped(activity: Activity) {
        if (startedActivityCount > 0) startedActivityCount--
        updateVisibility()
    }

    private fun updateVisibility() {
        val visible = startedActivityCount > 0
        if (visible != isVisible) {
            isVisible = visible
            onVisibilityChanged(visible)
        }
    }
}
