set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Pemakaian: scripts/build-mariadb-rootfs.sh [arm64|armv7]   (default arm64)
ABI="${1:-${ABI:-arm64}}"
case "$ABI" in
  arm64) PLATFORM="linux/arm64" ;;
  armv7) PLATFORM="linux/arm/v7" ;;
  *) echo "ABI harus arm64 atau armv7 (dapat: $ABI)"; exit 1 ;;
esac
ASSETS="$ROOT/app/src/$ABI/assets"   # rootfs MariaDB khusus per-ABI
mkdir -p "$ASSETS"
NAME="omphub-mariadb-$$"
trap 'docker rm -f "$NAME" >/dev/null 2>&1 || true' EXIT

echo ">> apk add mariadb di alpine:3.20 ($PLATFORM)"
docker run --platform "$PLATFORM" --name "$NAME" alpine:3.20 \
  sh -c "apk add --no-cache mariadb mariadb-client && rm -rf /var/cache/apk/*"

echo ">> export + kompres"
docker export "$NAME" | xz -T0 -6 > "$ASSETS/mariadb-rootfs.tar.xz"

LIST="$(mktemp)"
tar -tJf "$ASSETS/mariadb-rootfs.tar.xz" > "$LIST"
grep -q 'usr/bin/mariadbd$' "$LIST" || { echo "mariadbd tidak ada di rootfs hasil build"; exit 1; }
rm -f "$LIST"
ls -la "$ASSETS/mariadb-rootfs.tar.xz"
