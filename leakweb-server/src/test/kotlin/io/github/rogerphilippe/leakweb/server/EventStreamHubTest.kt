/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.server

import io.github.rogerphilippe.leakweb.model.Event
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class EventStreamHubTest {
    @Test(timeout = 10_000)
    fun `the producer never runs before the stream exists`() {
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val scope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> errors += e })
        val hub = EventStreamHub(MutableSharedFlow<Event>(), scope, maxClients = 1, pingIntervalMillis = 60_000)

        repeat(500) {
            val stream = hub.open()
            val buffer = ByteArray(64)
            val read = stream.read(buffer, 0, buffer.size)
            assertEquals(": connected\n\n", buffer.decodeToString(0, read))
            stream.close()
        }

        scope.cancel()
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(0, hub.clientCount)
    }
}
