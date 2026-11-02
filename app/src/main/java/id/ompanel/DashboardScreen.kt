package id.omphub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen() {
    val ctx = LocalContext.current
    val game by Controller.gameState.collectAsStateWithLifecycle()
    val db by Controller.dbState.collectAsStateWithLifecycle()
    val st by Controller.setupStatus.collectAsStateWithLifecycle()
    val perf by PerfMonitor.snapshot.collectAsStateWithLifecycle()
    var queryMs by remember { mutableStateOf<Long?>(null) }
    var info by remember { mutableStateOf<Samp.Info?>(null) }
    var players by remember { mutableStateOf(emptyList<Samp.Player>()) }

    LaunchedEffect(Unit) {
        PerfMonitor.ensureStarted()
        withContext(Dispatchers.IO) { Controller.refreshStatus() }
    }

    LaunchedEffect(game) {
        while (game == RunState.RUNNING) {
            val r = withContext(Dispatchers.IO) {
                val t0 = System.nanoTime()
                val i = Samp.info(Prefs.port)
                val ms = (System.nanoTime() - t0) / 1_000_000
                Triple(i, Samp.players(Prefs.port), if (i != null) ms else null)
            }
            info = r.first
            players = r.second
            queryMs = r.third
            delay(3000)
        }
        info = null
        players = emptyList()
        queryMs = null
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Column {
            Text("OmpHub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Server manager",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))

        if (!st.ready) {
            ModernCard("Belum siap", icon = Icons.Default.Warning) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!st.binaries) Text("• Binary proot/qemu tidak ada di APK. Jalankan scripts/fetch-binaries.sh lalu build ulang.")
                    if (!st.gameRootfs) Text("• Rootfs game belum dipasang (tab Setup).")
                    if (!st.serverInstalled) Text("• ${st.engine.label} belum dipasang (tab Setup).")
                }
            }
        }

        ModernCard("Kontrol", icon = Icons.Default.PlayArrow) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StateChip(st.engine.label, game)
                StateChip("MariaDB", db)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { ServerService.send(ctx, ServerService.ACTION_START) },
                    enabled = st.ready && game == RunState.STOPPED
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Start")
                }
                OutlinedButton(
                    onClick = { ServerService.send(ctx, ServerService.ACTION_STOP) },
                    enabled = game != RunState.STOPPED || db != RunState.STOPPED
                ) {
                    Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Stop")
                }
                OutlinedButton(
                    onClick = { ServerService.send(ctx, ServerService.ACTION_RESTART) },
                    enabled = game == RunState.RUNNING
                ) {
                    Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Restart")
                }
            }
        }

        ModernCard("Server", icon = Icons.Default.Dns) {
            val i = info
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (game != RunState.RUNNING) {
                    Text("Server tidak berjalan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (i == null) {
                    Text("Menunggu respons query UDP...")
                    Text(
                        "Kalau terus kosong: pastikan query aktif di ${st.engine.configName}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(i.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Pemain: ${i.players}/${i.maxPlayers}")
                    Text("Mode: ${i.mode}   Bahasa: ${i.language}")
                    if (i.password) Text("Server memakai password")
                }
                Spacer(Modifier.height(4.dp))
                Text("Port UDP: ${Prefs.port}", style = MaterialTheme.typography.bodySmall)
                val ips = localIps()
                if (ips.isNotEmpty()) {
                    Text("Alamat LAN: " + ips.joinToString { "$it:${Prefs.port}" }, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Di HP yang sama: 127.0.0.1:${Prefs.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ModernCard("Performa", icon = Icons.Default.Speed) {
            PerfContent(perf, queryMs)
        }

        if (game == RunState.RUNNING) {
            ModernCard("Pemain (${players.size})", icon = Icons.Default.People) {
                if (players.isEmpty()) {
                    Text("Tidak ada pemain online.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        players.forEach { Text("${it.name}   skor ${it.score}   ping ${it.ping}") }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}