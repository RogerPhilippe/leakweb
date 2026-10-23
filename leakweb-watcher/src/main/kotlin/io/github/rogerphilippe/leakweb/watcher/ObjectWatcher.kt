/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.watcher

import java.lang.ref.ReferenceQueue
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor

/** An object that is still in memory after a GC although it was expected to be garbage collected. */
data class RetainedObject(
    val key: String,
    val description: String,
    val className: String,
    val watchUptimeMillis: Long,
    val retainedUptimeMillis: Long,
)

fun interface OnObjectRetainedListener {
    /** Called on the [ObjectWatcher] check thread. [retainedObjectCount] includes [retainedObject]. */
    fun onObjectRetained(
        retainedObject: RetainedObject,
        retainedObjectCount: Int,
    )
}

/**
 * Watches objects that should become weakly reachable soon (e.g. destroyed activities).
 *
 * [expectWeaklyReachable] creates a [KeyedWeakReference] and schedules a check on
 * [checkRetainedExecutor], which is expected to run it after a delay (~5 s) on a background thread.
 * If the object was not collected by then, the check runs a GC with [gcTrigger]; objects still
 * reachable after that are marked retained and reported to the listeners.
 */
class ObjectWatcher(
    private val clock: Clock,
    private val checkRetainedExecutor: Executor,
    private val gcTrigger: GcTrigger = GcTrigger.Default,
) {
    private val watchedReferences = mutableMapOf<String, KeyedWeakReference>()
    private val queue = ReferenceQueue<Any>()
    private val listeners = CopyOnWriteArraySet<OnObjectRetainedListener>()

    /** Number of objects watched (retained or not) that are still in memory. */
    val watchedObjectCount: Int
        @Synchronized get() {
            removeWeaklyReachableReferences()
            return watchedReferences.size
        }

    val retainedObjectCount: Int
        @Synchronized get() {
            removeWeaklyReachableReferences()
            return watchedReferences.values.count { it.isRetained }
        }

    val retainedObjects: List<RetainedObject>
        @Synchronized get() {
            removeWeaklyReachableReferences()
            return watchedReferences.values.filter { it.isRetained }.mapNotNull { it.toRetainedObject() }
        }

    fun addOnObjectRetainedListener(listener: OnObjectRetainedListener) {
        listeners += listener
    }

    fun removeOnObjectRetainedListener(listener: OnObjectRetainedListener) {
        listeners -= listener
    }

    /** Starts watching [watchedObject]; [description] explains why it should be garbage collected. */
    @Synchronized
    fun expectWeaklyReachable(
        watchedObject: Any,
        description: String,
    ) {
        removeWeaklyReachableReferences()
        val key = UUID.randomUUID().toString()
        watchedReferences[key] = KeyedWeakReference(watchedObject, key, description, clock.uptimeMillis(), queue)
        checkRetainedExecutor.execute { checkRetained(key) }
    }

    /** Stops watching the objects watched before [uptimeMillis], e.g. once a heap dump includes them. */
    @Synchronized
    fun clearObjectsWatchedBefore(uptimeMillis: Long) {
        watchedReferences.values.removeAll { reference ->
            (reference.watchUptimeMillis <= uptimeMillis).also { if (it) reference.clear() }
        }
    }

    @Synchronized
    fun clearAllObjects() {
        watchedReferences.values.forEach { it.clear() }
        watchedReferences.clear()
    }

    private fun checkRetained(key: String) {
        if (!isStillWatched(key)) return
        // The GC blocks for a while: run it without holding the lock.
        gcTrigger.runGc()
        val retained = markRetained(key) ?: return
        listeners.forEach { it.onObjectRetained(retained.first, retained.second) }
    }

    @Synchronized
    private fun isStillWatched(key: String): Boolean {
        removeWeaklyReachableReferences()
        return key in watchedReferences
    }

    @Synchronized
    private fun markRetained(key: String): Pair<RetainedObject, Int>? {
        removeWeaklyReachableReferences()
        val reference = watchedReferences[key] ?: return null
        reference.retainedUptimeMillis = clock.uptimeMillis()
        return reference.toRetainedObject()?.let { it to watchedReferences.values.count { ref -> ref.isRetained } }
    }

    /** Drops the references whose object was garbage collected (enqueued by the VM). */
    private fun removeWeaklyReachableReferences() {
        while (true) {
            val reference = queue.poll() as KeyedWeakReference? ?: return
            watchedReferences.remove(reference.key)
        }
    }

    private fun KeyedWeakReference.toRetainedObject(): RetainedObject? {
        val watchedObject = get() ?: return null
        return RetainedObject(key, description, watchedObject.javaClass.name, watchUptimeMillis, retainedUptimeMillis)
    }
}
