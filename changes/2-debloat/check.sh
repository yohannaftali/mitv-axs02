#!/bin/sh
# Exit 0 when every installed package in packages.txt is disabled, 1 when
# some are still enabled, 2 when the TV cannot be checked.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"
PKGS=$(grep -v '^#' "$HERE/packages.txt" | tr '\n' ' ')

out=$(sh "$TV" shell "
  D=\$(pm list packages -d) || exit 1
  for p in $PKGS; do
    pm path \$p >/dev/null 2>&1 || continue
    echo \"\$D\" | grep -qx \"package:\$p\" || echo \$p
  done
  exit 0") || { echo "cannot check: TV unreachable or adb failed"; exit 2; }
left=$(printf '%s\n' "$out" | tr -d '\r' | tr '\n' ' ' | sed 's/ *$//')
if [ -z "$left" ]; then
  echo "ok: all listed packages disabled"; exit 0
fi
echo "not applied: still enabled: $left"; exit 1
