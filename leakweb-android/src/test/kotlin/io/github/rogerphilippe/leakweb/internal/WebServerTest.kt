/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import io.github.rogerphilippe.leakweb.LeakWebConfig
import io.github.rogerphilippe.leakweb.LeakWebSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WebServerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private var generated = 0
    private val generate = { "token-${++generated}" }
    private val tokenFile by lazy { File(tempFolder.root, "leakweb/token") }

    @Test
    fun `a fixed token wins`() {
        assertEquals("fixed", resolveToken(LeakWebConfig(token = "fixed", persistToken = true), tokenFile, generate))
        assertFalse(tokenFile.exists())
    }

    @Test
    fun `a new token is generated on every start by default`() {
        val first = resolveToken(LeakWebConfig(), tokenFile, generate)
        val second = resolveToken(LeakWebConfig(), tokenFile, generate)

        assertNotEquals(first, second)
        assertFalse(tokenFile.exists())
    }

    @Test
    fun `a persisted token is reused`() {
        val config = LeakWebConfig(persistToken = true)

        val first = resolveToken(config, tokenFile, generate)
        val second = resolveToken(config, tokenFile, generate)

        assertEquals("token-1", first)
        assertEquals(first, second)
        assertEquals(first, tokenFile.readText())
    }

    @Test
    fun `only server settings decide whether a new start restarts the server`() {
        val config = LeakWebConfig(port = 9090)

        assertEquals(
            config.serverSettings(),
            config.copy(retainedThreshold = 1, watchers = emptySet()).serverSettings(),
        )
        assertNotEquals(config.serverSettings(), config.copy(port = 9091).serverSettings())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tokens that would need escaping are rejected`() {
        LeakWebConfig(token = "a b;c")
    }

    @Test
    fun `startup message has the adb forward command and the URLs`() {
        val session = LeakWebSession(token = "abc", port = 8081, url = "http://localhost:8081/?token=abc")

        val message = startupMessage(session, listOf("192.168.0.5"))

        assertEquals(
            """
            LeakWeb server running on port 8081. On the computer, run:
              adb forward tcp:8081 tcp:8081
            then open http://localhost:8081/?token=abc
            On the local network: http://192.168.0.5:8081/?token=abc
            """.trimIndent(),
            message,
        )
    }
}
