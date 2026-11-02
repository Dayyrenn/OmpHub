package id.omphub

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DatabaseScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db by Controller.dbState.collectAsStateWithLifecycle()
    val st by Controller.setupStatus.collectAsStateWithLifecycle()
    val dbLines by Controller.dbLog.lines.collectAsStateWithLifecycle()
    var creds by remember { mutableStateOf<DbCredentials?>(null) }
    var sql by remember { mutableStateOf("SHOW DATABASES;") }
    var result by remember { mutableStateOf("") }

    LaunchedEffect(db) {
        creds = MariaDb.loadCredentials()
        Controller.refreshStatus()
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val tmp = File(Env.tmpDir, "import.sql")
            ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            val r = MariaDb.importFrom(tmp)
            tmp.delete()
            toast(ctx, if (r.ok) "Import selesai" else "Import gagal: ${r.output.take(300)}")
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Column {
            Text("Database", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "MariaDB untuk gamemode",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))

        ModernCard("MariaDB", icon = Icons.Default.Storage) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StateChip("MariaDB", db)
            }
            if (!st.db) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Belum terpasang. Pasang lewat tab Setup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { ServerService.send(ctx, ServerService.ACTION_START_DB) },
                    enabled = st.db && db == RunState.STOPPED
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Start")
                }
                OutlinedButton(
                    onClick = { ServerService.send(ctx, ServerService.ACTION_STOP_DB) },
                    enabled = db == RunState.RUNNING
                ) {
                    Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Stop")
                }
            }
        }

        creds?.let { c ->
            CollapsibleCard("Koneksi (dari gamemode)", icon = Icons.Default.VpnKey) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CopyRow("Host", c.host)
                    CopyRow("Port", c.port.toString())
                    CopyRow("User", c.user)
                    CopyRow("Password", c.password)
                    CopyRow("Database", c.database)
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        "Pawn:\nmysql_connect(\"${c.host}\", \"${c.user}\", \"<password>\", \"${c.database}\")",
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pakai 127.0.0.1, bukan localhost (rootfs game tidak punya unix socket MariaDB).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ModernCard("SQL console (root)", icon = Icons.Default.Terminal) {
            OutlinedTextField(
                value = sql,
                onValueChange = { sql = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("SQL") },
                minLines = 2,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val r = MariaDb.exec(sql, MariaDb.loadCredentials()?.database)
                        result = r.output.ifBlank { if (r.ok) "OK" else "exit ${r.exit}" }
                    }
                },
                enabled = db == RunState.RUNNING
            ) { Text("Jalankan") }
            if (result.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        result,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        ModernCard("Import / Export", icon = Icons.Default.Upload) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { importer.launch(arrayOf("*/*")) },
                    enabled = db == RunState.RUNNING
                ) {
                    Icon(Icons.Default.Upload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Import .sql")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                            val f = File(Env.backupDir, "db-$stamp.sql")
                            val r = MariaDb.dumpTo(f)
                            toast(ctx, if (r.ok) "Disimpan: ${f.absolutePath}" else "Dump gagal: ${r.output.take(300)}")
                        }
                    },
                    enabled = db == RunState.RUNNING
                ) {
                    Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Export dump")
                }
            }
        }

        CollapsibleCard("Log MariaDB", icon = Icons.Default.Terminal) {
            LogView(dbLines.takeLast(60), Modifier.fillMaxWidth().height(180.dp))
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CopyRow(label: String, value: String) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, fontFamily = FontFamily.Monospace)
        }
        IconButton(onClick = { copyText(ctx, label, value) }) {
            Icon(Icons.Default.ContentCopy, "Salin")
        }
    }
}