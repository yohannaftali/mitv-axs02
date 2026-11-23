#!/bin/sh
# Installs AXS Remote, makes it the TV's keyboard (so the phone can send keys
# and text to any app), allows it to bring up the home screen, turns on its
# touchpad pointer (an accessibility service, #55), and adds "Phone remote"
# to AXS Home's ☰ menu (re-runs #1's apply.sh, which pushes home.conf).
# SKIP_BUILD=1 reuses the last APKs. Idempotent.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TV="$ROOT/scripts/tv.sh"
PKG=io.github.yohannaftali.axsremote
IME="$PKG/.RemoteIme"
POINTER="$PKG/.PointerService"
APK="$ROOT/apps/remote/build/axsremote.apk"

if [ "${SKIP_BUILD:-0}" != 1 ] || [ ! -f "$APK" ]; then
  sh "$ROOT/apps/remote/build.sh" >/dev/null
fi

sh "$TV" install "$APK"
# HOME from the phone starts the home screen while another app is in front;
# Android 11 allows that background start only with this app-op.
sh "$TV" shell "appops set $PKG SYSTEM_ALERT_WINDOW allow"
sh "$TV" shell "ime enable $IME >/dev/null && ime set $IME >/dev/null"
# Add the pointer to whatever accessibility services are already on (none on this TV).
sh "$TV" shell "cur=\$(settings get secure enabled_accessibility_services)
case \"\$cur\" in
  *$POINTER*) ;;
  ''|null) settings put secure enabled_accessibility_services $POINTER ;;
  *) settings put secure enabled_accessibility_services \"\$cur:$POINTER\" ;;
esac
settings put secure accessibility_enabled 1"
sh "$ROOT/changes/1-fast-home/apply.sh" >/dev/null
sh "$(dirname -- "$0")/check.sh"
