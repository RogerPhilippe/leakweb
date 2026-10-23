/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.server

import io.github.rogerphilippe.leakweb.model.LeakWebJson
import io.github.rogerphilippe.leakweb.model.SCHEMA_VERSION
import io.github.rogerphilippe.leakweb.server.http.HttpResponse
import io.github.rogerphilippe.leakweb.server.http.HttpStatus
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"

private val noStore = mapOf("Cache-Control" to "no-store")

/** `{"schemaVersion":1,"data":<value>}` */
internal fun <T> jsonResponse(
    serializer: KSerializer<T>,
    value: T,
    status: Int = HttpStatus.OK,
): HttpResponse = jsonEnvelope(status, "data", LeakWebJson.encodeToJsonElement(serializer, value))

/** `{"schemaVersion":1,"error":{"status":404,"message":"..."}}` */
internal fun errorResponse(
    status: Int,
    message: String,
): HttpResponse =
    jsonEnvelope(
        status,
        "error",
        buildJsonObject {
            put("status", status)
            put("message", message)
        },
    )

private fun jsonEnvelope(
    status: Int,
    key: String,
    element: JsonElement,
): HttpResponse {
    val body =
        buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put(key, element)
        }
    return HttpResponse.text(status, JSON_CONTENT_TYPE, body.toString(), noStore)
}
