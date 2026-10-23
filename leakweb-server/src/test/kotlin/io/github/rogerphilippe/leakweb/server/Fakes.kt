/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.server

import io.github.rogerphilippe.leakweb.model.HeapAnalysis
import io.github.rogerphilippe.leakweb.model.HeapAnalysisFailure
import io.github.rogerphilippe.leakweb.model.HeapAnalysisSuccess
import io.github.rogerphilippe.leakweb.model.Leak
import io.github.rogerphilippe.leakweb.model.LeakDetails
import io.github.rogerphilippe.leakweb.model.LeakKind
import io.github.rogerphilippe.leakweb.model.LeakSummary
import io.github.rogerphilippe.leakweb.model.MemorySample
import io.github.rogerphilippe.leakweb.model.RetainedObjectInfo
import io.github.rogerphilippe.leakweb.model.ServerStatus
import java.io.File

internal const val TOKEN = "test-token"
internal const val LEAK_SIGNATURE = "abc123"

internal val testLeak =
    Leak(
        signature = LEAK_SIGNATURE,
        shortDescription = "Leaky leaked",
        kind = LeakKind.APPLICATION,
        traces = emptyList(),
        totalRetainedHeapByteSize = 100,
    )

internal val testSummary =
    LeakSummary(
        signature = LEAK_SIGNATURE,
        shortDescription = "Leaky leaked",
        kind = LeakKind.APPLICATION,
        leakingClassName = "com.example.Leaky",
        occurrenceCount = 1,
        retainedHeapByteSize = 100,
        firstSeenMillis = 1,
        lastSeenMillis = 1,
    )

internal class FakeStore(
    private val heapDumps: Map<String, File> = emptyMap(),
) : LeakStoreReader {
    val analyses: List<HeapAnalysis> =
        listOf(
            HeapAnalysisSuccess("ok-1", 2, "ok-1.hprof", 10, 5, listOf(testLeak)),
            HeapAnalysisFailure("failed-1", 1, "failed-1.hprof", 10, 5, "boom", "stack"),
        )

    override fun analyses() = analyses

    override fun analysis(id: String) = analyses.firstOrNull { it.id == id }

    override fun heapDumpFile(id: String) = heapDumps[id]

    override fun leaks() = listOf(testSummary)

    override fun leak(signature: String) =
        if (signature == LEAK_SIGNATURE) LeakDetails(testSummary, testLeak, listOf("ok-1")) else null
}

internal class FakeCommands : Commands {
    var heapDumpRequests = 0
    var gcRuns = 0
    val deleted = mutableListOf<String>()

    override fun status() = ServerStatus("1.0", "com.example", "com.example", port = 0, 3, 1)

    override fun retainedObjects() = listOf(RetainedObjectInfo("k1", "Activity destroyed", "com.example.A", 1, 2))

    override fun memory() = MemorySample(1, 2, 3, 4, null, 3, 1)

    override fun memoryHistory() = listOf(MemorySample(0, 1, 3, 4, 500, 3, 1), memory())

    val liveMemoryChanges = mutableListOf<Boolean>()

    override fun setLiveMemory(enabled: Boolean) {
        liveMemoryChanges += enabled
    }

    override fun requestHeapDump(): Boolean {
        heapDumpRequests++
        return true
    }

    override fun runGc() {
        gcRuns++
    }

    override fun deleteAnalysis(id: String): Boolean {
        deleted += id
        return id == "ok-1"
    }
}
