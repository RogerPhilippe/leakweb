/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import io.github.rogerphilippe.leakweb.model.HeapAnalysisFailure
import io.github.rogerphilippe.leakweb.model.HeapAnalysisSuccess
import io.github.rogerphilippe.leakweb.model.Leak
import io.github.rogerphilippe.leakweb.model.LeakKind
import io.github.rogerphilippe.leakweb.model.LeakTrace
import io.github.rogerphilippe.leakweb.model.LeakTraceObject
import io.github.rogerphilippe.leakweb.model.LeakingStatus
import io.github.rogerphilippe.leakweb.model.ObjectType

internal fun testLeak(
    signature: String,
    className: String = "com.example.Leaky",
) = Leak(
    signature = signature,
    shortDescription = "$className leaked",
    kind = LeakKind.APPLICATION,
    traces =
        listOf(
            LeakTrace(
                gcRootType = "Sticky class",
                referencePath = emptyList(),
                leakingObject =
                    LeakTraceObject(
                        objectType = ObjectType.INSTANCE,
                        className = className,
                        labels = emptyList(),
                        leakingStatus = LeakingStatus.LEAKING,
                        leakingStatusReason = "watched",
                    ),
                signature = signature,
            ),
        ),
    totalRetainedHeapByteSize = 100,
)

internal fun testSuccess(
    id: String,
    createdAtMillis: Long,
    vararg leaks: Leak,
) = HeapAnalysisSuccess(
    id = id,
    createdAtMillis = createdAtMillis,
    heapDumpFileName = "$id.hprof",
    heapDumpSizeBytes = 10L,
    durationMillis = 5L,
    leaks = leaks.toList(),
)

internal fun testFailure(
    id: String,
    createdAtMillis: Long,
) = HeapAnalysisFailure(
    id = id,
    createdAtMillis = createdAtMillis,
    heapDumpFileName = "$id.hprof",
    heapDumpSizeBytes = 10L,
    durationMillis = 5L,
    errorMessage = "boom",
    stackTrace = "stack",
)
