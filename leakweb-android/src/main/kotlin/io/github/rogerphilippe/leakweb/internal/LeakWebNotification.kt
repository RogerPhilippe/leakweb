/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import io.github.rogerphilippe.leakweb.model.Event

/** Optional ongoing status notification. Permission denial is reported in logcat, never swallowed. */
internal class LeakWebNotification(
    private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var enabled = false
    private var permissionWarningLogged = false
    private var currentStatus = "Watching for retained objects"

    fun setEnabled(value: Boolean) {
        if (enabled == value) {
            if (value) show(currentStatus)
            return
        }
        enabled = value
        if (!value) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        ensureChannel()
        show("Watching for retained objects")
    }

    fun onEvent(event: Event) {
        if (!enabled) return
        when (event) {
            is Event.ObjectRetained -> {
                show("${event.retainedObjectCount} object(s) retained")
            }

            is Event.HeapDumpStarted -> {
                show("Creating heap dump...")
            }

            is Event.HeapDumpFailed -> {
                show("Heap dump failed: ${event.message}")
            }

            is Event.AnalysisProgress -> {
                show("Analyzing heap: ${event.percent}%")
            }

            is Event.AnalysisDone -> {
                show(if (event.success) "Heap analysis complete" else "Heap analysis failed")
            }

            is Event.NewLeak -> {
                show("New leak found: ${event.shortDescription}")
            }

            is Event.MemorySampled, is Event.LiveMemoryChanged -> {
                Unit
            }
        }
    }

    private fun show(status: String) {
        currentStatus = status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (!permissionWarningLogged) {
                permissionWarningLogged = true
                Log.w(LEAKWEB_TAG, "Persistent notification not shown: POST_NOTIFICATIONS permission is not granted")
            }
            return
        }
        val builder =
            notificationBuilder()
        manager.notify(
            NOTIFICATION_ID,
            builder
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("LeakWeb")
                .setContentText(status)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent())
                .build(),
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "LeakWeb", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun notificationBuilder() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }

    private fun openAppIntent(): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_ID = "leakweb-status"
        const val NOTIFICATION_ID = 0x4c57
    }
}
