#!/bin/sh
# Exit 0 when AXS Clock with the market panel (versionCode >= 2, INTERNET,
# location granted) is installed and is the screensaver; 1 when not; 2 when
# the TV cannot be checked.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TV="$ROOT/scripts/tv.sh"
PKG=io.github.yohannaftali.axsclock

out=$(sh "$TV" shell "settings get secure screensaver_components; dumpsys package $PKG | grep -E 'versionCode=|android.permission.(INTERNET|ACCESS_COARSE_LOCATION)'") \
  || { echo "cannot check: TV unreachable or adb failed"; exit 2; }
out=$(printf '%s\n' "$out" | tr -d '\r')
saver=$(printf '%s\n' "$out" | head -n1)
version=$(printf '%s\n' "$out" | grep -m1 -o 'versionCode=[0-9]*' | cut -d= -f2)
[ -n "$version" ] || { echo "not applied: AXS Clock is not installed"; exit 1; }
[ "$version" -ge 2 ] || { echo "not applied: AXS Clock $version is the offline build"; exit 1; }
printf '%s\n' "$out" | grep -q 'android.permission.INTERNET' \
  || { echo "not applied: AXS Clock has no INTERNET permission"; exit 1; }
printf '%s\n' "$out" | grep -q 'ACCESS_COARSE_LOCATION: granted=true' \
  || { echo "not applied: location not granted (weather uses the fallback city only)"; exit 1; }
[ "$saver" = "$PKG/.ClockDream" ] || { echo "not applied: screensaver is $saver"; exit 1; }
echo "ok: AXS Clock $version with market panel is the screensaver"
