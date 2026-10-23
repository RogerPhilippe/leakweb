/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `leakweb-noop` must keep the public API of `leakweb-android`, so the same code compiles in debug and
 * release builds. The shared types live in `leakweb-api`; this checks the `LeakWeb` entry point,
 * which each module declares on its own.
 */
class ApiCompatibilityTest {
    @Test
    fun noopLeakWebHasTheSamePublicSignaturesAsAndroid() {
        val android = publicSignatures(requireNotNull(System.getProperty("leakweb.androidClasses")))
        val noop = publicSignatures(requireNotNull(System.getProperty("leakweb.noopClasses")))

        assertTrue("No public members found in leakweb-android", android.isNotEmpty())
        assertEquals(android, noop)
    }

    // javap does not load the referenced classes, so the android.* types need no classpath.
    private fun publicSignatures(classesDir: String): List<String> {
        val process =
            ProcessBuilder("javap", "-public", "-cp", classesDir, LEAKWEB_CLASS_NAME)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "javap failed for ${File(classesDir)}:\n$output" }
        return output
            .lines()
            .drop(1) // "Compiled from ..."
            .map { it.trim().replace("synchronized ", "") } // not part of the signature
            .filter { it.isNotEmpty() }
            .sorted()
    }

    private companion object {
        const val LEAKWEB_CLASS_NAME = "io.github.rogerphilippe.leakweb.LeakWeb"
    }
}
