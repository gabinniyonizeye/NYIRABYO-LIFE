package com.example.nyirabyolife.util

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageStatsHelper {

    fun getTodayForegroundMinutes(context: Context, packageName: String): Int {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        try {
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                now
            )

            if (!stats.isNullOrEmpty()) {
                val appStat = stats.find { it.packageName == packageName }
                if (appStat != null) {
                    return (appStat.totalTimeInForeground / (1000 * 60)).toInt()
                }
            }
        } catch (_: Exception) {}

        return 0
    }

    fun getAllTodayUsageMinutes(context: Context): Map<String, Int> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        val result = mutableMapOf<String, Int>()
        try {
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                now
            )
            stats?.forEach { stat ->
                val mins = (stat.totalTimeInForeground / (1000 * 60)).toInt()
                if (mins > 0) {
                    result[stat.packageName] = (result[stat.packageName] ?: 0) + mins
                }
            }
        } catch (_: Exception) {}

        return result
    }
}
