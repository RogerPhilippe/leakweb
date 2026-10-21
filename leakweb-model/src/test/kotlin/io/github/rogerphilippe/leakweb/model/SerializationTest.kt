/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.model

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SerializationTest {
    private val leakingObject =
        LeakTraceObject(
            objectType = ObjectType.INSTANCE,
            className = "com.example.MainActivity",
            labels = listOf("mDestroyed = true"),
            leakingStatus = LeakingStatus.LEAKING,
            leakingStatusReason = "Activity#mDestroyed is true",
            retainedHeapByteSize = 1_024,
            retainedObjectCount = 12,
        )

    private val leak =
        Leak(
            signature = "abc123",
            shortDescription = "MainActivity leaked",
            kind = LeakKind.APPLICATION,
            traces =
                listOf(
                    LeakTrace(
                        gcRootType = "Sticky class",
                        referencePath =
                            listOf(
                                LeakTraceReference(
                                    originObject =
                                        LeakTraceObject(
                                            objectType = ObjectType.CLASS,
                                            className = "com.example.Cache",
                                            labels = emptyList(),
                                            leakingStatus = LeakingStatus.NOT_LEAKING,
                                            leakingStatusReason = "a class is never leaking",
                                        ),
                                    referenceType = ReferenceType.STATIC_FIELD,
                                    owningClassName = "com.example.Cache",
                                    referenceName = "activity",
                                    referenceDisplayName = "activity",
                                    isSuspect = true,
                                ),
                            ),
                        leakingObject = leakingObject,
                        retainedHeapByteSize = 1_024,
                        signature = "abc123",
                    ),
                ),
            totalRetainedHeapByteSize = 1_024,
        )

    @Test
    fun `heap analysis success round trip`() {
        val analysis: HeapAnalysis =
            HeapAnalysisSuccess(
                id = "a1",
                createdAtMillis = 1_000L,
                heapDumpFileName = "a1.hprof",
                heapDumpSizeBytes = 2_048L,
                durationMillis = 300L,
                leaks = listOf(leak),
            )

        val json = LeakWebJson.encodeToString(HeapAnalysis.serializer(), analysis)

        assertEquals("success", typeOf(json))
        assertEquals(analysis, LeakWebJson.decodeFromString(HeapAnalysis.serializer(), json))
    }

    @Test
    fun `heap analysis failure round trip`() {
        val analysis: HeapAnalysis =
            HeapAnalysisFailure(
                id = "a2",
                createdAtMillis = 1_000L,
                heapDumpFileName = "a2.hprof",
                heapDumpSizeBytes = 0L,
                durationMillis = 10L,
                errorMessage = "boom",
                stackTrace = "java.lang.IllegalStateException: boom",
            )

        val json = LeakWebJson.encodeToString(HeapAnalysis.serializer(), analysis)

        assertEquals("failure", typeOf(json))
        assertEquals(analysis, LeakWebJson.decodeFromString(HeapAnalysis.serializer(), json))
    }

    @Test
    fun `events use the SSE event names as type`() {
        val sample =
            MemorySample(
                timestampMillis = 1L,
                javaHeapUsedBytes = 2L,
                javaHeapMaxBytes = 3L,
                nativeHeapAllocatedBytes = 4L,
                watchedObjectCount = 5,
                retainedObjectCount = 6,
            )
        val events =
            mapOf(
                "memory.sample" to Event.MemorySampled(sample),
                "object.retained" to Event.ObjectRetained("k", "d", "com.example.Foo", 1),
                "heapdump.started" to Event.HeapDumpStarted(5),
                "analysis.progress" to Event.AnalysisProgress("PARSING_HEAP_DUMP", 10),
                "analysis.done" to Event.AnalysisDone("a1", success = true),
                "leak.new" to Event.NewLeak("abc123", "MainActivity leaked"),
            )

        events.forEach { (name, event) ->
            val json = LeakWebJson.encodeToString(Event.serializer(), event)

            assertEquals(name, typeOf(json))
            assertEquals(event, LeakWebJson.decodeFromString(Event.serializer(), json))
        }
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = """{"type":"heapdump.started","retainedObjectCount":3,"addedInFutureVersion":true}"""

        assertEquals(Event.HeapDumpStarted(3), LeakWebJson.decodeFromString(Event.serializer(), json))
    }

    private fun typeOf(json: String): String =
        LeakWebJson
            .parseToJsonElement(json)
            .jsonObject
            .getValue("type")
            .jsonPrimitive.content
}
