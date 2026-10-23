/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.server.http

typealias Handler = (HttpRequest) -> HttpResponse

/**
 * Matches requests by method and path pattern (`/api/leaks/{signature}`), extracts the path
 * parameters and turns unknown paths into 404, wrong methods into 405 and handler exceptions into 500.
 */
class Router(
    private val errorResponse: (status: Int, message: String) -> HttpResponse,
) {
    private class Route(
        val method: HttpMethod,
        val segments: List<String>,
        val handler: Handler,
    )

    private val routes = mutableListOf<Route>()

    fun get(
        pattern: String,
        handler: Handler,
    ) = add(HttpMethod.GET, pattern, handler)

    fun post(
        pattern: String,
        handler: Handler,
    ) = add(HttpMethod.POST, pattern, handler)

    fun delete(
        pattern: String,
        handler: Handler,
    ) = add(HttpMethod.DELETE, pattern, handler)

    private fun add(
        method: HttpMethod,
        pattern: String,
        handler: Handler,
    ) {
        routes += Route(method, pattern.segments(), handler)
    }

    /** Null when no route matches the path, so the caller can fall back (e.g. to static files). */
    fun route(request: HttpRequest): HttpResponse? {
        val pathSegments = request.path.segments()
        val matches = routes.mapNotNull { route -> route.match(pathSegments)?.let { route to it } }
        if (matches.isEmpty()) return null
        val (route, params) =
            matches.firstOrNull { it.first.method == request.method }
                ?: return errorResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method ${request.method} not allowed")
                    .withHeaders(mapOf("Allow" to matches.joinToString(", ") { it.first.method.name }))
        return try {
            route.handler(request.withPathParams(params))
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, error.message ?: error.javaClass.name)
        }
    }

    private fun Route.match(pathSegments: List<String>): Map<String, String>? {
        if (segments.size != pathSegments.size) return null
        val params = mutableMapOf<String, String>()
        segments.zip(pathSegments).forEach { (pattern, actual) ->
            when {
                pattern.startsWith("{") && pattern.endsWith("}") -> params[pattern.drop(1).dropLast(1)] = actual
                pattern != actual -> return null
            }
        }
        return params
    }

    private fun String.segments(): List<String> = trim('/').split('/').filter { it.isNotEmpty() }
}
