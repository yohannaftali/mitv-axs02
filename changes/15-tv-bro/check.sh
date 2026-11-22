#!/bin/sh
# Exit 0 when TV Bro is installed, 1 when it is not, 2 when the TV cannot be
# checked.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"
PKG=$(grep -E '^package=' "$HERE/pinned.txt" | cut -d= -f2-)

out=$(sh "$TV" shell "pm list packages $PKG") \
  || { echo "cannot check: TV unreachable or adb failed"; exit 2; }
if printf '%s\n' "$out" | tr -d '\r' | grep -qx "package:$PKG"; then
  echo "ok: TV Bro installed"; exit 0
fi
echo "not applied: TV Bro not installed"; exit 1
