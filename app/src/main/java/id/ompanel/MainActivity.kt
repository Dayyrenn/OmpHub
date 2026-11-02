package id.omphub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OmpRoot() }
    }
}

private val TABS: List<Pair<String, ImageVector>> = listOf(
    "Server" to Icons.Default.Dashboard,
    "Console" to Icons.Default.Terminal,
    "Files" to Icons.Default.Folder,
    "Database" to Icons.Default.Storage,
    "Mesin" to Icons.Default.Settings
)

private val OmpColors = darkColorScheme(
    primary = Color(0xFF80B7FF),
    onPrimary = Color(0xFF062F59),
    secondary = Color(0xFF9CCBFF),
    background = Color(0xFF0B1220),
    surface = Color(0xFF111C2D),
    surfaceVariant = Color(0xFF1B2A40),
    onSurface = Color(0xFFE8F0FC),
    onSurfaceVariant = Color(0xFFB1C1D6),
    outline = Color(0xFF40536D)
)

@Composable
fun OmpRoot() {
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Controller.autoSetupOnFirstLaunch() } }
    MaterialTheme(colorScheme = OmpColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val gate by Controller.bootGate.collectAsState()
            if (gate != BootGate.DONE) {
                BootScreen()
                return@Surface
            }
            var tab by rememberSaveable { mutableIntStateOf(0) }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        TABS.forEachIndexed { index, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            ) { padding ->
                val setupBusy by Controller.setupBusy.collectAsState()
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (setupBusy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "Menyiapkan komponen server…",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        when (tab) {
                            0 -> DashboardScreen()
                            1 -> ConsoleScreen()
                            2 -> FilesScreen()
                            3 -> DatabaseScreen()
                            else -> SetupScreen()
                        }
                    }
                }
            }
        }
    }
}
