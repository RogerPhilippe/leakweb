/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.model

import kotlinx.serialization.json.Json

/** Version of the JSON format, sent in API responses and stored on disk to allow migrations. */
const val SCHEMA_VERSION = 1

/** JSON configuration shared by the store and the HTTP server. */
val LeakWebJson: Json =
    Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
