package id.omphub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SetupScreen() {
    val st by Controller.setupStatus.collectAsStateWithLifecycle()
    val busy by Controller.setupBusy.collectAsStateWithLifecycle()
    val log by Controller.setupLog.lines.collectAsStateWithLifecycle()
    val game by Controller.gameState.collectAsStateWithLifecycle()
    var sampVer by remember { mutableStateOf(Prefs.sampVersion) }

    LaunchedEffect(Unit) { Controller.refreshStatus() }
    LaunchedEffect(busy) { Controller.refreshStatus() }

    val isSamp = st.engine == ServerType.SAMP
    val serverName = if (isSamp) "SA-MP ${sampVer.label}" else "open.mp"
    val installedVersion = remember(st, sampVer, busy) {
        if (isSamp) SampInstaller.installedVersion() else null
    }
    val installed = st.serverInstalled
    val readyToInstall = st.binaries && st.gameRootfs && !busy && game == RunState.STOPPED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Mesin server", style = MaterialTheme.typography.headlineMedium)
            Text("Pilih platform game server yang ingin kamu jalankan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("PILIH MESIN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ServerType.entries.forEach { type ->
                        val selected = st.engine == type
                        if (selected) {
                            Button(onClick = { Prefs.serverType = type; Controller.refreshStatus() }, enabled = game == RunState.STOPPED, modifier = Modifier.weight(1f)) {
                                Text(type.label)
                            }
                        } else {
                            OutlinedButton(onClick = { Prefs.serverType = type; Controller.refreshStatus() }, enabled = game == RunState.STOPPED, modifier = Modifier.weight(1f)) {
                                Text(type.label)
                            }
                        }
                    }
                }
                if (isSamp) {
                    Text("Versi SA-MP", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SampVersion.entries.forEach { version ->
                            if (sampVer == version) {
                                Button(onClick = { sampVer = version; Prefs.sampVersion = version }, enabled = game == RunState.STOPPED) { Text(version.label) }
                            } else {
                                OutlinedButton(onClick = { sampVer = version; Prefs.sampVersion = version }, enabled = game == RunState.STOPPED) { Text(version.label) }
                            }
                        }
                    }
                }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("STATUS INSTALASI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when {
                                !st.binaries -> "Komponen runtime belum tersedia di APK"
                                !st.gameRootfs -> "Sistem dasar server belum disiapkan"
                                isSamp && installedVersion == null -> "SA-MP belum diunduh"
                                isSamp && installedVersion != sampVer -> "Terpasang SA-MP ${installedVersion?.label}; versi pilihan berbeda"
                                installed -> "$serverName siap digunakan"
                                else -> "$serverName belum diunduh"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Button(
                    onClick = { Controller.installServer() },
                    enabled = readyToInstall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.padding(start = 4.dp))
                    Text(if (installed) "Update $serverName" else "Unduh $serverName")
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp))
                        Text("Sedang menyiapkan mesin server…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (game != RunState.STOPPED) {
                    Text("Hentikan server sebelum mengganti atau memperbarui mesin.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (!st.binaries || !st.gameRootfs) {
                    Text("Runtime dan sistem dasar disiapkan otomatis saat pertama kali aplikasi dijalankan. Pastikan proses persiapan awal sudah selesai.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktivitas unduhan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { Controller.setupLog.clear() }, enabled = !busy) {
                        androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = null)
                        Text(" Bersihkan")
                    }
                }
                if (log.isEmpty()) {
                    Text("Belum ada aktivitas. Pilih mesin server lalu tekan Unduh.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LogView(log, Modifier.fillMaxWidth().height(230.dp))
                }
            }
        }
    }
}
