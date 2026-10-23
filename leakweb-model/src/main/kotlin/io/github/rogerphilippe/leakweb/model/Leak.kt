/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.model

import kotlinx.serialization.Serializable

/** A group of leak traces that share the same [signature]. */
@Serializable
data class Leak(
    /** Hash of the suspect references of the trace; identifies the same leak across analyses. */
    val signature: String,
    val shortDescription: String,
    val kind: LeakKind,
    /** Explanation of a known library leak, when [kind] is [LeakKind.LIBRARY]. */
    val libraryLeakDescription: String? = null,
    val traces: List<LeakTrace>,
    val totalRetainedHeapByteSize: Int? = null,
)

@Serializable
enum class LeakKind {
    /** Leak caused by the app code. */
    APPLICATION,

    /** Known leak in the Android framework or a third party library. */
    LIBRARY,
}

/** Shortest path from a GC root to a leaking object. */
@Serializable
data class LeakTrace(
    val gcRootType: String,
    val referencePath: List<LeakTraceReference>,
    val leakingObject: LeakTraceObject,
    val retainedHeapByteSize: Int? = null,
    val signature: String,
)

/** One step of the reference path: [originObject] holds a reference named [referenceName]. */
@Serializable
data class LeakTraceReference(
    val originObject: LeakTraceObject,
    val referenceType: ReferenceType,
    val owningClassName: String,
    val referenceName: String,
    val referenceDisplayName: String,
    /** True when this reference is part of the suspect subpath (likely cause of the leak). */
    val isSuspect: Boolean,
)

@Serializable
enum class ReferenceType {
    INSTANCE_FIELD,
    STATIC_FIELD,
    LOCAL,
    ARRAY_ENTRY,
}

@Serializable
data class LeakTraceObject(
    val objectType: ObjectType,
    val className: String,
    val labels: List<String>,
    val leakingStatus: LeakingStatus,
    val leakingStatusReason: String,
    val retainedHeapByteSize: Int? = null,
    val retainedObjectCount: Int? = null,
)

@Serializable
enum class ObjectType {
    CLASS,
    ARRAY,
    INSTANCE,
}

@Serializable
enum class LeakingStatus {
    NOT_LEAKING,
    LEAKING,
    UNKNOWN,
}
