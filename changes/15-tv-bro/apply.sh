#!/bin/sh
# Installs the TV Bro release pinned in pinned.txt: downloads it from the
# official GitHub release, checks its SHA-256 and signing certificate against
# the pinned values, then installs it. Refuses on any mismatch. Idempotent.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$HERE/../.." && pwd)
TV="$ROOT/scripts/tv.sh"
PINNED=${PINNED:-"$HERE/pinned.txt"}
pin() { grep -E "^$1=" "$PINNED" | head -n1 | cut -d= -f2-; }
REPO=$(pin repo); TAG=$(pin tag); ASSET=$(pin asset)
SHA=$(pin sha256); CERT=$(pin cert_sha256); PKG=$(pin package)
CACHE="$ROOT/.cache/tvbro"
APK=${APK:-"$CACHE/$ASSET"}

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[ -n "$SDK" ] || SDK="${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk"
BT=$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | grep -v -- '-rc' | sort -V | tail -n1); BT=${BT%/}
APKSIGNER="$BT/apksigner"; [ -f "$APKSIGNER" ] || APKSIGNER="$BT/apksigner.bat"
[ -f "$APKSIGNER" ] || { echo "apply: apksigner not found (Android SDK build-tools)" >&2; exit 1; }

if [ ! -f "$APK" ]; then
  mkdir -p "$CACHE"
  gh release download "$TAG" -R "$REPO" -p "$ASSET" -D "$CACHE" --clobber
fi

got=$(sha256sum "$APK" | cut -d' ' -f1)
[ "$got" = "$SHA" ] || { echo "apply: SHA-256 mismatch for $ASSET; not installing" >&2; exit 1; }
certs=$("$APKSIGNER" verify --print-certs "$APK" 2>&1) \
  || { echo "apply: APK signature does not verify; not installing" >&2; exit 1; }
printf '%s\n' "$certs" | grep -q "certificate SHA-256 digest: $CERT\$" \
  || { echo "apply: signing certificate does not match the pinned one; not installing" >&2; exit 1; }
[ "$(printf '%s\n' "$certs" | grep -c 'certificate SHA-256 digest:')" = 1 ] \
  || { echo "apply: unexpected extra signers; not installing" >&2; exit 1; }
echo "verified: $ASSET (sha256 and signing certificate match)"

sh "$TV" install "$APK"
sh "$HERE/check.sh"
