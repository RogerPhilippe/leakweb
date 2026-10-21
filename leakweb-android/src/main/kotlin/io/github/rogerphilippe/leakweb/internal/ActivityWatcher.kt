/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher

/** Watches every activity after `onDestroy()`. */
internal class ActivityWatcher(
    private val objectWatcher: ObjectWatcher,
) : SimpleActivityLifecycleCallbacks() {
    override fun onActivityDestroyed(activity: Activity) {
        objectWatcher.expectWeaklyReachable(
            activity,
            "${activity.javaClass.name} received Activity#onDestroy() callback",
        )
    }
}

/** [Application.ActivityLifecycleCallbacks] with empty implementations, to override only what is needed. */
internal abstract class SimpleActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
