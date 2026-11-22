#!/bin/sh
# Re-enables every package that baseline.txt recorded as enabled and restores
# the screensaver. Safe on a TV where nothing was disabled.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"
ENABLE=$(grep -E '^[a-z0-9_.]+=enabled$' "$HERE/baseline.txt" | cut -d= -f1 | tr '\n' ' ')
SAVER=$(grep '^screensaver_components=' "$HERE/baseline.txt" | cut -d= -f2-)

sh "$TV" shell "
  for p in $ENABLE; do
    pm path \$p >/dev/null 2>&1 && pm enable \$p >/dev/null
  done
  settings put secure screensaver_components '$SAVER'"
rc=0; sh "$HERE/check.sh" >/dev/null || rc=$?
case $rc in
  0) echo "revert: packages still disabled" >&2; exit 1 ;;
  1) ;;
  *) echo "revert: cannot verify, TV unreachable" >&2; exit 2 ;;
esac
echo "reverted: $(echo "$ENABLE" | wc -w | tr -d ' ') packages enabled, screensaver $SAVER"
