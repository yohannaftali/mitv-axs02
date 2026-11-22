#!/bin/sh
# Exit 0 when AXS Remote is installed and is the TV's keyboard, 1 when not,
# 2 when the TV cannot be checked.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TV="$ROOT/scripts/tv.sh"
PKG=io.github.yohannaftali.axsremote

out=$(sh "$TV" shell "settings get secure default_input_method; pm list packages $PKG") \
  || { echo "cannot check: TV unreachable or adb failed"; exit 2; }
out=$(printf '%s\n' "$out" | tr -d '\r')
ime=$(printf '%s\n' "$out" | head -n1)
printf '%s\n' "$out" | grep -qx "package:$PKG" || { echo "not applied: AXS Remote is not installed"; exit 1; }
[ "$ime" = "$PKG/.RemoteIme" ] || { echo "not applied: keyboard is $ime"; exit 1; }
echo "ok: AXS Remote is installed and is the keyboard"
