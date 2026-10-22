/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Live event pushed to the web UI (SSE). The serial name is the SSE event name. */
@Serializable
sealed interface Event {
    @Serializable
    @SerialName("memory.sample")
    data class MemorySampled(
        val sample: MemorySample,
    ) : Event

    /** Live memory sampling was turned on or off, maybe from another tab. */
    @Serializable
    @SerialName("memory.live")
    data class LiveMemoryChanged(
        val enabled: Boolean,
    ) : Event

    @Serializable
    @SerialName("object.retained")
    data class ObjectRetained(
        val key: String,
        val description: String,
        val className: String,
        val retainedObjectCount: Int,
    ) : Event

    @Serializable
    @SerialName("heapdump.started")
    data class HeapDumpStarted(
        val retainedObjectCount: Int,
    ) : Event

    @Serializable
    @SerialName("heapdump.failed")
    data class HeapDumpFailed(
        val message: String,
    ) : Event

    @Serializable
    @SerialName("analysis.progress")
    data class AnalysisProgress(
        val step: String,
        val percent: Int,
    ) : Event

    @Serializable
    @SerialName("analysis.done")
    data class AnalysisDone(
        val analysisId: String,
        val success: Boolean,
    ) : Event

    @Serializable
    @SerialName("leak.new")
    data class NewLeak(
        val signature: String,
        val shortDescription: String,
    ) : Event
}
