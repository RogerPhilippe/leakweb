/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher

private const val ANDROIDX_FRAGMENT_ACTIVITY = "androidx.fragment.app.FragmentActivity"

/**
 * Installs [AndroidXFragmentWatcher] only if the app has AndroidX Fragment on its classpath:
 * the library depends on it as `compileOnly`, so the classes may be missing at runtime.
 */
internal fun installFragmentWatcherIfAvailable(
    application: Application,
    objectWatcher: ObjectWatcher,
    classExists: (String) -> Boolean = ::classExists,
): Boolean {
    if (!classExists(ANDROIDX_FRAGMENT_ACTIVITY)) return false
    application.registerActivityLifecycleCallbacks(AndroidXFragmentWatcher(FragmentDestroyedReporter(objectWatcher)))
    return true
}

private fun classExists(className: String): Boolean =
    try {
        Class.forName(className)
        true
    } catch (_: ClassNotFoundException) {
        false
    }

/** Watches destroyed fragments and the views of fragments whose view was destroyed. */
internal class FragmentDestroyedReporter(
    private val objectWatcher: ObjectWatcher,
) {
    fun onFragmentViewDestroyed(
        fragment: Any,
        view: Any?,
    ) {
        if (view == null) return
        objectWatcher.expectWeaklyReachable(
            view,
            "${fragment.javaClass.name} received Fragment#onDestroyView() callback " +
                "(references to its views should be cleared to prevent leaks)",
        )
    }

    fun onFragmentDestroyed(fragment: Any) {
        objectWatcher.expectWeaklyReachable(
            fragment,
            "${fragment.javaClass.name} received Fragment#onDestroy() callback",
        )
    }
}

/** Registers fragment callbacks on each AndroidX [FragmentActivity], including child fragments. */
internal class AndroidXFragmentWatcher(
    reporter: FragmentDestroyedReporter,
) : SimpleActivityLifecycleCallbacks() {
    private val fragmentCallbacks =
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewDestroyed(
                fm: FragmentManager,
                f: Fragment,
            ) {
                reporter.onFragmentViewDestroyed(f, f.view)
            }

            override fun onFragmentDestroyed(
                fm: FragmentManager,
                f: Fragment,
            ) {
                reporter.onFragmentDestroyed(f)
            }
        }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        if (activity is FragmentActivity) {
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentCallbacks, true)
        }
    }
}
