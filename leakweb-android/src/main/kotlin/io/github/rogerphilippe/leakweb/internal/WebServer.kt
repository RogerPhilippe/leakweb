/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Application
import android.util.Base64
import android.util.Log
import io.github.rogerphilippe.leakweb.BindAddress
import io.github.rogerphilippe.leakweb.LeakWebConfig
import io.github.rogerphilippe.leakweb.LeakWebSession
import io.github.rogerphilippe.leakweb.WatcherType
import io.github.rogerphilippe.leakweb.model.RetainedObjectInfo
import io.github.rogerphilippe.leakweb.model.ServerStatus
import io.github.rogerphilippe.leakweb.server.Commands
import io.github.rogerphilippe.leakweb.server.LeakWebServer
import io.github.rogerphilippe.leakweb.server.ServerConfig
import io.github.rogerphilippe.leakweb.watcher.GcTrigger
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

internal const val LIBRARY_VERSION = "0.1.0-SNAPSHOT"
private const val TOKEN_BYTES = 32

/** The web server started by `LeakWeb.start()` and the config it was started with. */
internal class RunningServer(
    val config: LeakWebConfig,
    val session: LeakWebSession,
    private val server: LeakWebServer,
) {
    fun stop() = server.stop()

    companion object {
        /** Throws [java.io.IOException] if no port could be bound. */
        fun start(
            application: Application,
            runtime: LeakWebRuntime,
            config: LeakWebConfig,
        ): RunningServer {
            val token = resolveToken(config, File(application.filesDir, "leakweb/token"), ::newToken)
            val host =
                if (config.bindAddress ==
                    BindAddress.AllInterfaces
                ) {
                    ServerConfig.ALL_INTERFACES
                } else {
                    ServerConfig.LOOPBACK
                }
            val server =
                LeakWebServer(
                    config =
                        ServerConfig(
                            host = host,
                            port = config.port,
                            portFallback = config.portFallback,
                            token = token,
                        ),
                    store = runtime.store,
                    commands = AndroidCommands(application, runtime),
                    events = runtime.eventBus.events,
                )
            val port = server.start()
            val session = LeakWebSession(token = token, port = port, url = "http://localhost:$port/?token=$token")
            if (config.logStartup) {
                val lanAddresses =
                    if (config.bindAddress ==
                        BindAddress.AllInterfaces
                    ) {
                        lanIpv4Addresses()
                    } else {
                        emptyList()
                    }
                Log.i(LEAKWEB_TAG, startupMessage(session, lanAddresses))
            }
            return RunningServer(config, session, server)
        }
    }
}

/** [LeakWebConfig] without the watcher settings, which are applied without restarting the server. */
internal fun LeakWebConfig.serverSettings(): LeakWebConfig =
    copy(retainedThreshold = LeakWebConfig.DEFAULT_RETAINED_THRESHOLD, watchers = WatcherType.defaults)

/** The fixed token, the persisted one, or a new random one (persisted if asked). */
internal fun resolveToken(
    config: LeakWebConfig,
    tokenFile: File,
    generate: () -> String,
): String {
    config.token?.let { return it }
    if (!config.persistToken) return generate()
    return tokenFile
        .takeIf { it.isFile }
        ?.readText()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: generate().also { token ->
            tokenFile.parentFile?.mkdirs()
            tokenFile.writeText(token)
        }
}

/** 32 random bytes, Base64 URL-safe without padding (43 characters). */
private fun newToken(): String {
    val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

internal fun startupMessage(
    session: LeakWebSession,
    lanAddresses: List<String>,
): String =
    buildString {
        appendLine("LeakWeb server running on port ${session.port}. On the computer, run:")
        appendLine("  adb forward tcp:${session.port} tcp:${session.port}")
        append("then open ${session.url}")
        lanAddresses.forEach { address ->
            appendLine()
            append("On the local network: http://$address:${session.port}/?token=${session.token}")
        }
    }

private fun lanIpv4Addresses(): List<String> =
    runCatching {
        NetworkInterface
            .getNetworkInterfaces()
            .toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

/** What the web server can read and do in the app. */
internal class AndroidCommands(
    private val application: Application,
    private val runtime: LeakWebRuntime,
) : Commands {
    private val objectWatcher = runtime.objectWatcher

    override fun status() =
        ServerStatus(
            libraryVersion = LIBRARY_VERSION,
            appPackage = application.packageName,
            processName = application.applicationInfo.processName,
            port = -1,
            watchedObjectCount = objectWatcher.watchedObjectCount,
            retainedObjectCount = objectWatcher.retainedObjectCount,
        )

    override fun retainedObjects() =
        objectWatcher.retainedObjects.map {
            RetainedObjectInfo(it.key, it.description, it.className, it.watchUptimeMillis, it.retainedUptimeMillis)
        }

    override fun memory() = runtime.memorySampler.latest()

    override fun memoryHistory() = runtime.memorySampler.history

    override fun requestHeapDump() = runtime.heapDumpTrigger.requestManualDump()

    override fun runGc() = GcTrigger.Default.runGc()

    override fun deleteAnalysis(id: String) = runtime.store.delete(id)
}
