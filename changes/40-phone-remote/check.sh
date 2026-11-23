#!/bin/sh
# Exit 0 when AXS Remote is installed, is the TV keyboard, serves the remote
# on port 8077 (HTTP) and its touchpad pointer is on; 1 when not; 2 when the
# TV cannot be checked.
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
# Android rebinds the keyboard a few seconds after AXS Remote is (re)installed.
http=$(sh "$TV" shell 'for i in 1 2 3 4 5 6 7 8; do
  r=$( (printf "GET / HTTP/1.0\r\n\r\n"; sleep 1) | nc 127.0.0.1 8077 2>/dev/null | head -n1)
  case $r in HTTP*) break ;; esac
  sleep 1
done
echo "$r"') || { echo "cannot check: TV unreachable or adb failed"; exit 2; }
printf '%s\n' "$http" | grep -q '^HTTP/1.1 200' || { echo "not applied: nothing answers HTTP on port 8077"; exit 1; }
a11y=$(sh "$TV" shell "settings get secure enabled_accessibility_services; settings get secure accessibility_enabled") \
  || { echo "cannot check: TV unreachable or adb failed"; exit 2; }
a11y=$(printf '%s\n' "$a11y" | tr -d '\r')
printf '%s\n' "$a11y" | head -n1 | tr ':' '\n' | grep -qx "$PKG/.PointerService" \
  || { echo "not applied: touchpad pointer (accessibility service) is off"; exit 1; }
[ "$(printf '%s\n' "$a11y" | sed -n 2p)" = 1 ] || { echo "not applied: accessibility is off"; exit 1; }
echo "ok: AXS Remote is installed, is the keyboard, serves http on port 8077 and has its touchpad pointer"
