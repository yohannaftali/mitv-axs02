#!/bin/sh
# Restores what baseline.txt recorded before AXS Home: the HOME activity,
# whether the Google TV launcher was enabled, and whether AXS Home was
# installed. Safe on a TV that never had AXS Home.
# BASELINE=<file> reads another baseline (used to test bad input).
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"
PKG=io.github.yohannaftali.axshome
GOOGLE_HOME=com.google.android.tvlauncher
BASELINE=${BASELINE:-"$HERE/baseline.txt"}

base() { grep -E "^$1=" "$BASELINE" 2>/dev/null | head -n1 | cut -d= -f2- | tr -d '\r'; }
BASE_HOME=$(base home_activity)
GOOGLE_ENABLED=$(base google_tvlauncher_enabled)
WAS_INSTALLED=$(base axshome_installed)

# Validate before any TV command: these values end up in TV shell commands.
echo "$BASE_HOME" | grep -Eq '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$' \
  || { echo "revert: bad or missing home_activity in $BASELINE" >&2; exit 1; }
case $GOOGLE_ENABLED in yes|no) ;; *) echo "revert: bad or missing google_tvlauncher_enabled in $BASELINE" >&2; exit 1 ;; esac
case $WAS_INSTALLED in 0|1) ;; *) echo "revert: bad or missing axshome_installed in $BASELINE" >&2; exit 1 ;; esac

# Runs one TV step; on failure shows adb's (redacted) output and exits 2.
tv() {
  out=$(sh "$TV" shell "$1") || {
    printf '%s\n' "$out" >&2
    echo "revert: TV step failed or TV unreachable: $1" >&2; exit 2
  }
  printf '%s\n' "$out"
}

if [ "$GOOGLE_ENABLED" = yes ]; then
  tv "pm enable $GOOGLE_HOME" >/dev/null
fi
tv "cmd package set-home-activity --user 0 '$BASE_HOME'" >/dev/null
if [ "$WAS_INSTALLED" = 0 ] && tv "pm list packages $PKG" | grep -qx "package:$PKG"; then
  tv "pm uninstall $PKG" >/dev/null
fi
tv "input keyevent KEYCODE_HOME" >/dev/null

home=$(tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME')
home=$(printf '%s\n' "$home" | tr -d '\r' | tail -n1)
if [ "$home" != "$BASE_HOME" ]; then
  echo "revert: HOME is $home, baseline says $BASE_HOME" >&2; exit 1
fi
echo "reverted: HOME is $BASE_HOME (baseline)"
