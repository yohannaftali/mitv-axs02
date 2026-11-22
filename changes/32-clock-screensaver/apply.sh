#!/bin/sh
# Installs AXS Clock and makes it the screensaver. The screensaver timing
# settings are left as they are. Re-run after editing apps/clock/config/clock.conf
# (SKIP_BUILD=1 reuses the last APK). Idempotent.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TV="$ROOT/scripts/tv.sh"
PKG=io.github.yohannaftali.axsclock
APK="$ROOT/apps/clock/build/axsclock.apk"

if [ "${SKIP_BUILD:-0}" != 1 ] || [ ! -f "$APK" ]; then
  sh "$ROOT/apps/clock/build.sh" >/dev/null
fi

sh "$TV" install "$APK"
sh "$TV" shell "settings put secure screensaver_components $PKG/.ClockDream"
sh "$(dirname -- "$0")/check.sh"
