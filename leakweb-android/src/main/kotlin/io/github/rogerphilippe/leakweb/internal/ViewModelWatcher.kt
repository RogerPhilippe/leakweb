/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher

internal const val ANDROIDX_VIEW_MODEL_STORE_OWNER = "androidx.lifecycle.ViewModelStoreOwner"

/** Private field holding the ViewModels of a [ViewModelStore]: `map`, or `mMap` in older versions. */
private val STORE_MAP_FIELD_NAMES = listOf("map", "mMap")

/**
 * Watches the ViewModels of activities and fragments after `ViewModel#onCleared()`.
 *
 * There is no callback for the ViewModels of other code, and a ViewModel added to the store to be
 * cleared with the others cannot see them anymore: since Lifecycle 2.11 the store empties its map
 * before clearing its ViewModels. So [install] replaces that private map, by reflection, with a copy
 * that reports its ViewModels when the store clears it. If a future AndroidX version changes the
 * field, nothing is watched and a warning is logged.
 */
internal class ViewModelWatcher(
    private val objectWatcher: ObjectWatcher,
    private val isEnabled: () -> Boolean,
) {
    fun install(owner: ViewModelStoreOwner) {
        if (!isEnabled()) return
        if (!owner.viewModelStore.reportViewModelsOnClear(::onStoreCleared)) {
            Log.w(LEAKWEB_TAG, "ViewModels are not watched: ViewModelStore has no map field $STORE_MAP_FIELD_NAMES")
        }
    }

    private fun onStoreCleared(viewModels: List<ViewModel>) {
        if (!isEnabled()) return
        viewModels.forEach { viewModel ->
            objectWatcher.expectWeaklyReachable(
                viewModel,
                "${viewModel.javaClass.name} received ViewModel#onCleared() callback",
            )
        }
    }
}

/**
 * Makes the store call [onClear] with its ViewModels when it is cleared; false if its map was not found.
 * Does nothing if already done, e.g. for the store an activity keeps across a configuration change.
 */
internal fun ViewModelStore.reportViewModelsOnClear(onClear: (List<ViewModel>) -> Unit): Boolean {
    val field =
        STORE_MAP_FIELD_NAMES.firstNotNullOfOrNull { name ->
            runCatching { ViewModelStore::class.java.getDeclaredField(name) }.getOrNull()
        } ?: return false
    field.isAccessible = true
    val current = field.get(this) as? Map<*, *> ?: return false
    if (current !is ClearReportingMap) {
        // A LinkedHashMap is a HashMap (the field type in old versions) and a Map (in new ones).
        field.set(this, ClearReportingMap(current, onClear))
    }
    return true
}

private class ClearReportingMap(
    contents: Map<*, *>,
    private val onClear: (List<ViewModel>) -> Unit,
) : LinkedHashMap<Any?, Any?>(contents) {
    override fun clear() {
        val viewModels = values.filterIsInstance<ViewModel>()
        super.clear()
        if (viewModels.isNotEmpty()) onClear(viewModels)
    }
}

/** Installs [ViewModelWatcher] on every activity that has ViewModels (AndroidX `ComponentActivity`). */
internal class ActivityViewModelWatcher(
    private val viewModelWatcher: ViewModelWatcher,
) : SimpleActivityLifecycleCallbacks() {
    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        if (activity is ViewModelStoreOwner) viewModelWatcher.install(activity)
    }
}
