package id.omphub

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

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

    LaunchedEffect(tick, busy) {
        Controller.refreshStatus()
        backups = Controller.listBackups()
    }

    fun mark(ok: Boolean) = if (ok) "✓ " else "✗ "

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section("Langkah pemasangan") {
            Text(mark(st.binaries) + "Binary proot + qemu (ada di APK)")
            if (!st.binaries) {
                Text(
                    "Belum ada. Jalankan scripts/fetch-binaries.sh sebelum build APK.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(mark(st.gameRootfs) + "Rootfs game (Debian i386)")
            Button(onClick = { Controller.installGameRootfs() }, enabled = !busy && game == RunState.STOPPED && st.binaries) {
                Text(if (st.gameRootfs) "Pasang ulang rootfs" else "1. Pasang rootfs game")
            }
            Text(mark(st.openMp) + "open.mp (omp-server)")
            Button(onClick = { Controller.installOpenMp() }, enabled = !busy && game == RunState.STOPPED) {
                Text(if (st.openMp) "Update open.mp" else "2. Pasang open.mp")
            }
            Text(mark(st.db) + "MariaDB (opsional)")
            Button(onClick = { Controller.installDb() }, enabled = !busy && st.binaries) {
                Text(if (st.db) "Pasang ulang MariaDB" else "3. Pasang MariaDB")
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Controller.testProot(Proot.Target.GAME) }, enabled = !busy && st.gameRootfs) {
                    Text("Tes game")
                }
                OutlinedButton(onClick = { Controller.testProot(Proot.Target.DB) }, enabled = !busy && st.db) {
                    Text("Tes DB")
                }
                OutlinedButton(onClick = { Controller.setupLog.clear() }) { Text("Bersihkan log") }
            }
            Text(
                "Pemasangan berjalan selama app terbuka. Jangan tutup app sampai selesai.",
                style = MaterialTheme.typography.bodySmall
            )
            LogView(log, Modifier.fillMaxWidth().height(200.dp))
        }

        Section("Pengaturan server") {
            var port by remember { mutableStateOf(Prefs.port.toString()) }
            var dbPort by remember { mutableStateOf(Prefs.dbPort.toString()) }
            var dbOn by remember { mutableStateOf(Prefs.dbEnabled) }
            var hostName by remember { mutableStateOf("") }
            var maxPlayers by remember { mutableStateOf("") }

            LaunchedEffect(st.openMp) {
                val f = File(Env.serverDir, "config.json")
                if (f.exists()) {
                    val c = OpenMpConfig(f).load()
                    hostName = c.hostName
                    maxPlayers = c.maxPlayers.toString()
                }
            }

            OutlinedTextField(hostName, { hostName = it }, label = { Text("Nama server") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(maxPlayers, { maxPlayers = it.filter(Char::isDigit) }, label = { Text("Max pemain") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port UDP") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(dbPort, { dbPort = it.filter(Char::isDigit) }, label = { Text("Port DB") }, singleLine = true, modifier = Modifier.weight(1f))
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
                    val f = File(Env.serverDir, "config.json")
                    if (f.exists()) {
                        val c = OpenMpConfig(f).load()
                        if (hostName.isNotBlank()) c.hostName = hostName
                        c.maxPlayers = maxPlayers.toIntOrNull()?.coerceIn(1, 1000) ?: 50
                        c.port = Prefs.port
                        c.save()
                    }
                    scope.launch { toast(ctx, "Tersimpan. Restart server agar berlaku.") }
                },
                enabled = game == RunState.STOPPED
            ) { Text("Simpan") }
            Text("Edit lanjutan: tab Files > config.json", style = MaterialTheme.typography.bodySmall)
        }

        Section("Backup") {
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
            if (backups.isEmpty()) Text("Belum ada backup.")
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

        Section("URL unduhan") {
            var u1 by remember { mutableStateOf(Prefs.urlGameRootfs) }
            var u2 by remember { mutableStateOf(Prefs.urlOpenMp) }
            var u3 by remember { mutableStateOf(Prefs.urlAlpine) }
            OutlinedTextField(u1, { u1 = it; Prefs.urlGameRootfs = it.trim() }, label = { Text("Rootfs Debian i386 (.tar.xz/.tar.gz)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(u2, { u2 = it; Prefs.urlOpenMp = it.trim() }, label = { Text("open.mp Linux x86 (.tar.gz)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(u3, { u3 = it; Prefs.urlAlpine = it.trim() }, label = { Text("Alpine aarch64 minirootfs (.tar.gz)") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                Prefs.urlGameRootfs = Prefs.DEF_ROOTFS_GAME; u1 = Prefs.DEF_ROOTFS_GAME
                Prefs.urlOpenMp = Prefs.DEF_OPENMP; u2 = Prefs.DEF_OPENMP
                Prefs.urlAlpine = Prefs.DEF_ALPINE; u3 = Prefs.DEF_ALPINE
            }) { Text("Reset ke default") }
        }

        Section("Android") {
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
}
