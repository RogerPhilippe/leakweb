/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainProcessTest {
    @Test
    fun `same process name is the main process`() {
        assertTrue(isMainProcess("com.example.app", "com.example.app"))
    }

    @Test
    fun `secondary process is not the main process`() {
        assertFalse(isMainProcess("com.example.app:leakweb", "com.example.app"))
    }

    @Test
    fun `unknown process name is treated as main`() {
        assertTrue(isMainProcess(null, "com.example.app"))
    }

    @Test
    fun `process name is the first NUL separated argument`() {
        assertEquals("com.example.app:remote", processNameFromCmdline("com.example.app:remote\u0000\u0000\u0000"))
    }

    @Test
    fun `empty cmdline has no process name`() {
        assertNull(processNameFromCmdline("\u0000"))
    }
}
