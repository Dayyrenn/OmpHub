package id.omphub

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val PHPMYADMIN_URL = "http://127.0.0.1:8080/phpmyadmin/"

@Composable
fun DatabaseScreen() {
    val context = LocalContext.current
    val dbState by Controller.dbState.collectAsStateWithLifecycle()
    val setup by Controller.setupStatus.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("phpMyAdmin", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Kelola database MariaDB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    pageError = ""
                    loading = true
                    webView?.loadUrl(PHPMYADMIN_URL)
                },
                enabled = dbState == RunState.RUNNING
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Muat ulang phpMyAdmin")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                label = "MariaDB",
                active = dbState == RunState.RUNNING
            )
            StatusPill(
                label = "phpMyAdmin",
                active = dbState == RunState.RUNNING && pageError.isBlank()
            )
            Spacer(Modifier.weight(1f))
            Text(
                "LOCAL · PORT 8080",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!setup.db) {
            EmptyDatabaseState(
                title = "MariaDB belum dipasang",
                description = "Pasang komponen MariaDB dari tab Setup. phpMyAdmin akan ikut dipasang otomatis.",
                action = "Buka Setup",
                onAction = { toast(context, "Buka tab Setup untuk memasang MariaDB dan phpMyAdmin.") }
            )
        } else if (dbState != RunState.RUNNING) {
            EmptyDatabaseState(
                title = "Database sedang tidak berjalan",
                description = "Jalankan MariaDB untuk membuka phpMyAdmin dan mengelola database lokal OmpHub.",
                action = "Start MariaDB",
                onAction = { ServerService.send(context, ServerService.ACTION_START_DB) }
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp)),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webView = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.loadsImagesAutomatically = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        loading = true
                                        pageError = ""
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        loading = false
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            loading = false
                                            pageError = error?.description?.toString()
                                                ?: "Tidak dapat terhubung ke phpMyAdmin."
                                        }
                                    }
                                }
                                loadUrl(PHPMYADMIN_URL)
                            }
                        },
                        update = { view -> webView = view }
                    )

                    if (loading && pageError.isBlank()) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                Text("Memuat phpMyAdmin…", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    if (pageError.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("phpMyAdmin belum bisa dibuka", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    pageError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = {
                                    pageError = ""
                                    loading = true
                                    webView?.loadUrl(PHPMYADMIN_URL)
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Text("  Coba lagi")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyDatabaseState(
    title: String,
    description: String,
    action: String,
    onAction: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}
