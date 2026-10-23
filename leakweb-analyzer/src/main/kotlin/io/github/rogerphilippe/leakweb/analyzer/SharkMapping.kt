/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.analyzer

import io.github.rogerphilippe.leakweb.model.Leak
import io.github.rogerphilippe.leakweb.model.LeakKind
import io.github.rogerphilippe.leakweb.model.LeakTrace
import io.github.rogerphilippe.leakweb.model.LeakTraceObject
import io.github.rogerphilippe.leakweb.model.LeakTraceReference
import io.github.rogerphilippe.leakweb.model.LeakingStatus
import io.github.rogerphilippe.leakweb.model.ObjectType
import io.github.rogerphilippe.leakweb.model.ReferenceType
import shark.ApplicationLeak
import shark.LibraryLeak
import shark.LeakTrace as SharkLeakTrace
import shark.LeakTraceObject as SharkLeakTraceObject
import shark.LeakTraceReference as SharkLeakTraceReference

// Converts Shark types to leakweb-model types, so Shark never leaks into the public API.

internal fun ApplicationLeak.toModel(): Leak =
    Leak(
        signature = signature,
        shortDescription = shortDescription,
        kind = LeakKind.APPLICATION,
        traces = leakTraces.map { it.toModel() },
        totalRetainedHeapByteSize = totalRetainedHeapByteSize,
    )

internal fun LibraryLeak.toModel(): Leak =
    Leak(
        signature = signature,
        shortDescription = shortDescription,
        kind = LeakKind.LIBRARY,
        libraryLeakDescription = description,
        traces = leakTraces.map { it.toModel() },
        totalRetainedHeapByteSize = totalRetainedHeapByteSize,
    )

private fun SharkLeakTrace.toModel(): LeakTrace =
    LeakTrace(
        gcRootType = gcRootType.description,
        referencePath =
            referencePath.mapIndexed { index, reference ->
                reference.toModel(isSuspect = referencePathElementIsSuspect(index))
            },
        leakingObject = leakingObject.toModel(),
        retainedHeapByteSize = retainedHeapByteSize,
        signature = signature,
    )

private fun SharkLeakTraceReference.toModel(isSuspect: Boolean): LeakTraceReference =
    LeakTraceReference(
        originObject = originObject.toModel(),
        referenceType =
            when (referenceType) {
                SharkLeakTraceReference.ReferenceType.INSTANCE_FIELD -> ReferenceType.INSTANCE_FIELD
                SharkLeakTraceReference.ReferenceType.STATIC_FIELD -> ReferenceType.STATIC_FIELD
                SharkLeakTraceReference.ReferenceType.LOCAL -> ReferenceType.LOCAL
                SharkLeakTraceReference.ReferenceType.ARRAY_ENTRY -> ReferenceType.ARRAY_ENTRY
            },
        owningClassName = owningClassName,
        referenceName = referenceName,
        referenceDisplayName = referenceDisplayName,
        isSuspect = isSuspect,
    )

private fun SharkLeakTraceObject.toModel(): LeakTraceObject =
    LeakTraceObject(
        objectType =
            when (type) {
                SharkLeakTraceObject.ObjectType.CLASS -> ObjectType.CLASS
                SharkLeakTraceObject.ObjectType.ARRAY -> ObjectType.ARRAY
                SharkLeakTraceObject.ObjectType.INSTANCE -> ObjectType.INSTANCE
            },
        className = className,
        labels = labels.toList(),
        leakingStatus =
            when (leakingStatus) {
                SharkLeakTraceObject.LeakingStatus.NOT_LEAKING -> LeakingStatus.NOT_LEAKING
                SharkLeakTraceObject.LeakingStatus.LEAKING -> LeakingStatus.LEAKING
                SharkLeakTraceObject.LeakingStatus.UNKNOWN -> LeakingStatus.UNKNOWN
            },
        leakingStatusReason = leakingStatusReason,
        retainedHeapByteSize = retainedHeapByteSize,
        retainedObjectCount = retainedObjectCount,
    )
