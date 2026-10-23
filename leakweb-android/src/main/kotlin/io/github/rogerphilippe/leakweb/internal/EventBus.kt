/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import io.github.rogerphilippe.leakweb.model.Event
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Live events for the web UI (SSE). Never blocks the poster: slow collectors lose the oldest events. */
internal class EventBus(
    private val onEvent: (Event) -> Unit = {},
) {
    private val flow =
        MutableSharedFlow<Event>(extraBufferCapacity = BUFFER_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val events: SharedFlow<Event> = flow.asSharedFlow()

    fun post(event: Event) {
        flow.tryEmit(event)
        onEvent(event)
    }

    private companion object {
        const val BUFFER_CAPACITY = 64
    }
}
