/*
 * Copyright (c) 2026 Roger Philippe
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
    /** Proportional set size; sampled less often because it is expensive to read. */
    val pssKb: Long? = null,
    val watchedObjectCount: Int,
    val retainedObjectCount: Int,
)
