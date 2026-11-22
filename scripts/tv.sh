#!/bin/sh
# adb wrapper for the MiTV-AXS02. Reads the TV address from .env, never prints
# it, and pipes all output through scripts/redact.sh.
#
#   scripts/tv.sh connect            # adb connect (port 5555 or TV_ADB_PORT)
#   scripts/tv.sh info               # non-sensitive build facts
#   scripts/tv.sh shell '<cmd>'      # run a shell command on the TV
#   scripts/tv.sh screenshot <file>  # PNG into captures/ (git-ignored)
#   scripts/tv.sh adb <args...>      # any other adb command against the TV
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ENV_FILE="$ROOT/.env"
REDACT="$ROOT/scripts/redact.sh"

envval() {
  [ -f "$ENV_FILE" ] || return 0
  grep -E "^$1=" "$ENV_FILE" | head -n1 | cut -d= -f2- | tr -d '\r"'"'"' '
}

IP=$(envval tv_private_ip_address)
PORT=$(envval TV_ADB_PORT); PORT=${PORT:-5555}
[ -n "$IP" ] || { echo "tv_private_ip_address missing in .env" >&2; exit 2; }
SERIAL="$IP:$PORT"

ADB=$(envval ADB)
if [ -z "$ADB" ]; then
  if command -v adb >/dev/null 2>&1; then ADB=adb
  elif [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then ADB="$ANDROID_HOME/platform-tools/adb"
  elif [ -n "${LOCALAPPDATA:-}" ] && [ -x "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" ]; then ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
  else echo "adb not found: install platform-tools or set ADB= in .env" >&2; exit 2; fi
fi

# The serial number and Android ID only become known once connected; feed them
# to the redactor so they never leak either.
redact() {
  if [ -z "${REDACT_SERIAL:-}" ]; then
    # </dev/null: adb shell would otherwise eat the output piped into redact.
    REDACT_SERIAL=$("$ADB" -s "$SERIAL" shell getprop ro.serialno </dev/null 2>/dev/null | tr -d '\r' || true)
    REDACT_ANDROID_ID=$("$ADB" -s "$SERIAL" shell settings get secure android_id </dev/null 2>/dev/null | tr -d '\r' || true)
    export REDACT_SERIAL REDACT_ANDROID_ID
  fi
  sh "$REDACT"
}

cmd=${1:-}; [ $# -gt 0 ] && shift
case "$cmd" in
  connect)
    "$ADB" connect "$SERIAL" 2>&1 | sh "$REDACT" ;;
  info)
    "$ADB" -s "$SERIAL" shell '
      echo "model=$(getprop ro.product.model)"
      echo "device=$(getprop ro.product.device)"
      echo "manufacturer=$(getprop ro.product.manufacturer)"
      echo "android=$(getprop ro.build.version.release) (sdk $(getprop ro.build.version.sdk))"
      echo "security_patch=$(getprop ro.build.version.security_patch)"
      echo "kernel=$(uname -r)"
      echo "build=$(getprop ro.build.display.id)"
      echo "abi=$(getprop ro.product.cpu.abilist)"
      echo "display=$(wm size | tail -n1) $(wm density | tail -n1)"
      echo "home=$(cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -n1)"
      echo "shell_user=$(id -un)"' 2>&1 | redact ;;
  shell)
    "$ADB" -s "$SERIAL" shell "$@" 2>&1 | redact ;;
  screenshot)
    out=${1:-"$ROOT/captures/screen-$(date +%Y%m%d-%H%M%S).png"}
    mkdir -p "$(dirname -- "$out")"
    "$ADB" -s "$SERIAL" exec-out screencap -p > "$out"
    echo "$out" ;;
  adb)
    "$ADB" -s "$SERIAL" "$@" 2>&1 | redact ;;
  *)
    sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
