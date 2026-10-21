/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import io.github.rogerphilippe.leakweb.model.HeapAnalysis
import io.github.rogerphilippe.leakweb.model.HeapAnalysisSuccess
import io.github.rogerphilippe.leakweb.model.Leak
import io.github.rogerphilippe.leakweb.model.LeakDetails
import io.github.rogerphilippe.leakweb.model.LeakSummary
import io.github.rogerphilippe.leakweb.model.LeakWebJson
import io.github.rogerphilippe.leakweb.model.StoredAnalysis
import io.github.rogerphilippe.leakweb.server.LeakStoreReader
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

/**
 * Stores analyses as JSON in `<directory>/analyses` and heap dumps in `<directory>/dumps`.
 *
 * Leaks are grouped by signature across analyses. Only the [maxHeapDumps] most recent heap dumps
 * are kept (they are large); older analyses keep their JSON without the dump. Ids received from
 * outside are always resolved through the stored analyses, never used to build a path.
 */
internal class LeakStore(
    directory: File,
    private val maxHeapDumps: Int = DEFAULT_MAX_HEAP_DUMPS,
) : LeakStoreReader {
    private val analysesDir = File(directory, "analyses")
    private val dumpsDir = File(directory, "dumps")

    /** File where the heap dump of a new analysis [id] must be written. */
    fun newHeapDumpFile(id: String): File = File(dumpsDir.apply { mkdirs() }, "$id.hprof")

    /** Saves [analysis] and returns its leaks that were never seen in previous analyses. */
    @Synchronized
    fun save(analysis: HeapAnalysis): List<Leak> {
        val knownSignatures = successes().flatMap { it.leaks }.map { it.signature }.toSet()
        analysesDir.mkdirs()
        val json = LeakWebJson.encodeToString(StoredAnalysis.serializer(), StoredAnalysis(analysis = analysis))
        // Write then rename, so that a crash never leaves a truncated JSON behind.
        val tempFile = File(analysesDir, "${analysis.id}.json.tmp")
        tempFile.writeText(json)
        if (!tempFile.renameTo(File(analysesDir, "${analysis.id}.json"))) {
            tempFile.delete()
            throw IOException("Could not save analysis ${analysis.id}")
        }
        pruneHeapDumps()
        return (analysis as? HeapAnalysisSuccess)?.leaks.orEmpty().filter { it.signature !in knownSignatures }
    }

    /** All analyses, most recent first. Unreadable files are skipped. */
    @Synchronized
    override fun analyses(): List<HeapAnalysis> =
        analysesDir
            .listFiles { file -> file.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { it.readAnalysis() }
            .sortedByDescending { it.createdAtMillis }

    override fun analysis(id: String): HeapAnalysis? = analyses().firstOrNull { it.id == id }

    /** Heap dump of analysis [id], or null if unknown or already pruned. */
    override fun heapDumpFile(id: String): File? {
        val analysis = analysis(id) ?: return null
        return File(dumpsDir, analysis.heapDumpFileName).takeIf { it.isFile && it.parentFile == dumpsDir }
    }

    /** Deletes analysis [id] and its heap dump; returns false if the id is unknown. */
    @Synchronized
    fun delete(id: String): Boolean {
        val analysis = analysis(id) ?: return false
        File(dumpsDir, analysis.heapDumpFileName).delete()
        return File(analysesDir, "${analysis.id}.json").delete()
    }

    /** Leaks grouped by signature, most recently seen first. */
    override fun leaks(): List<LeakSummary> = leakDetails().map { it.summary }

    override fun leak(signature: String): LeakDetails? = leakDetails().firstOrNull { it.summary.signature == signature }

    private fun leakDetails(): List<LeakDetails> {
        // Analyses are most recent first, so the first occurrence of each signature is the latest one.
        val occurrences = linkedMapOf<String, MutableList<Pair<HeapAnalysisSuccess, Leak>>>()
        successes().forEach { analysis ->
            analysis.leaks.forEach { leak ->
                occurrences.getOrPut(leak.signature) { mutableListOf() } +=
                    analysis to leak
            }
        }
        return occurrences.map { (signature, found) ->
            val (latestAnalysis, latestLeak) = found.first()
            LeakDetails(
                summary =
                    LeakSummary(
                        signature = signature,
                        shortDescription = latestLeak.shortDescription,
                        kind = latestLeak.kind,
                        leakingClassName =
                            latestLeak.traces
                                .first()
                                .leakingObject.className,
                        occurrenceCount = found.size,
                        retainedHeapByteSize = latestLeak.totalRetainedHeapByteSize,
                        firstSeenMillis = found.last().first.createdAtMillis,
                        lastSeenMillis = latestAnalysis.createdAtMillis,
                    ),
                latest = latestLeak,
                analysisIds = found.map { it.first.id },
            )
        }
    }

    private fun successes() = analyses().filterIsInstance<HeapAnalysisSuccess>()

    private fun pruneHeapDumps() {
        analyses()
            .filter { File(dumpsDir, it.heapDumpFileName).isFile }
            .drop(maxHeapDumps)
            .forEach { File(dumpsDir, it.heapDumpFileName).delete() }
    }

    private companion object {
        const val DEFAULT_MAX_HEAP_DUMPS = 5
    }
}

/** Reads a stored analysis; null if the file is unreadable or from an incompatible version. */
private fun File.readAnalysis(): HeapAnalysis? =
    try {
        LeakWebJson.decodeFromString(StoredAnalysis.serializer(), readText()).analysis
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
