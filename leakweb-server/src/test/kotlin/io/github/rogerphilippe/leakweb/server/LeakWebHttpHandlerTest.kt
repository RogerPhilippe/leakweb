/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.server

import io.github.rogerphilippe.leakweb.model.Event
import io.github.rogerphilippe.leakweb.server.http.HttpBody
import io.github.rogerphilippe.leakweb.server.http.HttpMethod
import io.github.rogerphilippe.leakweb.server.http.HttpRequest
import io.github.rogerphilippe.leakweb.server.http.HttpResponse
import io.github.rogerphilippe.leakweb.server.http.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LeakWebHttpHandlerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val commands = FakeCommands()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val hub = EventStreamHub(MutableSharedFlow<Event>(), scope, maxClients = 1, pingIntervalMillis = 60_000)

    private fun handler(store: LeakStoreReader = FakeStore()): LeakWebHttpHandler {
        val router = Router(::errorResponse)
        ApiRoutes(store, commands, hub) { PORT }.registerOn(router)
        return LeakWebHttpHandler(TOKEN, PORT, router, StaticFiles())
    }

    private fun request(
        path: String,
        method: HttpMethod = HttpMethod.GET,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = mapOf(TOKEN_HEADER),
    ) = HttpRequest(method, path, query, mapOf("Host" to "localhost:$PORT") + headers)

    private fun get(path: String): HttpResponse = handler().handle(request(path))

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `rejects API calls without a token with 401 JSON`() {
        val response = handler().handle(request("/api/status", headers = emptyMap()))

        assertEquals(401, response.status)
        assertTrue(response.contentType.startsWith("application/json"))
    }

    @Test
    fun `rejects pages without a token with a 401 HTML page`() {
        val response = handler().handle(request("/", headers = emptyMap()))

        assertEquals(401, response.status)
        assertTrue(response.contentType.startsWith("text/html"))
        assertEquals("default-src 'self'", response.headers["Content-Security-Policy"])
    }

    @Test
    fun `rejects a wrong token`() {
        assertEquals(401, handler().handle(request("/api/status", headers = mapOf("X-LeakWeb-Token" to "x"))).status)
    }

    @Test
    fun `accepts the token as bearer, header, cookie or query parameter`() {
        val handler = handler()
        listOf(
            request("/api/status", headers = mapOf("Authorization" to "Bearer $TOKEN")),
            request("/api/status", headers = mapOf("X-LeakWeb-Token" to TOKEN)),
            request("/api/status", headers = mapOf("Cookie" to "other=1; leakweb_$PORT=$TOKEN")),
            request("/api/status", query = mapOf("token" to TOKEN), headers = emptyMap()),
        ).forEach { assertEquals(200, handler.handle(it).status) }
    }

    @Test
    fun `ignores the cookie of another port`() {
        val response = handler().handle(request("/api/status", headers = mapOf("Cookie" to "leakweb_1234=$TOKEN")))

        assertEquals(401, response.status)
    }

    @Test
    fun `exchanges the query token for a cookie and redirects without it`() {
        val response =
            handler().handle(request("/", query = mapOf("token" to TOKEN, "tab" to "leaks"), headers = emptyMap()))

        assertEquals(302, response.status)
        assertEquals("/?tab=leaks", response.headers["Location"])
        assertEquals("leakweb_$PORT=$TOKEN; HttpOnly; SameSite=Strict; Path=/", response.headers["Set-Cookie"])
    }

    @Test
    fun `rejects hosts that are not localhost or an IP, against DNS rebinding`() {
        val handler = handler()
        assertEquals(403, handler.handle(request("/api/status", headers = mapOf("Host" to "evil.com:8080"))).status)
        listOf("127.0.0.1:8080", "192.168.0.10:8080", "[::1]:8080", "localhost").forEach { host ->
            assertEquals(
                host,
                200,
                handler.handle(request("/api/status", headers = mapOf("Host" to host, TOKEN_HEADER))).status,
            )
        }
    }

    @Test
    fun `rejects POST from another origin`() {
        val handler = handler()

        fun postGc(origin: String) =
            handler.handle(request("/api/gc", HttpMethod.POST, headers = mapOf("Origin" to origin, TOKEN_HEADER)))
        val foreign = postGc("http://evil.com")
        val sameOrigin = postGc("http://localhost:$PORT")

        assertEquals(403, foreign.status)
        assertEquals(200, sameOrigin.status)
        assertEquals(1, commands.gcRuns)
    }

    @Test
    fun `wraps API responses with the schema version`() {
        val response = get("/api/status")

        assertEquals(200, response.status)
        assertTrue(response.bodyText, response.bodyText.startsWith("{\"schemaVersion\":1,\"data\":{"))
        assertTrue(response.bodyText, response.bodyText.contains("\"port\":$PORT"))
    }

    @Test
    fun `serves every read route`() {
        mapOf(
            "/api/leaks" to "\"signature\":\"$LEAK_SIGNATURE\"",
            "/api/leaks/$LEAK_SIGNATURE" to "\"analysisIds\":[\"ok-1\"]",
            "/api/retained" to "\"key\":\"k1\"",
            "/api/memory" to "\"javaHeapUsedBytes\":2",
            "/api/analyses" to "\"errorMessage\":\"boom\"",
        ).forEach { (path, expected) ->
            val response = get(path)
            assertEquals(path, 200, response.status)
            assertTrue(response.bodyText, response.bodyText.contains(expected))
        }
    }

    @Test
    fun `answers 404 for unknown leaks, analyses and routes`() {
        assertEquals(404, get("/api/leaks/unknown").status)
        assertEquals(404, get("/api/analyses/unknown/hprof").status)
        assertEquals(404, get("/api/nothing").status)
        assertEquals(404, get("/missing.js").status)
    }

    @Test
    fun `rejects malformed ids with 400`() {
        assertEquals(400, get("/api/leaks/a.b").status)
        assertEquals(400, get("/api/analyses/${"x".repeat(65)}/hprof").status)
        assertEquals(400, handler().handle(request("/api/analyses/..%2Fsecret", HttpMethod.DELETE)).status)
    }

    @Test
    fun `downloads the heap dump resolved by the store`() {
        val file = temporaryFolder.newFile("dump.hprof").apply { writeBytes(ByteArray(3)) }
        val response = handler(FakeStore(mapOf("ok-1" to file))).handle(request("/api/analyses/ok-1/hprof"))

        assertEquals(200, response.status)
        assertEquals(3L, (response.body as HttpBody.Stream).length)
        assertEquals("attachment; filename=\"ok-1.hprof\"", response.headers["Content-Disposition"])
        (response.body as HttpBody.Stream).stream.close()
    }

    @Test
    fun `runs commands`() {
        val handler = handler()

        assertEquals(202, handler.handle(request("/api/heapdump", HttpMethod.POST)).status)
        assertEquals(200, handler.handle(request("/api/analyses/ok-1", HttpMethod.DELETE)).status)
        assertEquals(404, handler.handle(request("/api/analyses/other", HttpMethod.DELETE)).status)
        assertEquals(1, commands.heapDumpRequests)
        assertEquals(listOf("ok-1", "other"), commands.deleted)
    }

    @Test
    fun `a new event stream client ends the oldest one when the limit is reached`() {
        val handler = handler()
        val first = (handler.handle(request("/api/events")).body as HttpBody.EventStream).stream
        val buffer = ByteArray(64)
        first.read(buffer, 0, buffer.size) // ": connected"

        val second = handler.handle(request("/api/events"))

        assertEquals(200, second.status)
        assertEquals(-1, first.read(buffer, 0, buffer.size))
        first.close()
        (second.body as HttpBody.EventStream).stream.close()
        assertEquals(0, hub.clientCount)
    }

    @Test
    fun `serves the web UI with security headers`() {
        val response = get("/")

        assertEquals(200, response.status)
        assertEquals("text/html; charset=utf-8", response.contentType)
        assertEquals("default-src 'self'", response.headers["Content-Security-Policy"])
    }

    @Test
    fun `never serves files outside the web root`() {
        val staticFiles = StaticFiles()

        assertNull(staticFiles.serve("/../leakweb/web/index.html"))
        assertNull(staticFiles.serve("/sub/../index.html"))
    }

    private companion object {
        const val PORT = 8080
        val TOKEN_HEADER = "X-LeakWeb-Token" to TOKEN
    }
}
