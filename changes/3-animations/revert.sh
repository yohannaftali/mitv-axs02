#!/bin/sh
# Restores each scale from baseline.txt; a key that was unset ("null") is
# deleted rather than set, so the TV is exactly as before.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"

cmds=""
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  v=$(grep "^$k=" "$HERE/baseline.txt" | cut -d= -f2-)
  if [ -z "$v" ] || [ "$v" = null ]; then
    cmds="$cmds settings delete global $k >/dev/null;"
  else
    cmds="$cmds settings put global $k $v;"
  fi
done
sh "$TV" shell "$cmds"
rc=0; sh "$HERE/check.sh" >/dev/null || rc=$?
case $rc in
  0) echo "revert: animation scales still changed" >&2; exit 1 ;;
  1) ;;
  *) echo "revert: cannot verify, TV unreachable" >&2; exit 2 ;;
esac
echo "reverted: animation scales restored from baseline"
