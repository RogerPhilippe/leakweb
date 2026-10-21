/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.server

import io.github.rogerphilippe.leakweb.model.HeapAnalysis
import io.github.rogerphilippe.leakweb.model.LeakDetails
import io.github.rogerphilippe.leakweb.model.LeakSummary
import io.github.rogerphilippe.leakweb.model.MemorySample
import io.github.rogerphilippe.leakweb.model.RetainedObjectInfo
import io.github.rogerphilippe.leakweb.model.ServerStatus
import java.io.File

// What the server needs from the app, kept as interfaces so this module has no Android dependency.

/** Read side of the analysis store. */
interface LeakStoreReader {
    /** Newest first. */
    fun analyses(): List<HeapAnalysis>

    fun analysis(id: String): HeapAnalysis?

    /** The heap dump of [id], resolved by the store; null if unknown or already deleted. */
    fun heapDumpFile(id: String): File?

    fun leaks(): List<LeakSummary>

    fun leak(signature: String): LeakDetails?
}

/** Actions and live state of the running app. */
interface Commands {
    /** The server fills in the real port. */
    fun status(): ServerStatus

    fun retainedObjects(): List<RetainedObjectInfo>

    fun memory(): MemorySample

    /** Starts a heap dump in the background, ignoring the retained threshold; false if one is running. */
    fun requestHeapDump(): Boolean

    fun runGc()

    /** Deletes the analysis and its heap dump; false if unknown. */
    fun deleteAnalysis(id: String): Boolean
}
