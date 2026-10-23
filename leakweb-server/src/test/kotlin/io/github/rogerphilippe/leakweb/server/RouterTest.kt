/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.server

import io.github.rogerphilippe.leakweb.server.http.HttpMethod
import io.github.rogerphilippe.leakweb.server.http.HttpRequest
import io.github.rogerphilippe.leakweb.server.http.HttpResponse
import io.github.rogerphilippe.leakweb.server.http.Router
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterTest {
    private val router =
        Router(::errorResponse).apply {
            get("/api/items/{id}") { HttpResponse.text(200, "text/plain", "item ${it.pathParams["id"]}") }
            delete("/api/items/{id}") { HttpResponse.text(200, "text/plain", "deleted") }
            get("/api/fail") { error("broken handler") }
        }

    @Test
    fun `extracts path parameters`() {
        val response = router.route(HttpRequest(HttpMethod.GET, "/api/items/42"))!!

        assertEquals(200, response.status)
        assertEquals("item 42", response.bodyText)
    }

    @Test
    fun `picks the route by method`() {
        assertEquals("deleted", router.route(HttpRequest(HttpMethod.DELETE, "/api/items/42"))!!.bodyText)
    }

    @Test
    fun `returns null for an unknown path so the caller can fall back`() {
        assertNull(router.route(HttpRequest(HttpMethod.GET, "/api/items")))
        assertNull(router.route(HttpRequest(HttpMethod.GET, "/api/items/42/extra")))
    }

    @Test
    fun `answers 405 with Allow for a known path and wrong method`() {
        val response = router.route(HttpRequest(HttpMethod.POST, "/api/items/42"))!!

        assertEquals(405, response.status)
        assertEquals("GET, DELETE", response.headers["Allow"])
    }

    @Test
    fun `turns handler exceptions into a 500 JSON error`() {
        val response = router.route(HttpRequest(HttpMethod.GET, "/api/fail"))!!

        assertEquals(500, response.status)
        assertTrue(response.bodyText, response.bodyText.contains("\"message\":\"broken handler\""))
        assertTrue(response.bodyText.contains("\"schemaVersion\":1"))
    }
}
