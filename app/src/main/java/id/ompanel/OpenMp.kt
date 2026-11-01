package id.omphub

import android.system.Os
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Baca/tulis config.json open.mp. Hanya key yang disentuh yang diubah, sisanya dibiarkan.
 * Nama key mengikuti docs open.mp (name, max_players, network.port, rcon.*, pawn.*).
 * Verifikasi ke versi yang dipakai: lihat config.json yang di-generate omp-server.
 */
class OpenMpConfig(private val file: File) {
    private var root = JSONObject()

    fun load(): OpenMpConfig {
        root = if (file.exists()) runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject()) else JSONObject()
        return this
    }

    fun save() {
        file.parentFile?.mkdirs()
        file.writeText(root.toString(2))
    }

    private fun section(name: String): JSONObject {
        root.optJSONObject(name)?.let { return it }
        return JSONObject().also { root.put(name, it) }
    }

    var hostName: String
        get() = root.optString("name", "open.mp server")
        set(v) { root.put("name", v) }

    var maxPlayers: Int
        get() = root.optInt("max_players", 50)
        set(v) { root.put("max_players", v) }

    var port: Int
        get() = section("network").optInt("port", 7777)
        set(v) { section("network").put("port", v) }

    var rconEnabled: Boolean
        get() = section("rcon").optBoolean("enable", false)
        set(v) { section("rcon").put("enable", v) }

    var rconPassword: String
        get() = section("rcon").optString("password", "")
        set(v) { section("rcon").put("password", v) }

    var legacyPlugins: List<String>
        get() = root.optJSONObject("pawn")?.optJSONArray("legacy_plugins").toStringList()
        set(v) { section("pawn").put("legacy_plugins", JSONArray(v)) }

    fun addLegacyPlugin(name: String) {
        if (name !in legacyPlugins) legacyPlugins = legacyPlugins + name
    }

    /** Ganti password RCON yang lemah. Return true bila diganti. */
    fun ensureStrongRcon(): Boolean {
        val p = rconPassword
        val weak = p.length < 8 || p.lowercase() in WEAK
        if (weak) rconPassword = randomToken(16)
        return weak
    }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else List(length()) { optString(it) }

    companion object {
        private val WEAK = setOf("", "changeme", "password", "1234", "12345678", "rcon", "admin")
    }
}

object OpenMpInstaller {
    private val KEEP_IF_EXISTS = listOf("config.json", "gamemodes/", "filterscripts/", "scriptfiles/", "plugins/")

    private fun keep(rel: String) = KEEP_IF_EXISTS.any { rel == it || rel.startsWith(it) }

    private fun mergeCopy(src: File, dst: File, rel: String = "") {
        dst.mkdirs()
        src.listFiles()?.forEach { f ->
            val r = if (rel.isEmpty()) f.name else "$rel/${f.name}"
            val t = File(dst, f.name)
            if (f.isDirectory) {
                mergeCopy(f, t, r)
            } else if (!(t.exists() && keep(r))) {
                t.delete()
                f.copyTo(t, overwrite = true)
                try { Os.chmod(t.path, Os.stat(f.path).st_mode and 4095) } catch (_: Exception) { }
            }
        }
    }

    /** Pasang / update omp-server ke Env.serverDir. gamemodes, plugins, dan config.json tidak ditimpa. */
    fun install(url: String, log: (String) -> Unit) {
        val archive = File(Env.dlDir, archiveNameFor("openmp", url))
        val tmp = File(Env.files, "tmp_openmp")
        deleteTree(tmp)
        tmp.mkdirs()

        log("Mengunduh open.mp dari $url")
        var lastPct = -1
        downloadFile(url, archive) { done, total ->
            if (total > 0) {
                val pct = (done * 100 / total).toInt() / 20 * 20
                if (pct != lastPct) { lastPct = pct; log("  unduh $pct%") }
            }
        }

        log("Mengekstrak...")
        Extractor.extractTar(archive, tmp)
        val bin = tmp.walkTopDown().firstOrNull { it.isFile && it.name == "omp-server" }
            ?: error("omp-server tidak ada di arsip. Cek URL rilis open.mp di Setup.")

        mergeCopy(bin.parentFile!!, Env.serverDir)
        listOf("gamemodes", "filterscripts", "scriptfiles", "plugins", "components").forEach {
            File(Env.serverDir, it).mkdirs()
        }
        fixPerms()

        // Config awal bila belum ada
        val cfgFile = File(Env.serverDir, "config.json")
        val cfg = OpenMpConfig(cfgFile).load()
        if (!cfgFile.exists()) { cfg.hostName = "OmpHub Server"; cfg.maxPlayers = 50 }
        cfg.port = Prefs.port
        cfg.rconEnabled = true
        if (cfg.ensureStrongRcon()) log("Password RCON baru di-generate (lihat Files > config.json)")
        cfg.save()

        deleteTree(tmp)
        archive.delete()
        log("open.mp terpasang di ${Env.serverDir.absolutePath}")
    }

    /** ZIP/restore menghilangkan bit executable; set ulang. */
    fun fixPerms() {
        File(Env.serverDir, "omp-server").takeIf { it.exists() }?.setExecutable(true, false)
        listOf("components", "plugins").forEach { d ->
            File(Env.serverDir, d).walkTopDown().filter { it.isFile && it.name.endsWith(".so") }
                .forEach { it.setExecutable(true, false) }
        }
    }
}
