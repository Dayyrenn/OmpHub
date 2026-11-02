package id.omphub

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.os.Parcel
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PHPMYADMIN_URL = "http://127.0.0.1:8080/phpmyadmin/"

private const val PHPMYADMIN_STATE_FILE = "phpmyadmin_webview_state.bin"

private fun readPhpMyAdminState(context: android.content.Context): Bundle? {
    val file = File(context.filesDir, PHPMYADMIN_STATE_FILE)
    if (!file.exists()) return null
    return runCatching {
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(file.readBytes(), 0, file.length().toInt())
            parcel.setDataPosition(0)
            parcel.readBundle(WebView::class.java.classLoader)
        } finally {
            parcel.recycle()
        }
    }.getOrNull()
}

private fun writePhpMyAdminState(context: android.content.Context, state: Bundle) {
    runCatching {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(state)
            File(context.filesDir, PHPMYADMIN_STATE_FILE).writeBytes(parcel.marshall())
        } finally {
            parcel.recycle()
        }
    }
}

@Composable
fun DatabaseScreen() {
    val context = LocalContext.current
    val dbState by Controller.dbState.collectAsStateWithLifecycle()
    val setup by Controller.setupStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf("") }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                operation = "Mengimpor file SQL..."
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        check(MariaDb.isRunning()) { "MariaDB tidak sedang berjalan." }
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("File SQL tidak dapat dibaca.")
                        val temp = File(Env.tmpDir, "omphub-import-${System.currentTimeMillis()}.sql")
                        try {
                            input.use { source -> temp.outputStream().use { target -> source.copyTo(target) } }
                            MariaDb.importFrom(temp)
                        } finally {
                            temp.delete()
                        }
                    }
                }
                busy = false
                operation = ""
                result.fold(
                    onSuccess = { r ->
                        toast(context, if (r.ok) "Import SQL ke database samp berhasil." else "Import gagal: ${r.output.take(500)}")
                    },
                    onFailure = { e -> toast(context, "Import gagal: ${e.message ?: "kesalahan tidak diketahui"}") }
                )
            }
        }
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/sql")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                operation = "Mengekspor database SQL..."
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        check(MariaDb.isRunning()) { "MariaDB tidak sedang berjalan." }
                        val temp = File(Env.tmpDir, "omphub-export-${System.currentTimeMillis()}.sql")
                        try {
                            val dump = MariaDb.dumpTo(temp)
                            check(dump.ok && temp.exists() && temp.length() > 0L) {
                                "Export gagal: ${dump.output.take(500).ifBlank { "mariadb-dump tidak menghasilkan file." }}"
                            }
                            val output = context.contentResolver.openOutputStream(uri)
                                ?: error("Lokasi tujuan tidak dapat ditulis.")
                            output.use { target -> temp.inputStream().use { it.copyTo(target) } }
                        } finally {
                            temp.delete()
                        }
                    }
                }
                busy = false
                operation = ""
                result.fold(
                    onSuccess = { toast(context, "Export database samp berhasil.") },
                    onFailure = { e -> toast(context, "Export gagal: ${e.message ?: "kesalahan tidak diketahui"}") }
                )
            }
        }
    }

    val dbReady = setup.db && dbState == RunState.RUNNING
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Database", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (dbReady) "phpMyAdmin berjalan di dalam OmpHub."
                    else "Jalankan MariaDB untuk membuka phpMyAdmin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { webView?.reload() },
                enabled = dbReady && !busy
            ) { Text("Refresh") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { importer.launch(arrayOf("application/sql", "application/x-sql", "text/plain", "*/*")) },
                enabled = dbReady && !busy,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Text(" Import SQL")
            }
            OutlinedButton(
                onClick = { exporter.launch("omphub-samp-backup.sql") },
                enabled = dbReady && !busy,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Text(" Export SQL")
            }
        }

        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(operation, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (!dbReady) {
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (!setup.db) "MariaDB/phpMyAdmin belum terpasang. Pasang melalui tab Setup."
                        else "MariaDB belum berjalan. Jalankan server database terlebih dahulu.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        webView = this
                        val restored = readPhpMyAdminState(ctx)?.let { restoreState(it) }
                        if (restored == null) loadUrl(PHPMYADMIN_URL)
                    }
                },
                update = { view ->
                    webView = view
                    if (view.url == null) view.loadUrl(PHPMYADMIN_URL)
                },
                onRelease = { view ->
                    runCatching {
                        val state = Bundle()
                        view.saveState(state)
                        writePhpMyAdminState(context, state)
                    }
                    if (webView === view) webView = null
                    view.stopLoading()
                    view.destroy()
                }
            )
        }
    }
}
