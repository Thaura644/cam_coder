package com.example.stablecamera.privacy

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.util.*

class AppUsageMonitor(private val context: Context) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager?
    private val handler = Handler(Looper.getMainLooper())
    private var lastAppPackage: String? = null

    private val prefs: SharedPreferences = context.getSharedPreferences("privacy_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PROTECTED_PACKAGES = "protected_packages"
    }

    private val protectedPackages: MutableSet<String>
        get() {
            val stored = prefs.getStringSet(KEY_PROTECTED_PACKAGES, null)
            return stored?.toMutableSet() ?: mutableSetOf("com.android.settings")
        }

    fun addProtectedPackage(packageName: String) {
        val packages = protectedPackages
        packages.add(packageName)
        prefs.edit().putStringSet(KEY_PROTECTED_PACKAGES, packages).apply()
    }

    fun removeProtectedPackage(packageName: String) {
        val packages = protectedPackages
        packages.remove(packageName)
        prefs.edit().putStringSet(KEY_PROTECTED_PACKAGES, packages).apply()
    }

    fun getProtectedPackages(): Set<String> = protectedPackages

    private val monitorRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, 2000)
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
            intent.action = PrivacyFilterService.ACTION_HIDE
        }
        context.startService(intent)
    }
}
