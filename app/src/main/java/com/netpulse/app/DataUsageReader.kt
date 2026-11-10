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
    val icon: android.graphics.Bitmap? = null,
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

        val sorted = byUid.entries.mapNotNull { (uid, arr) ->
            if (arr[0] + arr[1] <= 0) return@mapNotNull null
            resolveApp(pm, uid, arr[0], arr[1])
        }.sortedByDescending { it.total }
        // Load icons only for the apps actually shown.
        return sorted.mapIndexed { i, a ->
            if (i < 30 && a.packageName.isNotEmpty() && a.icon == null) {
                val ic = try { drawableToBitmap(pm.getApplicationIcon(a.packageName)) } catch (_: Exception) { null }
                if (ic != null) a.copy(icon = ic) else a
            } else a
        }
    }

    private fun resolveApp(pm: PackageManager, uid: Int, rx: Long, tx: Long): AppUsage {
        // Special shared UIDs first.
        when (uid) {
            0 -> return AppUsage("Root / kernel", "", rx, tx)
            Process.SYSTEM_UID -> return AppUsage("Android System", "android", rx, tx)
            1013 -> return AppUsage("Media server", "", rx, tx)
            1020 -> return AppUsage("MulticastDNS", "", rx, tx)
            1021 -> return AppUsage("GPS", "", rx, tx)
            else -> {}
        }
        val pkgs = try { pm.getPackagesForUid(uid) } catch (_: Exception) { null }
        if (pkgs.isNullOrEmpty()) {
            // Fallback to any name the system exposes for this uid.
            val nm = try { pm.getNameForUid(uid) } catch (_: Exception) { null }
            return AppUsage(nm ?: "App (UID $uid)", nm ?: "", rx, tx)
        }
        // Prefer the package with a loadable label/icon.
        for (pkg in pkgs) {
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(ai).toString()
                return AppUsage(label, pkg, rx, tx)
            } catch (_: Exception) {
            }
        }
        return AppUsage(pkgs.first(), pkgs.first(), rx, tx)
    }

    private fun drawableToBitmap(d: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) return d.bitmap
        val size = 96
        val b = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return b
    }
}
