#!/usr/bin/env bash
# Mengambil binary arm64 yang dibutuhkan dan menaruhnya di app/src/main/jniLibs/arm64-v8a/
# Nama harus lib*.so supaya Android mengekstraknya ke nativeLibraryDir (satu-satunya tempat yang boleh di-exec).
#
#   libproot.so         <- proot (paket Termux)
#   libproot-loader.so  <- loader proot (paket Termux)
#   libtalloc.so        <- dependency proot (paket Termux)
#   libqemu-i386.so     <- qemu-i386-static (paket Debian qemu-user-static, arm64)
#
# Butuh: curl ar tar awk find od (Linux/macOS/WSL/GitHub Actions).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$DEST"

for t in curl ar tar awk find od perl xz; do
  command -v "$t" >/dev/null || { echo "Butuh '$t'"; exit 1; }
done

TERMUX="https://packages.termux.dev/apt/termux-main"
echo ">> Mengambil indeks paket Termux (aarch64)"
curl -fsSL "$TERMUX/dists/stable/main/binary-aarch64/Packages" -o "$WORK/Packages"

termux_deb() {
  local pkg="$1" file
  file=$(awk -v p="$pkg" 'BEGIN{RS="";FS="\n"}
    { name=""; fn="";
      for(i=1;i<=NF;i++){
        if($i ~ /^Package: /)  name=substr($i,10);
        if($i ~ /^Filename: /) fn=substr($i,11);
      }
      if(name==p){print fn; exit} }' "$WORK/Packages")
  [ -n "$file" ] || { echo "Paket Termux '$pkg' tidak ditemukan"; exit 1; }
  echo ">> $pkg  ($file)"
  curl -fsSL "$TERMUX/$file" -o "$WORK/$pkg.deb"
  mkdir -p "$WORK/$pkg"
  ( cd "$WORK/$pkg" && ar x "../$pkg.deb" && tar xf data.tar.* )
}

termux_deb proot
P="$WORK/proot/data/data/com.termux/files/usr"
cp -L "$P/bin/proot" "$DEST/libproot.so"
cp -L "$P/libexec/proot/loader" "$DEST/libproot-loader.so"

termux_deb libtalloc
T="$WORK/libtalloc/data/data/com.termux/files/usr/lib"
cp -L "$(ls "$T"/libtalloc.so* | head -n1)" "$DEST/libtalloc.so"

# libproot.so meminta "libtalloc.so.2", padahal Android hanya mengekstrak file berakhiran .so.
# Ganti nama dependensinya jadi "libtalloc.so" (dipadatkan dengan NUL supaya panjang string tetap sama).
SZ_BEFORE=$(wc -c < "$DEST/libproot.so")
perl -0777 -pi -e 's/libtalloc\.so\.2\0/libtalloc.so\0\0\0/g' "$DEST/libproot.so"
[ "$(wc -c < "$DEST/libproot.so")" = "$SZ_BEFORE" ] || { echo "Patch mengubah ukuran libproot.so, dibatalkan"; exit 1; }

# proot Termux versi baru butuh libandroid-shmem.so (emulasi System V shared memory)
termux_deb libandroid-shmem
S="$WORK/libandroid-shmem/data/data/com.termux/files/usr/lib"
cp -L "$(ls "$S"/libandroid-shmem.so* | head -n1)" "$DEST/libandroid-shmem.so"

# ---------------------------------------------------------------------------
# qemu-i386 (Debian arm64)
# Versi Debian terbaru (qemu 10.x) tidak lagi menyertakan binary asli di paket
# qemu-user-static: /usr/bin/qemu-i386-static hanya symlink yang putus.
# Jadi coba paket dari yang terbaru ke yang lebih lama, dan pakai yang pertama
# berisi binary ELF aarch64 yang benar-benar ada.
# ---------------------------------------------------------------------------
echo ">> qemu-user-static (Debian arm64)"
BASE="https://deb.debian.org/debian/pool/main/q/qemu"
LIST=$(curl -fsSL "$BASE/" | grep -o 'qemu-user-static_[^"]*_arm64\.deb' | sort -uV | tac)
[ -n "$LIST" ] || { echo "qemu-user-static arm64 tidak ditemukan di $BASE"; exit 1; }

# true jika file ELF dengan arsitektur aarch64 (e_machine = 0xB7)
is_arm64_elf() {
  [ "$(head -c4 "$1" 2>/dev/null | od -An -c | tr -d ' ')" = '177ELF' ] || return 1
  [ "$(od -An -tx1 -j18 -N1 "$1" | tr -d ' ')" = "b7" ]
}

FOUND=""
TRIES=0
for DEB in $LIST; do
  TRIES=$((TRIES + 1))
  [ "$TRIES" -le 8 ] || break
  echo "   coba $DEB"
  rm -rf "$WORK/qemu"
  mkdir -p "$WORK/qemu"
  curl -fsSL "$BASE/$DEB" -o "$WORK/qemu.deb" || continue
  ( cd "$WORK/qemu" && ar x ../qemu.deb && tar xf data.tar.* ) || continue

  while IFS= read -r Q; do
    # -f mengikuti symlink, jadi symlink putus otomatis dilewati
    if [ -f "$Q" ] && is_arm64_elf "$Q"; then
      FOUND="$Q"
      break
    fi
  done < <(find "$WORK/qemu" \( -type f -o -type l \) \
             \( -name 'i386-binfmt-P' -o -name 'qemu-i386-static' -o -name 'qemu-i386' \))

  if [ -n "$FOUND" ]; then
    echo "   ditemukan: ${FOUND#$WORK/qemu}"
    break
  fi
  echo "   tidak ada binary qemu-i386 asli di $DEB, coba versi sebelumnya"
done

[ -n "$FOUND" ] || { echo "Tidak ada paket qemu-user-static arm64 yang berisi binary qemu-i386 yang valid"; exit 1; }
cp -L "$FOUND" "$DEST/libqemu-i386.so"

# ---------------------------------------------------------------------------
# Rootfs game (Debian i386) dibundel di APK supaya terpasang otomatis tanpa internet
# saat app dibuka pertama kali.
#   assets/rootfs-game.tar.xz     <- rootfs Debian bookworm-slim i386 (debuerreotype)
#   assets/libatomic1-i386.tar.xz <- isi paket libatomic1 (dibutuhkan omp-server), diekstrak di atas rootfs
# ---------------------------------------------------------------------------
ASSETS="$ROOT/app/src/main/assets"
mkdir -p "$ASSETS"
echo ">> Rootfs Debian i386 (bundel APK)"
curl -fsSL "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/dist-i386/bookworm/slim/rootfs.tar.xz" \
  -o "$ASSETS/rootfs-game.tar.xz"
xz -t "$ASSETS/rootfs-game.tar.xz" 2>/dev/null || tar -tJf "$ASSETS/rootfs-game.tar.xz" >/dev/null \
  || { echo "rootfs-game.tar.xz rusak"; exit 1; }

echo ">> libatomic1 i386 (bundel APK)"
GCC_POOL="https://deb.debian.org/debian/pool/main/g/gcc-12"
ATOMIC_DEB=$(curl -fsSL "$GCC_POOL/" | grep -o 'libatomic1_12[^"]*_i386\.deb' | sort -uV | tail -n1)
[ -n "$ATOMIC_DEB" ] || { echo "libatomic1 i386 tidak ditemukan di $GCC_POOL"; exit 1; }
echo "   $ATOMIC_DEB"
curl -fsSL "$GCC_POOL/$ATOMIC_DEB" -o "$WORK/libatomic1.deb"
mkdir -p "$WORK/atomic"
( cd "$WORK/atomic" && ar x ../libatomic1.deb )
DATA=$(ls "$WORK"/atomic/data.tar.* | head -n1)
case "$DATA" in
  *.xz) cp "$DATA" "$ASSETS/libatomic1-i386.tar.xz" ;;
  *)    tar -xf "$DATA" -C "$WORK/atomic" --one-top-level=content 2>/dev/null || { mkdir -p "$WORK/atomic/content"; tar -xf "$DATA" -C "$WORK/atomic/content"; }
        tar -C "$WORK/atomic/content" -cJf "$ASSETS/libatomic1-i386.tar.xz" . ;;
esac
tar -tJf "$ASSETS/libatomic1-i386.tar.xz" | grep -q 'libatomic\.so\.1' || { echo "libatomic.so.1 tidak ada di arsip"; exit 1; }
echo ">> Server game (bundel APK)"
curl -fsSL "https://github.com/openmultiplayer/open.mp/releases/latest/download/open.mp-linux-x86.tar.gz" \
  -o "$ASSETS/openmp-linux-x86.tar.gz"
curl -fsSL "https://sa-mp.co.id/files/samp037svr_R2-2-1.tar.gz" -o "$ASSETS/samp037svr.tar.gz"
curl -fsSL "https://sa-mp.co.id/files/samp03DLsvr_R1.tar.gz"    -o "$ASSETS/samp03DLsvr.tar.gz"
for f in openmp-linux-x86 samp037svr samp03DLsvr; do
  tar -tzf "$ASSETS/$f.tar.gz" >/dev/null || { echo "$f.tar.gz rusak"; exit 1; }
done

# mariadb-rootfs.tar.xz dibuat terpisah oleh scripts/build-mariadb-rootfs.sh (butuh docker + qemu arm64)

ls -la "$ASSETS"

chmod 755 "$DEST"/*.so
echo
echo ">> Selesai:"
ls -la "$DEST"

if command -v file >/dev/null; then
  echo; echo ">> Cek arsitektur (harus ARM aarch64):"
  file "$DEST"/*.so
fi

if command -v readelf >/dev/null; then
  echo; echo ">> Cek dependency (harus ada di APK atau berupa library sistem Android):"
  SYS=" libc.so libdl.so libm.so liblog.so libz.so "
  MISSING=0
  for so in "$DEST"/*.so; do
    case "$(basename "$so")" in libqemu-i386.so|libproot-loader.so) continue ;; esac
    for need in $(readelf -d "$so" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p'); do
      if [ -f "$DEST/$need" ] || [[ "$SYS" == *" $need "* ]]; then continue; fi
      echo "   KURANG: $(basename "$so") butuh $need"
      MISSING=1
    done
  done
  [ "$MISSING" = 0 ] || { echo "Ada dependency yang belum disertakan di APK"; exit 1; }
  echo "   semua dependency terpenuhi"
fi