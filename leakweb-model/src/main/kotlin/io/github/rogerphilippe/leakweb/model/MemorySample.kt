/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.model

import kotlinx.serialization.Serializable

/** Snapshot of the app memory usage at [timestampMillis] (epoch millis). */
@Serializable
data class MemorySample(
    val timestampMillis: Long,
    val javaHeapUsedBytes: Long,
    val javaHeapMaxBytes: Long,
    val nativeHeapAllocatedBytes: Long,
    /**
     * Proportional set size, null until first read. It is expensive to read, so it is refreshed less
     * often than the other values and samples in between repeat the last value.
     */
    val pssKb: Long? = null,
    val watchedObjectCount: Int,
    val retainedObjectCount: Int,
)
