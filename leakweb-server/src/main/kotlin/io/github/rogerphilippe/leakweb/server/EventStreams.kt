/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.server

import io.github.rogerphilippe.leakweb.model.Event
import io.github.rogerphilippe.leakweb.model.LeakWebJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Body of one SSE response. NanoHTTPD's worker thread blocks in [read] until a message is offered,
 * then writes it as a chunk. NanoHTTPD closes the stream when the client goes away (the next write
 * fails, at the latest on the next ping) or [finish] ends it, and [onClose] releases the producer.
 */
internal class EventStreamInputStream(
    private val onClose: () -> Unit,
) : InputStream() {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val closed = AtomicBoolean(false)
    private var current: ByteArray = EMPTY
    private var position = 0

    fun offer(message: String) {
        if (!closed.get()) queue.offer(message.encodeToByteArray())
    }

    /** Makes the reader see the end of the stream, which ends the response. */
    fun finish() {
        queue.offer(END_OF_STREAM)
    }

    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) == -1) -1 else single[0].toInt() and BYTE_MASK
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (position == current.size) {
            if (closed.get()) return -1
            current = queue.take()
            position = 0
            if (current === END_OF_STREAM) return -1
        }
        val count = minOf(length, current.size - position)
        current.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            queue.offer(END_OF_STREAM)
            onClose()
        }
    }

    private companion object {
        val EMPTY = ByteArray(0)
        val END_OF_STREAM = ByteArray(0)
        const val BYTE_MASK = 0xFF
    }
}

/**
 * Fans [events] out to at most [maxClients] SSE clients, with a comment ping so dead clients are noticed.
 * A client that went away keeps its slot until the next ping fails, so when the limit is reached the
 * oldest stream is ended instead of refusing the new client (usually the tab that was just reloaded).
 */
internal class EventStreamHub(
    private val events: Flow<Event>,
    private val scope: CoroutineScope,
    private val maxClients: Int,
    private val pingIntervalMillis: Long,
) {
    // Insertion order: the first one is the oldest.
    private val streams = linkedSetOf<EventStreamInputStream>()

    val clientCount: Int
        @Synchronized get() = streams.size

    @Synchronized
    fun open(): EventStreamInputStream {
        if (streams.size >= maxClients) {
            val oldest = streams.first()
            streams.remove(oldest)
            oldest.finish()
        }
        lateinit var stream: EventStreamInputStream
        // Lazy: the producer must not run before `stream` is assigned.
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                stream.offer(": connected\n\n")
                launch {
                    while (isActive) {
                        delay(pingIntervalMillis)
                        stream.offer(": ping\n\n")
                    }
                }
                events.collect { stream.offer(it.toServerSentEvent()) }
            }
        stream =
            EventStreamInputStream {
                job.cancel()
                synchronized(this) { streams.remove(stream) }
            }
        streams += stream
        job.start()
        return stream
    }

    /** Ends every open stream, e.g. when the server stops. */
    fun finishAll() {
        synchronized(this) { streams.toList() }.forEach { it.finish() }
    }
}

/** `event: <type>\ndata: <json>\n\n`; the type is the serial name used as the JSON discriminator. */
internal fun Event.toServerSentEvent(): String {
    val json = LeakWebJson.encodeToJsonElement(Event.serializer(), this)
    val type =
        json.jsonObject
            .getValue("type")
            .jsonPrimitive.content
    return "event: $type\ndata: $json\n\n"
}
