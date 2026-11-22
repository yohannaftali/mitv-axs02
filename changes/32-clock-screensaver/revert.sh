#!/bin/sh
# Restores what baseline.txt recorded before AXS Clock: the screensaver
# component, the two screensaver switches, and whether AXS Clock was
# installed. Then reads each value back and lists any difference.
# Safe on a TV that never had AXS Clock. VERIFY_ONLY=1 only compares.
# BASELINE=<file> reads another baseline (used to test bad input).
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"
PKG=io.github.yohannaftali.axsclock
BASELINE=${BASELINE:-"$HERE/baseline.txt"}

base() { grep -E "^$1=" "$BASELINE" 2>/dev/null | head -n1 | cut -d= -f2- | tr -d '\r'; }
SAVER=$(base screensaver_components)
ENABLED=$(base screensaver_enabled)
ON_SLEEP=$(base screensaver_activate_on_sleep)
WAS_INSTALLED=$(base axsclock_installed)

# Validate before any TV command: these values end up in TV shell commands.
[ "$SAVER" = null ] || echo "$SAVER" | grep -Eq '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+(,[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)*$' \
  || { echo "revert: bad or missing screensaver_components in $BASELINE" >&2; exit 1; }
for v in "$ENABLED" "$ON_SLEEP"; do
  case $v in 0|1|null) ;; *) echo "revert: bad or missing screensaver switch in $BASELINE" >&2; exit 1 ;; esac
done
case $WAS_INSTALLED in 0|1) ;; *) echo "revert: bad or missing axsclock_installed in $BASELINE" >&2; exit 1 ;; esac

# Runs one TV step; on failure shows adb's (redacted) output and exits 2.
tv() {
  out=$(sh "$TV" shell "$1") || {
    printf '%s\n' "$out" >&2
    echo "revert: TV step failed or TV unreachable: $1" >&2; exit 2
  }
  printf '%s\n' "$out"
}

put() { # put <key> <value>; "null" means the key was unset
  if [ "$2" = null ]; then tv "settings delete secure $1" >/dev/null
  else tv "settings put secure $1 '$2'" >/dev/null; fi
}

if [ "${VERIFY_ONLY:-0}" != 1 ]; then
  put screensaver_components "$SAVER"
  put screensaver_enabled "$ENABLED"
  put screensaver_activate_on_sleep "$ON_SLEEP"
  pkgs=$(tv "pm list packages $PKG")
  if [ "$WAS_INSTALLED" = 0 ] && printf '%s\n' "$pkgs" | tr -d '\r' | grep -qx "package:$PKG"; then
    tv "pm uninstall $PKG" >/dev/null
  fi
fi

now=$(tv "echo \"screensaver_components=\$(settings get secure screensaver_components)\"
echo \"screensaver_enabled=\$(settings get secure screensaver_enabled)\"
echo \"screensaver_activate_on_sleep=\$(settings get secure screensaver_activate_on_sleep)\"
pm list packages $PKG | grep -q . && echo axsclock_installed=1 || echo axsclock_installed=0")
now=$(printf '%s\n' "$now" | tr -d '\r')

diff=0
for key in screensaver_components screensaver_enabled screensaver_activate_on_sleep axsclock_installed; do
  want=$(base "$key")
  got=$(printf '%s\n' "$now" | grep "^$key=" | cut -d= -f2-)
  if [ "$want" != "$got" ]; then
    echo "differs: $key is $got, baseline says $want" >&2; diff=1
  fi
done
[ "$diff" = 0 ] || exit 1
echo "reverted: TV equals baseline (screensaver $SAVER, AXS Clock installed=$WAS_INSTALLED)"
