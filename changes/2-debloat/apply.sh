#!/bin/sh
# Disables the ad, tracking and unused preinstalled apps in packages.txt for
# user 0 (nothing is deleted) and moves the screensaver off Ambient mode,
# which is one of them. Idempotent; packages missing from the TV are skipped.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"
PKGS=$(grep -v '^#' "$HERE/packages.txt" | tr '\n' ' ')

# AXS Home (#1) must be HOME first: PatchWall is on the list.
rc=0; sh "$HERE/../1-fast-home/check.sh" >/dev/null || rc=$?
case $rc in
  0) ;;
  1) echo "apply: apply #1 (changes/1-fast-home) first" >&2; exit 1 ;;
  *) echo "apply: cannot reach TV" >&2; exit 2 ;;
esac

sh "$TV" shell "
  settings put secure screensaver_components com.android.dreams.basic/.Colors
  D=\$(pm list packages -d)
  for p in $PKGS; do
    pm path \$p >/dev/null 2>&1 || { echo \"skip \$p (not installed)\"; continue; }
    echo \"\$D\" | grep -qx \"package:\$p\" && continue
    pm disable-user --user 0 \$p
  done"
sh "$HERE/check.sh"
