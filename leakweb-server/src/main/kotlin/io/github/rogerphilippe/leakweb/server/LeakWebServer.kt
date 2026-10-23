/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.server

import fi.iki.elonen.NanoHTTPD
import io.github.rogerphilippe.leakweb.model.Event
import io.github.rogerphilippe.leakweb.server.http.HttpBody
import io.github.rogerphilippe.leakweb.server.http.HttpMethod
import io.github.rogerphilippe.leakweb.server.http.HttpRequest
import io.github.rogerphilippe.leakweb.server.http.HttpResponse
import io.github.rogerphilippe.leakweb.server.http.HttpStatus
import io.github.rogerphilippe.leakweb.server.http.Router
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

data class ServerConfig(
    /** `127.0.0.1` for loopback only, `0.0.0.0` for every interface. */
    val host: String = LOOPBACK,
    /** 0 picks a free port. */
    val port: Int,
    /** Tries the next [MAX_PORT_ATTEMPTS] ports if [port] is in use. */
    val portFallback: Boolean = true,
    val token: String,
    val maxEventClients: Int = DEFAULT_MAX_EVENT_CLIENTS,
    val pingIntervalMillis: Long = DEFAULT_PING_INTERVAL_MILLIS,
) {
    companion object {
        const val LOOPBACK = "127.0.0.1"
        const val ALL_INTERFACES = "0.0.0.0"
        const val MAX_PORT_ATTEMPTS = 10
        const val DEFAULT_MAX_EVENT_CLIENTS = 4
        const val DEFAULT_PING_INTERVAL_MILLIS = 15_000L
    }
}

/** The LeakWeb HTTP server: REST API, SSE events and the web UI, all behind the token. */
class LeakWebServer(
    private val config: ServerConfig,
    private val store: LeakStoreReader,
    private val commands: Commands,
    private val events: Flow<Event>,
) {
    private var scope: CoroutineScope? = null
    private var eventStreams: EventStreamHub? = null
    private var server: NanoHttpdAdapter? = null

    /** Real port, or -1 when stopped. */
    val port: Int
        @Synchronized get() = server?.listeningPort ?: -1

    internal val eventClientCount: Int
        @Synchronized get() = eventStreams?.clientCount ?: 0

    /** Binds the port (or the next free ones) and starts serving; returns the real port. */
    @Synchronized
    @Throws(IOException::class)
    fun start(): Int {
        check(server == null) { "Already started" }
        // A failure in an event stream must never crash the app that embeds the server.
        val errorHandler =
            CoroutineExceptionHandler { _, error -> logger.log(Level.WARNING, "LeakWeb event stream failed", error) }
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + errorHandler)
        val hub = EventStreamHub(events, newScope, config.maxEventClients, config.pingIntervalMillis)
        val candidates =
            if (config.portFallback && config.port != 0) {
                config.port until config.port + ServerConfig.MAX_PORT_ATTEMPTS
            } else {
                config.port..config.port
            }
        var lastError: IOException? = null
        for (candidate in candidates) {
            val adapter = NanoHttpdAdapter(config.host, candidate) { port -> handler(hub, port) }
            try {
                adapter.start(SOCKET_READ_TIMEOUT_MILLIS, true)
                scope = newScope
                eventStreams = hub
                server = adapter
                return adapter.listeningPort
            } catch (error: IOException) {
                lastError = error
            }
        }
        newScope.cancel()
        throw IOException("No free port in $candidates", lastError)
    }

    @Synchronized
    fun stop() {
        eventStreams?.finishAll()
        server?.stop()
        scope?.cancel()
        server = null
        eventStreams = null
        scope = null
    }

    private fun handler(
        hub: EventStreamHub,
        port: Int,
    ): LeakWebHttpHandler {
        val router = Router(::errorResponse)
        ApiRoutes(store, commands, hub) { port }.registerOn(router)
        return LeakWebHttpHandler(config.token, port, router, StaticFiles())
    }

    private companion object {
        const val SOCKET_READ_TIMEOUT_MILLIS = 5_000
        val logger: Logger = Logger.getLogger(LeakWebServer::class.java.name)
    }
}

/** Translates between NanoHTTPD and the HTTP types of this module. */
private class NanoHttpdAdapter(
    host: String,
    port: Int,
    handlerFactory: (port: Int) -> LeakWebHttpHandler,
) : NanoHTTPD(host, port) {
    private val handler by lazy { handlerFactory(listeningPort) }

    override fun serve(session: IHTTPSession): Response {
        val method =
            HttpMethod.entries.firstOrNull { it.name == session.method?.name }
                ?: return errorResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed").toNano()
        val request =
            HttpRequest(
                method = method,
                path = session.uri,
                query = session.parameters.mapValues { it.value.firstOrNull().orEmpty() },
                headers = session.headers,
            )
        return handler.handle(request).toNano()
    }

    /** Compressed SSE would be buffered by the gzip stream and never reach the browser. */
    override fun useGzipWhenAccepted(response: Response): Boolean =
        response.mimeType?.startsWith("text/event-stream") != true &&
            response.mimeType != "application/octet-stream" &&
            super.useGzipWhenAccepted(response)

    private fun HttpResponse.toNano(): Response {
        val status = NanoStatus(status)
        val response =
            when (val body = body) {
                is HttpBody.Bytes -> {
                    newFixedLengthResponse(
                        status,
                        contentType,
                        ByteArrayInputStream(body.bytes),
                        body.bytes.size.toLong(),
                    )
                }

                is HttpBody.Stream -> {
                    newFixedLengthResponse(status, contentType, body.stream, body.length)
                }

                is HttpBody.EventStream -> {
                    newChunkedResponse(status, contentType, body.stream)
                }
            }
        headers.forEach { (name, value) -> response.addHeader(name, value) }
        return response
    }
}

private class NanoStatus(
    private val code: Int,
) : NanoHTTPD.Response.IStatus {
    override fun getDescription(): String = "$code ${HttpStatus.description(code)}"

    override fun getRequestStatus(): Int = code
}
