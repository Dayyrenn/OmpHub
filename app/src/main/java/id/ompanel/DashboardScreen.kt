package id.ompanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
    var info by remember { mutableStateOf<Samp.Info?>(null) }
    var players by remember { mutableStateOf(emptyList<Samp.Player>()) }

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Controller.refreshStatus() } }

    LaunchedEffect(game) {
        while (game == RunState.RUNNING) {
            val r = withContext(Dispatchers.IO) { Samp.info(Prefs.port) to Samp.players(Prefs.port) }
            info = r.first
            players = r.second
            delay(3000)
        }
        info = null
        players = emptyList()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "OmpHub",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
        )

        if (!st.ready) {
            Section("Belum siap") {
                if (!st.binaries) Text("• Binary proot/qemu tidak ada di APK. Jalankan scripts/fetch-binaries.sh lalu build ulang.")
                if (!st.gameRootfs) Text("• Rootfs game belum dipasang (tab Setup).")
                if (!st.openMp) Text("• open.mp belum dipasang (tab Setup).")
            }
        }

        Section("Kontrol") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StateChip("open.mp", game)
                StateChip("MariaDB", db)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { ServerService.send(ctx, ServerService.ACTION_START) },
                    enabled = st.ready && game == RunState.STOPPED
                ) { Text("Start") }
                OutlinedButton(
                    onClick = { ServerService.send(ctx, ServerService.ACTION_STOP) },
                    enabled = game != RunState.STOPPED || db != RunState.STOPPED
                ) { Text("Stop") }
                OutlinedButton(
                    onClick = { ServerService.send(ctx, ServerService.ACTION_RESTART) },
                    enabled = game == RunState.RUNNING
                ) { Text("Restart") }
            }
        }

        Section("Server") {
            val i = info
            if (game != RunState.RUNNING) {
                Text("Server tidak berjalan.")
            } else if (i == null) {
                Text("Menunggu respons query UDP...")
                Text(
                    "Kalau terus kosong: pastikan query aktif di config.json open.mp.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(i.name, style = MaterialTheme.typography.titleSmall)
                Text("Pemain: ${i.players}/${i.maxPlayers}")
                Text("Mode: ${i.mode}   Bahasa: ${i.language}")
                if (i.password) Text("Server memakai password")
            }
            Text("Port UDP: ${Prefs.port}")
            val ips = localIps()
            if (ips.isNotEmpty()) Text("Alamat LAN: " + ips.joinToString { "$it:${Prefs.port}" })
            Text("Di HP yang sama: 127.0.0.1:${Prefs.port}", style = MaterialTheme.typography.bodySmall)
        }

        if (game == RunState.RUNNING) {
            Section("Pemain (${players.size})") {
                if (players.isEmpty()) Text("Tidak ada pemain online.")
                players.forEach { Text("${it.name}   skor ${it.score}   ping ${it.ping}") }
            }
        }
    }
}
