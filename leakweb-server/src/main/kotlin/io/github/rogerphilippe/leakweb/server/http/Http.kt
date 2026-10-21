/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.server.http

import java.io.InputStream

// HTTP types independent of NanoHTTPD: routes are plain (HttpRequest) -> HttpResponse functions,
// testable without a socket, and NanoHTTPD stays isolated in LeakWebServer.

enum class HttpMethod { GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH }

class HttpRequest(
    val method: HttpMethod,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
    /** Values of the `{name}` segments of the matched route. */
    val pathParams: Map<String, String> = emptyMap(),
) {
    /** Header names are case-insensitive; stored lowercase. */
    val headers: Map<String, String> = headers.mapKeys { it.key.lowercase() }

    fun header(name: String): String? = headers[name.lowercase()]

    fun cookie(name: String): String? =
        header("cookie")
            ?.split(';')
            ?.map { it.trim().split('=', limit = 2) }
            ?.firstOrNull { it.size == 2 && it[0] == name }
            ?.get(1)

    fun withPathParams(params: Map<String, String>) = HttpRequest(method, path, query, headers, params)
}

sealed interface HttpBody {
    class Bytes(
        val bytes: ByteArray,
    ) : HttpBody

    /** Fixed length stream, e.g. a file. */
    class Stream(
        val stream: InputStream,
        val length: Long,
    ) : HttpBody

    /** Endless chunked stream (Server-Sent Events); never compressed. */
    class EventStream(
        val stream: InputStream,
    ) : HttpBody
}

class HttpResponse(
    val status: Int,
    val contentType: String,
    val body: HttpBody,
    val headers: Map<String, String> = emptyMap(),
) {
    fun withHeaders(extra: Map<String, String>) = HttpResponse(status, contentType, body, headers + extra)

    val bodyText: String
        get() = (body as? HttpBody.Bytes)?.bytes?.decodeToString().orEmpty()

    companion object {
        fun text(
            status: Int,
            contentType: String,
            text: String,
            headers: Map<String, String> = emptyMap(),
        ) = HttpResponse(status, contentType, HttpBody.Bytes(text.encodeToByteArray()), headers)
    }
}

object HttpStatus {
    const val OK = 200
    const val ACCEPTED = 202
    const val NO_CONTENT = 204
    const val FOUND = 302
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val METHOD_NOT_ALLOWED = 405
    const val INTERNAL_SERVER_ERROR = 500
    const val SERVICE_UNAVAILABLE = 503

    fun description(status: Int): String =
        when (status) {
            OK -> "OK"
            ACCEPTED -> "Accepted"
            NO_CONTENT -> "No Content"
            FOUND -> "Found"
            BAD_REQUEST -> "Bad Request"
            UNAUTHORIZED -> "Unauthorized"
            FORBIDDEN -> "Forbidden"
            NOT_FOUND -> "Not Found"
            METHOD_NOT_ALLOWED -> "Method Not Allowed"
            SERVICE_UNAVAILABLE -> "Service Unavailable"
            else -> "Internal Server Error"
        }
}
