package id.omphub

import android.app.Application
import android.content.Context
import android.system.Os
import java.io.File

class OmpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Env.init(this)
    }
}

/** Path dan lokasi binary. Semua data ada di filesDir app. */
object Env {
    lateinit var ctx: Context
        private set

    fun init(c: Context) {
        ctx = c.applicationContext
        prepareDirs()
    }

    val files: File get() = ctx.filesDir
    val nativeDir: String get() = ctx.applicationInfo.nativeLibraryDir

    val rootfsGame: File get() = File(files, "rootfs/game")   // Debian i386 (jalan lewat qemu)
    val rootfsDb: File get() = File(files, "rootfs/db")       // Alpine arm64 (native)
    val serverDir: File get() = File(files, "server")         // di-bind ke /server di rootfs game
    val dbData: File get() = File(files, "db/data")           // di-bind ke /var/lib/mysql di rootfs db
    val dbCredFile: File get() = File(files, "db/credentials.json")
    val backupDir: File get() = File(files, "backups")
    val tmpDir: File get() = File(files, "tmp")
    val dlDir: File get() = File(files, "downloads")
    val libDir: File get() = File(files, "lib")               // symlink soname untuk libtalloc

    fun nativeLib(name: String) = File(nativeDir, name)

    fun binariesOk(): Boolean =
        listOf("libproot.so", "libproot-loader.so", "libqemu-i386.so", "libtalloc.so").all { nativeLib(it).exists() }

    fun prepareDirs() {
        listOf(tmpDir, libDir, serverDir, dbData, backupDir, dlDir, dbCredFile.parentFile!!).forEach { it.mkdirs() }
        // proot (Termux) butuh soname libtalloc.so.2, sedangkan jniLibs hanya boleh bernama lib*.so
        val src = nativeLib("libtalloc.so")
        val link = File(libDir, "libtalloc.so.2")
        if (src.exists()) {
            try {
                link.delete()
                Os.symlink(src.absolutePath, link.absolutePath)
            } catch (_: Exception) { }
        }
    }
}

object Prefs {
    const val DEF_ROOTFS_GAME =
        "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/dist-i386/bookworm/slim/rootfs.tar.xz"
    const val DEF_OPENMP =
        "https://github.com/openmultiplayer/open.mp/releases/latest/download/open.mp-linux-x86.tar.gz"
    const val DEF_ALPINE =
        "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz"

    private val sp get() = Env.ctx.getSharedPreferences("ompanel", Context.MODE_PRIVATE)
    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    var urlGameRootfs: String
        get() = str("urlGameRootfs", DEF_ROOTFS_GAME)
        set(v) = put("urlGameRootfs", v)
    var urlOpenMp: String
        get() = str("urlOpenMp", DEF_OPENMP)
        set(v) = put("urlOpenMp", v)
    var urlAlpine: String
        get() = str("urlAlpine", DEF_ALPINE)
        set(v) = put("urlAlpine", v)

    var port: Int
        get() = sp.getInt("port", 7777)
        set(v) = sp.edit().putInt("port", v).apply()
    var dbPort: Int
        get() = sp.getInt("dbPort", 3306)
        set(v) = sp.edit().putInt("dbPort", v).apply()
    var dbEnabled: Boolean
        get() = sp.getBoolean("dbEnabled", true)
        set(v) = sp.edit().putBoolean("dbEnabled", v).apply()
}

/**
 * Pembungkus PRoot.
 *
 * GAME : rootfs Debian i386, dijalankan lewat qemu-i386 (-q).
 * DB   : rootfs Alpine arm64, native (tanpa qemu).
 *
 * LD_LIBRARY_PATH / PROOT_* hanya untuk proot itu sendiri; di-unset lagi untuk proses di dalam rootfs
 * memakai `env -u`, supaya tidak mengganggu library guest.
 */
object Proot {
    enum class Target { GAME, DB }

    fun build(
        target: Target,
        cmd: List<String>,
        workDir: String = "/",
        binds: List<Pair<String, String>> = emptyList()
    ): ProcessBuilder {
        Env.prepareDirs()
        val rootfs = if (target == Target.GAME) Env.rootfsGame else Env.rootfsDb
        val args = mutableListOf(
            Env.nativeLib("libproot.so").absolutePath,
            "--kill-on-exit", "--link2symlink", "-0",
            "-r", rootfs.absolutePath,
            "-w", workDir
        )
        if (target == Target.GAME) {
            // -B: alamat dasar memori tamu. Tanpa ini qemu mulai dari 0x1000 dan ditolak kernel Android.
            args += listOf("-q", Env.nativeLib("libqemu-i386.so").absolutePath + " -B 0x100000")
        }
        args += listOf("-b", "/dev", "-b", "/proc")
        binds.forEach { (host, guest) -> args += listOf("-b", "$host:$guest") }

        args += listOf(
            "/usr/bin/env",
            "-u", "LD_LIBRARY_PATH", "-u", "PROOT_LOADER", "-u", "PROOT_TMP_DIR", "-u", "PROOT_NO_SECCOMP",
            "HOME=/root",
            "TMPDIR=/tmp",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm",
            "LANG=C.UTF-8"
        )
        args += cmd

        return ProcessBuilder(args).apply {
            environment().apply {
                put("LD_LIBRARY_PATH", Env.libDir.absolutePath + ":" + Env.nativeDir)
                put("PROOT_LOADER", Env.nativeLib("libproot-loader.so").absolutePath)
                put("PROOT_TMP_DIR", Env.tmpDir.absolutePath)
                put("PROOT_NO_SECCOMP", "1")
            }
        }
    }

    /** Tulis resolv.conf dan hosts di rootfs (rootfs docker tidak membawanya). */
    fun writeNetConfig(rootfs: File) {
        val etc = File(rootfs, "etc").apply { mkdirs() }
        File(etc, "resolv.conf").apply { delete(); writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n") }
        File(etc, "hosts").apply { delete(); writeText("127.0.0.1 localhost\n::1 localhost\n") }
        File(rootfs, "tmp").apply { mkdirs(); chmodSafe(path, 511 or 512) } // 1777
        File(rootfs, "root").mkdirs()
    }
}
