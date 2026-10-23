/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.watcher

/** Asks the VM to run a garbage collection, so that weakly reachable objects get enqueued. */
fun interface GcTrigger {
    /** Blocks the calling thread; never call it from the main thread. */
    fun runGc()

    /**
     * `System.gc()` alone is not guaranteed to collect anything. `Runtime.gc()` is more reliable on
     * Android, and the short wait gives the reference queue daemon time to enqueue the references.
     */
    object Default : GcTrigger {
        private const val ENQUEUE_WAIT_MILLIS = 100L

        // Triggering the GC is the whole point of this class.
        @Suppress("ExplicitGarbageCollectionCall")
        override fun runGc() {
            Runtime.getRuntime().gc()
            Thread.sleep(ENQUEUE_WAIT_MILLIS)
            @Suppress("DEPRECATION")
            System.runFinalization()
        }
    }
}
