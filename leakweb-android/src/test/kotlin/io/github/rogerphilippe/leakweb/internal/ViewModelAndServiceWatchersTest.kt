/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.rogerphilippe.leakweb.watcher.ObjectWatcher
import io.github.rogerphilippe.leakweb.watcher.RetainedObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewModelAndServiceWatchersTest {
    private val pendingChecks = mutableListOf<Runnable>()
    private val retained = mutableListOf<RetainedObject>()
    private val objectWatcher =
        ObjectWatcher(clock = { 0L }, checkRetainedExecutor = { pendingChecks += it }, gcTrigger = {})
            .apply { addOnObjectRetainedListener { retainedObject, _ -> retained += retainedObject } }

    private fun runPendingChecks() = pendingChecks.forEach { it.run() }

    private class AppViewModel : ViewModel()

    private class TestService : Service() {
        override fun onBind(intent: Intent?): IBinder? = null
    }

    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }

    /** Creates a ViewModel of the app in [owner], as `by viewModels()` would. */
    private fun appViewModel(): AppViewModel {
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel() as T
            }
        return ViewModelProvider(owner, factory)[AppViewModel::class.java]
    }

    @Test
    fun `ViewModels of a cleared store are watched`() {
        ViewModelWatcher(objectWatcher) { true }.install(owner)
        appViewModel()

        owner.viewModelStore.clear()
        runPendingChecks()

        val watched = retained.single()
        assertEquals(AppViewModel::class.java.name, watched.className)
        assertTrue(watched.description.endsWith("received ViewModel#onCleared() callback"))
    }

    @Test
    fun `ViewModels are not watched while the watcher is disabled`() {
        var enabled = true
        ViewModelWatcher(objectWatcher) { enabled }.install(owner)
        appViewModel()

        enabled = false
        owner.viewModelStore.clear()

        assertEquals(0, objectWatcher.watchedObjectCount)
    }

    @Test
    fun `ViewModels created before the install are kept and watched, once even if installed twice`() {
        val viewModel = appViewModel()
        val watcher = ViewModelWatcher(objectWatcher) { true }

        watcher.install(owner)
        watcher.install(owner)

        assertSame(viewModel, appViewModel())
        owner.viewModelStore.clear()
        runPendingChecks()
        assertEquals(1, retained.size)
    }

    @Test
    fun `the store still clears its ViewModels`() {
        var cleared = 0
        assertTrue(owner.viewModelStore.reportViewModelsOnClear { cleared += it.size })
        appViewModel()

        owner.viewModelStore.clear()

        assertEquals(1, cleared)
        assertTrue(owner.viewModelStore.keys().isEmpty())
    }

    @Test
    fun `a service is watched once it is done executing its stop`() {
        val token = Any()
        val service = TestService()
        val reporter = ServiceDestroyedReporter(objectWatcher, isEnabled = { true }) { mapOf(token to service) }

        reporter.onStopService(token)
        assertEquals(0, objectWatcher.watchedObjectCount)
        reporter.onServiceDoneExecuting(token)
        runPendingChecks()

        assertEquals(
            "${TestService::class.java.name} received Service#onDestroy() callback",
            retained.single().description,
        )
    }

    @Test
    fun `a service that is only done executing a start is not watched`() {
        val token = Any()
        val reporter = ServiceDestroyedReporter(objectWatcher, isEnabled = { true }) { mapOf(token to TestService()) }

        reporter.onServiceDoneExecuting(token)

        assertEquals(0, objectWatcher.watchedObjectCount)
    }

    @Test
    fun `services are not watched while the watcher is disabled`() {
        val token = Any()
        val reporter = ServiceDestroyedReporter(objectWatcher, isEnabled = { false }) { mapOf(token to TestService()) }

        reporter.onStopService(token)
        reporter.onServiceDoneExecuting(token)

        assertEquals(0, objectWatcher.watchedObjectCount)
    }
}
