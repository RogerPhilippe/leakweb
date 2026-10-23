/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.github.rogerphilippe.leakweb.WatcherType
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher

internal const val ANDROIDX_FRAGMENT_ACTIVITY = "androidx.fragment.app.FragmentActivity"

/**
 * Watches destroyed fragments ([WatcherType.FRAGMENT]) and the views of fragments whose view was
 * destroyed ([WatcherType.FRAGMENT_VIEW]), as currently enabled in [settings].
 */
internal class FragmentDestroyedReporter(
    private val objectWatcher: ObjectWatcher,
    private val settings: WatcherSettings,
) {
    fun onFragmentViewDestroyed(
        fragment: Any,
        view: Any?,
    ) {
        if (view == null || !settings.isEnabled(WatcherType.FRAGMENT_VIEW)) return
        objectWatcher.expectWeaklyReachable(
            view,
            "${fragment.javaClass.name} received Fragment#onDestroyView() callback " +
                "(references to its views should be cleared to prevent leaks)",
        )
    }

    fun onFragmentDestroyed(fragment: Any) {
        if (!settings.isEnabled(WatcherType.FRAGMENT)) return
        objectWatcher.expectWeaklyReachable(
            fragment,
            "${fragment.javaClass.name} received Fragment#onDestroy() callback",
        )
    }
}

/**
 * Registers fragment callbacks on each AndroidX [FragmentActivity], including child fragments.
 * [viewModelWatcher] is null when AndroidX Lifecycle ViewModel is not available.
 */
internal class AndroidXFragmentWatcher(
    reporter: FragmentDestroyedReporter,
    viewModelWatcher: ViewModelWatcher?,
) : SimpleActivityLifecycleCallbacks() {
    private val fragmentCallbacks =
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentCreated(
                fm: FragmentManager,
                f: Fragment,
                savedInstanceState: Bundle?,
            ) {
                viewModelWatcher?.install(f)
            }

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
