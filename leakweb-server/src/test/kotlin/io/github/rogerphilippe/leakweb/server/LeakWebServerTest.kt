/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.server

import io.github.rogerphilippe.leakweb.model.Event
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI

/** Real server on a real socket. */
class LeakWebServerTest {
    private val events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    private val servers = mutableListOf<LeakWebServer>()

    private fun startServer(
        port: Int = 0,
        portFallback: Boolean = true,
    ): LeakWebServer {
        val config = ServerConfig(port = port, portFallback = portFallback, token = TOKEN, pingIntervalMillis = 100)
        return LeakWebServer(config, FakeStore(), FakeCommands(), events).also {
            servers += it
            it.start()
        }
    }

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
    }

    @Test
    fun `serves the API over HTTP`() {
        val server = startServer()
        val connection = URI("http://127.0.0.1:${server.port}/api/status").toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $TOKEN")

        assertEquals(200, connection.responseCode)
        val body = connection.inputStream.bufferedReader().readText()
        assertTrue(body, body.contains("\"port\":${server.port}"))
    }

    @Test
    fun `falls back to the next port when the requested one is in use`() {
        ServerSocket(0, 0, InetAddress.getByName(ServerConfig.LOOPBACK)).use { busy ->
            val server = startServer(port = busy.localPort)

            assertNotEquals(busy.localPort, server.port)
            assertTrue(server.port in busy.localPort until busy.localPort + ServerConfig.MAX_PORT_ATTEMPTS)
        }
    }

    @Test(expected = java.io.IOException::class)
    fun `fails without fallback when the port is in use`() {
        ServerSocket(0, 0, InetAddress.getByName(ServerConfig.LOOPBACK)).use { busy ->
            startServer(port = busy.localPort, portFallback = false)
        }
    }

    @Test
    fun `streams events uncompressed even when gzip is accepted and releases the client on disconnect`() {
        val server = startServer()
        Socket(ServerConfig.LOOPBACK, server.port).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().write(
                (
                    "GET /api/events HTTP/1.1\r\nHost: localhost:${server.port}\r\n" +
                        "X-LeakWeb-Token: $TOKEN\r\nAccept-Encoding: gzip\r\n\r\n"
                ).toByteArray(),
            )
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val headers = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()

            assertTrue(headers.toString(), headers.first().startsWith("HTTP/1.1 200"))
            assertTrue(headers.toString(), headers.none { it.startsWith("Content-Encoding", ignoreCase = true) })
            assertTrue(headers.toString(), headers.contains("Content-Type: text/event-stream; charset=utf-8"))

            reader.readUntil(": connected")
            waitUntil { server.eventClientCount == 1 }
            events.tryEmit(Event.HeapDumpStarted(retainedObjectCount = 3))
            reader.readUntil("event: heapdump.started")
            val data = reader.readUntil("data: ")
            assertTrue(data, data.contains("\"retainedObjectCount\":3"))
            reader.readUntil(": ping")
        }

        // The next ping fails to write, NanoHTTPD closes the stream and the producer is cancelled.
        waitUntil { server.eventClientCount == 0 }
    }

    @Test
    fun `stop ends open event streams`() {
        val server = startServer()
        Socket(ServerConfig.LOOPBACK, server.port).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().write(
                "GET /api/events?token=$TOKEN HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(),
            )
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            reader.readUntil(": connected")

            server.stop()

            assertEquals(-1, server.port)
            assertEquals(0, server.eventClientCount)
        }
    }

    private fun BufferedReader.readUntil(prefix: String): String =
        generateSequence { readLine() }.firstOrNull { it.startsWith(prefix) } ?: error("Stream ended before $prefix")

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            Thread.sleep(10)
        }
    }
}
