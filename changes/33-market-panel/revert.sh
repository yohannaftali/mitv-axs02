#!/bin/sh
# Puts back the offline AXS Clock from #32: rebuilds apps/clock from the git
# commit in baseline.txt (offline_source), replaces the installed app with it
# (uninstall first, because Android refuses a lower versionCode), removes the
# pushed local.conf with it, and restores the screensaver component. Then
# reads each baseline value back and lists any difference (exit 1); a failed
# TV step exits 2. VERIFY_ONLY=1 only compares. Safe to run twice.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$HERE/../.." && pwd)
TV="$ROOT/scripts/tv.sh"
PKG=io.github.yohannaftali.axsclock
BASELINE=${BASELINE:-"$HERE/baseline.txt"}

base() { grep -E "^$1=" "$BASELINE" 2>/dev/null | head -n1 | cut -d= -f2- | tr -d '\r'; }
VERSION=$(base axsclock_version)
INTERNET=$(base axsclock_internet)
SAVER=$(base screensaver_components)
SOURCE=$(base offline_source)

# Validate before any TV or git command: these values end up in commands.
echo "$VERSION" | grep -Eq '^[0-9]+$' || { echo "revert: bad or missing axsclock_version in $BASELINE" >&2; exit 1; }
case $INTERNET in 0|1) ;; *) echo "revert: bad or missing axsclock_internet in $BASELINE" >&2; exit 1 ;; esac
echo "$SAVER" | grep -Eq '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$' \
  || { echo "revert: bad or missing screensaver_components in $BASELINE" >&2; exit 1; }
echo "$SOURCE" | grep -Eq '^[0-9a-f]{7,40}$' || { echo "revert: bad or missing offline_source in $BASELINE" >&2; exit 1; }

tv() {
  out=$(sh "$TV" shell "$1") || {
    printf '%s\n' "$out" >&2
    echo "revert: TV step failed or TV unreachable: $1" >&2; exit 2
  }
  printf '%s\n' "$out"
}

state() {
  s=$(tv "dumpsys package $PKG | grep -E 'versionCode=|android.permission.INTERNET'; settings get secure screensaver_components")
  printf '%s\n' "$s" | tr -d '\r'
}

if [ "${VERIFY_ONLY:-0}" != 1 ]; then
  now=$(state)
  installed=$(printf '%s\n' "$now" | grep -m1 -o 'versionCode=[0-9]*' | cut -d= -f2)
  if [ "$installed" != "$VERSION" ]; then
    work=$(mktemp -d)
    trap 'rm -rf "$work"' EXIT
    git -C "$ROOT" archive "$SOURCE" apps/clock | tar -x -C "$work"
    sh "$work/apps/clock/build.sh" >/dev/null
    if [ -n "$installed" ]; then tv "pm uninstall $PKG" >/dev/null; fi
    sh "$TV" install "$work/apps/clock/build/axsclock.apk" >/dev/null \
      || { echo "revert: installing the offline AXS Clock failed" >&2; exit 2; }
  fi
  tv "settings put secure screensaver_components '$SAVER'" >/dev/null
fi

now=$(state)
got_version=$(printf '%s\n' "$now" | grep -m1 -o 'versionCode=[0-9]*' | cut -d= -f2)
if printf '%s\n' "$now" | grep -q 'android.permission.INTERNET'; then got_internet=1; else got_internet=0; fi
got_saver=$(printf '%s\n' "$now" | tail -n1)

diff=0
[ "$got_version" = "$VERSION" ] || { echo "differs: axsclock_version is ${got_version:-absent}, baseline says $VERSION" >&2; diff=1; }
[ "$got_internet" = "$INTERNET" ] || { echo "differs: axsclock_internet is $got_internet, baseline says $INTERNET" >&2; diff=1; }
[ "$got_saver" = "$SAVER" ] || { echo "differs: screensaver_components is $got_saver, baseline says $SAVER" >&2; diff=1; }
[ "$diff" = 0 ] || exit 1
echo "reverted: TV equals baseline (offline AXS Clock $VERSION, screensaver $SAVER)"
