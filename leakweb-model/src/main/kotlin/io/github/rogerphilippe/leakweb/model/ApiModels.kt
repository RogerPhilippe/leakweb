/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.model

import kotlinx.serialization.Serializable

/** Response of `GET /api/status`. */
@Serializable
data class ServerStatus(
    val libraryVersion: String,
    val appPackage: String,
    val processName: String,
    val port: Int,
    val watchedObjectCount: Int,
    val retainedObjectCount: Int,
    /** Memory sampled every second for the live chart; turned on and off from the web UI. */
    val liveMemoryEnabled: Boolean = false,
)

/** An object retained in memory that was not analyzed yet (`GET /api/retained`). */
@Serializable
data class RetainedObjectInfo(
    val key: String,
    val description: String,
    val className: String,
    val watchUptimeMillis: Long,
    val retainedUptimeMillis: Long,
)

/** One analysis in the history (`GET /api/analyses`), without the leak traces. */
@Serializable
data class AnalysisSummary(
    val id: String,
    val createdAtMillis: Long,
    val success: Boolean,
    val durationMillis: Long,
    val heapDumpSizeBytes: Long,
    /** False once the heap dump was pruned or deleted; the analysis is still listed. */
    val heapDumpAvailable: Boolean,
    val leakCount: Int,
    val errorMessage: String? = null,
)

/** Response of the action routes (`POST /api/heapdump`, `POST /api/gc`, `DELETE /api/analyses/{id}`). */
@Serializable
data class CommandResult(
    val accepted: Boolean,
    val message: String,
)
