/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.sample

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rogerphilippe.leakweb.LeakWeb
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class LeakWebEndToEndTest {
    @Test
    fun destroyedActivityIsDetectedAndRecordedByAnalysis() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val session = LeakWeb.session
        assertTrue("LeakWeb must be running in the sample", session.isRunning)

        val activity =
            instrumentation.startActivitySync(
                Intent(context, LeakingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as LeakingActivity
        try {
            instrumentation.runOnMainSync(activity::finish)
            await("destroyed activity to be reported as retained") {
                data("/api/retained").any { it.optString("className").endsWith(".LeakingActivity") }
            }
            val existingAnalysisIds = data("/api/analyses").map { it.optString("id") }.toSet()
            post("/api/heapdump")
            await("heap analysis to complete") {
                data("/api/analyses").any {
                    it.optString("id") !in existingAnalysisIds && it.optBoolean("success")
                }
            }
            assertTrue(
                "analysis should report the destroyed Activity",
                data("/api/leaks").any { it.optString("leakingClassName").endsWith(".LeakingActivity") },
            )
        } finally {
            IntentionalLeaks.activities.removeAll { it is LeakingActivity }
        }
    }

    private fun data(path: String): List<JSONObject> {
        val response = request("GET", path)
        val array = response.getJSONArray("data")
        return (0 until array.length()).map(array::getJSONObject)
    }

    private fun post(path: String) {
        assertTrue(request("POST", path).getJSONObject("data").optBoolean("accepted"))
    }

    private fun request(
        method: String,
        path: String,
    ): JSONObject {
        val session = LeakWeb.session
        val connection =
            URL("http://127.0.0.1:${session.port}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 5_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("X-LeakWeb-Token", session.token)
        if (method != "GET") connection.setRequestProperty("Origin", "http://127.0.0.1:${session.port}")
        return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    }

    private fun await(
        description: String,
        timeoutMillis: Long = 180_000,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(500)
        }
        throw AssertionError("Timed out waiting for $description")
    }
}
