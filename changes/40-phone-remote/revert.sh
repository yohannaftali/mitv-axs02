#!/bin/sh
# Restores the keyboard settings from baseline.txt (Gboard), uninstalls AXS
# Remote (its ☰ item disappears with it), then reads each value back and lists
# any difference (exit 1); a failed TV step exits 2. VERIFY_ONLY=1 only
# compares. Safe on a TV that never had AXS Remote.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"
PKG=io.github.yohannaftali.axsremote
BASELINE=${BASELINE:-"$HERE/baseline.txt"}

base() { grep -E "^$1=" "$BASELINE" 2>/dev/null | head -n1 | cut -d= -f2- | tr -d '\r'; }
DEFAULT=$(base default_input_method)
ENABLED=$(base enabled_input_methods)
WAS_INSTALLED=$(base axsremote_installed)

# Validate before any TV command: these values end up in TV shell commands.
echo "$DEFAULT" | grep -Eq '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$' \
  || { echo "revert: bad or missing default_input_method in $BASELINE" >&2; exit 1; }
echo "$ENABLED" | grep -Eq '^[A-Za-z0-9_./$;:]+$' \
  || { echo "revert: bad or missing enabled_input_methods in $BASELINE" >&2; exit 1; }
case $WAS_INSTALLED in 0|1) ;; *) echo "revert: bad or missing axsremote_installed in $BASELINE" >&2; exit 1 ;; esac

tv() {
  out=$(sh "$TV" shell "$1") || {
    printf '%s\n' "$out" >&2
    echo "revert: TV step failed or TV unreachable: $1" >&2; exit 2
  }
  printf '%s\n' "$out"
}

if [ "${VERIFY_ONLY:-0}" != 1 ]; then
  tv "ime set '$DEFAULT' >/dev/null" >/dev/null
  if [ "$WAS_INSTALLED" = 0 ]; then
    pkgs=$(tv "pm list packages $PKG")
    if printf '%s\n' "$pkgs" | tr -d '\r' | grep -qx "package:$PKG"; then
      tv "pm uninstall $PKG" >/dev/null
    fi
  fi
  tv "settings put secure enabled_input_methods '$ENABLED'" >/dev/null
  tv "settings put secure default_input_method '$DEFAULT'" >/dev/null
fi

now=$(tv "echo \"default_input_method=\$(settings get secure default_input_method)\"
echo \"enabled_input_methods=\$(settings get secure enabled_input_methods)\"
pm list packages $PKG | grep -q . && echo axsremote_installed=1 || echo axsremote_installed=0")
now=$(printf '%s\n' "$now" | tr -d '\r')

diff=0
for key in default_input_method enabled_input_methods axsremote_installed; do
  want=$(base "$key")
  got=$(printf '%s\n' "$now" | grep "^$key=" | cut -d= -f2-)
  if [ "$want" != "$got" ]; then
    echo "differs: $key is $got, baseline says $want" >&2; diff=1
  fi
done
[ "$diff" = 0 ] || exit 1
echo "reverted: TV equals baseline (keyboard $DEFAULT, AXS Remote installed=$WAS_INSTALLED)"
