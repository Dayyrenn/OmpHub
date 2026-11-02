package id.omphub

import java.io.File

/** Mesin server yang jalan di rootfs game. Keduanya memakai folder server/ yang sama. */
enum class ServerType(val label: String, val binary: String, val configName: String) {
    OMP("open.mp", "omp-server", "config.json"),
    SAMP("SA-MP", "samp03svr", "server.cfg");

    val configFile: File get() = File(Env.serverDir, configName)
    val installed: Boolean get() = File(Env.serverDir, binary).exists()

    companion object {
        fun from(name: String?): ServerType = entries.firstOrNull { it.name == name } ?: OMP
    }
}

/** Versi server SA-MP. Keduanya memakai binary `samp03svr`, hanya arsip unduhannya yang beda. */
enum class SampVersion(val label: String, val code: String) {
    V037("0.3.7", "037"),
    DL("0.3.DL", "DL");

    companion object {
        fun from(code: String?): SampVersion = entries.firstOrNull { it.code == code } ?: V037
    }
}

/** Nama mesin terpilih untuk ditampilkan, mis. "open.mp" atau "SA-MP 0.3.DL". */
fun serverLabel(): String =
    if (Prefs.serverType == ServerType.SAMP) "SA-MP ${Prefs.sampVersion.label}" else ServerType.OMP.label

/** Pengaturan dasar yang sama di kedua mesin; dipakai layar Setup dan Controller. */
interface ServerConfig {
    var hostName: String
    var maxPlayers: Int
    var port: Int
    var rconPassword: String
    fun ensureStrongRcon(): Boolean
    fun save()
}

/** Config mesin [type], atau null bila file-nya belum ada. */
fun openServerConfig(type: ServerType): ServerConfig? =
    if (!type.configFile.exists()) null else when (type) {
        ServerType.OMP -> OpenMpConfig(type.configFile).load()
        ServerType.SAMP -> SampConfig(type.configFile).load()
    }

/**
 * Baca/tulis server.cfg SA-MP (format "kunci nilai" per baris).
 * Hanya baris yang disentuh yang diubah; baris lain (gamemode0, filterscripts, plugins, dst.) dibiarkan.
 * Ditulis dengan LF, karena server Linux bisa salah baca nilai bila ada CR di akhir baris.
 */
class SampConfig(private val file: File) : ServerConfig {
    private var lines = mutableListOf<String>()

    fun load(): SampConfig {
        lines = if (file.exists()) {
            runCatching { file.readText().split("\r\n", "\n").toMutableList() }.getOrDefault(mutableListOf())
        } else mutableListOf()
        while (lines.isNotEmpty() && lines.last().isEmpty()) lines.removeAt(lines.size - 1)
        return this
    }

    override fun save() {
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString("\n") + "\n")
    }

    private fun indexOf(key: String) = lines.indexOfFirst { l ->
        val t = l.trimStart()
        t.startsWith(key) && (t.length == key.length || t[key.length].isWhitespace())
    }

    fun read(key: String): String? {
        val i = indexOf(key)
        if (i < 0) return null
        return lines[i].trim().substring(key.length).trim()
    }

    fun write(key: String, value: String) {
        val line = "$key ${value.replace(Regex("[\\r\\n]"), " ")}"
        val i = indexOf(key)
        if (i >= 0) lines[i] = line else lines.add(line)
    }

    override var hostName: String
        get() = read("hostname") ?: "SA-MP Server"
        set(v) = write("hostname", v)

    override var maxPlayers: Int
        get() = read("maxplayers")?.toIntOrNull() ?: 50
        set(v) = write("maxplayers", v.toString())

    override var port: Int
        get() = read("port")?.toIntOrNull() ?: 7777
        set(v) = write("port", v.toString())

    override var rconPassword: String
        get() = read("rcon_password") ?: ""
        set(v) = write("rcon_password", v)

    /** Query UDP harus aktif supaya Dashboard bisa menampilkan info server dan pemain. */
    fun ensureQuery() {
        if (read("query") != "1") write("query", "1")
    }

    /** Ganti password RCON yang lemah. Return true bila diganti. */
    override fun ensureStrongRcon(): Boolean {
        val p = rconPassword
        val weak = p.length < 8 || p.lowercase() in WEAK
        if (weak) rconPassword = randomToken(16)
        return weak
    }

    companion object {
        private val WEAK = setOf("", "changeme", "password", "1234", "12345678", "rcon", "admin")
    }
}

object SampInstaller {
    private val marker get() = File(Env.serverDir, ".samp-version")

    /** Versi SA-MP yang terpasang, atau null bila belum ada. Versi tak dikenal dianggap 0.3.7. */
    fun installedVersion(): SampVersion? =
        if (!ServerType.SAMP.installed) null
        else SampVersion.from(runCatching { marker.readText().trim() }.getOrNull())

    /** Pasang / update samp03svr ke Env.serverDir. server.cfg, gamemodes, filterscripts, models, dst. tidak ditimpa. */
    fun install(url: String, version: SampVersion, log: (String) -> Unit) {
        val tmp = File(Env.files, "tmp_samp")
        deleteTree(tmp)
        tmp.mkdirs()

        val archive = File(Env.dlDir, archiveNameFor("samp", url))
        log("Mengunduh server SA-MP ${version.label} dari $url")
        var lastPct = -1
        downloadFile(url, archive) { done, total ->
            if (total > 0) {
                val pct = (done * 100 / total).toInt() / 20 * 20
                if (pct != lastPct) { lastPct = pct; log("  unduh $pct%") }
            }
        }

        log("Mengekstrak...")
        Extractor.extractTar(archive, tmp)
        val bin = tmp.walkTopDown().firstOrNull { it.isFile && it.name == ServerType.SAMP.binary }
            ?: error("samp03svr tidak ada di arsip. Cek URL server SA-MP di Setup.")

        val cfgExisted = ServerType.SAMP.configFile.exists()
        OpenMpInstaller.mergeCopy(bin.parentFile!!, Env.serverDir)
        listOf("gamemodes", "filterscripts", "scriptfiles", "plugins", "npcmodes").forEach {
            File(Env.serverDir, it).mkdirs()
        }
        OpenMpInstaller.fixPerms()

        val cfg = SampConfig(ServerType.SAMP.configFile).load()
        if (!cfgExisted) cfg.hostName = "OmpHub Server"
        cfg.port = Prefs.port
        cfg.ensureQuery()
        if (cfg.ensureStrongRcon()) log("Password RCON baru di-generate (lihat Files > server.cfg)")
        cfg.save()

        runCatching { marker.writeText(version.code) }
        deleteTree(tmp)
        archive.delete()
        log("SA-MP ${version.label} terpasang di ${Env.serverDir.absolutePath}")
    }
}
