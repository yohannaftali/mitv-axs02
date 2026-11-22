#!/bin/sh
# Installs AXS Clock 1.1 (clock + weather + market tiles), grants it
# approximate location for the weather, keeps it as the screensaver, and
# pushes the optional weather_city from .env to the TV (never printed, never
# committed). Re-run after editing apps/clock/config/clock.conf or .env
# (SKIP_BUILD=1 reuses the last APK). Idempotent. Needs #32.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TV="$ROOT/scripts/tv.sh"
PKG=io.github.yohannaftali.axsclock
APK="$ROOT/apps/clock/build/axsclock.apk"
LOCAL="/sdcard/Android/data/$PKG/files/local.conf"

if [ "${SKIP_BUILD:-0}" != 1 ] || [ ! -f "$APK" ]; then
  sh "$ROOT/apps/clock/build.sh" >/dev/null
fi

sh "$TV" install "$APK"
sh "$TV" shell "pm grant $PKG android.permission.ACCESS_COARSE_LOCATION"
sh "$TV" shell "settings put secure screensaver_components $PKG/.ClockDream"

# Optional fallback city for the weather when the TV has no location.
CITY=""
[ -f "$ROOT/.env" ] && CITY=$(grep -E '^weather_city=' "$ROOT/.env" | head -n1 | cut -d= -f2- | tr -d '\r"')
if [ -n "$CITY" ]; then
  case $CITY in *'|'*) echo "apply: weather_city must not contain |" >&2; exit 1 ;; esac
  tmp=$(mktemp)
  trap 'rm -f "$tmp"' EXIT
  printf 'weather_city|%s\n' "$CITY" > "$tmp"
  sh "$TV" push "$tmp" "$LOCAL" >/dev/null
  echo "weather: fallback city pushed to the TV"
else
  sh "$TV" shell "rm -f $LOCAL"
fi

sh "$(dirname -- "$0")/check.sh"
