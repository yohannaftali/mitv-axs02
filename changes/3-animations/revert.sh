#!/bin/sh
# Restores each animation scale from baseline.txt (a key recorded as "null"
# was unset and is deleted, not set to 1.0), then reads all three back from
# the TV and compares them with baseline.txt.
#   exit 0  TV equals baseline.txt
#   exit 1  bad baseline, or values differ (listed)
#   exit 2  a TV step failed or the TV is unreachable
# VERIFY_ONLY=1 skips the restore and only compares (read-only).
# BASELINE=<file> reads another baseline.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"
BASELINE=${BASELINE:-"$HERE/baseline.txt"}
KEYS="window_animation_scale transition_animation_scale animator_duration_scale"

# Validate every value before any TV command: they end up in TV shell commands.
cmds="fail=0;"
expected=""
for k in $KEYS; do
  v=$(grep "^$k=" "$BASELINE" 2>/dev/null | head -n1 | cut -d= -f2- | tr -d '\r')
  echo "$v" | grep -Eq '^(null|[0-9]+(\.[0-9]+)?)$' \
    || { echo "revert: bad or missing $k in $BASELINE" >&2; exit 1; }
  expected="$expected$k=$v
"
  if [ "$v" = null ]; then
    cmds="$cmds settings delete global $k >/dev/null || { echo 'failed: delete $k'; fail=1; };"
  else
    cmds="$cmds settings put global $k $v || { echo 'failed: put $k'; fail=1; };"
  fi
done

if [ "${VERIFY_ONLY:-0}" != 1 ]; then
  out=$(sh "$TV" shell "$cmds exit \$fail") || {
    printf '%s\n' "$out" >&2
    echo "revert: TV step failed or TV unreachable" >&2; exit 2
  }
fi

actual=$(sh "$TV" shell "for k in $KEYS; do echo \"\$k=\$(settings get global \$k)\" || exit 1; done") \
  || { echo "revert: cannot read TV state, TV unreachable" >&2; exit 2; }
actual=$(printf '%s\n' "$actual" | tr -d '\r')

diffs=$(printf '%s' "$expected" | while IFS= read -r line; do
  printf '%s\n' "$actual" | grep -qxF -- "$line" \
    || echo "  ${line%%=*}: baseline ${line#*=}, TV $(printf '%s\n' "$actual" | grep -F -- "${line%%=*}=" | head -n1 | cut -d= -f2-)"
done)
if [ -n "$diffs" ]; then
  echo "revert: TV differs from baseline:" >&2; printf '%s\n' "$diffs" >&2; exit 1
fi
echo "reverted: animation scales equal baseline"
