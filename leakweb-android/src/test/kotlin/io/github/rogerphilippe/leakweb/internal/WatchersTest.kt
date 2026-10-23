/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Activity
import android.app.Application
import androidx.fragment.app.Fragment
import io.github.rogerphilippe.leakweb.LeakWebConfig
import io.github.rogerphilippe.leakweb.WatcherType
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher
import io.github.rogerphilippe.leakweb.watcher.RetainedObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchersTest {
    private val pendingChecks = mutableListOf<Runnable>()
    private val retained = mutableListOf<RetainedObject>()
    private val objectWatcher =
        ObjectWatcher(clock = { 0L }, checkRetainedExecutor = { pendingChecks += it }, gcTrigger = {})
            .apply { addOnObjectRetainedListener { retainedObject, _ -> retained += retainedObject } }
    private val settings = WatcherSettings(LeakWebConfig())

    private val registered = mutableListOf<Application.ActivityLifecycleCallbacks>()
    private var serviceHookInstalls = 0

    private fun installer(existingClasses: Set<String> = ANDROIDX_CLASSES) =
        WatcherInstaller(
            objectWatcher = objectWatcher,
            settings = settings,
            registerCallbacks = { registered += it },
            classExists = { it in existingClasses },
            installServiceHooks = {
                serviceHookInstalls++
                true
            },
        )

    private fun runPendingChecks() = pendingChecks.forEach { it.run() }

    @Test
    fun `destroyed activity is watched`() {
        val activity = Activity()

        ActivityWatcher(objectWatcher, settings).onActivityDestroyed(activity)
        runPendingChecks()

        assertEquals(
            "android.app.Activity received Activity#onDestroy() callback",
            retained.single().description,
        )
    }

    @Test
    fun `other activity callbacks do not watch anything`() {
        val watcher = ActivityWatcher(objectWatcher, settings)
        val activity = Activity()

        watcher.onActivityCreated(activity, null)
        watcher.onActivityStopped(activity)

        assertEquals(0, objectWatcher.watchedObjectCount)
    }

    @Test
    fun `destroyed fragment is watched`() {
        val fragment = Fragment()

        FragmentDestroyedReporter(objectWatcher, settings).onFragmentDestroyed(fragment)
        runPendingChecks()

        assertEquals(
            "androidx.fragment.app.Fragment received Fragment#onDestroy() callback",
            retained.single().description,
        )
    }

    @Test
    fun `view of a fragment whose view was destroyed is watched`() {
        val fragment = Fragment()
        val view = Any()

        FragmentDestroyedReporter(objectWatcher, settings).onFragmentViewDestroyed(fragment, view)
        runPendingChecks()

        val description = retained.single().description
        assertTrue(
            description,
            description.startsWith("androidx.fragment.app.Fragment received Fragment#onDestroyView()"),
        )
    }

    @Test
    fun `fragment without view is ignored`() {
        FragmentDestroyedReporter(objectWatcher, settings).onFragmentViewDestroyed(Fragment(), view = null)

        assertEquals(0, objectWatcher.watchedObjectCount)
    }

    @Test
    fun `fragments and their views are watched separately`() {
        settings.update(LeakWebConfig(watchers = setOf(WatcherType.FRAGMENT_VIEW)))
        val reporter = FragmentDestroyedReporter(objectWatcher, settings)
        val fragment = Fragment()

        reporter.onFragmentDestroyed(fragment)
        reporter.onFragmentViewDestroyed(fragment, Any())
        runPendingChecks()

        assertTrue(retained.single().description.contains("Fragment#onDestroyView()"))
    }

    @Test
    fun `default watchers install every hook once`() {
        val installer = installer()

        installer.apply(LeakWebConfig())
        installer.apply(LeakWebConfig())

        assertEquals(
            setOf(ActivityWatcher::class, ActivityViewModelWatcher::class, AndroidXFragmentWatcher::class),
            registered.map { it::class }.toSet(),
        )
        assertEquals(3, registered.size)
        assertEquals(1, serviceHookInstalls)
    }

    @Test
    fun `fragment and ViewModel watchers are not installed without the AndroidX classes`() {
        installer(existingClasses = emptySet()).apply(LeakWebConfig())

        assertEquals(listOf(ActivityWatcher::class), registered.map { it::class })
    }

    @Test
    fun `a watcher enabled by a later start is installed then`() {
        val installer = installer()
        installer.apply(LeakWebConfig(watchers = emptySet()))
        assertTrue(registered.isEmpty())

        installer.apply(LeakWebConfig(watchers = setOf(WatcherType.ACTIVITY), retainedThreshold = 2))

        assertEquals(listOf(ActivityWatcher::class), registered.map { it::class })
        assertEquals(0, serviceHookInstalls)
        assertEquals(2, settings.retainedThreshold)
    }

    @Test
    fun `a watcher disabled by a later start ignores the events`() {
        val installer = installer()
        installer.apply(LeakWebConfig())
        installer.apply(LeakWebConfig(watchers = setOf(WatcherType.FRAGMENT)))

        registered.filterIsInstance<ActivityWatcher>().single().onActivityDestroyed(Activity())

        assertEquals(0, objectWatcher.watchedObjectCount)
    }

    @Test
    fun `retained object log message`() {
        val message =
            RetainedObjectLogger.retainedObjectMessage(
                RetainedObject("key", "destroyed", "com.example.MainActivity", 1_000L, 6_000L),
                retainedObjectCount = 2,
            )

        assertEquals(
            "Retained object: com.example.MainActivity (destroyed), still in memory 5000ms after being watched. " +
                "Retained objects: 2",
            message,
        )
    }

    private companion object {
        val ANDROIDX_CLASSES = setOf(ANDROIDX_FRAGMENT_ACTIVITY, ANDROIDX_VIEW_MODEL_STORE_OWNER)
    }
}
