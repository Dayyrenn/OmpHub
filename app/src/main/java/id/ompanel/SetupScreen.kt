package id.omphub

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SetupScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by Controller.setupStatus.collectAsStateWithLifecycle()
    val busy by Controller.setupBusy.collectAsStateWithLifecycle()
    val log by Controller.setupLog.lines.collectAsStateWithLifecycle()
    val game by Controller.gameState.collectAsStateWithLifecycle()
    var backups by remember { mutableStateOf(Controller.listBackups()) }
    var tick by remember { mutableIntStateOf(0) }
    var sampVer by remember { mutableStateOf(Prefs.sampVersion) }
    val serverName = if (st.engine == ServerType.SAMP) "SA-MP ${sampVer.label}" else st.engine.label

    // Pengaturan server (di-hoist supaya state tidak hilang saat collapse)
    var port by remember { mutableStateOf(Prefs.port.toString()) }
    var dbPort by remember { mutableStateOf(Prefs.dbPort.toString()) }
    var dbOn by remember { mutableStateOf(Prefs.dbEnabled) }
    var hostName by remember { mutableStateOf("") }
    var maxPlayers by remember { mutableStateOf("") }

    LaunchedEffect(tick, busy) {
        Controller.refreshStatus()
        backups = Controller.listBackups()
    }

    LaunchedEffect(st.serverInstalled, st.engine) {
        openServerConfig(st.engine)?.let { c ->
            hostName = c.hostName
            maxPlayers = c.maxPlayers.toString()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Column {
            Text("Setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Pilih dan pasang mesin server",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))

        // ============ MESIN SERVER (PRIMARY) ============
        ModernCard("Mesin server", icon = Icons.Default.Memory) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EngineCard(
                    "open.mp", "Modern & open-source",
                    selected = st.engine == ServerType.OMP,
                    enabled = game == RunState.STOPPED,
                    onClick = { Prefs.serverType = ServerType.OMP; Controller.refreshStatus() },
                    modifier = Modifier.weight(1f)
                )
                EngineCard(
                    "SA-MP", "Klasik & kompatibel",
                    selected = st.engine == ServerType.SAMP,
                    enabled = game == RunState.STOPPED,
                    onClick = {
                        Prefs.serverType = ServerType.SAMP
                        sampVer = Prefs.sampVersion
                        Controller.refreshStatus()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (st.engine == ServerType.SAMP) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SampVersion.entries.forEach { v ->
                        FilterChip(
                            selected = sampVer == v,
                            onClick = { Prefs.sampVersion = v; sampVer = v },
                            label = { Text(v.label) },
                            enabled = game == RunState.STOPPED
                        )
                    }
                }
            }

            val installedV = if (st.engine == ServerType.SAMP) SampInstaller.installedVersion() else null
            val upToDate = st.serverInstalled &&
                (st.engine != ServerType.SAMP || installedV == sampVer)

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (upToDate) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (upToDate) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                        null,
                        tint = if (upToDate) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when {
                            upToDate -> "$serverName terpasang"
                            st.serverInstalled && installedV != null && installedV != sampVer ->
                                "Terpasang: ${installedV.label}"
                            st.serverInstalled -> "$serverName terpasang"
                            else -> "$serverName belum terpasang"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { Controller.installServer() },
                enabled = !busy && game == RunState.STOPPED,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (upToDate) "Update $serverName" else "Unduh $serverName")
            }

            if (busy) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "open.mp dan SA-MP memakai folder server yang sama — gamemode (.amx) & plugin (.so) tidak perlu upload ulang.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ============ DEPENDENSI (muncul hanya jika belum lengkap) ============
        if (!st.binaries || !st.gameRootfs || !st.db) {
            ModernCard("Dependensi", icon = Icons.Default.Build) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!st.binaries) {
                        Text(
                            "Binary proot/qemu tidak ada di APK. Jalankan scripts/fetch-binaries.sh sebelum build.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (!st.gameRootfs) {
                        DepRow(
                            label = "Rootfs game (Debian i386)",
                            action = "Pasang",
                            enabled = !busy && game == RunState.STOPPED && st.binaries
                        ) { Controller.installGameRootfs() }
                    }
                    if (!st.db) {
                        DepRow(
                            label = "MariaDB (opsional)",
                            action = "Pasang",
                            enabled = !busy && st.binaries
                        ) { Controller.installDb() }
                    }
                }
            }
        }

        // ============ ADVANCED (collapsible, default tertutup) ============
        CollapsibleCard("Pengaturan server", icon = Icons.Default.Settings) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    hostName, { hostName = it },
                    label = { Text("Nama server") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        maxPlayers, { maxPlayers = it.filter(Char::isDigit) },
                        label = { Text("Max pemain") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit) },
                        label = { Text("Port UDP") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        dbPort, { dbPort = it.filter(Char::isDigit) },
                        label = { Text("Port DB") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Jalankan MariaDB bersama server", Modifier.weight(1f))
                    Switch(checked = dbOn, onCheckedChange = { dbOn = it })
                }
                Button(
                    onClick = {
                        Prefs.port = port.toIntOrNull()?.coerceIn(1024, 65535) ?: 7777
                        Prefs.dbPort = dbPort.toIntOrNull()?.coerceIn(1024, 65535) ?: 3306
                        Prefs.dbEnabled = dbOn
                        openServerConfig(Prefs.serverType)?.let { c ->
                            if (hostName.isNotBlank()) c.hostName = hostName
                            c.maxPlayers = maxPlayers.toIntOrNull()?.coerceIn(1, 1000) ?: 50
                            c.port = Prefs.port
                            c.save()
                        }
                        scope.launch { toast(ctx, "Tersimpan. Restart server agar berlaku.") }
                    },
                    enabled = game == RunState.STOPPED
                ) { Text("Simpan") }
                Text(
                    "Edit lanjutan: tab Files > ${st.engine.configName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        CollapsibleCard("Backup", icon = Icons.Default.Backup) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            runCatching { Controller.createBackup() }
                                .onSuccess { toast(ctx, "Backup: ${it.name}") }
                                .onFailure { toast(ctx, "Backup gagal: ${it.message}") }
                            tick++
                        }
                    },
                    enabled = !busy
                ) { Text("Buat backup (server + database)") }
                if (backups.isEmpty()) Text("Belum ada backup.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                backups.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name)
                            Text(humanSize(f.length()), style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    runCatching { Controller.restoreBackup(f) }
                                        .onSuccess { toast(ctx, it) }
                                        .onFailure { toast(ctx, "Restore gagal: ${it.message}") }
                                    tick++
                                }
                            },
                            enabled = game == RunState.STOPPED
                        ) { Text("Restore") }
                        IconButton(onClick = { f.delete(); tick++ }) { Icon(Icons.Default.Delete, "Hapus") }
                    }
                }
                Text("Lokasi: ${Env.backupDir.absolutePath}", style = MaterialTheme.typography.bodySmall)
            }
        }

        CollapsibleCard("URL unduhan", icon = Icons.Default.Link) {
            var u1 by remember { mutableStateOf(Prefs.urlGameRootfs) }
            var u2 by remember { mutableStateOf(Prefs.urlOpenMp) }
            var u3 by remember { mutableStateOf(Prefs.urlAlpine) }
            var u4 by remember { mutableStateOf(Prefs.urlSamp) }
            var u5 by remember { mutableStateOf(Prefs.urlSampDl) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(u1, { u1 = it; Prefs.urlGameRootfs = it.trim() }, label = { Text("Rootfs Debian i386 (.tar.xz/.tar.gz)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(u2, { u2 = it; Prefs.urlOpenMp = it.trim() }, label = { Text("open.mp Linux x86 (.tar.gz)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(u4, { u4 = it; Prefs.urlSamp = it.trim() }, label = { Text("SA-MP 0.3.7 server Linux (.tar.gz)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(u5, { u5 = it; Prefs.urlSampDl = it.trim() }, label = { Text("SA-MP 0.3.DL server Linux (.tar.gz)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(u3, { u3 = it; Prefs.urlAlpine = it.trim() }, label = { Text("Alpine aarch64 minirootfs (.tar.gz)") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    Prefs.urlGameRootfs = Prefs.DEF_ROOTFS_GAME; u1 = Prefs.DEF_ROOTFS_GAME
                    Prefs.urlOpenMp = Prefs.DEF_OPENMP; u2 = Prefs.DEF_OPENMP
                    Prefs.urlAlpine = Prefs.DEF_ALPINE; u3 = Prefs.DEF_ALPINE
                    Prefs.urlSamp = Prefs.DEF_SAMP; u4 = Prefs.DEF_SAMP
                    Prefs.urlSampDl = Prefs.DEF_SAMP_DL; u5 = Prefs.DEF_SAMP_DL
                }) { Text("Reset ke default") }
            }
        }

        CollapsibleCard("Android", icon = Icons.Default.PhoneAndroid) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Android bisa mematikan proses background. Matikan optimasi baterai untuk app ini supaya server stabil.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = {
                    val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
                    val i = if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    } else {
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    }
                    ctx.startActivity(i)
                }) { Text("Pengaturan baterai") }
            }
        }

        CollapsibleCard("Log pemasangan", icon = Icons.Default.Terminal) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Controller.testProot(Proot.Target.GAME) }, enabled = !busy && st.gameRootfs) {
                        Text("Tes game")
                    }
                    OutlinedButton(onClick = { Controller.testProot(Proot.Target.DB) }, enabled = !busy && st.db) {
                        Text("Tes DB")
                    }
                    OutlinedButton(onClick = { Controller.setupLog.clear() }) { Text("Bersihkan") }
                }
                Text(
                    "Pemasangan berjalan selama app terbuka. Jangan tutup app sampai selesai.",
                    style = MaterialTheme.typography.bodySmall
                )
                LogView(log, Modifier.fillMaxWidth().height(200.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EngineCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Icon(
                        Icons.Default.CheckCircle, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DepRow(label: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = onClick, enabled = enabled) { Text(action) }
    }
}