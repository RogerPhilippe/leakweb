/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.server

import io.github.rogerphilippe.leakweb.server.http.HttpBody
import io.github.rogerphilippe.leakweb.server.http.HttpResponse
import io.github.rogerphilippe.leakweb.server.http.HttpStatus

/** Serves the web UI bundled in the classpath under [root]; `/` is `index.html`. */
internal class StaticFiles(
    private val classLoader: ClassLoader = StaticFiles::class.java.classLoader,
    private val root: String = "leakweb/web",
) {
    /** Null if there is no such file. */
    fun serve(path: String): HttpResponse? {
        val relative = path.trimStart('/').ifEmpty { "index.html" }
        val segments = relative.split('/')
        if (segments.any { it in FORBIDDEN_SEGMENTS || '\\' in it }) return null
        val bytes = classLoader.getResourceAsStream("$root/$relative")?.use { it.readBytes() } ?: return null
        return HttpResponse(HttpStatus.OK, mimeType(relative), HttpBody.Bytes(bytes))
    }

    private companion object {
        val FORBIDDEN_SEGMENTS = setOf("", ".", "..")
    }

    private fun mimeType(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html; charset=utf-8"
            "js", "mjs" -> "text/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "json", "map" -> JSON_CONTENT_TYPE
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "ico" -> "image/x-icon"
            "woff2" -> "font/woff2"
            "txt" -> "text/plain; charset=utf-8"
            else -> "application/octet-stream"
        }
}
