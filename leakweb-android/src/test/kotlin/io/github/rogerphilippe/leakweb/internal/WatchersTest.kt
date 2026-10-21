/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Activity
import android.app.Application
import androidx.fragment.app.Fragment
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher
import io.github.rogerphilippe.leakweb.watcher.RetainedObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchersTest {
    private val pendingChecks = mutableListOf<Runnable>()
    private val retained = mutableListOf<RetainedObject>()
    private val objectWatcher =
        ObjectWatcher(clock = { 0L }, checkRetainedExecutor = { pendingChecks += it }, gcTrigger = {})
            .apply { addOnObjectRetainedListener { retainedObject, _ -> retained += retainedObject } }

    private fun runPendingChecks() = pendingChecks.forEach { it.run() }

    @Test
    fun `destroyed activity is watched`() {
        val activity = Activity()

        ActivityWatcher(objectWatcher).onActivityDestroyed(activity)
        runPendingChecks()

        assertEquals(
            "android.app.Activity received Activity#onDestroy() callback",
            retained.single().description,
        )
    }

    @Test
    fun `other activity callbacks do not watch anything`() {
        val watcher = ActivityWatcher(objectWatcher)
        val activity = Activity()

        watcher.onActivityCreated(activity, null)
        watcher.onActivityStopped(activity)

        assertEquals(0, objectWatcher.watchedObjectCount)
    }

    @Test
    fun `destroyed fragment is watched`() {
        val fragment = Fragment()

        FragmentDestroyedReporter(objectWatcher).onFragmentDestroyed(fragment)
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

        FragmentDestroyedReporter(objectWatcher).onFragmentViewDestroyed(fragment, view)
        runPendingChecks()

        val description = retained.single().description
        assertTrue(
            description,
            description.startsWith("androidx.fragment.app.Fragment received Fragment#onDestroyView()"),
        )
    }

    @Test
    fun `fragment without view is ignored`() {
        FragmentDestroyedReporter(objectWatcher).onFragmentViewDestroyed(Fragment(), view = null)

        assertEquals(0, objectWatcher.watchedObjectCount)
    }

    @Test
    fun `fragment watcher is not installed without AndroidX Fragment`() {
        val installed = installFragmentWatcherIfAvailable(Application(), objectWatcher, classExists = { false })

        assertFalse(installed)
    }

    @Test
    fun `fragment watcher is installed with AndroidX Fragment`() {
        val installed = installFragmentWatcherIfAvailable(Application(), objectWatcher, classExists = { true })

        assertTrue(installed)
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
}
