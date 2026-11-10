package com.netpulse.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

data class WifiDetails(
    val connected: Boolean,
    val ssid: String,
    val bssid: String,
    val linkSpeedMbps: Int,
    val rssi: Int,
    val frequencyMhz: Int,
    val band: String,
    val channel: Int,
    val ipAddress: String,
    val gateway: String,
    val dns: String,
    val txLinkSpeed: Int,
    val rxLinkSpeed: Int,
    val signalLevel: Int, // 0..4
    val standard: String,
)

data class MobileDetails(
    val connected: Boolean,
    val operator: String,
    val networkType: String,
    val generation: String,
    val signalDbm: Int,
    val signalLevel: Int, // 0..4
    val roaming: Boolean,
)

data class PublicNet(
    val ip: String,
    val isp: String,
    val city: String,
    val country: String,
)

object NetworkInfoReader {

    fun activeTransport(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "None"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Unknown"
        }
    }

    @Suppress("DEPRECATION")
    fun wifi(context: Context): WifiDetails {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (!onWifi || !wm.isWifiEnabled) {
            return WifiDetails(false, "", "", 0, 0, 0, "", 0, "", "", "", 0, 0, 0, "")
        }

        val info = wm.connectionInfo
        val freq = info.frequency
        val band = when {
            freq >= 5925 -> "6 GHz"
            freq >= 4900 -> "5 GHz"
            freq in 2400..2500 -> "2.4 GHz"
            else -> "—"
        }
        val rssi = info.rssi
        val level = WifiManager.calculateSignalLevel(rssi, 5).coerceIn(0, 4)

        val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        var ssid = info.ssid?.trim('"') ?: ""
        if (ssid.isEmpty() || ssid == "<unknown ssid>") ssid = if (hasLoc) "(hidden)" else "(enable location to see)"

        val tx = info.txLinkSpeedMbps
        val rx = info.rxLinkSpeedMbps
        val std = if (Build.VERSION.SDK_INT >= 30) wifiStd(info.wifiStandard) else ""

        val dhcp = wm.dhcpInfo
        val gw = intToIp(dhcp?.gateway ?: 0)
        val dns = intToIp(dhcp?.dns1 ?: 0)

        return WifiDetails(
            connected = true,
            ssid = ssid,
            bssid = info.bssid ?: "",
            linkSpeedMbps = info.linkSpeed,
            rssi = rssi,
            frequencyMhz = freq,
            band = band,
            channel = freqToChannel(freq),
            ipAddress = localIpv4(),
            gateway = gw,
            dns = dns,
            txLinkSpeed = tx,
            rxLinkSpeed = rx,
            signalLevel = level,
            standard = std,
        )
    }

    private fun wifiStd(s: Int): String = when (s) {
        4 -> "Wi-Fi 4 (n)"
        5 -> "Wi-Fi 5 (ac)"
        6 -> "Wi-Fi 6 (ax)"
        7 -> "Wi-Fi 6E"
        8 -> "Wi-Fi 7 (be)"
        1 -> "802.11 legacy"
        3 -> "802.11g"
        else -> "—"
    }

    fun mobile(context: Context): MobileDetails {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val onCell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val operator = tm.networkOperatorName ?: ""
        var dbm = 0
        var level = 0
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val ss = tm.signalStrength
                val list = ss?.cellSignalStrengths
                val best: CellSignalStrength? = list?.firstOrNull()
                if (best != null) {
                    dbm = best.dbm
                    level = best.level
                }
            } catch (_: Throwable) {
            }
        }

        val type = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            try { dataNetworkType(tm.dataNetworkType) } catch (_: Throwable) { "—" to "—" }
        } else "—" to "—"

        return MobileDetails(
            connected = onCell,
            operator = operator,
            networkType = type.first,
            generation = type.second,
            signalDbm = dbm,
            signalLevel = level,
            roaming = tm.isNetworkRoaming,
        )
    }

    private fun dataNetworkType(t: Int): Pair<String, String> = when (t) {
        TelephonyManager.NETWORK_TYPE_NR -> "NR" to "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE" to "4G"
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_UMTS -> "HSPA/UMTS" to "3G"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "EDGE/GPRS" to "2G"
        else -> "Type $t" to "—"
    }

    fun publicNet(): PublicNet? {
        return try {
            val txt = (URL("https://ipwho.is/").openConnection() as HttpURLConnection).run {
                connectTimeout = 4000; readTimeout = 4000
                inputStream.bufferedReader().readText().also { disconnect() }
            }
            fun field(k: String): String {
                val m = Regex("\"$k\"\\s*:\\s*\"([^\"]*)\"").find(txt)
                return m?.groupValues?.get(1) ?: ""
            }
            val conn = field("connection_isp").ifEmpty {
                Regex("\"isp\"\\s*:\\s*\"([^\"]*)\"").find(txt)?.groupValues?.get(1) ?: ""
            }
            PublicNet(
                ip = field("ip"),
                isp = conn.ifEmpty { field("org") },
                city = field("city"),
                country = field("country"),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun localIpv4(): String {
        try {
            NetworkInterface.getNetworkInterfaces().toList().forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                nif.inetAddresses.toList().forEach { addr ->
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {
        }
        return ""
    }

    private fun intToIp(v: Int): String {
        if (v == 0) return ""
        return "${v and 0xFF}.${v shr 8 and 0xFF}.${v shr 16 and 0xFF}.${v shr 24 and 0xFF}"
    }

    private fun freqToChannel(f: Int): Int = when {
        f == 2484 -> 14
        f in 2412..2472 -> (f - 2412) / 5 + 1
        f in 5170..5825 -> (f - 5000) / 5
        f in 5955..7115 -> (f - 5950) / 5
        else -> 0
    }
}
