#!/bin/sh
# Restores baseline.txt: uninstalls TV Bro if it was absent before, keeps it
# if it was already installed, then confirms the TV matches the baseline.
#   exit 0  TV equals baseline.txt
#   exit 1  bad baseline, or the TV differs
#   exit 2  a TV step failed or the TV is unreachable
# VERIFY_ONLY=1 skips the restore and only compares (read-only).
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"
PKG=$(grep -E '^package=' "$HERE/pinned.txt" | cut -d= -f2-)
BASELINE=${BASELINE:-"$HERE/baseline.txt"}

want=$(grep -E "^$PKG=" "$BASELINE" 2>/dev/null | head -n1 | cut -d= -f2- | tr -d '\r')
case $want in installed|absent) ;; *) echo "revert: bad or missing $PKG in $BASELINE" >&2; exit 1 ;; esac

state() {
  out=$(sh "$TV" shell "pm list packages $PKG") \
    || { echo "revert: cannot read TV state, TV unreachable" >&2; exit 2; }
  if printf '%s\n' "$out" | tr -d '\r' | grep -qx "package:$PKG"; then echo installed; else echo absent; fi
}

now=$(state)
if [ "${VERIFY_ONLY:-0}" != 1 ] && [ "$want" = absent ] && [ "$now" = installed ]; then
  out=$(sh "$TV" shell "pm uninstall $PKG") || {
    printf '%s\n' "$out" >&2; echo "revert: TV step failed or TV unreachable" >&2; exit 2
  }
  now=$(state)
fi
if [ "$now" != "$want" ]; then
  echo "revert: TV differs from baseline: $PKG baseline $want, TV $now" >&2; exit 1
fi
echo "reverted: TV equals baseline ($PKG $want)"
