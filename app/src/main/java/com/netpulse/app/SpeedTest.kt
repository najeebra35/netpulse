package com.netpulse.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection

import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis

data class SpeedTestResult(
    val pingMs: Double,
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
)

enum class TestPhase { PING, DOWNLOAD, UPLOAD, DONE }

/**
 * Lightweight speed test against Cloudflare's public speed endpoints
 * (speed.cloudflare.com) — no API key, widely reachable.
 */
class SpeedTest {

    private val downUrl = "https://speed.cloudflare.com/__down?bytes="
    private val upUrl = "https://speed.cloudflare.com/__up"

    suspend fun ping(onProgress: (Double) -> Unit): Pair<Double, Double> = withContext(Dispatchers.IO) {
        // TCP-connect latency to Cloudflare DNS on 443 — reliable without root (ICMP needs it).
        val samples = ArrayList<Double>()
        repeat(7) {
            if (!coroutineContext.isActive) return@repeat
            try {
                val socket = java.net.Socket()
                val t = measureTimeMillis {
                    socket.connect(java.net.InetSocketAddress("1.1.1.1", 443), 1500)
                }
                socket.close()
                if (samples.isNotEmpty() || it > 0) { // drop the first (connection warm-up) sample
                    samples.add(t.toDouble())
                    onProgress(samples.average())
                }
            } catch (_: Exception) {
            }
        }
        if (samples.isEmpty()) return@withContext 0.0 to 0.0
        val avg = samples.average()
        val jitter = if (samples.size > 1)
            samples.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average() else 0.0
        avg to jitter
    }

    /** Downloads a sized payload and measures sustained Mbps. */
    suspend fun download(onProgress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        var bestMbps = 0.0
        val sizes = longArrayOf(10_000_000, 25_000_000)
        for (size in sizes) {
            if (!coroutineContext.isActive) break
            try {
                val conn = (URL("$downUrl$size").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 20000
                }
                val buf = ByteArray(64 * 1024)
                var total = 0L
                val start = System.nanoTime()
                conn.inputStream.use { input ->
                    while (coroutineContext.isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        val secs = (System.nanoTime() - start) / 1e9
                        if (secs > 0.3) {
                            val mbps = total * 8 / secs / 1e6
                            onProgress(mbps)
                        }
                    }
                }
                conn.disconnect()
                val secs = (System.nanoTime() - start) / 1e9
                if (secs > 0) bestMbps = maxOf(bestMbps, total * 8 / secs / 1e6)
            } catch (_: IOException) {
            }
        }
        bestMbps
    }

    /** Uploads a buffer and measures sustained Mbps. */
    suspend fun upload(onProgress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val payload = ByteArray(8 * 1024 * 1024) // 8 MB
        var bestMbps = 0.0
        repeat(2) {
            if (!coroutineContext.isActive) return@repeat
            try {
                val conn = (URL(upUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 20000
                    setFixedLengthStreamingMode(payload.size)
                    setRequestProperty("Content-Type", "application/octet-stream")
                }
                val start = System.nanoTime()
                conn.outputStream.use { out ->
                    var off = 0
                    val chunk = 64 * 1024
                    while (off < payload.size && coroutineContext.isActive) {
                        val n = minOf(chunk, payload.size - off)
                        out.write(payload, off, n)
                        off += n
                        val secs = (System.nanoTime() - start) / 1e9
                        if (secs > 0.3) onProgress(off * 8 / secs / 1e6)
                    }
                    out.flush()
                }
                try { conn.responseCode } catch (_: Exception) {}
                conn.disconnect()
                val secs = (System.nanoTime() - start) / 1e9
                if (secs > 0) bestMbps = maxOf(bestMbps, payload.size * 8 / secs / 1e6)
            } catch (_: IOException) {
            }
        }
        bestMbps
    }
}
