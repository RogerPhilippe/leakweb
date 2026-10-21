/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.watcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectWatcherTest {
    private var now = 1_000L
    private val pendingChecks = mutableListOf<Runnable>()
    private var gcCount = 0
    private val retained = mutableListOf<Pair<RetainedObject, Int>>()

    // Fake GC trigger that runs a real GC, so that unreachable objects are actually collected.
    private val watcher =
        ObjectWatcher(
            clock = { now },
            checkRetainedExecutor = { pendingChecks += it },
            gcTrigger = {
                gcCount++
                GcTrigger.Default.runGc()
            },
        ).apply { addOnObjectRetainedListener { retainedObject, count -> retained += retainedObject to count } }

    private class Watched

    // Keeps watched objects strongly reachable: an unused local variable could be collected by the GC.
    private val strongReferences = mutableListOf<Any>()

    private fun reachable() = Watched().also { strongReferences += it }

    private fun runPendingChecks() {
        val checks = pendingChecks.toList()
        pendingChecks.clear()
        checks.forEach { it.run() }
    }

    // Separate function so that no local variable keeps the object reachable.
    private fun watchUnreachableObject() {
        watcher.expectWeaklyReachable(Watched(), "unreachable")
    }

    @Test
    fun `check is scheduled on the executor, not run immediately`() {
        val watched = reachable()

        watcher.expectWeaklyReachable(watched, "destroyed")

        assertEquals(1, pendingChecks.size)
        assertEquals(1, watcher.watchedObjectCount)
        assertEquals(0, watcher.retainedObjectCount)
        assertEquals(0, gcCount)
    }

    @Test
    fun `object still reachable after GC is retained and reported`() {
        val watched = reachable()
        watcher.expectWeaklyReachable(watched, "destroyed")
        now = 6_000L

        runPendingChecks()

        assertEquals(1, gcCount)
        assertEquals(1, watcher.retainedObjectCount)
        val (retainedObject, count) = retained.single()
        assertEquals(1, count)
        assertEquals("destroyed", retainedObject.description)
        assertEquals(Watched::class.java.name, retainedObject.className)
        assertEquals(1_000L, retainedObject.watchUptimeMillis)
        assertEquals(6_000L, retainedObject.retainedUptimeMillis)
        assertEquals(listOf(retainedObject), watcher.retainedObjects)
    }

    @Test
    fun `object collected by the GC is not retained`() {
        watchUnreachableObject()

        runPendingChecks()

        assertTrue(retained.isEmpty())
        assertEquals(0, watcher.watchedObjectCount)
    }

    @Test
    fun `retained count includes previously retained objects`() {
        val first = reachable()
        val second = reachable()
        watcher.expectWeaklyReachable(first, "first")
        watcher.expectWeaklyReachable(second, "second")

        runPendingChecks()

        assertEquals(listOf(1, 2), retained.map { it.second })
    }

    @Test
    fun `clear objects watched before a time keeps the newer ones`() {
        val old = reachable()
        val recent = reachable()
        watcher.expectWeaklyReachable(old, "old")
        now = 2_000L
        watcher.expectWeaklyReachable(recent, "recent")

        watcher.clearObjectsWatchedBefore(1_500L)

        assertEquals(1, watcher.watchedObjectCount)
        runPendingChecks()
        assertEquals(listOf("recent"), retained.map { it.first.description })
    }

    @Test
    fun `cleared objects are not checked`() {
        val watched = reachable()
        watcher.expectWeaklyReachable(watched, "destroyed")

        watcher.clearAllObjects()
        runPendingChecks()

        assertEquals(0, gcCount)
        assertTrue(retained.isEmpty())
    }

    @Test
    fun `removed listener is not notified`() {
        val calls = mutableListOf<RetainedObject>()
        val listener = OnObjectRetainedListener { retainedObject, _ -> calls += retainedObject }
        watcher.addOnObjectRetainedListener(listener)
        watcher.removeOnObjectRetainedListener(listener)
        val watched = reachable()
        watcher.expectWeaklyReachable(watched, "destroyed")

        runPendingChecks()

        assertTrue(calls.isEmpty())
    }
}
