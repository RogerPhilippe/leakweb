/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.internal

import android.annotation.SuppressLint
import android.app.Service
import android.os.Build
import android.os.Handler
import android.util.Log
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.WeakHashMap

/**
 * Watches services after `Service#onDestroy()`, from two framework events (see [installServiceHooks]):
 * the main thread receives the message to stop a service, then the service tells the system it is
 * done (`serviceDoneExecuting`), after `onDestroy()` returned. Services are keyed by their binder token.
 */
internal class ServiceDestroyedReporter(
    private val objectWatcher: ObjectWatcher,
    private val isEnabled: () -> Boolean,
    /** The app's running services by token (`ActivityThread.mServices`). */
    private val services: () -> Map<*, Service>,
) {
    // Weak keys and values: a service that never finishes stopping must not be kept in memory by LeakWeb.
    private val stoppingServices = WeakHashMap<Any, WeakReference<Service>>()

    @Synchronized
    fun onStopService(token: Any) {
        if (!isEnabled()) return
        services()[token]?.let { stoppingServices[token] = WeakReference(it) }
    }

    fun onServiceDoneExecuting(token: Any) {
        val service = synchronized(this) { stoppingServices.remove(token) }?.get() ?: return
        objectWatcher.expectWeaklyReachable(
            service,
            "${service.javaClass.name} received Service#onDestroy() callback",
        )
    }
}

/** `ActivityThread.H.STOP_SERVICE`, stable since the first Android versions. */
private const val STOP_SERVICE_MESSAGE = 116

/**
 * Feeds [ServiceDestroyedReporter] by hooking hidden framework internals, as LeakCanary does:
 * - the callback of the `ActivityThread.mH` handler sees `STOP_SERVICE` before the service is destroyed;
 * - a proxy replacing the app's `IActivityManager` sees `serviceDoneExecuting()` after `onDestroy()`.
 *
 * These are non-SDK interfaces: a future Android version may change or block them. Any failure is
 * logged and leaves services unwatched; nothing else in LeakWeb depends on it. Returns false on failure.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
internal fun installServiceHooks(
    objectWatcher: ObjectWatcher,
    isEnabled: () -> Boolean,
): Boolean =
    runCatching {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val activityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)

        @Suppress("UNCHECKED_CAST")
        val services = activityThreadClass.accessibleField("mServices").get(activityThread) as Map<*, Service>
        val reporter = ServiceDestroyedReporter(objectWatcher, isEnabled) { services }

        // The proxy first: if it cannot be installed, the handler hook would collect services for nothing.
        hookServiceDoneExecuting(reporter)
        val handler = activityThreadClass.accessibleField("mH").get(activityThread) as Handler
        val callbackField = Handler::class.java.accessibleField("mCallback")
        val originalCallback = callbackField.get(handler) as Handler.Callback?
        callbackField.set(
            handler,
            Handler.Callback { message ->
                if (message.what == STOP_SERVICE_MESSAGE) message.obj?.let(reporter::onStopService)
                originalCallback?.handleMessage(message) ?: false
            },
        )
    }.onFailure { error ->
        Log.w(LEAKWEB_TAG, "Services are not watched: this Android version blocks the hooks LeakWeb needs", error)
    }.isSuccess

@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
private fun hookServiceDoneExecuting(reporter: ServiceDestroyedReporter) {
    val (holderClassName, singletonFieldName) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            "android.app.ActivityManager" to "IActivityManagerSingleton"
        } else {
            "android.app.ActivityManagerNative" to "gDefault"
        }
    val singleton = Class.forName(holderClassName).accessibleField(singletonFieldName).get(null)
    val instanceField = Class.forName("android.util.Singleton").accessibleField("mInstance")
    val activityManager = checkNotNull(instanceField.get(singleton)) { "IActivityManager not created yet" }
    val activityManagerInterface = Class.forName("android.app.IActivityManager")

    val proxy =
        Proxy.newProxyInstance(
            activityManagerInterface.classLoader,
            arrayOf(activityManagerInterface),
        ) { _, method, args ->
            // The token is the first argument in every Android version.
            if (method.name == "serviceDoneExecuting") args?.firstOrNull()?.let(reporter::onServiceDoneExecuting)
            try {
                @Suppress("SpreadOperator")
                method.invoke(activityManager, *(args ?: emptyArray()))
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        }
    instanceField.set(singleton, proxy)
}

private fun Class<*>.accessibleField(name: String): Field = getDeclaredField(name).apply { isAccessible = true }
