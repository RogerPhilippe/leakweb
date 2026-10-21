/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.watcher

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/**
 * Weak reference to an object that should become unreachable soon (e.g. a destroyed Activity).
 *
 * The heap analyzer looks for instances of this class in the heap dump, so its fully qualified
 * name and field names are part of the contract with `leakweb-analyzer`.
 */
class KeyedWeakReference(
    referent: Any,
    /** Unique key used to find this reference again (UUID). */
    val key: String,
    /** Why the object is watched, shown as the leaking reason in the leak trace. */
    val description: String,
    /** Uptime when the object started being watched. */
    val watchUptimeMillis: Long,
    referenceQueue: ReferenceQueue<Any>,
) : WeakReference<Any>(referent, referenceQueue) {
    /** Uptime when the object was found retained, or [NOT_RETAINED] while it is only watched. */
    @Volatile
    var retainedUptimeMillis: Long = NOT_RETAINED

    val isRetained: Boolean
        get() = retainedUptimeMillis != NOT_RETAINED

    companion object {
        const val NOT_RETAINED = -1L
    }
}
