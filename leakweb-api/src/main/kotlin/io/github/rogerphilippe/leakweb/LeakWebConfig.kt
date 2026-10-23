/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb

/** Configuration of `LeakWeb.start()`. The defaults are what the automatic start uses. */
data class LeakWebConfig(
    /** Port of the web server. */
    val port: Int = DEFAULT_PORT,
    /** Tries the next ports if [port] is in use; the real port is in [LeakWebSession.port]. */
    val portFallback: Boolean = true,
    val bindAddress: BindAddress = BindAddress.Loopback,
    /** Fixed token (handy to bookmark the URL), made of `A-Z a-z 0-9 . _ ~ -`; null generates a random one. */
    val token: String? = null,
    /** Reuses the generated token across restarts (stored in the app files). Ignored if [token] is set. */
    val persistToken: Boolean = false,
    /** Logs the URL with the token and the `adb forward` command when the server starts. */
    val logStartup: Boolean = true,
    /**
     * Retained objects needed to dump the heap while the app is visible (1 in background).
     * Like [watchers], a later start with another value applies it without restarting the server.
     */
    val retainedThreshold: Int = DEFAULT_RETAINED_THRESHOLD,
    /** Shows an ongoing notification while LeakWeb is active. Disabled by default. */
    val persistentNotification: Boolean = false,
    /**
     * What is watched automatically. Empty keeps only the web server, live memory and `LeakWeb.watch()`,
     * e.g. next to LeakCanary. Watchers turned on by a later start apply to activities created afterwards.
     */
    val watchers: Set<WatcherType> = WatcherType.defaults,
) {
    init {
        require(port in 0..MAX_PORT) { "Invalid port $port" }
        require(token == null || TOKEN_PATTERN.matches(token)) { "The token must match $TOKEN_PATTERN" }
        require(retainedThreshold >= 1) { "retainedThreshold must be at least 1" }
    }

    companion object {
        private const val MAX_PORT = 65_535

        // Characters that go unescaped in a URL query and in a cookie value.
        private val TOKEN_PATTERN = Regex("^[A-Za-z0-9._~-]{1,256}$")

        const val DEFAULT_PORT = 8080
        const val DEFAULT_RETAINED_THRESHOLD = 5
    }
}

/** Network interfaces the web server listens on. */
enum class BindAddress {
    /** Only `127.0.0.1`: reachable from the computer with `adb forward`. */
    Loopback,

    /** Every interface, including the local network. Anyone on the network with the token gets heap dumps. */
    AllInterfaces,
}

/** Kinds of objects watched automatically after their lifecycle ends. */
enum class WatcherType {
    /** Activities after `onDestroy()`. */
    ACTIVITY,

    /** AndroidX fragments after `onDestroy()` (only if the app uses AndroidX Fragment). */
    FRAGMENT,

    /**
     * Views of AndroidX fragments after `onDestroyView()`, e.g. a view or binding kept in a field of a
     * fragment that is in the back stack (only if the app uses AndroidX Fragment).
     */
    FRAGMENT_VIEW,

    /** AndroidX ViewModels after `onCleared()`, in activities and fragments (only with AndroidX Lifecycle). */
    VIEW_MODEL,

    /**
     * Services after `onDestroy()`. Android has no callback for it, so this hooks hidden framework
     * internals by reflection; if a future Android version blocks them, it logs a warning and stays off.
     */
    SERVICE,
    ;

    companion object {
        val defaults: Set<WatcherType> = setOf(ACTIVITY, FRAGMENT, FRAGMENT_VIEW, VIEW_MODEL, SERVICE)
    }
}

/** A running LeakWeb server, or [NOT_RUNNING]. */
data class LeakWebSession(
    val token: String,
    /** Real port; may differ from the requested one if it was in use. -1 when not running. */
    val port: Int,
    /** `http://localhost:<port>/?token=<token>`, ready to open after `adb forward`. */
    val url: String,
) {
    val isRunning: Boolean
        get() = port > 0

    companion object {
        /** No server: stopped, not started, failed to start, or the no-op library. */
        val NOT_RUNNING = LeakWebSession(token = "", port = -1, url = "")
    }
}
