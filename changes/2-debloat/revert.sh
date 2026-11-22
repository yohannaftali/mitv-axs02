#!/bin/sh
# Puts every package in baseline.txt back in its recorded state (enabled,
# disabled, absent = skipped) and restores the screensaver, then reads every
# value back from the TV and compares it with baseline.txt.
#   exit 0  TV equals baseline.txt
#   exit 1  bad baseline, or values differ (listed)
#   exit 2  a TV step failed or the TV is unreachable
# VERIFY_ONLY=1 skips the restore and only compares (read-only).
# BASELINE=<file> reads another baseline.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"
BASELINE=${BASELINE:-"$HERE/baseline.txt"}

expected=$(grep -E '^[^#]' "$BASELINE" 2>/dev/null | grep -v '^mem_available_kb=' | tr -d '\r') || true
pkgs() { printf '%s\n' "$expected" | grep -E "^[a-z0-9_.]+=$1\$" | cut -d= -f1 | tr '\n' ' '; }
ENABLE=$(pkgs enabled)
DISABLE=$(pkgs disabled)
ALL=$(printf '%s\n' "$expected" | grep -E '^[a-z0-9_.]+=(enabled|disabled|absent)$' | cut -d= -f1 | tr '\n' ' ')
SAVER=$(printf '%s\n' "$expected" | grep '^screensaver_components=' | cut -d= -f2-)

# Validate before any TV command: these values end up in TV shell commands.
bad=$(printf '%s\n' "$expected" | grep -vE '^[a-z0-9_.]+=(enabled|disabled|absent)$|^screensaver_components=' || true)
[ -z "$bad" ] && [ -n "$ALL" ] || { echo "revert: bad or empty $BASELINE" >&2; exit 1; }
echo "$SAVER" | grep -Eq '^(null|[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)$' \
  || { echo "revert: bad or missing screensaver_components in $BASELINE" >&2; exit 1; }

if [ "${VERIFY_ONLY:-0}" != 1 ]; then
  if [ "$SAVER" = null ]; then saver_cmd="settings delete secure screensaver_components >/dev/null"
  else saver_cmd="settings put secure screensaver_components '$SAVER'"; fi
  out=$(sh "$TV" shell "
    fail=0
    for p in $ENABLE; do
      pm path \$p >/dev/null 2>&1 || continue
      pm enable \$p >/dev/null || { echo \"failed: pm enable \$p\"; fail=1; }
    done
    for p in $DISABLE; do
      pm path \$p >/dev/null 2>&1 || continue
      pm disable-user --user 0 \$p >/dev/null || { echo \"failed: pm disable-user \$p\"; fail=1; }
    done
    $saver_cmd || { echo 'failed: screensaver'; fail=1; }
    exit \$fail") || {
    printf '%s\n' "$out" >&2
    echo "revert: TV step failed or TV unreachable" >&2; exit 2
  }
fi

actual=$(sh "$TV" shell "
  D=\$(pm list packages -d) || exit 1
  for p in $ALL; do
    if ! pm path \$p >/dev/null 2>&1; then echo \"\$p=absent\"
    elif echo \"\$D\" | grep -qx \"package:\$p\"; then echo \"\$p=disabled\"
    else echo \"\$p=enabled\"; fi
  done
  echo \"screensaver_components=\$(settings get secure screensaver_components)\"") \
  || { echo "revert: cannot read TV state, TV unreachable" >&2; exit 2; }
actual=$(printf '%s\n' "$actual" | tr -d '\r')

diffs=$(printf '%s\n' "$expected" | while IFS= read -r line; do
  printf '%s\n' "$actual" | grep -qxF -- "$line" \
    || echo "  ${line%%=*}: baseline ${line#*=}, TV $(printf '%s\n' "$actual" | grep -F -- "${line%%=*}=" | head -n1 | cut -d= -f2-)"
done)
if [ -n "$diffs" ]; then
  echo "revert: TV differs from baseline:" >&2; printf '%s\n' "$diffs" >&2; exit 1
fi
echo "reverted: TV equals baseline ($(echo "$ALL" | wc -w | tr -d ' ') packages, screensaver $SAVER)"
