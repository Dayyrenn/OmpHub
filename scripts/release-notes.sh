set -euo pipefail
TAG="$1"
PREV=$(git describe --tags --abbrev=0 "$TAG^" 2>/dev/null || true)
RANGE="${PREV:+$PREV..}$TAG"

sec() {
  local out; out=$(grep -i -E "$2" all.txt | sed -E 's/^[A-Za-z]+(\([^)]*\))?!?:[[:space:]]*//; s/^/- /' || true)
  [ -n "$out" ] && printf '### %s\n%s\n\n' "$1" "$out" || true
}

{
  if [ -s note.md ]; then
    cat note.md
    echo
  else
    git log "$RANGE" --no-merges --pretty='%s' \
      | grep -v -i -E '^(build|buil|\.|update )' | awk '!s[$0]++' > all.txt || true
    echo "## Perubahan"
    echo
    sec "Fitur baru" '^feat'
    sec "Perbaikan" '^fix'
    other=$(grep -v -i -E '^(feat|fix)' all.txt | sed 's/^/- /' || true)
    [ -n "$other" ] && printf '### Lainnya\n%s\n\n' "$other" || true
    [ -s all.txt ] || echo "_Tidak ada perubahan yang tercatat._"
    echo
  fi
  echo "### Unduhan"
  echo "- \`app-arm64-release.apk\` : HP 64-bit (arm64-v8a)"
  echo "- \`app-armv7-release.apk\` : HP 32-bit (armeabi-v7a), eksperimental"
  [ -n "$PREV" ] && printf '\n**Full changelog:** https://github.com/%s/compare/%s...%s\n' "${GITHUB_REPOSITORY:-OWNER/REPO}" "$PREV" "$TAG"
} > release-body.md