/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.watcher

/** Monotonic time source; `SystemClock.uptimeMillis()` on Android, injectable in tests. */
fun interface Clock {
    fun uptimeMillis(): Long
}
