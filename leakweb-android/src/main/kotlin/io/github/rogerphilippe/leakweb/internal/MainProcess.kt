/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.IOException

/**
 * True if this is the app's main process. `Application.onCreate` and androidx.startup run in every
 * process of the app; watchers and the server must only start in the main one.
 */
internal fun Context.isMainProcess(): Boolean = isMainProcess(currentProcessName(), applicationInfo.processName)

/** An unknown current process name is treated as main: better to watch than to silently do nothing. */
internal fun isMainProcess(
    currentProcessName: String?,
    mainProcessName: String,
): Boolean = currentProcessName == null || currentProcessName == mainProcessName

private fun currentProcessName(): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Application.getProcessName()
    } else {
        try {
            processNameFromCmdline(File("/proc/self/cmdline").readText())
        } catch (_: IOException) {
            null
        }
    }

/** `/proc/self/cmdline` holds the arguments separated (and padded) by NUL characters. */
internal fun processNameFromCmdline(cmdline: String): String? =
    cmdline.substringBefore('\u0000').trim().ifEmpty { null }
