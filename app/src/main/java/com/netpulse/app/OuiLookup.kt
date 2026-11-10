package com.netpulse.app

/**
 * Tiny built-in OUI (MAC prefix → maker) table for offline vendor guesses.
 * Covers common phone/router/IoT makers. Not exhaustive — unknown prefixes
 * just show blank. (A full IEEE OUI file is ~30k rows; this keeps the APK small.)
 */
object OuiLookup {
    private val table = mapOf(
        "FC:FB:FB" to "Apple", "F0:18:98" to "Apple", "A4:83:E7" to "Apple",
        "3C:15:C2" to "Apple", "D0:03:4B" to "Apple", "F4:F1:5A" to "Apple",
        "88:66:5A" to "Apple", "6C:72:E7" to "Apple",
        "00:1A:11" to "Google", "F4:F5:E8" to "Google", "3C:28:6D" to "Google",
        "D8:6C:63" to "Google", "AC:67:84" to "Google (Nest)",
        "50:8F:4C" to "Xiaomi", "64:09:80" to "Xiaomi", "F8:A4:5F" to "Xiaomi",
        "28:6C:07" to "Xiaomi", "A4:50:46" to "Xiaomi", "64:B4:73" to "Xiaomi",
        "AC:C1:EE" to "Xiaomi", "FC:64:BA" to "Xiaomi",
        "2C:5B:B8" to "OnePlus", "94:65:2D" to "OnePlus", "C0:EE:FB" to "OnePlus",
        "00:08:22" to "Oppo", "5C:C9:D3" to "Oppo", "F0:AF:50" to "Oppo",
        "34:C3:AC" to "Samsung", "5C:0A:5B" to "Samsung", "8C:77:12" to "Samsung",
        "E8:50:8B" to "Samsung", "D0:17:6A" to "Samsung", "A0:21:95" to "Samsung",
        "CC:07:AB" to "Samsung", "38:AA:3C" to "Samsung",
        "00:1D:0F" to "TP-Link", "50:C7:BF" to "TP-Link", "C4:6E:1F" to "TP-Link",
        "AC:84:C6" to "TP-Link", "98:DA:C4" to "TP-Link", "60:A4:B7" to "TP-Link",
        "B0:BE:76" to "TP-Link", "14:CC:20" to "TP-Link",
        "C8:3A:35" to "Tenda", "D8:32:14" to "Tenda",
        "44:D9:E7" to "Ubiquiti", "FC:EC:DA" to "Ubiquiti", "74:AC:B9" to "Ubiquiti",
        "00:18:E7" to "Netgear", "A0:40:A0" to "Netgear", "9C:3D:CF" to "Netgear",
        "00:26:5A" to "D-Link", "14:D6:4D" to "D-Link",
        "F4:92:BF" to "Cisco", "00:1B:0D" to "Cisco",
        "B8:27:EB" to "Raspberry Pi", "DC:A6:32" to "Raspberry Pi", "E4:5F:01" to "Raspberry Pi",
        "D8:3A:DD" to "Raspberry Pi",
        "EC:FA:BC" to "Espressif (ESP)", "24:0A:C4" to "Espressif (ESP)",
        "A0:20:A6" to "Espressif (ESP)", "7C:9E:BD" to "Espressif (ESP)",
        "C8:2B:96" to "Espressif (ESP)", "30:AE:A4" to "Espressif (ESP)",
        "68:C6:3A" to "Espressif (ESP)", "84:F3:EB" to "Espressif (ESP)",
        "00:17:88" to "Philips Hue", "EC:B5:FA" to "Philips Hue",
        "18:B4:30" to "Nest", "64:16:66" to "Nest",
        "44:65:0D" to "Amazon (Echo)", "68:37:E9" to "Amazon", "FC:65:DE" to "Amazon",
        "0C:47:C9" to "Amazon", "74:C2:46" to "Amazon",
        "B0:4E:26" to "TP-Link (Tapo)", "3C:52:A1" to "Sony",
        "00:24:E4" to "Withings", "00:55:DA" to "IEEE",
        "DC:A6:32" to "Raspberry Pi",
    )

    fun vendor(mac: String): String {
        if (mac.length < 8 || !mac.contains(':')) return ""
        val prefix = mac.substring(0, 8).uppercase()
        return table[prefix] ?: ""
    }
}
