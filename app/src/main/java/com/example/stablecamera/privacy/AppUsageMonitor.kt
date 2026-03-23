package com.example.stablecamera.privacy

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.*

class AppUsageMonitor(private val context: Context) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager?
    private val handler = Handler(Looper.getMainLooper())
    private var lastAppPackage: String? = null

    // In a production app, these should be loaded from SharedPreferences/DB
    private var protectedPackages = setOf("com.example.bankapp", "com.android.settings")

    private val monitorRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, 2000) // Poll every 2 seconds for efficiency
        }
    }

    fun start() {
        if (usageStatsManager != null) {
            handler.post(monitorRunnable)
        }
    }

    fun stop() {
        handler.removeCallbacks(monitorRunnable)
    }

    private fun checkForegroundApp() {
        val time = System.currentTimeMillis()
        val stats = usageStatsManager?.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 2000 * 60,
            time
        )

        if (stats != null && stats.isNotEmpty()) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            val foregroundApp = sortedStats[0].packageName
            if (foregroundApp != lastAppPackage) {
                onAppChanged(foregroundApp)
                lastAppPackage = foregroundApp
            }
        }
    }

    private fun onAppChanged(packageName: String) {
        val intent = Intent(context, PrivacyFilterService::class.java)
        if (protectedPackages.contains(packageName)) {
            intent.action = PrivacyFilterService.ACTION_SHOW
        } else {
            // Only hide if the previous app was protected
            intent.action = PrivacyFilterService.ACTION_HIDE
        }
        context.startService(intent)
    }
}
