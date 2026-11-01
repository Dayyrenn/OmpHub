#!/usr/bin/env bash
# Mengambil binary arm64 yang dibutuhkan dan menaruhnya di app/src/main/jniLibs/arm64-v8a/
# Nama harus lib*.so supaya Android mengekstraknya ke nativeLibraryDir (satu-satunya tempat yang boleh di-exec).
#
#   libproot.so         <- proot (paket Termux)
#   libproot-loader.so  <- loader proot (paket Termux)
#   libtalloc.so        <- dependency proot (paket Termux)
#   libqemu-i386.so     <- qemu-i386-static (paket Debian qemu-user-static, arm64)
#
# Butuh: curl ar tar awk find (Linux/macOS/WSL/GitHub Actions).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$DEST"

for t in curl ar tar awk find; do
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

echo ">> qemu-user-static (Debian arm64)"
BASE="https://deb.debian.org/debian/pool/main/q/qemu"
DEB=$(curl -fsSL "$BASE/" | grep -o 'qemu-user-static_[^"]*_arm64\.deb' | sort -uV | tail -n1)
[ -n "$DEB" ] || { echo "qemu-user-static arm64 tidak ditemukan di $BASE"; exit 1; }
echo "   $DEB"
curl -fsSL "$BASE/$DEB" -o "$WORK/qemu.deb"
mkdir -p "$WORK/qemu"
# Ekstrak SEMUA isi paket. Di Debian baru, /usr/bin/qemu-i386-static hanyalah symlink
# ke /usr/libexec/qemu-binfmt/i386-binfmt-P, jadi mengekstrak satu path saja menghasilkan
# symlink yang putus (penyebab error "cannot stat").
( cd "$WORK/qemu" && ar x ../qemu.deb && tar xf data.tar.* )

# Prioritaskan file asli (bukan symlink), lalu fallback ke symlink.
Q=$(find "$WORK/qemu" -type f \( -name 'i386-binfmt-P' -o -name 'qemu-i386-static' -o -name 'qemu-i386' \) | head -n1)
if [ -z "$Q" ]; then
  Q=$(find "$WORK/qemu" -type l \( -name 'qemu-i386-static' -o -name 'qemu-i386' \) | head -n1)
fi
if [ -z "$Q" ]; then
  echo "qemu-i386 tidak ketemu di dalam $DEB. Isi yang berkaitan:"
  find "$WORK/qemu" -name '*i386*'
  find "$WORK/qemu/usr/bin" 2>/dev/null | head -50
  exit 1
fi
echo "   ditemukan: ${Q#$WORK/qemu}"
cp -L "$Q" "$DEST/libqemu-i386.so"

chmod 755 "$DEST"/*.so
echo
echo ">> Selesai:"
ls -la "$DEST"

if command -v file >/dev/null; then
  echo; echo ">> Cek arsitektur (harus ARM aarch64):"
  file "$DEST"/*.so
fi

if command -v readelf >/dev/null; then
  echo; echo ">> Dependency libproot.so (harus hanya libc/libdl/libm/libtalloc):"
  readelf -d "$DEST/libproot.so" | grep NEEDED || true
fi