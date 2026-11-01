# OmpHub

Panel untuk menjalankan server **open.mp** + **MariaDB** langsung dari HP Android (arm64), **tanpa root**.

Start/stop server, console (stdin & RCON), dashboard pemain (query UDP), file manager + editor,
SQL console, serta backup/restore, semuanya dari satu aplikasi.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B%20(arm64)-3DDC84)
![Version](https://img.shields.io/badge/version-1.0.0-blue)

---

## Daftar isi

- [Fitur](#fitur)
- [Persyaratan](#persyaratan)
- [Instalasi](#instalasi)
- [Pemakaian](#pemakaian)
- [Menghubungkan gamemode ke database](#menghubungkan-gamemode-ke-database)
- [Konfigurasi](#konfigurasi)
- [Backup & restore](#backup--restore)
- [Lokasi data](#lokasi-data)
- [Arsitektur](#arsitektur)
- [Troubleshooting](#troubleshooting)
- [Batasan & catatan penting](#batasan--catatan-penting)
- [Atribusi & lisensi komponen](#atribusi--lisensi-komponen)

---

## Fitur

**Kontrol server**
- Start / Stop / Restart open.mp dari tombol atau dari notifikasi (aksi *Stop*).
- Urutan start otomatis: MariaDB dulu, baru open.mp. Stop dilakukan terbalik.
- Berjalan sebagai *foreground service* dengan wake lock, jadi server tetap hidup saat app di background.
- Stop halus lewat perintah `exit` di stdin; kalau tidak merespons dalam 8 detik, proses dihentikan paksa.

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
- Editor teks bawaan untuk `config.json` dan file teks lain (file biner/terlalu besar tidak bisa diedit).

**Database**
- MariaDB berjalan **native arm64** (tidak lewat emulasi), hanya listen di `127.0.0.1`.
- Database `samp`, user, dan password acak dibuat otomatis saat start pertama.
- Kredensial bisa disalin dengan satu tap, lengkap dengan contoh `mysql_connect` untuk Pawn.
- SQL console (sebagai root), import `.sql`, dan export dump.

**Setup & keamanan dasar**
- Pemasangan rootfs, open.mp, dan MariaDB dengan satu tombol per langkah, ada log progres.
- Tes PRoot untuk game dan DB (`uname -a`) supaya masalah ketahuan sebelum server dijalankan.
- Password RCON yang lemah (kosong, `changeme`, `password`, `1234`, `12345678`, `rcon`, `admin`, atau kurang dari 8 karakter) otomatis diganti dengan password acak.
- Update open.mp **tidak menimpa** `config.json`, `gamemodes/`, `filterscripts/`, `scriptfiles/`, dan `plugins/`.
- Shortcut ke pengaturan optimasi baterai.

---

## Persyaratan

| Item | Keterangan |
|---|---|
| Android | 8.0 (API 26) atau lebih baru |
| Arsitektur | **arm64-v8a** saja |
| Root | Tidak perlu |
| Ruang kosong | ±600 MB (rootfs + open.mp + MariaDB) |
| Internet | Hanya saat pemasangan awal / update |
| RAM | Disarankan 4 GB ke atas bila game dan DB jalan bersamaan |

---

## Instalasi

1. Unduh APK terbaru dari tab **Releases**.
2. Izinkan pemasangan dari sumber tidak dikenal, lalu install.
3. Izinkan notifikasi saat diminta (dipakai untuk foreground service).

---

## Pemakaian

1. Buka app → tab **Setup**.
2. Jalankan berurutan:
   1. **Pasang rootfs game**: mengunduh Debian i386 dari Docker Hub, lalu memasang `libatomic1`.
   2. **Pasang open.mp**: mengunduh rilis terbaru `omp-server` (Linux x86).
   3. **Pasang MariaDB** (opsional): mengunduh Alpine arm64 dan memasang paket `mariadb`.

   Jangan tutup app selama proses berjalan. Semuanya butuh internet.
3. Tekan **Tes game** dan **Tes DB**. Output `uname -a` berarti PRoot berfungsi.
4. Buka tab **Files**, upload gamemode (`.amx`) ke `gamemodes/`, lalu atur `pawn.main_scripts` di `config.json`.
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
- Taruh di folder `plugins/`, lalu daftarkan di `pawn.legacy_plugins` pada `config.json`.
- Sesuaikan sintaks `mysql_connect` dengan versi plugin yang dipakai.

---

## Konfigurasi

Di tab **Setup → Pengaturan server**:

| Opsi | Default | Keterangan |
|---|---|---|
| Nama server | `OmpHub Server` | Ditulis ke `name` di `config.json` |
| Max pemain | `50` | `max_players` |
| Port UDP | `7777` | `network.port`, disinkronkan setiap kali server start |
| Port DB | `3306` | Port MariaDB di `127.0.0.1` |
| Jalankan MariaDB bersama server | aktif | Bila mati, Start hanya menjalankan open.mp |

Perubahan baru berlaku setelah server di-restart. Untuk opsi lanjutan, edit `config.json` lewat tab **Files**.

Panel selalu memastikan RCON aktif (`rcon.enable = true`) dengan password yang kuat, karena RCON dipakai oleh tab Console.

**URL unduhan** (Setup → URL unduhan) bisa diganti tanpa build ulang bila link default berubah:

| Komponen | Default |
|---|---|
| open.mp (Linux x86, `.tar.gz`) | rilis terbaru di `github.com/openmultiplayer/open.mp` |
| Alpine aarch64 minirootfs (`.tar.gz`) | Alpine 3.20.3 |

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
| `server/` | open.mp, `config.json`, `gamemodes/`, `plugins/`, dst. (di-bind ke `/server`) |
| `rootfs/game/` | Debian i386 |
| `rootfs/db/` | Alpine arm64 + MariaDB |
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
- Rootfs game diambil langsung dari Docker Hub (`i386/debian:bookworm-slim`) lewat registry API tanpa Docker.
- MariaDB dijalankan dengan setelan hemat memori (`innodb-buffer-pool-size=32M`, `max-connections=30`,
  `performance-schema=OFF`, tanpa native AIO).
- Query dan RCON memakai protokol UDP kompatibel SA-MP yang juga dipakai open.mp.

---

## Troubleshooting

| Gejala | Kemungkinan penyebab / solusi |
|---|---|
| **Tes game** gagal | Seccomp/SELinux beberapa ROM memblokir syscall untuk `qemu-i386`. Lihat pesan error di log Setup. |
| Pemasangan rootfs/open.mp gagal | Cek koneksi. Untuk open.mp, URL bisa diganti di Setup → URL unduhan. |
| `omp-server tidak ada di arsip` | URL open.mp salah / format rilis berubah. Ganti URL ke arsip Linux x86 `.tar.gz`. |
| Dashboard terus "Menunggu respons query UDP" | Server belum siap, atau query tidak aktif di `config.json` open.mp. |
| Console RCON: "tidak ada balasan" | RCON mati, password salah, atau server tidak berjalan. |
| Gamemode tidak bisa konek DB | Pakai `127.0.0.1`, pastikan MariaDB berstatus *running*, cek password di tab Database. |
| Plugin gagal dimuat | Plugin harus Linux **32-bit** dan terdaftar di `pawn.legacy_plugins`. |
| Server mati sendiri saat layar off | Matikan optimasi baterai untuk app ini; di beberapa ROM perlu juga izin *autostart* / *background activity*. |
| MariaDB tidak siap | Lihat Log MariaDB di tab Database. Bila inisialisasi gagal, **Pasang ulang MariaDB** dari Setup. |

---

## Batasan & catatan penting

1. **Performa**: game berjalan lewat emulasi QEMU. Cocok untuk server kecil, development, dan testing,
   bukan untuk ratusan pemain.
2. **Hanya arm64**: HP 32-bit atau x86 tidak didukung.
3. **Kompatibilitas per perangkat**: PRoot di Android sangat bergantung pada seccomp/SELinux tiap ROM.
   Hasil bisa berbeda antar merek dan versi Android. Gunakan tombol **Tes game** / **Tes DB** sebelum memakai.
4. **Key `config.json` open.mp** yang disentuh panel: `name`, `max_players`, `network.port`, `rcon.enable`,
   `rcon.password`, `pawn.legacy_plugins`. Cocokkan dengan versi open.mp yang kamu pakai.
5. **Keamanan**: MariaDB hanya listen di `127.0.0.1`, tapi port game (UDP) terbuka di jaringan yang sama.
   Jangan expose port ke internet tanpa memahami risikonya, dan jangan bagikan isi tab Database.
6. **Lalu lintas cleartext** (`usesCleartextTraffic`) diaktifkan karena unduhan bisa lewat URL kustom.

---

## Atribusi & lisensi komponen

Aplikasi ini membawa dan/atau mengunduh komponen pihak ketiga (PRoot, talloc, QEMU, open.mp, MariaDB,
Debian, Alpine, dan lainnya) yang masing-masing memiliki lisensi sendiri. Daftar lengkapnya ada di
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

Proyek ini tidak berafiliasi dengan open.mp, Rockstar Games, atau Take-Two Interactive.