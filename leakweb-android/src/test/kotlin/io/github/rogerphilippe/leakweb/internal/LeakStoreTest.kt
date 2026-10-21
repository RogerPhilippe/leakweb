/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import io.github.rogerphilippe.leakweb.model.SCHEMA_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LeakStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val directory by lazy { tempFolder.root }
    private val store by lazy { LeakStore(directory, maxHeapDumps = 2) }

    private fun saveWithDump(analysis: io.github.rogerphilippe.leakweb.model.HeapAnalysis) =
        store.newHeapDumpFile(analysis.id).writeText("hprof").let { store.save(analysis) }

    @Test
    fun `analyses are listed most recent first and include failures`() {
        store.save(testSuccess("a1", 1_000L))
        store.save(testFailure("a2", 3_000L))
        store.save(testSuccess("a3", 2_000L))

        assertEquals(listOf("a2", "a3", "a1"), store.analyses().map { it.id })
        assertEquals(testFailure("a2", 3_000L), store.analysis("a2"))
    }

    @Test
    fun `stored file has the schema version`() {
        store.save(testSuccess("a1", 1_000L))

        val json = File(directory, "analyses/a1.json").readText()

        assertTrue(json, json.contains("\"schemaVersion\":$SCHEMA_VERSION"))
    }

    @Test
    fun `save returns only leaks never seen before`() {
        val first = store.save(testSuccess("a1", 1_000L, testLeak("s1")))
        val second = store.save(testSuccess("a2", 2_000L, testLeak("s1"), testLeak("s2")))

        assertEquals(listOf("s1"), first.map { it.signature })
        assertEquals(listOf("s2"), second.map { it.signature })
    }

    @Test
    fun `leaks are grouped by signature across analyses`() {
        store.save(testSuccess("a1", 1_000L, testLeak("s1", "com.example.Old")))
        store.save(testSuccess("a2", 2_000L, testLeak("s2")))
        store.save(testSuccess("a3", 3_000L, testLeak("s1", "com.example.New")))

        val leaks = store.leaks()

        assertEquals(listOf("s1", "s2"), leaks.map { it.signature })
        val s1 = leaks.first()
        assertEquals(2, s1.occurrenceCount)
        assertEquals(1_000L, s1.firstSeenMillis)
        assertEquals(3_000L, s1.lastSeenMillis)
        assertEquals("com.example.New", s1.leakingClassName)
    }

    @Test
    fun `leak details have the latest leak and the analyses where it was found`() {
        store.save(testSuccess("a1", 1_000L, testLeak("s1", "com.example.Old")))
        store.save(testSuccess("a2", 2_000L, testLeak("s1", "com.example.New")))

        val details = store.leak("s1")!!

        assertEquals(listOf("a2", "a1"), details.analysisIds)
        assertEquals("com.example.New leaked", details.latest.shortDescription)
        assertNull(store.leak("unknown"))
    }

    @Test
    fun `only the most recent heap dumps are kept`() {
        saveWithDump(testSuccess("a1", 1_000L))
        saveWithDump(testSuccess("a2", 2_000L))
        saveWithDump(testSuccess("a3", 3_000L))

        assertNull(store.heapDumpFile("a1"))
        assertEquals("a2.hprof", store.heapDumpFile("a2")?.name)
        assertEquals("a3.hprof", store.heapDumpFile("a3")?.name)
        assertEquals(3, store.analyses().size)
    }

    @Test
    fun `delete removes the analysis and its heap dump`() {
        saveWithDump(testSuccess("a1", 1_000L))

        assertTrue(store.delete("a1"))

        assertTrue(store.analyses().isEmpty())
        assertFalse(File(directory, "dumps/a1.hprof").exists())
        assertFalse(store.delete("a1"))
    }

    @Test
    fun `unknown or path-like ids never resolve to a file`() {
        File(directory, "secret.hprof").writeText("secret")

        assertNull(store.heapDumpFile("../secret"))
        assertNull(store.heapDumpFile("unknown"))
        assertFalse(store.delete("../secret"))
        assertTrue(File(directory, "secret.hprof").exists())
    }

    @Test
    fun `unreadable files are skipped`() {
        store.save(testSuccess("a1", 1_000L))
        File(directory, "analyses/broken.json").writeText("{not json")

        assertEquals(listOf("a1"), store.analyses().map { it.id })
    }
}
