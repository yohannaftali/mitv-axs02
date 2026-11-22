#!/bin/sh
# Exit 0 when AXS Home is installed and is the HOME activity, 1 when it is
# not, 2 when the TV cannot be checked.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TV="$ROOT/scripts/tv.sh"
PKG=io.github.yohannaftali.axshome

out=$(sh "$TV" shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME') \
  || { echo "cannot check: TV unreachable or adb failed"; exit 2; }
home=$(printf '%s\n' "$out" | tr -d '\r' | tail -n1)
if [ "$home" = "$PKG/.HomeActivity" ]; then
  echo "ok: HOME is AXS Home"; exit 0
fi
echo "not applied: HOME is $home"; exit 1
