/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.watcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.ReferenceQueue

class KeyedWeakReferenceTest {
    private val queue = ReferenceQueue<Any>()

    @Test
    fun `new reference is watched but not retained`() {
        val referent = Any()
        val reference = KeyedWeakReference(referent, "key", "description", 100L, queue)

        assertSame(referent, reference.get())
        assertEquals(KeyedWeakReference.NOT_RETAINED, reference.retainedUptimeMillis)
        assertFalse(reference.isRetained)
    }

    @Test
    fun `reference is retained once retained uptime is set`() {
        val reference = KeyedWeakReference(Any(), "key", "description", 100L, queue)

        reference.retainedUptimeMillis = 5_100L

        assertTrue(reference.isRetained)
    }
}
