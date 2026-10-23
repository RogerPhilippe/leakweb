/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.internal

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.util.Log
import io.github.rogerphilippe.leakweb.analyzer.HprofAnalyzer
import io.github.rogerphilippe.leakweb.model.Event
import io.github.rogerphilippe.leakweb.model.LeakWebJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs Shark in Android's `:leakweb` process. JobScheduler starts the process even if the app has
 * moved to the background and lets the OS retry if it stops the job.
 */
internal class LeakWebAnalysisService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runningJobs = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val id = params.extras.getString(EXTRA_ID)
        val uptimeMillis = params.extras.getLong(EXTRA_UPTIME)
        val dumpFile = id?.let(::resolveDumpFile)
        if (id == null || dumpFile == null || !dumpFile.isFile) {
            Log.e(LEAKWEB_TAG, "Analysis job has invalid or missing heap dump")
            return false
        }

        val store = LeakStore(File(filesDir, "leakweb"))
        val events = EventBus { event -> AnalysisEventTransport.publish(this, event) }
        val runner =
            HeapAnalysisRunner(scope, store, events) { heapDump, progressListener ->
                HprofAnalyzer().analyze(heapDump.file, heapDump.id, heapDump.uptimeMillis, progressListener)
            }
        val job = runner.analyze(HeapDump(id, dumpFile, uptimeMillis))
        runningJobs[params.jobId] = job
        job.invokeOnCompletion { error ->
            if (!runningJobs.remove(params.jobId, job)) return@invokeOnCompletion
            if (error != null) {
                Log.e(LEAKWEB_TAG, "Heap analysis job failed for $id", error)
                AnalysisEventTransport.publish(this, Event.AnalysisDone(id, success = false))
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJobs.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun resolveDumpFile(id: String): File? {
        if (!ANALYSIS_ID_PATTERN.matches(id)) return null
        val dumpsDirectory = File(filesDir, "leakweb/dumps")
        return runCatching { File(dumpsDirectory, "$id.hprof").canonicalFile }
            .getOrNull()
            ?.takeIf { it.parentFile == dumpsDirectory.canonicalFile }
    }

    companion object {
        private const val EXTRA_ID = "io.github.rogerphilippe.leakweb.analysis.ID"
        private const val EXTRA_UPTIME = "io.github.rogerphilippe.leakweb.analysis.UPTIME"
        private const val JOB_ID_PREFS = "leakweb-analysis-jobs"
        private const val NEXT_JOB_ID = "next-job-id"

        @Synchronized
        fun enqueue(
            context: Context,
            heapDump: HeapDump,
        ): Boolean {
            val appContext = context.applicationContext
            val scheduler = appContext.getSystemService(JobScheduler::class.java)
            val jobId = nextJobId(appContext)
            val extras =
                PersistableBundle().apply {
                    putString(EXTRA_ID, heapDump.id)
                    putLong(EXTRA_UPTIME, heapDump.uptimeMillis)
                }
            val job =
                JobInfo
                    .Builder(jobId, ComponentName(appContext, LeakWebAnalysisService::class.java))
                    .setExtras(extras)
                    .build()
            val accepted = scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
            if (!accepted) {
                Log.e(LEAKWEB_TAG, "Android rejected the heap analysis job for ${heapDump.id}")
            }
            return accepted
        }

        @Suppress("UseKtx")
        private fun nextJobId(context: Context): Int {
            val preferences = context.getSharedPreferences(JOB_ID_PREFS, Context.MODE_PRIVATE)
            val jobId = preferences.getInt(NEXT_JOB_ID, 1)
            check(preferences.edit().putInt(NEXT_JOB_ID, if (jobId == Int.MAX_VALUE) 1 else jobId + 1).commit()) {
                "Could not persist LeakWeb analysis job id"
            }
            return jobId
        }
    }
}

internal class AnalysisEventReceiver(
    private val eventBus: EventBus,
) : android.content.BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != AnalysisEventTransport.ACTION || intent.`package` != context.packageName) return
        val json = intent.getStringExtra(AnalysisEventTransport.EXTRA_EVENT) ?: return
        try {
            eventBus.post(LeakWebJson.decodeFromString<Event>(json))
        } catch (error: SerializationException) {
            Log.e(LEAKWEB_TAG, "Could not decode event from heap analysis process", error)
        } catch (error: IllegalArgumentException) {
            Log.e(LEAKWEB_TAG, "Invalid event from heap analysis process", error)
        }
    }
}

internal object AnalysisEventTransport {
    const val ACTION = "io.github.rogerphilippe.leakweb.ANALYSIS_EVENT"
    const val EXTRA_EVENT = "event"

    fun publish(
        context: Context,
        event: Event,
    ) {
        val json = LeakWebJson.encodeToString(Event.serializer(), event)
        context.sendBroadcast(
            Intent(ACTION)
                .setPackage(context.packageName)
                .putExtra(EXTRA_EVENT, json),
        )
    }
}

private val ANALYSIS_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")
