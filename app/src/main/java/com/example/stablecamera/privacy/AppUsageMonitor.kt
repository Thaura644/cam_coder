package com.example.stablecamera.privacy

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.*

class AppUsageMonitor(private val context: Context) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastAppPackage: String? = null

    // Set of packages that trigger the privacy filter
    private var protectedPackages = mutableSetOf("com.android.settings")

    fun addProtectedPackage(packageName: String) {
        protectedPackages.add(packageName)
    }

    fun removeProtectedPackage(packageName: String) {
        protectedPackages.remove(packageName)
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, 1000) // Poll every second
        }
    }

    fun start() {
        handler.post(monitorRunnable)
    }

    fun stop() {
        handler.removeCallbacks(monitorRunnable)
    }

    private fun checkForegroundApp() {
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 60,
            time
        )

        if (stats != null) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            if (sortedStats.isNotEmpty()) {
                val foregroundApp = sortedStats[0].packageName
                if (foregroundApp != lastAppPackage) {
                    onAppChanged(foregroundApp)
                    lastAppPackage = foregroundApp
                }
            }
        }
    }

    private fun onAppChanged(packageName: String) {
        val intent = Intent(context, PrivacyFilterService::class.java)
        if (protectedPackages.contains(packageName)) {
            intent.action = PrivacyFilterService.ACTION_SHOW
        } else {
            intent.action = PrivacyFilterService.ACTION_HIDE
        }
        context.startService(intent)
    }
}
