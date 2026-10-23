/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Result of analyzing one heap dump. */
@Serializable
sealed interface HeapAnalysis {
    val id: String

    /** Wall clock time (epoch millis) when the analysis finished. */
    val createdAtMillis: Long
    val heapDumpFileName: String
    val heapDumpSizeBytes: Long
    val durationMillis: Long
}

@Serializable
@SerialName("success")
data class HeapAnalysisSuccess(
    override val id: String,
    override val createdAtMillis: Long,
    override val heapDumpFileName: String,
    override val heapDumpSizeBytes: Long,
    override val durationMillis: Long,
    val leaks: List<Leak>,
) : HeapAnalysis

@Serializable
@SerialName("failure")
data class HeapAnalysisFailure(
    override val id: String,
    override val createdAtMillis: Long,
    override val heapDumpFileName: String,
    override val heapDumpSizeBytes: Long,
    override val durationMillis: Long,
    val errorMessage: String,
    val stackTrace: String,
) : HeapAnalysis
