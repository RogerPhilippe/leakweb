/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import io.github.rogerphilippe.leakweb.model.MemorySample
import org.junit.Assert.assertEquals
import org.junit.Test

class MemorySamplerTest {
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
    fun `latest is the last sample, or a new one before the first`() {
        val first = sampler.latest()
        assertEquals(1, reads)

        sampleEverySecond(1)

        assertEquals(sampler.history.last(), sampler.latest())
        assertEquals(listOf(first.timestampMillis, 2_000L), sampler.history.map { it.timestampMillis })
    }
}
