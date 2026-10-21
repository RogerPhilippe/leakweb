/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.model

import kotlinx.serialization.Serializable

/** One leak (all occurrences with the same signature across analyses), for the leak list. */
@Serializable
data class LeakSummary(
    val signature: String,
    val shortDescription: String,
    val kind: LeakKind,
    /** Class of the leaking object in the most recent occurrence. */
    val leakingClassName: String,
    /** Number of analyses in which this leak was found. */
    val occurrenceCount: Int,
    val retainedHeapByteSize: Int? = null,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
)

/** A leak with the most recent traces and the analyses where it was found. */
@Serializable
data class LeakDetails(
    val summary: LeakSummary,
    /** The leak as found in the most recent analysis. */
    val latest: Leak,
    /** Ids of the analyses where this leak was found, most recent first. */
    val analysisIds: List<String>,
)

/** Envelope of an analysis stored on disk; [schemaVersion] allows migrating old files. */
@Serializable
data class StoredAnalysis(
    val schemaVersion: Int = SCHEMA_VERSION,
    val analysis: HeapAnalysis,
)
