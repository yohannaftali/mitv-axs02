#!/bin/sh
# Exit 0 when AXS Clock is installed and is the screensaver, 1 when it is not,
# 2 when the TV cannot be checked.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TV="$ROOT/scripts/tv.sh"
PKG=io.github.yohannaftali.axsclock

out=$(sh "$TV" shell "settings get secure screensaver_components; pm list packages $PKG") \
  || { echo "cannot check: TV unreachable or adb failed"; exit 2; }
out=$(printf '%s\n' "$out" | tr -d '\r')
saver=$(printf '%s\n' "$out" | head -n1)
if ! printf '%s\n' "$out" | grep -qx "package:$PKG"; then
  echo "not applied: AXS Clock is not installed (screensaver $saver)"; exit 1
fi
if [ "$saver" = "$PKG/.ClockDream" ]; then
  echo "ok: screensaver is AXS Clock"; exit 0
fi
echo "not applied: screensaver is $saver"; exit 1
