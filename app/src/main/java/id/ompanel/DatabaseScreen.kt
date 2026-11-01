package id.omphub

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section("MariaDB") {
            StateChip("MariaDB", db)
            if (!st.db) Text("Belum terpasang. Pasang lewat tab Setup.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { ServerService.send(ctx, ServerService.ACTION_START_DB) },
                    enabled = st.db && db == RunState.STOPPED
                ) { Text("Start") }
                OutlinedButton(
                    onClick = { ServerService.send(ctx, ServerService.ACTION_STOP_DB) },
                    enabled = db == RunState.RUNNING
                ) { Text("Stop") }
            }
        }

        creds?.let { c ->
            Section("Koneksi (dari gamemode)") {
                @Composable
                fun row(label: String, value: String) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodySmall)
                            Text(value, fontFamily = FontFamily.Monospace)
                        }
                        IconButton(onClick = { copyText(ctx, label, value) }) { Icon(Icons.Default.ContentCopy, "Salin") }
                    }
                }
                row("Host", c.host)
                row("Port", c.port.toString())
                row("User", c.user)
                row("Password", c.password)
                row("Database", c.database)
                Text(
                    "Pawn:\nmysql_connect(\"${c.host}\", \"${c.user}\", \"<password>\", \"${c.database}\")",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                Text(
                    "Pakai 127.0.0.1, bukan localhost (rootfs game tidak punya unix socket MariaDB).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Section("SQL console (root)") {
            OutlinedTextField(
                value = sql,
                onValueChange = { sql = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("SQL") },
                minLines = 2
            )
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
                Text(result, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }

        Section("Import / Export") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { importer.launch(arrayOf("*/*")) },
                    enabled = db == RunState.RUNNING
                ) { Text("Import .sql") }
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
                ) { Text("Export dump") }
            }
        }

        Section("Log MariaDB") {
            LogView(dbLines.takeLast(60), Modifier.fillMaxWidth().height(180.dp))
        }
    }
}
