/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.rogerphilippe.leakweb.LeakWeb
import io.github.rogerphilippe.leakweb.sample.ui.theme.LeakwebAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeakwebAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LeakScenarios(
                        serverUrl = LeakWeb.session.url,
                        onOpenLeakingActivity = { startActivity(Intent(this, LeakingActivity::class.java)) },
                        onOpenViewModelLeak = { startActivity(Intent(this, ViewModelLeakActivity::class.java)) },
                        onOpenFragmentViewLeak = { startActivity(Intent(this, FragmentViewLeakActivity::class.java)) },
                        onStartLeakingService = { startService(Intent(this, LeakingService::class.java)) },
                        onLeakWatchedObject = ::leakWatchedObject,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    /** Leaks an arbitrary object watched with the public API (no-op in release builds). */
    private fun leakWatchedObject() {
        val leaked = LeakedObject()
        IntentionalLeaks.objects += leaked
        LeakWeb.watch(leaked, "the sample leaked it on purpose with LeakWeb.watch()")
    }
}

/** Object leaked by the "watch an object" scenario. */
class LeakedObject

@Composable
fun LeakScenarios(
    serverUrl: String,
    onOpenLeakingActivity: () -> Unit,
    onOpenViewModelLeak: () -> Unit,
    onOpenFragmentViewLeak: () -> Unit,
    onStartLeakingService: () -> Unit,
    onLeakWatchedObject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("LeakWeb sample", style = MaterialTheme.typography.headlineSmall)
        Text("Each scenario leaks on purpose. Retained objects are logged ~5 s later (logcat tag LeakWeb).")
        Text(if (serverUrl.isEmpty()) "LeakWeb server not running" else "LeakWeb: $serverUrl")
        Button(onClick = onOpenLeakingActivity) {
            Text("Open an activity that leaks (then press back)")
        }
        Button(onClick = onOpenViewModelLeak) {
            Text("Open an activity whose ViewModel leaks (then press back)")
        }
        Button(onClick = onOpenFragmentViewLeak) {
            Text("Open a fragment that keeps its destroyed view")
        }
        Button(onClick = onStartLeakingService) {
            Text("Start a service that leaks (it stops itself)")
        }
        Button(onClick = onLeakWatchedObject) {
            Text("Leak an object watched with LeakWeb.watch()")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LeakScenariosPreview() {
    LeakwebAppTheme {
        LeakScenarios(
            serverUrl = "http://localhost:9090/?token=abc",
            onOpenLeakingActivity = {},
            onOpenViewModelLeak = {},
            onOpenFragmentViewLeak = {},
            onStartLeakingService = {},
            onLeakWatchedObject = {},
        )
    }
}
