# Changelog

Format mengikuti [Keep a Changelog](https://keepachangelog.com/id-ID/1.1.0/).

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