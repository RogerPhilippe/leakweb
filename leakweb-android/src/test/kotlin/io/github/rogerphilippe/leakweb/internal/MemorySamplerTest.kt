/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import io.github.rogerphilippe.leakweb.model.MemorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CompletableFuture

class MemorySamplerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private var now = 0L
    private var pssReads = 0
    private var reads = 0
    private val published = mutableListOf<MemorySample>()

    private val reader =
        object : MemoryReader {
            override fun read() =
                MemorySample(
                    timestampMillis = ++reads * 1_000L,
                    javaHeapUsedBytes = 10,
                    javaHeapMaxBytes = 100,
                    nativeHeapAllocatedBytes = 20,
                    watchedObjectCount = 0,
                    retainedObjectCount = 0,
                )

            override fun readPssKb() = (++pssReads * 1_000).toLong()
        }

    private val sampler =
        MemorySampler(
            clock = { now },
            reader = reader,
            onSample = { published += it },
            pssIntervalMillis = 10_000L,
            historySize = 3,
        )

    private fun sampleEverySecond(count: Int) =
        repeat(count) {
            sampler.sample()
            now += 1_000L
        }

    @Test
    fun `PSS is read on the first sample, then only once per interval`() {
        sampleEverySecond(12)

        assertEquals(2, pssReads)
        // Samples at 9, 10 and 11 s: the second PSS read is at 10 s.
        assertEquals(listOf(1_000L, 2_000L, 2_000L), published.takeLast(3).map { it.pssKb })
    }

    @Test
    fun `history keeps the last samples, oldest first`() {
        sampleEverySecond(5)

        assertEquals(listOf(3_000L, 4_000L, 5_000L), sampler.history.map { it.timestampMillis })
    }

    @Test
    fun `every sample is published`() {
        sampleEverySecond(4)

        assertEquals(4, published.size)
    }

    @Test
    fun `a snapshot reads PSS and is not kept in the history`() {
        val snapshot = sampler.snapshot()

        assertEquals(1_000L, snapshot.pssKb)
        assertTrue(sampler.history.isEmpty())
        assertTrue(published.isEmpty())
    }

    @Test
    fun `clearing forgets the history and reads PSS again`() {
        sampleEverySecond(2)

        sampler.clear()
        sampler.sample()

        assertEquals(1, sampler.history.size)
        assertEquals(2, pssReads)
    }

    // LiveMemory

    private val stateFile by lazy { File(tempFolder.root, "leakweb/live-memory") }
    private val scheduled = mutableListOf<Pair<Runnable, CompletableFuture<Unit>>>()
    private val changes = mutableListOf<Boolean>()

    private fun liveMemory() =
        LiveMemory(
            sampler = sampler,
            stateFile = stateFile,
            executor = { _, command -> CompletableFuture<Unit>().also { scheduled += command to it } },
            onChanged = { changes += it },
        )

    @Test
    fun `live memory is off by default and samples nothing`() {
        val liveMemory = liveMemory()
        liveMemory.restore()

        assertFalse(liveMemory.isEnabled)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun `turning live memory on clears the history and samples until it is turned off`() {
        sampleEverySecond(3)
        val liveMemory = liveMemory()

        liveMemory.setEnabled(true)
        assertTrue(sampler.history.isEmpty())
        scheduled.single().first.run()
        assertEquals(1, sampler.history.size)

        liveMemory.setEnabled(false)
        assertTrue(scheduled.single().second.isCancelled)
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun `turning it on twice starts sampling once`() {
        val liveMemory = liveMemory()

        liveMemory.setEnabled(true)
        liveMemory.setEnabled(true)

        assertEquals(1, scheduled.size)
        assertEquals(listOf(true), changes)
    }

    @Test
    fun `live memory stays on across app restarts until turned off`() {
        liveMemory().setEnabled(true)

        val afterRestart = liveMemory().apply { restore() }
        assertTrue(afterRestart.isEnabled)

        afterRestart.setEnabled(false)
        assertFalse(liveMemory().apply { restore() }.isEnabled)
    }
}
