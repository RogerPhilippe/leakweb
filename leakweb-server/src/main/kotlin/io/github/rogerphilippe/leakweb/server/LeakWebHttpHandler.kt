/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.server

import io.github.rogerphilippe.leakweb.server.http.HttpMethod
import io.github.rogerphilippe.leakweb.server.http.HttpRequest
import io.github.rogerphilippe.leakweb.server.http.HttpResponse
import io.github.rogerphilippe.leakweb.server.http.HttpStatus
import io.github.rogerphilippe.leakweb.server.http.Router
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

private const val TOKEN_QUERY_PARAMETER = "token"
private const val TOKEN_HEADER = "X-LeakWeb-Token"
private const val BEARER_PREFIX = "Bearer "
private const val HTML_CONTENT_TYPE = "text/html; charset=utf-8"

private val htmlSecurityHeaders =
    mapOf(
        "Content-Security-Policy" to "default-src 'self'",
        "X-Content-Type-Options" to "nosniff",
        "Referrer-Policy" to "no-referrer",
    )

/**
 * Everything between the socket and the routes, in order: Host/Origin checks, token, `/api` routes,
 * then static files. Independent of NanoHTTPD, so it is tested without sockets.
 */
internal class LeakWebHttpHandler(
    token: String,
    /** Real listening port; the session cookie is per port so two apps on one machine do not clash. */
    port: Int,
    private val router: Router,
    private val staticFiles: StaticFiles,
) {
    private val tokenBytes = token.encodeToByteArray()
    private val cookieName = "leakweb_$port"

    fun handle(request: HttpRequest): HttpResponse {
        val isApi = request.path == "/api" || request.path.startsWith("/api/")
        (rejectUnsafe(request) ?: authenticate(request, isApi))?.let { return it }
        val response =
            if (isApi) {
                router.route(request) ?: errorResponse(HttpStatus.NOT_FOUND, "Unknown route ${request.path}")
            } else {
                staticFiles.serve(request.path) ?: htmlPage(HttpStatus.NOT_FOUND, "Not found")
            }
        return if (response.contentType.startsWith("text/html")) response.withHeaders(htmlSecurityHeaders) else response
    }

    /** Null when a valid token is presented; otherwise the cookie exchange redirect or a 401. */
    private fun authenticate(
        request: HttpRequest,
        isApi: Boolean,
    ): HttpResponse? {
        val queryToken = request.query[TOKEN_QUERY_PARAMETER]
        if (!isApi && request.method == HttpMethod.GET && queryToken != null) {
            queryToken.takeIf(::isValid)?.let { return exchangeTokenForCookie(request, it) }
        }
        val presented =
            listOfNotNull(
                request.cookie(cookieName),
                request.header("authorization")?.takeIf { it.startsWith(BEARER_PREFIX) }?.removePrefix(BEARER_PREFIX),
                request.header(TOKEN_HEADER),
                queryToken,
            )
        return if (presented.any(::isValid)) null else unauthorized(isApi)
    }

    /** Blocks DNS rebinding (Host must be localhost or an IP) and cross-site POST/DELETE (Origin must match). */
    private fun rejectUnsafe(request: HttpRequest): HttpResponse? {
        val host = request.header("host") ?: return errorResponse(HttpStatus.BAD_REQUEST, "Missing Host header")
        if (!isAllowedHost(host.substringBeforePortOf())) {
            return errorResponse(HttpStatus.FORBIDDEN, "Host not allowed")
        }
        val origin = request.header("origin")
        val changesState = request.method != HttpMethod.GET && request.method != HttpMethod.HEAD
        if (changesState && origin != null && originAuthority(origin) != host.lowercase()) {
            return errorResponse(HttpStatus.FORBIDDEN, "Origin not allowed")
        }
        return null
    }

    private fun exchangeTokenForCookie(
        request: HttpRequest,
        token: String,
    ): HttpResponse {
        val otherParameters = request.query.filterKeys { it != TOKEN_QUERY_PARAMETER }
        val location =
            if (otherParameters.isEmpty()) {
                request.path
            } else {
                request.path + "?" +
                    otherParameters.entries.joinToString("&") { "${it.key.urlEncoded()}=${it.value.urlEncoded()}" }
            }
        return HttpResponse.text(
            HttpStatus.FOUND,
            HTML_CONTENT_TYPE,
            "",
            mapOf(
                "Location" to location,
                "Set-Cookie" to "$cookieName=$token; HttpOnly; SameSite=Strict; Path=/",
                "Cache-Control" to "no-store",
            ),
        )
    }

    private fun isValid(candidate: String) = MessageDigest.isEqual(candidate.encodeToByteArray(), tokenBytes)

    private fun unauthorized(isApi: Boolean): HttpResponse =
        if (isApi) {
            errorResponse(HttpStatus.UNAUTHORIZED, "Missing or invalid token")
        } else {
            htmlPage(
                HttpStatus.UNAUTHORIZED,
                "Missing or invalid token. Open the URL with the token printed in logcat (tag LeakWeb).",
            ).withHeaders(htmlSecurityHeaders)
        }

    private fun htmlPage(
        status: Int,
        message: String,
    ) = HttpResponse.text(
        status,
        HTML_CONTENT_TYPE,
        "<!doctype html><html><head><meta charset=\"utf-8\"><title>LeakWeb</title></head>" +
            "<body><p>$message</p></body></html>",
    )
}

private fun String.substringBeforePortOf(): String =
    if (startsWith("[")) substringBefore(']') + "]" else substringBeforeLast(':', this)

private fun isAllowedHost(hostName: String): Boolean {
    val name = hostName.lowercase()
    return name == "localhost" || name.isIpv4Literal() || (name.startsWith("[") && name.endsWith("]"))
}

private fun String.isIpv4Literal(): Boolean {
    val parts = split('.')
    return parts.size == IPV4_PARTS && parts.all { part -> part.toIntOrNull()?.let { it in 0..MAX_IPV4_PART } == true }
}

private const val IPV4_PARTS = 4
private const val MAX_IPV4_PART = 255

/** `http://localhost:8080` -> `localhost:8080`; null if malformed. */
private fun originAuthority(origin: String): String? = runCatching { URI(origin).rawAuthority?.lowercase() }.getOrNull()

private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")
