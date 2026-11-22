#!/bin/sh
# Exit 0 when AXS Home is installed and is the HOME activity.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TV="$ROOT/scripts/tv.sh"
PKG=io.github.yohannaftali.axshome

home=$(sh "$TV" shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tr -d '\r' | tail -n1)
if [ "$home" = "$PKG/.HomeActivity" ]; then
  echo "ok: HOME is AXS Home"; exit 0
fi
echo "not applied: HOME is $home"; exit 1
