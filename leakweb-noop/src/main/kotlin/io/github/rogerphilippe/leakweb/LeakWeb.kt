/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb

/** No-op version of LeakWeb for release builds: same API as `leakweb-android`, does nothing. */
object LeakWeb {
    @Suppress("UNUSED_PARAMETER", "UnusedParameter")
    fun watch(
        obj: Any,
        description: String,
    ) = Unit
}
