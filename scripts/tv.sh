#!/bin/sh
# adb wrapper for the MiTV-AXSO2. Reads the TV address from .env, never prints
# it, and pipes all output through scripts/redact.sh.
#
#   scripts/tv.sh connect            # adb connect (port 5555 or TV_ADB_PORT)
#   scripts/tv.sh info               # non-sensitive build facts
#   scripts/tv.sh shell '<cmd>'      # run a shell command on the TV
#   scripts/tv.sh screenshot <file>  # PNG into captures/ (git-ignored)
#   scripts/tv.sh install <apk>      # adb install -r
#   scripts/tv.sh push <local> <tv>  # adb push
#   scripts/tv.sh adb <args...>      # any other adb command against the TV
#
# Exits with adb's own status (the remote command's status for `shell`), so
# callers can tell a failed step or an unreachable TV from success.
set -eu
# Git Bash would rewrite TV-side paths such as /sdcard/... into Windows paths.
export MSYS_NO_PATHCONV=1
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ENV_FILE="$ROOT/.env"
REDACT="$ROOT/scripts/redact.sh"

envval() {
  [ -f "$ENV_FILE" ] || return 0
  grep -E "^$1=" "$ENV_FILE" | head -n1 | cut -d= -f2- | tr -d '\r"'"'"' '
}

IP=$(envval tv_private_ip_address)
PORT=${TV_ADB_PORT:-$(envval TV_ADB_PORT)}; PORT=${PORT:-5555}
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

# Runs adb with output captured, prints it redacted, returns adb's status.
# A plain `adb … | redact` would return redact's status, which is always 0.
OUT=$(mktemp "${TMPDIR:-/tmp}/tvsh.XXXXXX")
trap 'rm -f "$OUT"' EXIT
run() {
  rc=0
  "$ADB" "$@" >"$OUT" 2>&1 || rc=$?
  redact <"$OUT"
  return "$rc"
}

cmd=${1:-}; [ $# -gt 0 ] && shift
case "$cmd" in
  connect)
    rc=0; "$ADB" connect "$SERIAL" >"$OUT" 2>&1 || rc=$?
    sh "$REDACT" <"$OUT"
    # adb connect exits 0 even when it could not connect.
    [ "$rc" -eq 0 ] && grep -qE '(already )?connected to' "$OUT" || exit 1 ;;
  info)
    run -s "$SERIAL" shell '
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
      echo "shell_user=$(id -un)"' ;;
  shell)
    run -s "$SERIAL" shell "$@" ;;
  screenshot)
    out=${1:-"$ROOT/captures/screen-$(date +%Y%m%d-%H%M%S).png"}
    mkdir -p "$(dirname -- "$out")"
    rc=0; "$ADB" -s "$SERIAL" exec-out screencap -p >"$out" 2>"$OUT" || rc=$?
    if [ "$rc" -ne 0 ] || [ ! -s "$out" ]; then
      sh "$REDACT" <"$OUT" >&2; rm -f "$out"; exit "$(( rc ? rc : 1 ))"
    fi
    echo "$out" ;;
  install)
    run -s "$SERIAL" install -r "$(winpath "$1")" ;;
  push)
    run -s "$SERIAL" push "$(winpath "$1")" "$2" ;;
  adb)
    run -s "$SERIAL" "$@" ;;
  *)
    sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
