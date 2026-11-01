package id.omphub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

enum class RunState { STOPPED, STARTING, RUNNING, STOPPING }

data class SetupStatus(
    val binaries: Boolean = false,
    val gameRootfs: Boolean = false,
    val openMp: Boolean = false,
    val db: Boolean = false
) {
    val ready get() = binaries && gameRootfs && openMp
}

/** Pusat state aplikasi. Proses hidup di sini (bukan di UI) dan dijaga oleh ServerService. */
object Controller {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val gameLog = LogBuffer(1000)
    val dbLog = LogBuffer(300)
    val setupLog = LogBuffer(600)

    val gameState = MutableStateFlow(RunState.STOPPED)
    val dbState = MutableStateFlow(RunState.STOPPED)
    val setupBusy = MutableStateFlow(false)
    val setupStatus = MutableStateFlow(SetupStatus())

    @Volatile private var gameProc: Process? = null
    @Volatile private var gameStdin: BufferedWriter? = null

    // ---------- Status ----------

    fun refreshStatus() {
        setupStatus.value = SetupStatus(
            binaries = Env.binariesOk(),
            gameRootfs = File(Env.rootfsGame, "usr/bin/env").exists(),
            openMp = File(Env.serverDir, "omp-server").exists(),
            db = MariaDb.installed && MariaDb.initialized
        )
    }

    // ---------- Library tambahan untuk rootfs game ----------

    private const val ATOMIC_LIB = "usr/lib/i386-linux-gnu/libatomic.so.1"

    fun gameDepsOk() = File(Env.rootfsGame, ATOMIC_LIB).exists()

    /** Pasang library tambahan yang dibutuhkan omp-server di rootfs Debian slim. */
    fun ensureGameDeps(log: (String) -> Unit) {
        if (gameDepsOk()) return
        log("Memasang library tambahan di rootfs (libatomic1), butuh beberapa menit...")
        val script = "export DEBIAN_FRONTEND=noninteractive; " +
            "apt-get -o APT::Sandbox::User=root update && " +
            "apt-get -o APT::Sandbox::User=root install -y --no-install-recommends libatomic1"
        val r = Proot.build(Proot.Target.GAME, listOf("/bin/sh", "-c", script)).runAndCapture(900)
        log(r.output.takeLast(1500))
        check(r.ok && gameDepsOk()) { "Gagal memasang libatomic1 (exit ${r.exit})" }
    }

    // ---------- Server game (open.mp) ----------

    suspend fun startGame(): Unit = withContext(Dispatchers.IO) {
        if (gameProc?.isAlive == true) return@withContext
        refreshStatus()
        val st = setupStatus.value
        if (!st.binaries) {
            gameLog.add("[panel] Binary proot/qemu tidak ada di APK. Jalankan scripts/fetch-binaries.sh lalu build ulang.")
            return@withContext
        }
        if (!st.gameRootfs || !st.openMp) {
            gameLog.add("[panel] Rootfs/open.mp belum terpasang. Buka tab Setup.")
            return@withContext
        }
        gameState.value = RunState.STARTING
        try {
            ensureGameDeps { gameLog.add(it) }

            // Sinkronkan port & pastikan RCON aktif dengan password kuat
            val cfg = OpenMpConfig(File(Env.serverDir, "config.json")).load()
            cfg.port = Prefs.port
            cfg.rconEnabled = true
            if (cfg.ensureStrongRcon()) gameLog.add("[panel] Password RCON lemah diganti otomatis.")
            cfg.save()
            OpenMpInstaller.fixPerms()

            val pb = Proot.build(
                Proot.Target.GAME, listOf("./omp-server"), "/server",
                listOf(Env.serverDir.absolutePath to "/server")
            )
            pb.redirectErrorStream(true)
            gameLog.add("[panel] Menjalankan omp-server di port ${Prefs.port}...")
            val p = pb.start()
            gameProc = p
            gameStdin = p.outputStream.bufferedWriter()
            gameState.value = RunState.RUNNING
            thread(name = "omp-log", isDaemon = true) {
                p.inputStream.bufferedReader().forEachLine { gameLog.add(it) }
                val code = p.waitFor()
                gameLog.add("[panel] omp-server berhenti (exit $code)")
                gameProc = null
                gameStdin = null
                gameState.value = RunState.STOPPED
            }
        } catch (e: Throwable) {
            gameLog.add("[panel] Gagal start: ${e.message}")
            gameState.value = RunState.STOPPED
        }
    }

    suspend fun stopGame(): Unit = withContext(Dispatchers.IO) {
        val p = gameProc ?: run { gameState.value = RunState.STOPPED; return@withContext }
        gameState.value = RunState.STOPPING
        runCatching { gameStdin?.apply { write("exit\n"); flush() } }
        if (!p.waitFor(8, TimeUnit.SECONDS)) {
            p.destroy()
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
        }
    }

    fun sendStdin(line: String) {
        gameLog.add("> $line")
        try {
            gameStdin?.apply { write(line + "\n"); flush() }
                ?: gameLog.add("[panel] Server tidak berjalan")
        } catch (e: Exception) {
            gameLog.add("[panel] Gagal kirim: ${e.message}")
        }
    }

    fun rcon(cmd: String): List<String> {
        val pass = OpenMpConfig(File(Env.serverDir, "config.json")).load().rconPassword
        return Samp.rcon(Prefs.port, pass, cmd)
    }

    // ---------- MariaDB ----------

    suspend fun startDb(): Unit = withContext(Dispatchers.IO) {
        if (dbState.value == RunState.RUNNING || dbState.value == RunState.STARTING) return@withContext
        refreshStatus()
        if (!setupStatus.value.db) {
            dbLog.add("[panel] MariaDB belum terpasang. Buka tab Setup.")
            return@withContext
        }
        dbState.value = RunState.STARTING
        try {
            MariaDb.start(
                onLine = { dbLog.add(it) },
                onExit = { code ->
                    dbLog.add("[panel] mariadbd berhenti (exit $code)")
                    dbState.value = RunState.STOPPED
                }
            )
            if (MariaDb.waitReady()) {
                dbState.value = RunState.RUNNING
                if (MariaDb.loadCredentials() == null) {
                    val c = MariaDb.provision()
                    dbLog.add("[panel] Database '${c.database}' dan user '${c.user}' dibuat.")
                }
            } else {
                dbLog.add("[panel] MariaDB tidak siap dalam waktu yang ditentukan.")
                MariaDb.stop()
                dbState.value = RunState.STOPPED
            }
        } catch (e: Throwable) {
            dbLog.add("[panel] Gagal start MariaDB: ${e.message}")
            dbState.value = RunState.STOPPED
        }
    }

    suspend fun stopDb(): Unit = withContext(Dispatchers.IO) {
        if (dbState.value == RunState.STOPPED) return@withContext
        dbState.value = RunState.STOPPING
        MariaDb.stop()
        dbState.value = RunState.STOPPED
    }

    // ---------- Gabungan ----------

    suspend fun startAll() {
        if (Prefs.dbEnabled) {
            refreshStatus()
            if (setupStatus.value.db) startDb()
        }
        startGame()
    }

    suspend fun stopAll() {
        stopGame()
        stopDb()
    }

    // ---------- Setup / instalasi ----------

    private fun runSetup(title: String, block: () -> Unit) {
        if (setupBusy.value) return
        setupBusy.value = true
        scope.launch {
            setupLog.add("( $title )")
            try {
                block()
                setupLog.add("[Sukses] $title selesai")
            } catch (e: Throwable) {
                setupLog.add("[ERROR] ${e.message ?: e}")
            } finally {
                setupBusy.value = false
                refreshStatus()
            }
        }
    }

    private fun doInstallGameRootfs() {
        check(gameState.value == RunState.STOPPED) { "Hentikan server dulu" }
        val archive = File(Env.dlDir, "rootfs-game.tar.gz")
        setupLog.add("Mengunduh i386/debian:bookworm-slim dari Docker Hub")
        var lastPct = -1
        DockerHub.downloadRootfs("i386/debian", "bookworm-slim", archive) { done, total ->
            if (total > 0) {
                val pct = (done * 100 / total).toInt() / 20 * 20
                if (pct != lastPct) { lastPct = pct; setupLog.add("  unduh $pct%") }
            }
        }
        setupLog.add("Mengekstrak (bisa 1-3 menit)...")
        deleteTree(Env.rootfsGame)
        var lastCount = 0
        Extractor.extractTar(archive, Env.rootfsGame) { n ->
            if (n - lastCount >= 1000) { lastCount = n; setupLog.add("  $n entri") }
        }
        Proot.writeNetConfig(Env.rootfsGame)
        archive.delete()
        ensureGameDeps { setupLog.add(it) }
    }

    private fun doInstallOpenMp() {
        check(gameState.value == RunState.STOPPED) { "Hentikan server dulu" }
        OpenMpInstaller.install(Prefs.urlOpenMp) { setupLog.add(it) }
    }

    private fun doInstallDb() {
        check(dbState.value == RunState.STOPPED) { "Hentikan MariaDB dulu" }
        MariaDb.install(Prefs.urlAlpine) { setupLog.add(it) }
    }

    fun installGameRootfs() = runSetup("Pasang rootfs game (Debian i386)") { doInstallGameRootfs() }
    fun installOpenMp() = runSetup("Pasang / update open.mp") { doInstallOpenMp() }
    fun installDb() = runSetup("Pasang MariaDB") { doInstallDb() }

    /** Pasang semua komponen yang belum ada, berurutan: rootfs game, open.mp, lalu MariaDB (bila aktif). */
    fun installAll() = runSetup("Pasang semua komponen") {
        check(Env.binariesOk()) { "Binary proot/qemu belum ada di APK" }
        check(gameState.value == RunState.STOPPED && dbState.value == RunState.STOPPED) { "Hentikan server dulu" }
        refreshStatus()
        if (!setupStatus.value.gameRootfs) doInstallGameRootfs()
        refreshStatus()
        if (!setupStatus.value.openMp) doInstallOpenMp()
        refreshStatus()
        if (Prefs.dbEnabled && !setupStatus.value.db) doInstallDb()
    }

    /** Dipanggil saat app dibuka: pasang otomatis satu kali bila komponen belum lengkap. */
    fun autoSetupOnFirstLaunch() {
        refreshStatus()
        val st = setupStatus.value
        if (Prefs.autoSetupTried || st.ready || !st.binaries) return
        Prefs.autoSetupTried = true
        installAll()
    }

    /** Uji cepat: jalankan `uname -a` di dalam rootfs. */
    fun testProot(target: Proot.Target) = runSetup("Tes PRoot ($target)") {
        check(Env.binariesOk()) { "Binary proot/qemu belum ada di APK" }
        val rootfs = if (target == Proot.Target.GAME) Env.rootfsGame else Env.rootfsDb
        check(rootfs.exists()) { "Rootfs belum dipasang" }
        val r = Proot.build(target, listOf("/bin/uname", "-a")).runAndCapture(60)
        setupLog.add("exit=${r.exit}")
        setupLog.add(r.output.trim().ifEmpty { "(tanpa output)" })
        check(r.ok) { "PRoot gagal, lihat output di atas" }
    }

    // ---------- Backup / restore ----------

    fun listBackups(): List<File> =
        Env.backupDir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun createBackup(): File {
        Env.backupDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(Env.backupDir, "backup-$stamp.zip")
        var sql: File? = null
        if (dbState.value == RunState.RUNNING) {
            val f = File(Env.tmpDir, "database.sql")
            if (MariaDb.dumpTo(f).ok) sql = f
        }
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
            Zips.addDir(z, Env.serverDir, "server")
            sql?.let { Zips.addFile(z, it, "database.sql") }
        }
        sql?.delete()
        return out
    }

    fun restoreBackup(zip: File): String {
        check(gameState.value == RunState.STOPPED) { "Hentikan server dulu" }
        val sqlTmp = File(Env.tmpDir, "restore.sql")
        sqlTmp.delete()
        Zips.unzip(zip) { name ->
            when {
                name.startsWith("server/") -> Zips.safeResolve(Env.serverDir, name.removePrefix("server/"))
                name == "database.sql" -> sqlTmp
                else -> null
            }
        }
        OpenMpInstaller.fixPerms()
        var msg = "Server dipulihkan."
        if (sqlTmp.exists()) {
            msg += if (dbState.value == RunState.RUNNING) {
                val r = MariaDb.importFrom(sqlTmp)
                if (r.ok) " Database di-import." else " Import database gagal: ${r.output.take(200)}"
            } else " database.sql ada di backup; start MariaDB lalu import manual lewat tab Database."
        }
        return msg
    }
}