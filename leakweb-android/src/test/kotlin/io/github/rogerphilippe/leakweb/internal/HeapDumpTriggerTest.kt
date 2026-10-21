/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class HeapDumpTriggerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private var now = 10_000L
    private var visible = true
    private var dumpFails = false
    private val scheduled = mutableListOf<Pair<Long, Runnable>>()
    private val dumpedFiles = mutableListOf<File>()
    private val heapDumps = mutableListOf<HeapDump>()
    private val strongReferences = mutableListOf<Any>()
    private val eventBus = EventBus()

    private val objectWatcher =
        ObjectWatcher(clock = { now }, checkRetainedExecutor = { it.run() }, gcTrigger = {})

    private val store by lazy { LeakStore(tempFolder.root) }

    private val trigger by lazy {
        HeapDumpTrigger(
            objectWatcher = objectWatcher,
            clock = { now },
            isAppVisible = { visible },
            executor = { delayMillis, command -> scheduled += delayMillis to command },
            heapDumper = { file ->
                if (dumpFails) throw IOException("disk full")
                file.writeText("hprof")
                dumpedFiles += file
            },
            store = store,
            eventBus = eventBus,
            onHeapDumped = { heapDumps += it },
            visibleThreshold = 3,
            backgroundThreshold = 1,
            waitBetweenDumpsMillis = 60_000L,
            newId = { "dump-${heapDumps.size + dumpedFiles.size}" },
        )
    }

    /** Watches [count] objects that stay reachable; the watcher checks them immediately (retained). */
    private fun retain(count: Int) =
        repeat(count) { objectWatcher.expectWeaklyReachable(Any().also { strongReferences += it }, "retained") }

    private fun runScheduled(): List<Long> {
        val commands = scheduled.toList()
        scheduled.clear()
        commands.forEach { it.second.run() }
        return commands.map { it.first }
    }

    @Test
    fun `visible app does not dump below the visible threshold`() {
        retain(2)

        trigger.onObjectRetained()
        runScheduled()

        assertTrue(heapDumps.isEmpty())
    }

    @Test
    fun `visible app dumps once the threshold is reached`() {
        retain(3)

        trigger.onObjectRetained()
        runScheduled()

        val heapDump = heapDumps.single()
        assertEquals("dump-0", heapDump.id)
        assertEquals(10_000L, heapDump.uptimeMillis)
        assertEquals(store.newHeapDumpFile("dump-0"), heapDump.file)
    }

    @Test
    fun `app in background dumps with a single retained object`() {
        retain(1)
        visible = false

        trigger.onAppVisibilityChanged()
        runScheduled()

        assertEquals(1, heapDumps.size)
    }

    @Test
    fun `dumped objects are no longer watched`() {
        retain(3)

        trigger.onObjectRetained()
        runScheduled()

        assertEquals(0, objectWatcher.retainedObjectCount)
    }

    @Test
    fun `checks requested before the scheduled one runs are merged`() {
        retain(3)

        trigger.onObjectRetained()
        trigger.onObjectRetained()

        assertEquals(1, scheduled.size)
    }

    @Test
    fun `second dump waits for the remaining time between dumps`() {
        retain(3)
        trigger.onObjectRetained()
        runScheduled()
        now += 20_000L
        retain(3)

        trigger.onObjectRetained()
        runScheduled()

        assertEquals(1, heapDumps.size)
        assertEquals(listOf(40_000L), runScheduled())
        assertEquals(1, heapDumps.size)
        now += 40_000L
        trigger.onObjectRetained()
        runScheduled()
        assertEquals(2, heapDumps.size)
    }

    @Test
    fun `failed dump is not analyzed and keeps the objects watched`() {
        retain(3)
        dumpFails = true

        trigger.onObjectRetained()
        runScheduled()

        assertTrue(heapDumps.isEmpty())
        assertEquals(3, objectWatcher.retainedObjectCount)
        assertFalse(store.newHeapDumpFile("dump-0").exists())
    }
}
