# OmpHub

Panel untuk menjalankan server **open.mp** atau **SA-MP** (0.3.7 / 0.3.DL) + **MariaDB** langsung dari HP Android (arm64), **tanpa root**.

Start/stop server, console (stdin & RCON), dashboard pemain (query UDP), file manager + editor,
SQL console, serta backup/restore, semuanya dari satu aplikasi.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B%20(arm64)-3DDC84)
![Version](https://img.shields.io/badge/version-1.1.0-blue)

Semua komponen (rootfs Debian, server open.mp / SA-MP, dan MariaDB) **sudah dibundel di dalam APK** dan dipasang otomatis saat app dibuka pertama kali, tanpa internet. Riwayat perubahan ada di [`CHANGELOG.md`](CHANGELOG.md).

---

## Daftar isi

- [Fitur](#fitur)
- [Persyaratan](#persyaratan)
- [Instalasi](#instalasi)
- [Pemakaian](#pemakaian)
- [Menghubungkan gamemode ke database](#menghubungkan-gamemode-ke-database)
- [Konfigurasi](#konfigurasi)
- [Build dari source](#build-dari-source)
- [Backup & restore](#backup--restore)
- [Lokasi data](#lokasi-data)
- [Arsitektur](#arsitektur)
- [Troubleshooting](#troubleshooting)
- [Batasan & catatan penting](#batasan--catatan-penting)
- [Atribusi & lisensi komponen](#atribusi--lisensi-komponen)

---

## Fitur

**Kontrol server**
- Pilih mesin server di tab Setup: **open.mp** atau **SA-MP** (versi **0.3.7** atau **0.3.DL**). Keduanya memakai folder server yang sama, jadi gamemode (`.amx`) dan plugin (`.so`) tidak perlu diupload ulang saat berpindah.
- Start / Stop / Restart server dari tombol atau dari notifikasi (aksi *Stop*).
- Urutan start otomatis: MariaDB dulu, baru server game. Stop dilakukan terbalik.
- Berjalan sebagai *foreground service* dengan wake lock, jadi server tetap hidup saat app di background.
- Stop halus lewat perintah `exit` (stdin untuk open.mp, RCON untuk SA-MP); kalau tidak merespons dalam 8 detik, proses dihentikan paksa.

**Dashboard**
- Info server real-time lewat query UDP: nama, jumlah pemain, mode, bahasa, status password.
- Daftar pemain online beserta skor dan ping.
- Alamat LAN dan alamat lokal (`127.0.0.1:<port>`) ditampilkan langsung.

**Console**
- Dua mode input: **stdin** (langsung ke proses `omp-server`) dan **RCON** (lewat UDP).
- Log game (1000 baris terakhir) dan log MariaDB.

**File manager**
- Menjelajah folder server (`gamemodes/`, `filterscripts/`, `plugins/`, `scriptfiles/`, dst).
- Upload file dari penyimpanan HP, buat file/folder, hapus, dan unzip arsip `.zip` langsung di HP.
- Editor teks bawaan untuk `config.json` / `server.cfg` dan file teks lain (file biner/terlalu besar tidak bisa diedit).

**Database**
- MariaDB berjalan **native arm64** (tidak lewat emulasi), hanya listen di `127.0.0.1`.
- Database `samp`, user, dan password acak dibuat otomatis saat start pertama.
- Kredensial bisa disalin dengan satu tap, lengkap dengan contoh `mysql_connect` untuk Pawn.
- SQL console (sebagai root), import `.sql`, dan export dump.

**Setup & keamanan dasar**
- **Pemasangan otomatis** saat app dibuka pertama kali: rootfs, server game (open.mp atau SA-MP), dan MariaDB diekstrak dari aset APK, lengkap dengan banner progres. Tombol per langkah di tab Setup tetap ada untuk pasang ulang.
- Tes PRoot untuk game dan DB (`uname -a`) supaya masalah ketahuan sebelum server dijalankan.
- Password RCON yang lemah (kosong, `changeme`, `password`, `1234`, `12345678`, `rcon`, `admin`, atau kurang dari 8 karakter) otomatis diganti dengan password acak.
- Update server **tidak menimpa** `config.json`, `server.cfg`, `gamemodes/`, `filterscripts/`, `scriptfiles/`, `npcmodes/`, `plugins/`, `models/`, dan `artconfig.txt`.
- Shortcut ke pengaturan optimasi baterai.

---

## Persyaratan

| Item | Keterangan |
|---|---|
| Android | 8.0 (API 26) atau lebih baru |
| Arsitektur | **arm64-v8a** saja |
| Root | Tidak perlu |
| Ruang kosong | ±1 GB (APK + hasil ekstrak rootfs, server game, dan MariaDB) |
| Internet | Tidak perlu untuk pemasangan awal; hanya untuk update server |
| Ukuran APK | ±100 MB (rootfs, server game, dan MariaDB ikut di dalamnya) |
| RAM | Disarankan 4 GB ke atas bila game dan DB jalan bersamaan |

---

## Instalasi

1. Unduh APK terbaru dari tab **Releases** (ukurannya ±100 MB karena semua komponen ikut di dalamnya).
2. Izinkan pemasangan dari sumber tidak dikenal, lalu install.
3. Buka app. **Pemasangan otomatis berjalan sendiri** (±2-5 menit, tanpa internet) dan progresnya tampil di bagian atas layar. Jangan tutup app sampai selesai.
4. Izinkan notifikasi saat diminta (dipakai untuk foreground service).

> Android tidak mengizinkan app menjalankan kode saat proses install, jadi pemasangan komponen baru bisa dimulai setelah app dibuka pertama kali. Kalau app sempat ditutup / dibunuh di tengah jalan, pemasangan dilanjutkan otomatis saat app dibuka lagi.

---

## Pemakaian

1. Buka app dan tunggu pemasangan otomatis selesai (banner progres di atas layar hilang). Detail log ada di tab **Setup**.
2. Komponen yang dipasang, semuanya dari aset APK:
   - **Rootfs game**: Debian i386 + `libatomic1`.
   - **Server game**: open.mp (default) atau SA-MP 0.3.7 / 0.3.DL. Ganti mesin atau versi di bagian **Mesin server** lalu tekan tombol pasang, tetap offline. Tombol **Update** open.mp mengunduh rilis terbaru dari internet.
   - **MariaDB** (opsional, ikut dipasang bila "Jalankan MariaDB bersama server" aktif): rootfs Alpine arm64 + paket `mariadb`, lalu database diinisialisasi.

   Bila pemasangan otomatis gagal atau ingin mengulang, gunakan tombol **Pasang semua** atau tombol per langkah di tab Setup.
3. Tekan **Tes game** dan **Tes DB**. Output `uname -a` berarti PRoot berfungsi.
4. Buka tab **Files**, upload gamemode (`.amx`) ke `gamemodes/`, lalu daftarkan gamemode-nya: di `config.json` lewat `pawn.main_scripts` (open.mp), atau di `server.cfg` lewat baris `gamemode0 namagamemode 1` tanpa `.amx` (SA-MP).
5. Buka tab **Dashboard** → **Start**.
6. Tab **Database** menampilkan host, port, user, password, dan nama database.
7. Matikan optimasi baterai untuk app ini (tombol di Setup), supaya Android tidak membunuh server.

Pemain di HP yang sama konek ke `127.0.0.1:7777`. Pemain di jaringan yang sama konek ke alamat LAN yang tampil di Dashboard.

---

## Menghubungkan gamemode ke database

Gunakan `127.0.0.1`, **bukan** `localhost`. Rootfs game tidak punya akses ke unix socket MariaDB.

```pawn
new MySQL:g_SQL;

public OnGameModeInit()
{
    g_SQL = mysql_connect("127.0.0.1", "samp", "PASSWORD_DARI_TAB_DATABASE", "samp");
    return 1;
}
```

Catatan plugin MySQL:
- Harus build **Linux 32-bit** (game berjalan sebagai proses i386).
- Taruh di folder `plugins/`, lalu daftarkan di `pawn.legacy_plugins` pada `config.json` (open.mp) atau di baris `plugins` pada `server.cfg` (SA-MP, contoh: `plugins mysql.so streamer.so`).
- Sesuaikan sintaks `mysql_connect` dengan versi plugin yang dipakai.

---

## Konfigurasi

Di tab **Setup → Pengaturan server**:

| Opsi | Default | Keterangan |
|---|---|---|
| Nama server | `OmpHub Server` | `name` di `config.json` (open.mp) / `hostname` di `server.cfg` (SA-MP) |
| Max pemain | `50` | `max_players` / `maxplayers` |
| Port UDP | `7777` | `network.port` / `port`, disinkronkan setiap kali server start |
| Port DB | `3306` | Port MariaDB di `127.0.0.1` |
| Jalankan MariaDB bersama server | aktif | Bila mati, Start hanya menjalankan server game |

Perubahan baru berlaku setelah server di-restart. Untuk opsi lanjutan, edit `config.json` (open.mp) atau `server.cfg` (SA-MP) lewat tab **Files**.

Panel selalu memastikan RCON aktif dengan password yang kuat (`rcon.enable = true` di open.mp, `rcon_password` di SA-MP), karena RCON dipakai oleh tab Console. Untuk SA-MP, panel juga memastikan `query 1` supaya Dashboard bisa membaca info server dan pemain.

Mesin server hanya bisa diganti saat server berhenti.

**URL unduhan** (Setup → URL unduhan) bisa diganti tanpa build ulang bila link default berubah:

| Komponen | Default |
|---|---|
| open.mp (Linux x86, `.tar.gz`) | rilis terbaru di `github.com/openmultiplayer/open.mp` |
| SA-MP 0.3.7 server Linux (`.tar.gz`) | `samp037svr_R2-2-1.tar.gz` dari `sa-mp.co.id/files` (situs komunitas, bukan situs resmi SA-MP) |
| SA-MP 0.3.DL server Linux (`.tar.gz`) | `samp03DLsvr_R1.tar.gz` dari `sa-mp.co.id/files` |
| Alpine aarch64 minirootfs (`.tar.gz`) | Alpine 3.20.3 |

URL ini hanya dipakai bila aset yang dibundel tidak ada di APK (cadangan), atau saat menekan **Update**. Untuk SA-MP, aset bawaan dipakai selama URL-nya masih default.

---

## Build dari source

Binary dan aset besar tidak ikut di-commit; semuanya dibuat oleh skrip, lalu APK dibuild dengan Gradle.

```bash
# 1. Binary native + rootfs game + server game (butuh curl ar tar awk find od perl xz jq)
bash scripts/fetch-binaries.sh

# 2. Rootfs MariaDB (butuh docker dengan emulasi arm64, mis. docker/setup-qemu-action di CI)
bash scripts/build-mariadb-rootfs.sh

# 3. Build APK (JDK 17, Gradle 8.9)
gradle :app:assembleDebug
```

Hasil skrip:

| Lokasi | Isi |
|---|---|
| `app/src/main/jniLibs/arm64-v8a/` | `libproot.so`, `libproot-loader.so`, `libtalloc.so`, `libandroid-shmem.so`, `libqemu-i386.so` |
| `app/src/main/assets/rootfs-game.tar.gz` | Rootfs Debian bookworm-slim i386 |
| `app/src/main/assets/libatomic1-i386.tar.xz` | Paket `libatomic1` (dibutuhkan omp-server) |
| `app/src/main/assets/openmp-linux-x86.tar.gz` | open.mp rilis terbaru saat build |
| `app/src/main/assets/samp037svr.tar.gz`, `samp03DLsvr.tar.gz` | Server SA-MP 0.3.7 dan 0.3.DL |
| `app/src/main/assets/mariadb-rootfs.tar.xz` | Alpine 3.20 arm64 + `mariadb` |

Workflow `.github/workflows/build.yml` menjalankan ketiga langkah di atas. Bila aset tidak ada, app tetap jalan dan mengunduh komponen dari internet sebagai cadangan.

---

## Backup & restore

Setup → **Backup** → *Buat backup (server + database)* menghasilkan satu file `.zip` berisi:

- `server/`: seluruh folder server (gamemode, plugin, config, scriptfiles).
- `database.sql`: dump database (hanya bila MariaDB sedang berjalan saat backup dibuat).

**Restore** mengembalikan folder server. Jika backup membawa `database.sql` dan MariaDB sedang jalan, dump di-import otomatis. Kalau MariaDB belum jalan, nyalakan dulu lalu import manual lewat tab **Database → Import .sql**. Server game harus berhenti sebelum restore.

Tab Database juga punya **Export dump** dan **Import .sql** sendiri.

---

## Lokasi data

Semua data ada di penyimpanan privat app (`/data/data/<package>/files/`):

| Path | Isi |
|---|---|
| `server/` | open.mp / SA-MP, `config.json` / `server.cfg`, `gamemodes/`, `plugins/`, dst. (di-bind ke `/server`) |
| `rootfs/game/` | Debian i386 (sementara di `rootfs/game.tmp/` saat diekstrak) |
| `rootfs/db/` | Alpine arm64 + MariaDB (sementara di `rootfs/db.tmp/` saat diekstrak) |
| `db/data/` | Datadir MariaDB (di-bind ke `/var/lib/mysql`, tetap ada bila rootfs dipasang ulang) |
| `db/credentials.json` | Kredensial database yang di-generate |
| `backups/` | File backup `.zip` dan dump `.sql` |

Karena `allowBackup="false"` dan data berada di storage privat, **menghapus app atau "Clear data" menghapus semuanya**. Buat backup dan salin keluar HP sebelum itu.

---

## Arsitektur

```
Android app (Kotlin + Jetpack Compose)
 ├─ ServerService (foreground service + wake lock)
 ├─ Controller   (state, start/stop, setup, backup)
 │
 ├─ PRoot + qemu-i386 ──► rootfs Debian i386  ──► /server/omp-server   (game, UDP 7777)
 │        (bundled)        files/rootfs/game      ▲ bind files/server
 │
 └─ PRoot (native)   ──► rootfs Alpine arm64  ──► mariadbd 127.0.0.1:3306
          (bundled)        files/rootfs/db        ▲ bind files/db/data
```

Poin penting:

- Binary `proot`, `loader`, `talloc`, `libandroid-shmem`, dan `qemu-i386` dibundel di dalam APK dan dijalankan dari lokasi native library app (satu-satunya lokasi yang boleh di-exec di Android modern).
- PRoot tidak punya network namespace, jadi `127.0.0.1` di dalam rootfs sama dengan localhost Android.
  Gamemode konek ke MariaDB lewat TCP `127.0.0.1`.
- MariaDB jalan **native arm64**, sedangkan game jalan lewat **QEMU user-mode**. Keduanya dipisah supaya
  database tidak ikut melambat oleh emulasi.
- Rootfs game (`i386/debian:bookworm-slim`) diambil dari Docker Hub lewat registry API saat **build APK** (`scripts/fetch-binaries.sh`), lalu dibundel sebagai aset. Rootfs MariaDB dibuat di CI lewat Docker (`scripts/build-mariadb-rootfs.sh`). Di HP, semuanya hanya diekstrak.
- Ekstraksi ke folder sementara lalu di-rename, jadi pemasangan yang terputus tidak pernah dianggap sudah terpasang.
- MariaDB dijalankan dengan setelan hemat memori (`innodb-buffer-pool-size=32M`, `max-connections=30`,
  `performance-schema=OFF`, tanpa native AIO).
- Query dan RCON memakai protokol UDP kompatibel SA-MP yang juga dipakai open.mp.

---

## Troubleshooting

| Gejala | Kemungkinan penyebab / solusi |
|---|---|
| Banner "Menyiapkan komponen" tidak selesai-selesai | Tunggu beberapa menit (ekstraksi di HP lambat), lalu lihat log di tab Setup. Bila ada `[ERROR]`, tutup dan buka app lagi, atau tekan **Pasang semua**. |
| Log Setup bilang "Mengunduh ..." padahal harusnya bawaan APK | APK dibuild tanpa aset (build lokal yang melewatkan `fetch-binaries.sh` / `build-mariadb-rootfs.sh`). Pemasangan jatuh ke unduhan internet. Build ulang dengan skrip lengkap. |
| **Tes game** gagal | Seccomp/SELinux beberapa ROM memblokir syscall untuk `qemu-i386`. Lihat pesan error di log Setup. |
| Pemasangan rootfs/server gagal | Cek koneksi. URL open.mp dan SA-MP bisa diganti di Setup → URL unduhan. |
| `omp-server tidak ada di arsip` | URL open.mp salah / format rilis berubah. Ganti URL ke arsip Linux x86 `.tar.gz`. |
| `samp03svr tidak ada di arsip` | URL SA-MP salah. Ganti ke arsip server Linux `.tar.gz` (misalnya `samp037svr_R2-1.tar.gz` atau `samp03DLsvr_R1.tar.gz`). |
| SA-MP 0.3.DL: model custom tidak sampai ke pemain | Pastikan `useartwork 1` dan `artpath models` ada di `server.cfg`, serta `artconfig.txt` dan folder `models/` sudah diupload. Server DL melayani model lewat TCP di port yang sama dengan UDP. |
| SA-MP: log di Console kosong atau terlambat | Buka `server_log.txt` lewat tab Files. |
| SA-MP: perintah di Console tidak jalan | Perintah dikirim lewat RCON, jadi pastikan `rcon_password` di `server.cfg` valid dan server sudah berjalan. |
| Dashboard terus "Menunggu respons query UDP" | Server belum siap, atau query tidak aktif (`config.json` untuk open.mp, `query 1` di `server.cfg` untuk SA-MP). |
| Console RCON: "tidak ada balasan" | RCON mati, password salah, atau server tidak berjalan. |
| Gamemode tidak bisa konek DB | Pakai `127.0.0.1`, pastikan MariaDB berstatus *running*, cek password di tab Database. |
| Plugin gagal dimuat | Plugin harus Linux **32-bit** dan terdaftar di `pawn.legacy_plugins`. |
| Server mati sendiri saat layar off | Matikan optimasi baterai untuk app ini; di beberapa ROM perlu juga izin *autostart* / *background activity*. |
| MariaDB tidak siap | Lihat Log MariaDB di tab Database. Bila inisialisasi gagal, **Pasang ulang MariaDB** dari Setup. |

---

## Batasan & catatan penting

1. **Ukuran APK**: ±100 MB karena rootfs, server game, dan MariaDB dibundel. Versi open.mp dan paket MariaDB mengikuti versi saat APK dibuild, bukan saat user install.
2. **Performa**: game berjalan lewat emulasi QEMU. Cocok untuk server kecil, development, dan testing,
   bukan untuk ratusan pemain.
3. **Hanya arm64**: HP 32-bit atau x86 tidak didukung.
4. **Kompatibilitas per perangkat**: PRoot di Android sangat bergantung pada seccomp/SELinux tiap ROM.
   Hasil bisa berbeda antar merek dan versi Android. Gunakan tombol **Tes game** / **Tes DB** sebelum memakai.
5. **Key config yang disentuh panel.** open.mp (`config.json`): `name`, `max_players`, `network.port`, `rcon.enable`,
   `rcon.password`, `pawn.legacy_plugins`. SA-MP (`server.cfg`): `hostname`, `maxplayers`, `port`, `rcon_password`, `query`.
   Baris lain tidak diubah.
6. **Keamanan**: MariaDB hanya listen di `127.0.0.1`, tapi port game (UDP) terbuka di jaringan yang sama.
   Jangan expose port ke internet tanpa memahami risikonya, dan jangan bagikan isi tab Database.
7. **Lalu lintas cleartext** (`usesCleartextTraffic`) diaktifkan karena unduhan bisa lewat URL kustom.

---

## Atribusi & lisensi komponen

Aplikasi ini membawa dan/atau mengunduh komponen pihak ketiga (PRoot, talloc, QEMU, open.mp, server SA-MP, MariaDB,
Debian, Alpine, dan lainnya) yang masing-masing memiliki lisensi sendiri. Daftar lengkapnya ada di
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

Proyek ini tidak berafiliasi dengan open.mp, Rockstar Games, atau Take-Two Interactive.