package id.omphub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
            val result = withContext(Dispatchers.IO) {
                val started = System.nanoTime()
                val serverInfo = Samp.info(Prefs.port)
                val elapsed = (System.nanoTime() - started) / 1_000_000
                Triple(serverInfo, Samp.players(Prefs.port), if (serverInfo != null) elapsed else null)
            }
            info = result.first
            players = result.second
            queryMs = result.third
            delay(3000)
        }
        info = null
        players = emptyList()
        queryMs = null
    }

    val running = game == RunState.RUNNING
    val statusColor = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("OmpHub", style = MaterialTheme.typography.headlineMedium)
                Text("Server control center", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("v1.1", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("STATUS SERVER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (running) "Server berjalan" else "Server berhenti", style = MaterialTheme.typography.headlineSmall, color = statusColor)
                    }
                    StateChip(st.engine.label, game)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { ServerService.send(ctx, ServerService.ACTION_START) },
                        enabled = st.ready && game == RunState.STOPPED,
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(" Start")
                    }
                    OutlinedButton(
                        onClick = { ServerService.send(ctx, ServerService.ACTION_STOP) },
                        enabled = game != RunState.STOPPED || db != RunState.STOPPED,
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Stop, contentDescription = null)
                        Text(" Stop")
                    }
                    OutlinedButton(
                        onClick = { ServerService.send(ctx, ServerService.ACTION_RESTART) },
                        enabled = running,
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
                if (!st.ready) {
                    Text("Mesin server belum siap. Buka tab Mesin untuk mengunduh open.mp atau SA-MP.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("PEMAIN ONLINE", if (running && info != null) "${info!!.players}/${info!!.maxPlayers}" else "—", Modifier.weight(1f))
            MetricCard("QUERY PING", queryMs?.let { "${it} ms" } ?: "—", Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Informasi server", style = MaterialTheme.typography.titleMedium)
                val serverInfo = info
                Text(if (running && serverInfo != null) serverInfo.name else if (running) "Menunggu respons query UDP…" else "Server belum aktif", style = MaterialTheme.typography.bodyLarge)
                InfoLine("Mesin", st.engine.label)
                InfoLine("Port UDP", Prefs.port.toString())
                InfoLine("Mode", serverInfo?.mode ?: "—")
                InfoLine("Bahasa", serverInfo?.language ?: "—")
                val ips = localIps()
                if (ips.isNotEmpty()) InfoLine("Alamat LAN", ips.joinToString { "$it:${Prefs.port}" })
                InfoLine("Lokal", "127.0.0.1:${Prefs.port}")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Performa perangkat", style = MaterialTheme.typography.titleMedium)
                PerfContent(perf, queryMs)
            }
        }

        if (running) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pemain online (${players.size})", style = MaterialTheme.typography.titleMedium)
                    if (players.isEmpty()) Text("Belum ada pemain online.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    players.forEach { player ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(player.name, Modifier.weight(1f))
                            Text("${player.score} • ${player.ping} ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
