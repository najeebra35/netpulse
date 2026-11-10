package com.netpulse.app

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetAddress
import kotlin.coroutines.coroutineContext

data class LanDevice(
    val ip: String,
    val mac: String,
    val hostname: String,
    val vendor: String,
    val isGateway: Boolean,
    val isSelf: Boolean,
    val reachableMs: Long,
) {
    /** Sort key: self first, then gateway, then by IP. */
    val ipSortKey: Long
        get() = ip.split(".").fold(0L) { acc, p -> acc * 256 + (p.toLongOrNull() ?: 0) }
}

/**
 * Discovers devices on the local Wi-Fi subnet: a parallel ping sweep of the /24,
 * then the kernel ARP cache (/proc/net/arp) for MAC addresses, then reverse-DNS
 * for hostnames and an OUI lookup for the maker. All addresses are the user's
 * own LAN — the same thing router admin pages and tools like Fing show.
 */
class LanScanner(private val context: Context) {

    suspend fun scan(onProgress: (done: Int, total: Int) -> Unit): List<LanDevice> =
        withContext(Dispatchers.IO) {
            val selfIp = NetworkInfoReader.localIpv4()
            if (selfIp.isEmpty() || !selfIp.contains('.')) return@withContext emptyList()
            val prefix = selfIp.substringBeforeLast('.')
            val gateway = gatewayIp()

            val total = 254
            val counter = java.util.concurrent.atomic.AtomicInteger(0)

            // Ping sweep in parallel batches so a /24 finishes in a few seconds.
            val alive = coroutineScope {
                (1..254).map { host ->
                    async {
                        val ip = "$prefix.$host"
                        val ms = ping(ip)
                        val done = counter.incrementAndGet()
                        onProgress(done, total)
                        if (ms >= 0) ip to ms else null
                    }
                }.awaitAll().filterNotNull()
            }

            val arp = readArpTable()
            val reachable = alive.associate { it.first to it.second }.toMutableMap()
            // Anything in ARP we didn't ping-hit is still a real neighbour.
            arp.keys.forEach { ip -> if (ip.startsWith("$prefix.")) reachable.putIfAbsent(ip, -1L) }
            reachable.putIfAbsent(selfIp, 0L)
            if (gateway.isNotEmpty()) reachable.putIfAbsent(gateway, -1L)

            reachable.keys.map { ip ->
                val mac = arp[ip] ?: if (ip == selfIp) "(this device)" else ""
                LanDevice(
                    ip = ip,
                    mac = mac,
                    hostname = resolveHost(ip),
                    vendor = OuiLookup.vendor(mac),
                    isGateway = ip == gateway,
                    isSelf = ip == selfIp,
                    reachableMs = reachable[ip] ?: -1L,
                )
            }.sortedWith(compareByDescending<LanDevice> { it.isSelf }
                .thenByDescending { it.isGateway }
                .thenBy { it.ipSortKey })
        }

    private suspend fun ping(ip: String): Long {
        if (!coroutineContext.isActive) return -1
        return try {
            val addr = InetAddress.getByName(ip)
            val start = System.nanoTime()
            if (addr.isReachable(600)) ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
            else -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun resolveHost(ip: String): String = try {
        val h = InetAddress.getByName(ip).canonicalHostName
        if (h == ip) "" else h
    } catch (_: Exception) {
        ""
    }

    @Suppress("DEPRECATION")
    private fun gatewayIp(): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val g = wm.dhcpInfo?.gateway ?: 0
            if (g == 0) "" else "${g and 0xFF}.${g shr 8 and 0xFF}.${g shr 16 and 0xFF}.${g shr 24 and 0xFF}"
        } catch (_: Exception) {
            ""
        }
    }

    /** Reads /proc/net/arp. Readable on most devices; empty if the kernel hides it. */
    private fun readArpTable(): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            val f = File("/proc/net/arp")
            if (!f.exists()) return map
            BufferedReader(FileReader(f)).use { br ->
                br.readLine() // header
                var line = br.readLine()
                while (line != null) {
                    val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val mac = parts[3]
                        if (mac.matches(Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")) && mac != "00:00:00:00:00:00") {
                            map[ip] = mac.uppercase()
                        }
                    }
                    line = br.readLine()
                }
            }
        } catch (_: Exception) {
        }
        return map
    }
}
