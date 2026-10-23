/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.sample

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rogerphilippe.leakweb.sample.ui.theme.LeakwebAppTheme

/** Adds itself to a static list, so it stays in memory after being destroyed (back or rotation). */
class LeakingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IntentionalLeaks.activities += this
        enableEdgeToEdge()
        setContent {
            LeakwebAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Text(
                        "This activity leaks itself. Press back or rotate the device, then check logcat.",
                        modifier = Modifier.padding(innerPadding).padding(16.dp),
                    )
                }
            }
        }
    }
}

/** Static references that keep objects in memory on purpose. Never do this in a real app. */
object IntentionalLeaks {
    val activities = mutableListOf<Activity>()
    val objects = mutableListOf<Any>()
}
