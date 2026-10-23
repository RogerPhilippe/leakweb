/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
@file:SuppressLint("SetTextI18n")

package io.github.rogerphilippe.leakweb.sample

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Kept in a static list by [ViewModelLeakActivity], so it outlives `onCleared()`. */
class LeakingViewModel : ViewModel()

/** Its ViewModel is cleared when it finishes, but stays in memory. Press back to leak it. */
class ViewModelLeakActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IntentionalLeaks.objects += ViewModelProvider(this)[LeakingViewModel::class.java]
        setContentView(
            TextView(this).apply {
                text = "This activity's ViewModel leaks. Press back, then check logcat."
                setPadding(PADDING, PADDING, PADDING, PADDING)
            },
        )
    }
}

/** Adds itself to a static list and stops right away, so it stays in memory after `onDestroy()`. */
class LeakingService : Service() {
    override fun onCreate() {
        super.onCreate()
        IntentionalLeaks.objects += this
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/** Shows [ViewHoldingFragment]; its button replaces it and keeps it in the back stack. */
class FragmentViewLeakActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = R.id.fragment_container })
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .add(R.id.fragment_container, ViewHoldingFragment())
                .commit()
        }
    }
}

/** Keeps its view in a field after `onDestroyView()`: the classic view binding leak. */
class ViewHoldingFragment : Fragment() {
    private var leakedView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        Button(requireContext())
            .apply {
                text = "Replace this fragment (it keeps its view), then check logcat"
                setOnClickListener {
                    parentFragmentManager
                        .beginTransaction()
                        .replace(R.id.fragment_container, ReplacementFragment())
                        .addToBackStack(null)
                        .commit()
                }
            }.also { leakedView = it }
}

class ReplacementFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        TextView(requireContext()).apply {
            text = "The previous fragment is in the back stack and still holds its destroyed view."
            setPadding(PADDING, PADDING, PADDING, PADDING)
        }
}

private const val PADDING = 48
