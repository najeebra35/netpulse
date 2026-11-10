package com.netpulse.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext

data class PingReply(val seq: Int, val timeMs: Double?, val ttl: Int?)

data class PingSummary(
    val host: String,
    val ip: String,
    val sent: Int,
    val received: Int,
    val minMs: Double,
    val avgMs: Double,
    val maxMs: Double,
    val jitterMs: Double,
) {
    val lossPct: Int get() = if (sent == 0) 0 else (sent - received) * 100 / sent
}

data class Hop(val ttl: Int, val ip: String, val host: String, val timeMs: Double?, val reached: Boolean)

data class OpenPort(val port: Int, val service: String)

object NetTools {

    private val timeRe = Regex("time[=<]([0-9.]+)")
    private val ttlRe = Regex("ttl=([0-9]+)", RegexOption.IGNORE_CASE)
    private val fromRe = Regex("[Ff]rom ([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)")
    private val bytesFromRe = Regex("bytes from (?:[^(]*\\()?([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)")

    fun resolve(host: String): String = try {
        InetAddress.getByName(host).hostAddress ?: ""
    } catch (_: Exception) {
        ""
    }

    /** ICMP ping via the system binary; falls back to TCP-connect timing. */
    suspend fun ping(host: String, count: Int, onReply: (PingReply) -> Unit): PingSummary =
        withContext(Dispatchers.IO) {
            val ip = resolve(host)
            val times = ArrayList<Double>()
            var received = 0
            var usedExec = false

            try {
                val proc = ProcessBuilder("/system/bin/ping", "-c", count.toString(), "-i", "0.3", "-W", "2", host)
                    .redirectErrorStream(true).start()
                usedExec = true
                var seq = 0
                proc.inputStream.bufferedReader().forEachLine { line ->
                    if (line.contains("time=") || line.contains("time<")) {
                        val t = timeRe.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                        val ttl = ttlRe.find(line)?.groupValues?.get(1)?.toIntOrNull()
                        if (t != null) {
                            times.add(t); received++
                            onReply(PingReply(seq++, t, ttl))
                        }
                    }
                }
                proc.waitFor()
            } catch (_: Exception) {
                usedExec = false
            }

            // Fallback: TCP connect to 443/80 if exec ping is blocked.
            if (!usedExec || times.isEmpty()) {
                for (i in 0 until count) {
                    if (!coroutineContext.isActive) break
                    val t = tcpPing(ip.ifEmpty { host })
                    if (t != null) { times.add(t); received++; onReply(PingReply(i, t, null)) }
                    else onReply(PingReply(i, null, null))
                }
            }

            val min = times.minOrNull() ?: 0.0
            val max = times.maxOrNull() ?: 0.0
            val avg = if (times.isNotEmpty()) times.average() else 0.0
            val jitter = if (times.size > 1) times.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average() else 0.0
            PingSummary(host, ip, count, received, min, avg, max, jitter)
        }

    private fun tcpPing(target: String): Double? = try {
        val s = Socket()
        val start = System.nanoTime()
        s.connect(InetSocketAddress(target, 443), 2000)
        val t = (System.nanoTime() - start) / 1e6
        s.close()
        t
    } catch (_: Exception) {
        null
    }

    /** Traceroute via incremental TTL pings (no root needed on most devices). */
    suspend fun traceroute(host: String, maxHops: Int, onHop: (Hop) -> Unit): List<Hop> =
        withContext(Dispatchers.IO) {
            val target = resolve(host)
            val hops = ArrayList<Hop>()
            for (ttl in 1..maxHops) {
                if (!coroutineContext.isActive) break
                var hopIp = ""
                var time: Double? = null
                var reached = false
                try {
                    val proc = ProcessBuilder("/system/bin/ping", "-c", "1", "-t", ttl.toString(), "-W", "2", host)
                        .redirectErrorStream(true).start()
                    val out = proc.inputStream.bufferedReader().readText()
                    proc.waitFor()
                    time = timeRe.find(out)?.groupValues?.get(1)?.toDoubleOrNull()
                    val reachedIp = bytesFromRe.find(out)?.groupValues?.get(1)
                    val exceededIp = fromRe.find(out)?.groupValues?.get(1)
                    if (reachedIp != null && (out.contains("time=") || out.contains("time<"))) {
                        hopIp = reachedIp; reached = reachedIp == target || host == reachedIp
                    } else if (exceededIp != null) {
                        hopIp = exceededIp
                    }
                } catch (_: Exception) {
                }
                val hostname = if (hopIp.isNotEmpty()) revDns(hopIp) else ""
                val hop = Hop(ttl, hopIp.ifEmpty { "*" }, hostname, time, reached || hopIp == target)
                hops.add(hop); onHop(hop)
                if (hop.reached) break
            }
            hops
        }

    private fun revDns(ip: String): String = try {
        val h = InetAddress.getByName(ip).canonicalHostName
        if (h == ip) "" else h
    } catch (_: Exception) {
        ""
    }

    private val commonPorts = linkedMapOf(
        20 to "FTP-data", 21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP",
        53 to "DNS", 80 to "HTTP", 110 to "POP3", 139 to "NetBIOS", 143 to "IMAP",
        443 to "HTTPS", 445 to "SMB", 554 to "RTSP", 631 to "IPP/Print", 993 to "IMAPS",
        995 to "POP3S", 1883 to "MQTT", 3306 to "MySQL", 3389 to "RDP", 5000 to "UPnP",
        5555 to "ADB", 7070 to "RTSP", 8008 to "Chromecast", 8080 to "HTTP-alt",
        8443 to "HTTPS-alt", 8883 to "MQTT-TLS", 9000 to "App", 9100 to "Printer",
        32400 to "Plex", 62078 to "iPhone-sync",
    )

    /** Parallel TCP connect-scan of common ports on one LAN host. */
    suspend fun scanPorts(ip: String, onProgress: (Int, Int) -> Unit): List<OpenPort> =
        withContext(Dispatchers.IO) {
            val entries = commonPorts.entries.toList()
            val total = entries.size
            val counter = java.util.concurrent.atomic.AtomicInteger(0)
            coroutineScope {
                entries.map { (port, svc) ->
                    async {
                        val open = try {
                            val s = Socket()
                            s.connect(InetSocketAddress(ip, port), 500)
                            s.close(); true
                        } catch (_: Exception) { false }
                        onProgress(counter.incrementAndGet(), total)
                        if (open) OpenPort(port, svc) else null
                    }
                }.awaitAll().filterNotNull().sortedBy { it.port }
            }
        }
}
