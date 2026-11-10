package com.netpulse.app

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import java.util.Calendar

data class AppUsage(
    val label: String,
    val packageName: String,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val total: Long get() = rxBytes + txBytes
}

data class UsageTotals(
    val wifiRx: Long,
    val wifiTx: Long,
    val mobileRx: Long,
    val mobileTx: Long,
)

object DataUsageReader {

    fun hasUsageAccess(context: Context): Boolean {
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Month-to-date totals split by Wi-Fi and mobile. */
    fun totals(context: Context): UsageTotals {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val start = startOfMonth()
        val end = System.currentTimeMillis()
        fun bucket(type: Int): Pair<Long, Long> = try {
            val b = nsm.querySummaryForDevice(type, null, start, end)
            b.rxBytes to b.txBytes
        } catch (_: Exception) {
            0L to 0L
        }
        val wifi = bucket(NetworkCapabilities.TRANSPORT_WIFI)
        val mobile = bucket(NetworkCapabilities.TRANSPORT_CELLULAR)
        return UsageTotals(wifi.first, wifi.second, mobile.first, mobile.second)
    }

    /** Per-app usage this month (needs Usage Access permission). */
    fun perApp(context: Context): List<AppUsage> {
        if (!hasUsageAccess(context)) return emptyList()
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val pm = context.packageManager
        val start = startOfMonth()
        val end = System.currentTimeMillis()
        val byUid = HashMap<Int, LongArray>() // uid -> [rx, tx]

        for (type in intArrayOf(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_CELLULAR)) {
            try {
                val stats = nsm.querySummary(type, null, start, end)
                val bucket = android.app.usage.NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val arr = byUid.getOrPut(bucket.uid) { LongArray(2) }
                    arr[0] += bucket.rxBytes
                    arr[1] += bucket.txBytes
                }
                stats.close()
            } catch (_: Exception) {
            }
        }

        val installed = pm.getInstalledApplications(0).associateBy { it.uid }
        return byUid.entries.mapNotNull { (uid, arr) ->
            if (arr[0] + arr[1] <= 0) return@mapNotNull null
            val label = when (uid) {
                Process.SYSTEM_UID, 0 -> "Android System"
                else -> installed[uid]?.let { pm.getApplicationLabel(it).toString() } ?: "UID $uid"
            }
            val pkg = installed[uid]?.packageName ?: ""
            AppUsage(label, pkg, arr[0], arr[1])
        }.sortedByDescending { it.total }
    }
}
