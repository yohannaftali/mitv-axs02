#!/bin/sh
# Installs AXS Home, pushes the layout from apps/home/config/home.conf and
# makes it the HOME screen. Re-run after editing home.conf (SKIP_BUILD=1 to
# reuse the last APK). Idempotent.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TV="$ROOT/scripts/tv.sh"
PKG=io.github.yohannaftali.axshome
APK="$ROOT/apps/home/build/axshome.apk"
CONF_DIR="/sdcard/Android/data/$PKG/files"

if [ "${SKIP_BUILD:-0}" != 1 ] || [ ! -f "$APK" ]; then
  sh "$ROOT/apps/home/build.sh" >/dev/null
fi

GOOGLE_HOME=com.google.android.tvlauncher

sh "$TV" install "$APK"
# First start creates the app's external files dir with the right owner, and
# proves the app runs before the Google launcher is taken away.
if ! sh "$TV" shell "am start -W -n $PKG/.HomeActivity" | grep -q 'Status: ok'; then
  echo "apply: AXS Home did not start; Google TV launcher left untouched" >&2; exit 1
fi
sh "$TV" push "$ROOT/apps/home/config/home.conf" "$CONF_DIR/home.conf"
sh "$TV" shell "cmd package set-home-activity --user 0 $PKG/.HomeActivity"
# Android TV 11 keeps sending HOME to the Google launcher while it is enabled.
sh "$TV" shell "pm disable-user --user 0 $GOOGLE_HOME"
sh "$TV" shell "input keyevent KEYCODE_HOME"
sh "$(dirname -- "$0")/check.sh"
