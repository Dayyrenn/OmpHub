# Changelog

Format mengikuti [Keep a Changelog](https://keepachangelog.com/id-ID/1.0.0/).

## [v1.1.1]

### Ditambahkan
- Kartu "Performa" di Dashboard: CPU, RAM, thread, dan uptime per komponen (server game, MariaDB, panel) lengkap dengan grafik ~2 menit terakhir.
- Info perangkat: RAM HP, baterai, suhu, status termal, trafik jaringan, ruang penyimpanan, dan waktu respons query server.

### Diubah
- Persiapan awal kini tampil sebagai layar loading penuh ("Menyiapkan OmpHub") dengan langkah yang sedang berjalan. Panel baru terbuka setelah rootfs dan MariaDB selesai dipasang; kalau gagal ada tombol "Coba lagi" dan "Buka panel".
- `scripts/fetch-binaries.sh` kini mengambil `qemu-user-static` dan `libatomic1` dari Ubuntu, sebelumnya dari Debian.

## [1.1.0] - 2026-11-02

Semua komponen kini ada di dalam APK dan dipasang otomatis saat app dibuka pertama kali, tanpa internet dan tanpa menekan tombol di tab Setup.

### Ditambahkan
- Komponen bawaan APK: rootfs Debian i386 + `libatomic1`, server open.mp, SA-MP 0.3.7, SA-MP 0.3.DL, dan rootfs Alpine arm64 + MariaDB.
- Pemasangan otomatis saat app dibuka pertama kali.
- Banner progres di atas semua tab selama pemasangan berjalan.

### Diubah
- Pemasangan rootfs game, open.mp, SA-MP, dan MariaDB memakai komponen bawaan APK lebih dulu. Unduhan internet hanya menjadi cadangan bila komponen bawaan tidak ditemukan.
- Tombol **Update** open.mp tetap mengunduh rilis terbaru. SA-MP memakai versi bawaan selama URL-nya masih default.
- Pemasangan otomatis tidak lagi sekali seumur hidup: dilanjutkan setiap app dibuka sampai semua komponen lengkap.
- Rootfs game dan DB diekstrak ke folder sementara (`rootfs/game.tmp`, `rootfs/db.tmp`) lalu di-rename.
- Ukuran APK naik menjadi ±100 MB.
- Dokumentasi diperbarui (instalasi, pemakaian, arsitektur, troubleshooting).

### Diperbaiki
- Rootfs setengah jadi (app ditutup di tengah ekstrak) tidak lagi terdeteksi sebagai terpasang.

## [1.0.0]

Rilis awal: panel open.mp / SA-MP (0.3.7 dan 0.3.DL) + MariaDB di Android arm64 tanpa root, dengan dashboard, console (stdin dan RCON), file manager, SQL console, serta backup dan restore. Rootfs, server game, dan MariaDB diunduh dari internet lewat tab Setup.
## UI refresh
- Redesigned the main dashboard with a dark server-control layout, clearer status, player/query metrics, server information, and performance sections.
- Renamed the Setup navigation item to Mesin.
- Simplified the Mesin screen to only select and download/update open.mp or SA-MP, choose SA-MP version, and view installation logs.
- Kept base runtime preparation in the existing first-launch flow; removed unrelated database, rootfs, Android, backup, and advanced URL controls from the Mesin screen.


## Revisi panel Setup
- Nama navigasi tetap **Setup**; bagian di dalamnya diberi judul Setup Mesin Server.
- URL unduhan hanya menampilkan open.mp dan satu URL SA-MP.
- Mengembalikan pengelolaan backup: buat, restore, dan hapus backup.
- Menghilangkan informasi baterai dari Dashboard dan bagian performa.
- Menghilangkan pengaturan optimasi baterai dari halaman Setup.
