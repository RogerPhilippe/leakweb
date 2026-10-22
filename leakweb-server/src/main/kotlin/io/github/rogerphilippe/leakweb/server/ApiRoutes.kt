/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.server

import io.github.rogerphilippe.leakweb.model.AnalysisSummary
import io.github.rogerphilippe.leakweb.model.CommandResult
import io.github.rogerphilippe.leakweb.model.HeapAnalysis
import io.github.rogerphilippe.leakweb.model.HeapAnalysisFailure
import io.github.rogerphilippe.leakweb.model.HeapAnalysisSuccess
import io.github.rogerphilippe.leakweb.model.LeakDetails
import io.github.rogerphilippe.leakweb.model.LeakSummary
import io.github.rogerphilippe.leakweb.model.MemorySample
import io.github.rogerphilippe.leakweb.model.RetainedObjectInfo
import io.github.rogerphilippe.leakweb.model.ServerStatus
import io.github.rogerphilippe.leakweb.server.http.HttpBody
import io.github.rogerphilippe.leakweb.server.http.HttpRequest
import io.github.rogerphilippe.leakweb.server.http.HttpResponse
import io.github.rogerphilippe.leakweb.server.http.HttpStatus
import io.github.rogerphilippe.leakweb.server.http.Router
import kotlinx.serialization.builtins.ListSerializer
import java.io.FileInputStream

/** Ids and leak signatures accepted in paths; anything else is rejected before reaching the store. */
private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")

/** The `/api` routes of section 5 of the plan. */
internal class ApiRoutes(
    private val store: LeakStoreReader,
    private val commands: Commands,
    private val eventStreams: EventStreamHub,
    private val port: () -> Int,
) {
    fun registerOn(router: Router) {
        router.get("/api/status") {
            jsonResponse(ServerStatus.serializer(), commands.status().copy(port = port()))
        }
        router.get("/api/leaks") { jsonResponse(ListSerializer(LeakSummary.serializer()), store.leaks()) }
        router.get("/api/leaks/{signature}") { request ->
            request.withValidParam("signature") { signature ->
                store.leak(signature)?.let { jsonResponse(LeakDetails.serializer(), it) }
                    ?: errorResponse(HttpStatus.NOT_FOUND, "Unknown leak $signature")
            }
        }
        router.get("/api/analyses") {
            jsonResponse(ListSerializer(AnalysisSummary.serializer()), store.analyses().map { it.toSummary() })
        }
        router.get("/api/analyses/{id}/hprof") { request -> request.withValidParam("id", ::heapDumpDownload) }
        router.delete("/api/analyses/{id}") { request ->
            request.withValidParam("id") { id ->
                if (commands.deleteAnalysis(id)) {
                    jsonResponse(CommandResult.serializer(), CommandResult(accepted = true, message = "Deleted $id"))
                } else {
                    errorResponse(HttpStatus.NOT_FOUND, "Unknown analysis $id")
                }
            }
        }
        router.get("/api/retained") {
            jsonResponse(ListSerializer(RetainedObjectInfo.serializer()), commands.retainedObjects())
        }
        router.post("/api/heapdump") {
            val accepted = commands.requestHeapDump()
            val message = if (accepted) "Heap dump requested" else "A heap dump is already running"
            jsonResponse(CommandResult.serializer(), CommandResult(accepted, message), HttpStatus.ACCEPTED)
        }
        router.post("/api/gc") {
            commands.runGc()
            jsonResponse(CommandResult.serializer(), CommandResult(accepted = true, message = "Garbage collection run"))
        }
        router.get("/api/memory") { jsonResponse(MemorySample.serializer(), commands.memory()) }
        router.get("/api/memory/history") {
            jsonResponse(ListSerializer(MemorySample.serializer()), commands.memoryHistory())
        }
        router.post("/api/memory/live/start") {
            commands.setLiveMemory(true)
            jsonResponse(CommandResult.serializer(), CommandResult(accepted = true, message = "Live memory started"))
        }
        router.post("/api/memory/live/stop") {
            commands.setLiveMemory(false)
            jsonResponse(CommandResult.serializer(), CommandResult(accepted = true, message = "Live memory stopped"))
        }
        router.get("/api/events") {
            HttpResponse(
                status = HttpStatus.OK,
                contentType = "text/event-stream; charset=utf-8",
                body = HttpBody.EventStream(eventStreams.open()),
                headers = mapOf("Cache-Control" to "no-cache", "X-Accel-Buffering" to "no"),
            )
        }
    }

    private fun heapDumpDownload(id: String): HttpResponse {
        val file = store.heapDumpFile(id) ?: return errorResponse(HttpStatus.NOT_FOUND, "No heap dump for $id")
        return HttpResponse(
            status = HttpStatus.OK,
            contentType = "application/octet-stream",
            body = HttpBody.Stream(FileInputStream(file), file.length()),
            headers = mapOf("Content-Disposition" to "attachment; filename=\"$id.hprof\""),
        )
    }

    private fun HeapAnalysis.toSummary() =
        AnalysisSummary(
            id = id,
            createdAtMillis = createdAtMillis,
            success = this is HeapAnalysisSuccess,
            durationMillis = durationMillis,
            heapDumpSizeBytes = heapDumpSizeBytes,
            heapDumpAvailable = store.heapDumpFile(id) != null,
            leakCount = (this as? HeapAnalysisSuccess)?.leaks?.size ?: 0,
            errorMessage = (this as? HeapAnalysisFailure)?.errorMessage,
        )

    private inline fun HttpRequest.withValidParam(
        name: String,
        block: (String) -> HttpResponse,
    ): HttpResponse {
        val value = pathParams.getValue(name)
        return if (ID_PATTERN.matches(value)) block(value) else errorResponse(HttpStatus.BAD_REQUEST, "Invalid $name")
    }
}
