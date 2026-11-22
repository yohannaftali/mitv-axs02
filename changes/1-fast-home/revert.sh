#!/bin/sh
# Puts the Google TV launcher back as HOME and uninstalls AXS Home. Safe on a
# TV that never had it.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TV="$ROOT/scripts/tv.sh"
PKG=io.github.yohannaftali.axshome
BASE_HOME=com.google.android.tvlauncher/.MainActivity

sh "$TV" shell "pm enable com.google.android.tvlauncher"
sh "$TV" shell "cmd package set-home-activity --user 0 $BASE_HOME"
if sh "$TV" shell "pm path $PKG" | grep -q '^package:'; then
  sh "$TV" shell "pm uninstall $PKG"
fi
sh "$TV" shell "input keyevent KEYCODE_HOME"
if sh "$(dirname -- "$0")/check.sh" >/dev/null; then
  echo "revert: AXS Home is still HOME" >&2; exit 1
fi
echo "reverted: HOME is $BASE_HOME"
