package id.ompanel

import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class DbCredentials(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val database: String
)

/**
 * MariaDB native arm64 di rootfs Alpine terpisah (tanpa qemu). Listen hanya di 127.0.0.1.
 * Datadir di Env.dbData (di-bind ke /var/lib/mysql) jadi tetap ada bila rootfs dipasang ulang.
 * Semua fungsi blocking.
 */
object MariaDb {
    private const val SOCK = "/run/mysqld/mysqld.sock"
    private val binds get() = listOf(Env.dbData.absolutePath to "/var/lib/mysql")

    @Volatile private var proc: Process? = null

    val installed: Boolean get() = File(Env.rootfsDb, "usr/bin/mariadbd").exists()
    val initialized: Boolean get() = File(Env.dbData, "mysql").isDirectory

    private fun sh(script: String, timeoutSec: Long = 300): CmdResult =
        Proot.build(Proot.Target.DB, listOf("/bin/sh", "-c", script), "/", binds).runAndCapture(timeoutSec)

    // ---------- Instalasi ----------

    fun install(url: String, log: (String) -> Unit) {
        if (!File(Env.rootfsDb, "bin/busybox").exists()) {
            val tgz = File(Env.dlDir, archiveNameFor("alpine", url))
            log("Mengunduh Alpine arm64...")
            var lastPct = -1
            downloadFile(url, tgz) { done, total ->
                if (total > 0) {
                    val pct = (done * 100 / total).toInt() / 25 * 25
                    if (pct != lastPct) { lastPct = pct; log("  unduh $pct%") }
                }
            }
            deleteTree(Env.rootfsDb)
            log("Mengekstrak rootfs...")
            Extractor.extractTar(tgz, Env.rootfsDb)
            tgz.delete()
        }
        Proot.writeNetConfig(Env.rootfsDb)

        if (!installed) {
            log("Memasang paket mariadb (apk), butuh beberapa menit...")
            val r = sh("apk add --no-cache mariadb mariadb-client", 900)
            log(r.output.takeLast(600))
            check(r.ok && installed) { "apk add gagal (exit ${r.exit}). Cek koneksi internet." }
        }
        initDataDir(log)
    }

    private fun initDataDir(log: (String) -> Unit) {
        Env.dbData.mkdirs()
        if (initialized) return
        // bersihkan sisa percobaan yang gagal
        Env.dbData.listFiles()?.forEach { deleteTree(it) }
        log("Inisialisasi database...")
        val r = sh(
            "mkdir -p /run/mysqld /tmp && mariadb-install-db --user=root --datadir=/var/lib/mysql " +
                "--skip-test-db --auth-root-authentication-method=normal --skip-name-resolve " +
                "--innodb-use-native-aio=0 --innodb-buffer-pool-size=32M --verbose"
        )
        log(r.output.takeLast(3000))
        if (!(r.ok && initialized)) {
            Env.dbData.listFiles()?.forEach { deleteTree(it) }
            error("mariadb-install-db gagal (exit ${r.exit}), lihat log di atas")
        }
    }

    // ---------- Jalan / berhenti ----------

    fun isRunning(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", Prefs.dbPort), 400); true }
    } catch (_: Exception) { false }

    @Synchronized
    fun start(onLine: (String) -> Unit, onExit: (Int) -> Unit) {
        if (proc?.isAlive == true) return
        check(installed && initialized) { "MariaDB belum terpasang (tab Setup)" }
        val cmd = "mkdir -p /run/mysqld && exec mariadbd --user=root --datadir=/var/lib/mysql " +
            "--bind-address=127.0.0.1 --port=${Prefs.dbPort} --socket=$SOCK " +
            "--pid-file=/run/mysqld/mysqld.pid --skip-name-resolve --innodb-use-native-aio=0 " +
            "--performance-schema=OFF --innodb-buffer-pool-size=32M --max-connections=30"
        val pb = Proot.build(Proot.Target.DB, listOf("/bin/sh", "-c", cmd), "/", binds)
        pb.redirectErrorStream(true)
        val p = pb.start()
        proc = p
        thread(name = "mariadb-log", isDaemon = true) {
            p.inputStream.bufferedReader().forEachLine(onLine)
            val code = p.waitFor()
            proc = null
            onExit(code)
        }
    }

    fun waitReady(timeoutMs: Long = 90_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (isRunning()) return true
            if (proc?.isAlive != true) return false
            Thread.sleep(500)
        }
        return false
    }

    /** Shutdown bersih dulu; kill hanya sebagai cadangan. */
    @Synchronized
    fun stop() {
        val p = proc ?: return
        if (isRunning()) sh("mariadb-admin -S $SOCK -uroot shutdown", 60)
        if (!p.waitFor(20, TimeUnit.SECONDS)) {
            p.destroy()
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
        }
        proc = null
    }

    // ---------- Provisioning, SQL, dump, import ----------

    fun loadCredentials(): DbCredentials? {
        val f = Env.dbCredFile
        if (!f.exists()) return null
        return runCatching {
            val j = JSONObject(f.readText())
            DbCredentials(j.getString("host"), j.getInt("port"), j.getString("user"), j.getString("password"), j.getString("database"))
        }.getOrNull()
    }

    /** Buat database + user dengan password acak (sekali). Panggil saat server DB sudah hidup. */
    fun provision(database: String = "samp", user: String = "samp"): DbCredentials {
        loadCredentials()?.let { return it }
        val pass = randomToken(20)
        val sql = "CREATE DATABASE IF NOT EXISTS `$database` CHARACTER SET utf8mb4; " +
            "CREATE USER IF NOT EXISTS '$user'@'127.0.0.1' IDENTIFIED BY '$pass'; " +
            "CREATE USER IF NOT EXISTS '$user'@'localhost' IDENTIFIED BY '$pass'; " +
            "GRANT ALL PRIVILEGES ON `$database`.* TO '$user'@'127.0.0.1'; " +
            "GRANT ALL PRIVILEGES ON `$database`.* TO '$user'@'localhost'; FLUSH PRIVILEGES;"
        val r = exec(sql)
        check(r.ok) { "provision gagal: ${r.output}" }
        val c = DbCredentials("127.0.0.1", Prefs.dbPort, user, pass, database)
        Env.dbCredFile.writeText(
            JSONObject().put("host", c.host).put("port", c.port).put("user", c.user)
                .put("password", c.password).put("database", c.database).toString(2)
        )
        return c
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** SQL sebagai root lewat unix socket di dalam rootfs DB. */
    fun exec(sql: String, database: String? = null): CmdResult {
        val db = database?.let { " ${q(it)}" } ?: ""
        return sh("mariadb -S $SOCK -uroot --table -e ${q(sql)}$db", 60)
    }

    fun dumpTo(dest: File): CmdResult {
        val dbName = loadCredentials()?.database ?: "samp"
        dest.parentFile?.mkdirs()
        val pb = Proot.build(
            Proot.Target.DB,
            listOf("mariadb-dump", "-S", SOCK, "-uroot", "--single-transaction", "--routines", dbName),
            "/", binds
        )
        pb.redirectOutput(dest)
        val p = pb.start()
        val err = p.errorStream.bufferedReader().readText()
        val code = if (p.waitFor(300, TimeUnit.SECONDS)) p.exitValue() else -1
        return CmdResult(code, err)
    }

    fun importFrom(src: File): CmdResult {
        val dbName = loadCredentials()?.database ?: "samp"
        val pb = Proot.build(Proot.Target.DB, listOf("mariadb", "-S", SOCK, "-uroot", dbName), "/", binds)
        pb.redirectInput(src)
        return pb.runAndCapture(600)
    }
}
