package id.omphub

import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DatabaseScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbState by Controller.dbState.collectAsStateWithLifecycle()
    val setup by Controller.setupStatus.collectAsStateWithLifecycle()
    val logs by Controller.dbLog.lines.collectAsStateWithLifecycle()

    var databases by remember { mutableStateOf<List<String>>(emptyList()) }
    var tables by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDb by remember { mutableStateOf("") }
    var selectedTable by remember { mutableStateOf("") }
    var tableResult by remember { mutableStateOf("") }
    var sql by remember { mutableStateOf("SHOW DATABASES;") }
    var sqlResult by remember { mutableStateOf("") }
    var newDatabase by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun refreshDatabases() {
        scope.launch {
            busy = true; error = ""
            try {
                val result = withContext(Dispatchers.IO) { MariaDb.listDatabases() }
                databases = result
                if (selectedDb !in result) {
                    selectedDb = result.firstOrNull { it !in listOf("information_schema", "performance_schema", "mysql", "sys") }.orEmpty()
                    selectedTable = ""; tables = emptyList(); tableResult = ""
                }
            } catch (e: Exception) { error = e.message ?: "Gagal memuat database" }
            busy = false
        }
    }

    fun refreshTables(database: String) {
        scope.launch {
            busy = true; error = ""
            try {
                tables = withContext(Dispatchers.IO) { MariaDb.listTables(database) }
                selectedTable = tables.firstOrNull().orEmpty()
                tableResult = ""
            } catch (e: Exception) { error = e.message ?: "Gagal memuat tabel"; tables = emptyList() }
            busy = false
        }
    }

    fun browse(database: String, table: String) {
        scope.launch {
            busy = true; error = ""
            try {
                val r = withContext(Dispatchers.IO) { MariaDb.browseTable(database, table) }
                if (!r.ok) error = r.output
                else tableResult = r.output.ifBlank { "(Tabel kosong)" }
            } catch (e: Exception) { error = e.message ?: "Gagal membaca tabel" }
            busy = false
        }
    }

    LaunchedEffect(dbState, setup.db) {
        if (dbState == RunState.RUNNING && setup.db) refreshDatabases()
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val tmp = File(Env.tmpDir, "import.sql")
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
                val r = MariaDb.importFrom(tmp)
                withContext(Dispatchers.Main) { toast(ctx, if (r.ok) "Import SQL selesai" else "Import gagal: ${r.output.take(300)}") }
            }.onFailure { withContext(Dispatchers.Main) { toast(ctx, "Import gagal: ${it.message}") } }
            tmp.delete()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Section("Database Manager") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("MariaDB lokal", style = MaterialTheme.typography.titleMedium)
                    Text("127.0.0.1:${Prefs.dbPort} · root via socket internal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StateChip("MariaDB", dbState)
            }
            if (!setup.db) Text("MariaDB belum terpasang. Pasang melalui Setup.", color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ServerService.send(ctx, ServerService.ACTION_START_DB) }, enabled = setup.db && dbState == RunState.STOPPED) { Text("Start") }
                OutlinedButton(onClick = { ServerService.send(ctx, ServerService.ACTION_STOP_DB) }, enabled = dbState == RunState.RUNNING) { Text("Stop") }
                IconButton(onClick = { refreshDatabases() }, enabled = dbState == RunState.RUNNING && !busy) { Icon(Icons.Default.Refresh, "Refresh") }
            }
            OutlinedButton(
                onClick = {
                    val phpMyAdminUrl = "http://127.0.0.1:8080/phpmyadmin/"
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(phpMyAdminUrl))
                    ctx.startActivity(Intent.createChooser(browserIntent, "Buka phpMyAdmin dengan browser"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                Text("  Buka phpMyAdmin di Browser")
            }
            Text(
                "Alamat: http://127.0.0.1:8080/phpmyadmin/ · Pastikan layanan web phpMyAdmin sudah berjalan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (busy) CircularProgressIndicator()
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        if (dbState == RunState.RUNNING) {
            Section("Database") {
                if (databases.isEmpty()) Text("Belum ada database atau gagal memuat daftar.", style = MaterialTheme.typography.bodySmall)
                databases.forEach { name ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
                        FilterChip(
                            selected = selectedDb == name,
                            onClick = { selectedDb = name; selectedTable = ""; refreshTables(name) },
                            label = { Text(name) },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newDatabase, { newDatabase = it }, Modifier.weight(1f), label = { Text("Nama database baru") }, singleLine = true)
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val r = MariaDb.createDatabase(newDatabase.trim())
                            withContext(Dispatchers.Main) {
                                if (r.ok) { newDatabase = ""; refreshDatabases(); toast(ctx, "Database dibuat") }
                                else toast(ctx, "Gagal: ${r.output.take(200)}")
                            }
                        }
                    }, enabled = newDatabase.isNotBlank() && !busy) { Text("Buat") }
                }
            }

            if (selectedDb.isNotBlank()) {
                Section("Tabel · $selectedDb") {
                    if (tables.isEmpty()) Text("Database ini belum memiliki tabel.", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tables.forEach { table ->
                            FilterChip(
                                selected = selectedTable == table,
                                onClick = { selectedTable = table; browse(selectedDb, table) },
                                label = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.TableChart, null); Text(table, modifier = Modifier.padding(start = 5.dp)) } }
                            )
                        }
                    }
                    if (selectedTable.isNotBlank()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Isi tabel (maks. 100 baris)", style = MaterialTheme.typography.labelLarge)
                            OutlinedButton(onClick = { browse(selectedDb, selectedTable) }, enabled = !busy) { Text("Muat ulang") }
                        }
                        if (tableResult.isNotBlank()) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Text(tableResult, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }

            Section("SQL Query") {
                Text("Jalankan query langsung ke MariaDB. Query DDL/DML dapat mengubah atau menghapus data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = sql, onValueChange = { sql = it }, modifier = Modifier.fillMaxWidth(), label = { Text("SQL") }, minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val r = MariaDb.query(sql, selectedDb.takeIf { it.isNotBlank() })
                            withContext(Dispatchers.Main) { sqlResult = r.output.ifBlank { if (r.ok) "Query berhasil." else "Query gagal (exit ${r.exit})" }; if (!r.ok) error = r.output }
                        }
                    }, enabled = !busy) { Text("Jalankan") }
                    OutlinedButton(onClick = { sql = "SHOW TABLES;" }) { Text("SHOW TABLES") }
                    IconButton(onClick = { copyText(ctx, "Hasil query", sqlResult) }, enabled = sqlResult.isNotBlank()) { Icon(Icons.Default.ContentCopy, "Salin hasil") }
                }
                if (sqlResult.isNotBlank()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(sqlResult, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }

            Section("Import / Export SQL") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { importer.launch(arrayOf("application/sql", "text/plain", "*/*")) }) { Text("Import .sql") }
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                            val f = File(Env.backupDir, "db-$stamp.sql")
                            val r = MariaDb.dumpTo(f)
                            withContext(Dispatchers.Main) { toast(ctx, if (r.ok) "Export tersimpan: ${f.absolutePath}" else "Export gagal: ${r.output.take(300)}") }
                        }
                    }) { Text("Export database") }
                }
            }
        }

        Section("Log MariaDB") { LogView(logs.takeLast(50), Modifier.fillMaxWidth().height(160.dp)) }
    }
}
