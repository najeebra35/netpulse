package com.netpulse.app

import android.net.TrafficStats
import android.os.SystemClock

data class SpeedSample(
    val downBps: Long,
    val upBps: Long,
    val totalRxBytes: Long,
    val totalTxBytes: Long,
)

/**
 * Device-wide live throughput using TrafficStats counters.
 * Call [sample] on a fixed interval; the first call just seeds the baseline.
 */
class SpeedMonitor {
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastMs = 0L
    private var seeded = false

    // Bytes counted since this monitor started (for "session used").
    private var startRx = 0L
    private var startTx = 0L

    fun sample(): SpeedSample {
        val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
        val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
        val now = SystemClock.elapsedRealtime()

        if (!seeded) {
            lastRx = rx; lastTx = tx; lastMs = now
            startRx = rx; startTx = tx
            seeded = true
            return SpeedSample(0, 0, 0, 0)
        }

        val dt = (now - lastMs).coerceAtLeast(1)
        val down = (rx - lastRx).coerceAtLeast(0) * 1000 / dt
        val up = (tx - lastTx).coerceAtLeast(0) * 1000 / dt

        lastRx = rx; lastTx = tx; lastMs = now
        return SpeedSample(down, up, (rx - startRx).coerceAtLeast(0), (tx - startTx).coerceAtLeast(0))
    }
}

object Fmt {
    /** Bytes-per-second → human readable speed. */
    fun speed(bps: Long): String {
        val bits = bps * 8.0
        return when {
            bits >= 1_000_000_000 -> "%.2f Gbps".format(bits / 1e9)
            bits >= 1_000_000 -> "%.1f Mbps".format(bits / 1e6)
            bits >= 1_000 -> "%.0f Kbps".format(bits / 1e3)
            else -> "$bps B/s"
        }
    }

    /** Bytes-per-second → MB/s style (for notification compactness). */
    fun rate(bps: Long): String = when {
        bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1e6)
        bps >= 1_000 -> "%.0f KB/s".format(bps / 1e3)
        else -> "$bps B/s"
    }

    fun bytes(b: Long): String = when {
        b >= 1_000_000_000 -> "%.2f GB".format(b / 1e9)
        b >= 1_000_000 -> "%.1f MB".format(b / 1e6)
        b >= 1_000 -> "%.0f KB".format(b / 1e3)
        else -> "$b B"
    }
}
