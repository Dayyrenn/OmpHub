# OmpPanel

Panel untuk menjalankan server **open.mp** + **MariaDB** langsung dari HP Android (arm64), **tanpa root**.
Start/stop, console (stdin & RCON), dashboard (query UDP), file manager + editor, SQL console, backup/restore.

> Status: kode ini ditulis tanpa bisa dikompilasi atau diuji di perangkat. Anggap sebagai
> titik awal yang lengkap, bukan produk jadi. Lihat "Risiko & hal yang perlu diverifikasi".

## Arsitektur

```
Android app (Kotlin + Compose)
 ├─ ServerService (foreground service + wake lock)
 ├─ Controller   (state, start/stop, setup, backup)
 │
 ├─ PRoot + qemu-i386 ──► rootfs Debian i386  ──► /server/omp-server   (game, port UDP 7777)
 │        (jniLibs)         files/rootfs/game      ▲ bind files/server
 │
 └─ PRoot (native)   ──► rootfs Alpine arm64  ──► mariadbd 127.0.0.1:3306
          (jniLibs)         files/rootfs/db        ▲ bind files/db/data
```

* Binary `proot`, `loader`, `talloc`, `qemu-i386` ditaruh di `app/src/main/jniLibs/arm64-v8a/lib*.so`
  supaya diekstrak ke `nativeLibraryDir` (satu-satunya lokasi yang boleh di-exec di Android modern).
* `targetSdk = 28` disengaja (sama seperti Termux), karena Android 10+ melarang exec dari folder data
  untuk app yang menargetkan API 29+.
* PRoot tidak punya network namespace, jadi `127.0.0.1` di dalam rootfs = localhost Android.
  Gamemode connect ke MariaDB lewat TCP `127.0.0.1` (bukan `localhost`).
* MariaDB jalan **native arm64**, game jalan lewat **QEMU**; keduanya dipisah supaya DB tidak ikut lambat.

## Build

Butuh: JDK 17, Android SDK (platform 34), `curl ar tar awk`.

```bash
# 1. ambil binary proot + qemu (sekali)
bash scripts/fetch-binaries.sh

# 2. build
gradle wrapper --gradle-version 8.9      # sekali, untuk membuat gradlew
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Tanpa PC: push project ke GitHub, workflow `.github/workflows/build.yml` akan menjalankan langkah di atas
dan menyimpan APK sebagai artifact.

## Pemakaian

1. Install APK, buka tab **Setup**.
2. Tekan berurutan: **Pasang rootfs game** → **Pasang open.mp** → **Pasang MariaDB** (opsional).
   Butuh internet dan ±600 MB ruang. Jangan tutup app saat proses berjalan.
3. Tekan **Tes game** / **Tes DB**. Output `uname -a` berarti PRoot berfungsi.
4. Upload gamemode (`.amx`) ke `gamemodes/` lewat tab **Files**, lalu atur `pawn.main_scripts` di `config.json`.
5. Tab **Dashboard** → **Start**. Urutan: MariaDB dulu, lalu open.mp.
6. Tab **Database** menampilkan host/port/user/password yang di-generate otomatis.
7. Matikan optimasi baterai app ini (tombol di Setup).

Contoh Pawn:

```pawn
new MySQL:g_SQL;
public OnGameModeInit() {
    g_SQL = mysql_connect("127.0.0.1", "samp", "PASSWORD_DARI_TAB_DATABASE", "samp");
    return 1;
}
```

Plugin MySQL harus build Linux 32-bit, ditaruh di `plugins/`, dan didaftarkan di `pawn.legacy_plugins`
(`config.json`). Sesuaikan sintaks `mysql_connect` dengan versi plugin.

## Struktur kode

| File | Isi |
|---|---|
| `Env.kt` | path, `Prefs`, pembungkus `Proot` |
| `Util.kt` | ekstrak tar(.gz/.xz) dengan symlink, download, zip, `deleteTree` aman symlink |
| `OpenMp.kt` | `OpenMpConfig` (config.json) + `OpenMpInstaller` |
| `MariaDb.kt` | install, start/stop, provision user, SQL, dump/import |
| `Samp.kt` | query & RCON UDP |
| `Controller.kt` | state global, start/stop, setup, backup |
| `ServerService.kt` | foreground service |
| `*Screen.kt` | UI Compose |

## Risiko & hal yang perlu diverifikasi

1. **Belum dikompilasi/diuji.** Kemungkinan ada error kecil saat build pertama.
2. **`fetch-binaries.sh`** mengambil proot dari Termux dan qemu dari Debian. Cek hasil `readelf` yang dicetak
   script: `libproot.so` hanya boleh butuh libc/libdl/libm/libtalloc. Kalau ada dependency lain, tambahkan ke jniLibs.
3. **Seccomp/SELinux per ROM** berbeda. Kalau "Tes game" gagal, lihat pesan error di log Setup. Biasanya masalah
   di `qemu-i386` (syscall diblokir) atau opsi PRoot.
4. **URL default** (rootfs Debian i386, asset rilis open.mp, Alpine 3.20.3) ditulis dari ingatan.
   Semuanya bisa diganti di Setup > URL unduhan tanpa build ulang.
5. **Key `config.json` open.mp** (`rcon.enable`, `rcon.password`, `network.port`, `pawn.legacy_plugins`, `name`,
   `max_players`) harus dicocokkan dengan versi yang kamu pakai. Dashboard butuh query UDP aktif; kalau tetap kosong,
   cek opsi query di config open.mp.
6. **Perintah `exit` via stdin** dipakai untuk stop yang halus; kalau tidak didukung, proses di-`destroy()` setelah 8 detik.
7. Performa: game lewat emulasi QEMU, cocok untuk server kecil/dev, bukan untuk ratusan pemain.
8. Lisensi: lihat `THIRD_PARTY_NOTICES.md`. Pilih lisensi untuk kode kamu sendiri (GPL-3.0-or-later cocok bila
   APK membawa binary GPL).
