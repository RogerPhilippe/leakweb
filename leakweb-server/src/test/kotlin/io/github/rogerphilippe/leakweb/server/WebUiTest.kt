/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the rules of the web UI: served with the right types, CSP-compatible, no HTML from strings. */
class WebUiTest {
    private val staticFiles = StaticFiles()

    private val webRoot =
        File(
            requireNotNull(javaClass.classLoader.getResource("leakweb/web/index.html")).toURI(),
        ).parentFile

    private val scripts = webRoot.walk().filter { it.extension == "js" }.toList()

    @Test
    fun `serves every UI file with its MIME type`() {
        mapOf(
            "/" to "text/html; charset=utf-8",
            "/app.js" to "text/javascript; charset=utf-8",
            "/styles.css" to "text/css; charset=utf-8",
            "/favicon.svg" to "image/svg+xml",
        ).forEach { (path, contentType) ->
            assertEquals(path, contentType, staticFiles.serve(path)?.contentType)
        }
    }

    @Test
    fun `every module imported by the UI exists`() {
        val imports = Regex("""from '\./([\w-]+\.js)'|import '\./([\w-]+\.js)'""")
        scripts.forEach { script ->
            imports.findAll(script.readText()).forEach { match ->
                val imported = match.groupValues.drop(1).first { it.isNotEmpty() }
                assertTrue("${script.name} imports $imported", File(webRoot, imported).isFile)
            }
        }
    }

    @Test
    fun `the page has no inline script or style, which the Content-Security-Policy blocks`() {
        val html = File(webRoot, "index.html").readText()

        assertFalse(Regex("<script(?![^>]*\\ssrc=)").containsMatchIn(html))
        assertFalse(html.contains("<style"))
        assertFalse(html.contains(" style="))
        assertFalse(Regex("\\son[a-z]+=").containsMatchIn(html))
    }

    @Test
    fun `scripts never turn strings into HTML, so heap strings cannot inject markup`() {
        val forbidden =
            listOf("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(", "new Function")
        assertTrue(scripts.isNotEmpty())
        scripts.forEach { script ->
            val source = script.readText()
            forbidden.forEach { api -> assertFalse("${script.name} uses $api", source.contains(api)) }
        }
    }
}
