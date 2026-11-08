## v1.1.6

### Ditambahkan
- Dukungan perangkat 32-bit (armeabi-v7a / arm32). Sekarang ada APK terpisah untuk arm64 dan armv7.
- APK arm64 dan armv7 langsung terlampir di halaman Release.

### Diubah
- Rootfs MariaDB (Alpine) mengikuti arsitektur perangkat (aarch64 atau armv7).
- Build memakai binary proot, talloc, dan qemu sesuai ABI.

### Catatan
- Pilih **arm64** untuk HP 64-bit, dan **armv7** hanya untuk HP 32-bit.
- Build armv7 masih eksperimental: emulasi i386 di CPU 32-bit lebih lambat dan RAM terbatas.