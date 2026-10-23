/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.analyzer

import io.github.rogerphilippe.leakweb.watcher.KeyedWeakReference
import shark.HeapGraph
import shark.HeapObject.HeapInstance
import shark.LeakingObjectFinder
import shark.ObjectInspector
import shark.ObjectReporter

/** A [KeyedWeakReference] read from the heap dump. */
internal class WatchedReference(
    /** Object id of the watched object, or null if it was garbage collected. */
    val referentId: Long?,
    val key: String,
    val description: String,
    val watchUptimeMillis: Long,
    val retainedUptimeMillis: Long,
) {
    val isRetained: Boolean
        get() = retainedUptimeMillis != KeyedWeakReference.NOT_RETAINED
}

private val KEYED_WEAK_REFERENCE_CLASS_NAME: String = KeyedWeakReference::class.java.name
private const val WATCHED_REFERENCES_CONTEXT_KEY = "leakweb.watchedReferences"

/** All [KeyedWeakReference] instances in the heap, read once and cached in the graph context. */
internal fun HeapGraph.findWatchedReferences(): List<WatchedReference> =
    context.getOrPut(WATCHED_REFERENCES_CONTEXT_KEY) {
        findClassByName(KEYED_WEAK_REFERENCE_CLASS_NAME)
            ?.instances
            ?.map { it.toWatchedReference() }
            ?.toList()
            .orEmpty()
    }

private fun HeapInstance.toWatchedReference(): WatchedReference {
    fun field(name: String) = checkNotNull(this[KEYED_WEAK_REFERENCE_CLASS_NAME, name]) { "Missing field $name" }.value
    return WatchedReference(
        referentId = this["java.lang.ref.Reference", "referent"]?.value?.asObjectId?.takeIf { it != 0L },
        key = checkNotNull(field("key").readAsJavaString()),
        description = checkNotNull(field("description").readAsJavaString()),
        watchUptimeMillis = checkNotNull(field("watchUptimeMillis").asLong),
        retainedUptimeMillis = checkNotNull(field("retainedUptimeMillis").asLong),
    )
}

/** Reports the objects of retained [KeyedWeakReference]s that are still in memory. */
internal class KeyedWeakReferenceFinder : LeakingObjectFinder {
    override fun findLeakingObjectIds(graph: HeapGraph): Set<Long> =
        graph
            .findWatchedReferences()
            .filter { it.isRetained }
            .mapNotNull { it.referentId }
            .toSet()
}

/**
 * Marks watched objects as LEAKING. Shark's own inspector only knows LeakCanary's
 * `KeyedWeakReference`, so without this the leaking node would show as UNKNOWN.
 */
internal class KeyedWeakReferenceInspector(
    private val heapDumpUptimeMillis: Long,
) : ObjectInspector {
    override fun inspect(reporter: ObjectReporter) {
        val objectId = reporter.heapObject.objectId
        reporter.heapObject.graph
            .findWatchedReferences()
            .filter { it.referentId == objectId }
            .forEach { reference ->
                reporter.leakingReasons += "LeakWeb was watching this because ${reference.description}"
                reporter.labels += "key = ${reference.key}"
                reporter.labels += "watchDurationMillis = ${heapDumpUptimeMillis - reference.watchUptimeMillis}"
                if (reference.isRetained) {
                    reporter.labels +=
                        "retainedDurationMillis = ${heapDumpUptimeMillis - reference.retainedUptimeMillis}"
                }
            }
    }
}
