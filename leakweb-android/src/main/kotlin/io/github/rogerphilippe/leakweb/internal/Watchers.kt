/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Application
import android.util.Log
import io.github.rogerphilippe.leakweb.LeakWebConfig
import io.github.rogerphilippe.leakweb.WatcherType
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher

/** The settings of [LeakWebConfig] that a new `LeakWeb.start()` changes while the app runs. */
internal class WatcherSettings(
    config: LeakWebConfig,
) {
    @Volatile
    var watchers: Set<WatcherType> = config.watchers
        private set

    @Volatile
    var retainedThreshold: Int = config.retainedThreshold
        private set

    fun isEnabled(type: WatcherType): Boolean = type in watchers

    fun update(config: LeakWebConfig) {
        watchers = config.watchers
        retainedThreshold = config.retainedThreshold
    }
}

/**
 * Installs each watcher the first time its [WatcherType] is enabled. Once installed, a watcher stays
 * registered and checks [settings] on every event, so turning it off just makes it ignore the events.
 * Fragment and ViewModel watchers are installed only if the app has the AndroidX classes they need.
 */
internal class WatcherInstaller(
    private val objectWatcher: ObjectWatcher,
    private val settings: WatcherSettings,
    private val registerCallbacks: (Application.ActivityLifecycleCallbacks) -> Unit,
    private val classExists: (String) -> Boolean = ::classExists,
    private val installServiceHooks: () -> Boolean = {
        installServiceHooks(objectWatcher) { settings.isEnabled(WatcherType.SERVICE) }
    },
) {
    private enum class Hook { ACTIVITIES, ACTIVITY_VIEW_MODELS, FRAGMENTS, SERVICES }

    private val attempted = mutableSetOf<Hook>()

    private val viewModelWatcher by lazy {
        ViewModelWatcher(objectWatcher) { settings.isEnabled(WatcherType.VIEW_MODEL) }
    }

    /** Applies [config] to [settings] and installs what it enables for the first time. */
    @Synchronized
    fun apply(config: LeakWebConfig) {
        settings.update(config)
        config.watchers.flatMap { it.hooks() }.toSet().forEach { hook ->
            if (attempted.add(hook)) install(hook)
        }
    }

    private fun WatcherType.hooks(): List<Hook> =
        when (this) {
            WatcherType.ACTIVITY -> listOf(Hook.ACTIVITIES)

            WatcherType.FRAGMENT, WatcherType.FRAGMENT_VIEW -> listOf(Hook.FRAGMENTS)

            // ViewModels of fragments come with the fragment callbacks.
            WatcherType.VIEW_MODEL -> listOf(Hook.ACTIVITY_VIEW_MODELS, Hook.FRAGMENTS)

            WatcherType.SERVICE -> listOf(Hook.SERVICES)
        }

    private val hasViewModels: Boolean
        get() = classExists(ANDROIDX_VIEW_MODEL_STORE_OWNER)

    private fun install(hook: Hook) {
        when (hook) {
            Hook.ACTIVITIES -> {
                registerCallbacks(ActivityWatcher(objectWatcher, settings))
            }

            Hook.ACTIVITY_VIEW_MODELS -> {
                if (hasViewModels) {
                    registerCallbacks(ActivityViewModelWatcher(viewModelWatcher))
                } else {
                    Log.d(LEAKWEB_TAG, "AndroidX Lifecycle ViewModel not found, ViewModels are not watched")
                }
            }

            Hook.FRAGMENTS -> {
                if (classExists(ANDROIDX_FRAGMENT_ACTIVITY)) {
                    val reporter = FragmentDestroyedReporter(objectWatcher, settings)
                    // Not takeIf: the ViewModel classes must not even be loaded when they are missing.
                    registerCallbacks(AndroidXFragmentWatcher(reporter, if (hasViewModels) viewModelWatcher else null))
                } else {
                    Log.d(LEAKWEB_TAG, "AndroidX Fragment not found, fragments are not watched")
                }
            }

            Hook.SERVICES -> {
                installServiceHooks()
            }
        }
    }
}

private fun classExists(className: String): Boolean =
    try {
        Class.forName(className)
        true
    } catch (_: ClassNotFoundException) {
        false
    }
