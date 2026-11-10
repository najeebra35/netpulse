package com.netpulse.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

private const val GRAPH_N = 120

// ---------------- LIVE ----------------

@Composable
fun LiveScreen() {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val monitor = remember { SpeedMonitor() }
    var sample by remember { mutableStateOf(SpeedSample(0, 0, 0, 0)) }
    val downHist = remember { mutableStateListOf<Float>() }
    val upHist = remember { mutableStateListOf<Float>() }
    var transport by remember { mutableStateOf("—") }
    var notifOn by remember { mutableStateOf(false) }

    val notifLauncher = rememberPermission { granted -> if (granted) { SpeedService.start(context); notifOn = true } }

    LaunchedEffect(Unit) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            monitor.sample()
            while (true) {
                delay(1000)
                val s = monitor.sample()
                sample = s
                transport = NetworkInfoReader.activeTransport(context)
                downHist.add(s.downBps.toFloat()); if (downHist.size > GRAPH_N) downHist.removeAt(0)
                upHist.add(s.upBps.toFloat()); if (upHist.size > GRAPH_N) upHist.removeAt(0)
            }
        }
    }

    ScreenScaffold("📶 Live speed") {
        item {
            Section {
                Text(transport, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BigSpeed("Download", sample.downBps, Sky, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    BigSpeed("Upload", sample.upBps, Emerald, Modifier.weight(1f))
                }
            }
        }
        item {
            Section("Last 2 minutes") {
                DualGraph(downHist.toList(), upHist.toList())
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(Sky, "Download"); LegendDot(Emerald, "Upload")
                }
            }
        }
        item {
            Section("This session") {
                InfoRow("Downloaded", Fmt.bytes(sample.totalRxBytes))
                InfoRow("Uploaded", Fmt.bytes(sample.totalTxBytes))
                InfoRow("Total", Fmt.bytes(sample.totalRxBytes + sample.totalTxBytes), Sky)
            }
        }
        item {
            Section("Status bar speed") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Live ↓/↑ in notification", fontWeight = FontWeight.Medium)
                        Text("App close pannalum status bar-la speed theriyum",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = notifOn,
                        onCheckedChange = { want ->
                            if (want) {
                                val np = notifPerm()
                                if (np.isEmpty() || hasPermission(context, np)) {
                                    SpeedService.start(context); notifOn = true
                                } else notifLauncher.launch(np)
                            } else {
                                SpeedService.stop(context); notifOn = false
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Sky)
                    )
                }
            }
        }
    }
}

@Composable
private fun BigSpeed(label: String, bps: Long, color: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (label == "Download") "↓" else "↑", color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        val bits = bps * 8.0
        val num = when {
            bits >= 1e6 -> "%.1f".format(bits / 1e6)
            bits >= 1e3 -> "%.0f".format(bits / 1e3)
            else -> "$bps"
        }
        val unit = when {
            bits >= 1e6 -> "Mbps"
            bits >= 1e3 -> "Kbps"
            else -> "B/s"
        }
        Text(num, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = color)
        Text(unit, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).let { it }) {
            Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DualGraph(down: List<Float>, up: List<Float>) {
    val grid = MaterialTheme.colorScheme.surfaceVariant
    val maxV = max(down.maxOrNull() ?: 0f, up.maxOrNull() ?: 0f)
    val scale = max(maxV * 1.15f, 1f)
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        for (i in 0..4) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        fun plot(data: List<Float>, color: Color, fill: Boolean) {
            if (data.size < 2) return
            val stepX = size.width / (GRAPH_N - 1)
            val startX = size.width - stepX * (data.size - 1)
            val line = Path(); val area = Path()
            data.forEachIndexed { i, v ->
                val x = startX + i * stepX
                val y = size.height - (v / scale).coerceIn(0f, 1f) * size.height
                if (i == 0) { line.moveTo(x, y); area.moveTo(x, size.height); area.lineTo(x, y) }
                else { line.lineTo(x, y); area.lineTo(x, y) }
            }
            if (fill) {
                area.lineTo(size.width, size.height); area.close()
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
            }
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        plot(down, Sky, true)
        plot(up, Emerald, false)
    }
}

// ---------------- SPEED TEST ----------------

@Composable
fun SpeedTestScreen() {
    val scope = rememberCoroutineScope()
    val tester = remember { SpeedTest() }
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }
    var ping by remember { mutableFloatStateOf(0f) }
    var jitter by remember { mutableFloatStateOf(0f) }
    var download by remember { mutableFloatStateOf(0f) }
    var upload by remember { mutableFloatStateOf(0f) }
    var liveDown by remember { mutableFloatStateOf(0f) }
    var liveUp by remember { mutableFloatStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }

    ScreenScaffold("🚀 Speed test") {
        item {
            Section {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val showLive = running && (phase == "DOWNLOAD" || phase == "UPLOAD")
                        val big = when {
                            phase == "DOWNLOAD" -> liveDown
                            phase == "UPLOAD" -> liveUp
                            else -> max(download, 0f)
                        }
                        if (running && phase == "PING") {
                            CircularProgressIndicator(color = Sky)
                            Spacer(Modifier.height(8.dp))
                            Text("Testing ping…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("%.1f".format(if (showLive || download > 0) big else 0f),
                                fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Sky)
                            Text(if (phase == "UPLOAD") "Mbps · upload" else "Mbps · download",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            Section {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox("Ping", if (ping > 0) "%.0f".format(ping) else "—", "ms", Amber, Modifier.weight(1f))
                    StatBox("Jitter", if (jitter > 0) "%.0f".format(jitter) else "—", "ms", Violet, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox("Download", if (download > 0) "%.1f".format(download) else "—", "Mbps", Sky, Modifier.weight(1f))
                    StatBox("Upload", if (upload > 0) "%.1f".format(upload) else "—", "Mbps", Emerald, Modifier.weight(1f))
                }
            }
        }
        item {
            Button(
                onClick = {
                    if (running) { job?.cancel(); running = false; phase = ""; return@Button }
                    running = true; ping = 0f; jitter = 0f; download = 0f; upload = 0f
                    liveDown = 0f; liveUp = 0f
                    job = scope.launch {
                        phase = "PING"
                        val p = tester.ping { ping = it.toFloat() }
                        ping = p.first.toFloat(); jitter = p.second.toFloat()
                        phase = "DOWNLOAD"
                        download = tester.download { liveDown = it.toFloat() }.toFloat()
                        phase = "UPLOAD"
                        upload = tester.upload { liveUp = it.toFloat() }.toFloat()
                        phase = "DONE"; running = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (running) Rose else Sky)
            ) {
                Text(if (running) "Stop" else "Start test", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        item {
            Text("Cloudflare (speed.cloudflare.com) server vachi test pannudhu. " +
                "WiFi-la actual router/broadband speed-ah kaattum.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------- WIFI ----------------

@Composable
fun WifiScreen() {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    var w by remember { mutableStateOf<WifiDetails?>(null) }
    var pub by remember { mutableStateOf<PublicNet?>(null) }
    var hasLoc by remember { mutableStateOf(hasPermission(context, locationPerm())) }
    val locLauncher = rememberPermission { hasLoc = it }

    LaunchedEffect(Unit) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch(Dispatchers.IO) { pub = NetworkInfoReader.publicNet() }
            while (true) {
                w = withContext(Dispatchers.IO) { NetworkInfoReader.wifi(context) }
                delay(2000)
            }
        }
    }

    ScreenScaffold("📡 Wi-Fi") {
        if (!hasLoc) {
            item {
                Section {
                    Text("Wi-Fi name (SSID) & signal-ah paakka location permission thevai (Android rule).",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { locLauncher.launch(locationPerm()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Sky)) {
                        Text("Allow location")
                    }
                }
            }
        }
        val wd = w
        if (wd == null || !wd.connected) {
            item {
                Section { Text("Wi-Fi connect aagala. Mobile data-la iruntha idhu kaaliya irukkum.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else {
            item {
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignalBars(wd.signalLevel, signalColor(wd.signalLevel), Modifier.height(34.dp).width(40.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(wd.ssid, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("${wd.rssi} dBm · ${wd.band} · Ch ${wd.channel}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            }
            item {
                Section("Connection") {
                    InfoRow("Link speed", "${wd.linkSpeedMbps} Mbps")
                    if (wd.txLinkSpeed >= 0) InfoRow("Tx / Rx link", "${wd.txLinkSpeed} / ${wd.rxLinkSpeed} Mbps")
                    if (wd.standard.isNotEmpty()) InfoRow("Wi-Fi standard", wd.standard)
                    InfoRow("Frequency", "${wd.frequencyMhz} MHz (${wd.band})")
                    InfoRow("Channel", "${wd.channel}")
                    InfoRow("Signal", "${wd.rssi} dBm", signalColor(wd.signalLevel))
                }
            }
            item {
                Section("Addresses") {
                    InfoRow("Device IP", wd.ipAddress, mono = true)
                    InfoRow("Router / Gateway", wd.gateway, mono = true)
                    InfoRow("DNS", wd.dns, mono = true)
                    InfoRow("Router MAC (BSSID)", wd.bssid.uppercase().ifEmpty { "—" }, mono = true)
                }
            }
        }
        item {
            Section("Internet (public)") {
                val p = pub
                if (p == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Sky)
                        Spacer(Modifier.width(10.dp)); Text("Fetching…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    InfoRow("Public IP", p.ip, mono = true)
                    InfoRow("ISP", p.isp)
                    InfoRow("Location", listOf(p.city, p.country).filter { it.isNotEmpty() }.joinToString(", "))
                }
            }
        }
    }
}

// ---------------- DEVICES ----------------

@Composable
fun DevicesScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val scanner = remember { LanScanner(context) }
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val devices = remember { mutableStateListOf<LanDevice>() }
    var done by remember { mutableStateOf(false) }
    var hasLoc by remember { mutableStateOf(hasPermission(context, locationPerm())) }
    val locLauncher = rememberPermission { hasLoc = it }

    fun startScan() {
        scanning = true; done = false; progress = 0f; devices.clear()
        scope.launch {
            val result = scanner.scan { d, t -> progress = d.toFloat() / t }
            devices.clear(); devices.addAll(result)
            scanning = false; done = true
        }
    }

    ScreenScaffold("🖧 Devices on my Wi-Fi") {
        if (!hasLoc) {
            item {
                Section {
                    Text("Device peyar/details sariya kaatta location permission thevai.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { locLauncher.launch(locationPerm()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Sky)) { Text("Allow location") }
                }
            }
        }
        item {
            Button(
                onClick = { if (!scanning) startScan() },
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sky)
            ) {
                if (scanning) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("Scanning… ${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold)
                } else Text(if (done) "Scan again" else "Scan network", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        if (scanning) {
            item { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Sky) }
        }
        if (done || devices.isNotEmpty()) {
            item {
                Text("${devices.size} device${if (devices.size == 1) "" else "s"} found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        items(devices) { d -> DeviceCard(d) }
        if (done && devices.isEmpty()) {
            item { Section { Text("Edhuvum kidaikala. WiFi-la connect aagirukeengala-nu check pannunga.",
                color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        item {
            Text("Ella IP-um unga sondha WiFi network-la irukkura device thaan (router admin page kaatradhe maari). " +
                "Android 10+ la sila phones MAC address-ah hide pannum.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeviceCard(d: LanDevice) {
    val tag = when {
        d.isSelf -> "This device" to Emerald
        d.isGateway -> "Router" to Amber
        else -> null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val name = when {
                        d.hostname.isNotEmpty() -> d.hostname
                        d.vendor.isNotEmpty() -> d.vendor
                        d.isSelf -> "My phone"
                        d.isGateway -> "Router"
                        else -> "Unknown device"
                    }
                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(d.ip, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Sky)
                }
                if (tag != null) Pill(tag.first, tag.second)
            }
            Spacer(Modifier.height(6.dp))
            if (d.mac.isNotEmpty()) Text("MAC: ${d.mac}", fontFamily = FontFamily.Monospace,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (d.vendor.isNotEmpty()) Text("Maker: ${d.vendor}", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (d.reachableMs > 0) Text("Ping: ${d.reachableMs} ms", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------- SIGNAL ----------------

@Composable
fun SignalScreen() {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    var m by remember { mutableStateOf<MobileDetails?>(null) }
    var hasPerms by remember {
        mutableStateOf(hasPermission(context, locationPerm()) && hasPermission(context, phonePerm()))
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { res -> hasPerms = res.values.all { it } }

    LaunchedEffect(hasPerms) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                m = withContext(Dispatchers.IO) { NetworkInfoReader.mobile(context) }
                delay(2000)
            }
        }
    }

    ScreenScaffold("📶 Mobile signal") {
        if (!hasPerms) {
            item {
                Section {
                    Text("Signal strength (dBm), network type-ah paakka location + phone permission thevai.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { launcher.launch(arrayOf(locationPerm(), phonePerm())) },
                        colors = ButtonDefaults.buttonColors(containerColor = Sky)) { Text("Allow") }
                }
            }
        }
        val md = m
        if (md != null) {
            item {
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignalBars(md.signalLevel, signalColor(md.signalLevel), Modifier.height(34.dp).width(40.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(md.operator.ifEmpty { "No SIM / data" }, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Row {
                                if (md.generation != "—") Pill(md.generation, Sky)
                                Spacer(Modifier.width(6.dp))
                                if (md.signalDbm != 0) Text("${md.signalDbm} dBm",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            item {
                Section("Details") {
                    InfoRow("Operator", md.operator.ifEmpty { "—" })
                    InfoRow("Generation", md.generation)
                    InfoRow("Network type", md.networkType)
                    InfoRow("Signal", if (md.signalDbm != 0) "${md.signalDbm} dBm" else "—", signalColor(md.signalLevel))
                    InfoRow("Quality", qualityLabel(md.signalLevel), signalColor(md.signalLevel))
                    InfoRow("Roaming", if (md.roaming) "Yes" else "No")
                    InfoRow("Connected via", if (md.connected) "Mobile data" else "Not active")
                }
            }
            item {
                Text("dBm 0 pakkam: romba nalla signal. -110 dBm maari: romba weak. " +
                    "Veetla poga poga endha room-la signal nalla-nu ingaye paarkalaam.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun qualityLabel(level: Int) = when (level) {
    4 -> "Excellent"
    3 -> "Good"
    2 -> "Fair"
    1 -> "Poor"
    else -> "No signal"
}

// ---------------- DATA ----------------

@Composable
fun DataScreen() {
    val context = LocalContext.current.applicationContext
    var totals by remember { mutableStateOf<UsageTotals?>(null) }
    var apps by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var hasAccess by remember { mutableStateOf(DataUsageReader.hasUsageAccess(context)) }
    val owner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            hasAccess = DataUsageReader.hasUsageAccess(context)
            totals = withContext(Dispatchers.IO) { runCatching { DataUsageReader.totals(context) }.getOrNull() }
            apps = withContext(Dispatchers.IO) { runCatching { DataUsageReader.perApp(context) }.getOrDefault(emptyList()) }
        }
    }

    ScreenScaffold("📊 Data usage (this month)") {
        item {
            Section {
                val t = totals
                val wifi = (t?.wifiRx ?: 0) + (t?.wifiTx ?: 0)
                val mob = (t?.mobileRx ?: 0) + (t?.mobileTx ?: 0)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox("Wi-Fi", Fmt.bytes(wifi), "down+up", Sky, Modifier.weight(1f))
                    StatBox("Mobile", Fmt.bytes(mob), "down+up", Emerald, Modifier.weight(1f))
                }
            }
        }
        if (!hasAccess) {
            item {
                Section {
                    Text("App-wise data usage-ah paakka \"Usage access\" permission kudukkanum.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { openUsageAccessSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Sky)) {
                        Text("Open settings")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Settings-la NetPulse-ah thedi ON pannunga, appuram indha tab-ku thirumba vaanga.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            item {
                Text("Top apps", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
            val top = apps.take(25)
            val maxTotal = top.maxOfOrNull { it.total } ?: 1L
            items(top) { a -> AppUsageRow(a, maxTotal) }
            if (apps.isEmpty()) {
                item { Section { Text("Data illa, illa innum calculate aagala.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
        }
    }
}

@Composable
private fun AppUsageRow(a: AppUsage, maxTotal: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(a.label, Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1)
                Text(Fmt.bytes(a.total), fontWeight = FontWeight.SemiBold, color = Sky, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (a.total.toFloat() / maxTotal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = Sky, trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text("↓ ${Fmt.bytes(a.rxBytes)}   ↑ ${Fmt.bytes(a.txBytes)}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
