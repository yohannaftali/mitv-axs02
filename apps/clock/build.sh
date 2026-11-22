#!/bin/sh
# Builds apps/clock/build/axsclock.apk with the Android SDK command-line tools
# only (aapt2, javac, d8, zipalign, apksigner). No Gradle, no libraries.
#
# Needs: Android SDK (ANDROID_HOME, or %LOCALAPPDATA%\Android\Sdk) with a
# build-tools and a platforms/android-30+ package, and a JDK (JAVA_HOME, or
# the one bundled with Android Studio).
# Signed with the standard local debug key (~/.android/debug.keystore),
# created on first build. The key never goes into the repo.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUT="$HERE/build"

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[ -n "$SDK" ] || SDK="${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk"
[ -d "$SDK" ] || { echo "Android SDK not found; set ANDROID_HOME" >&2; exit 2; }
BT=$(ls -d "$SDK"/build-tools/*/ | grep -v -- '-rc' | sort -V | tail -n1); BT=${BT%/}
PLATFORM=$(ls -d "$SDK"/platforms/android-*/ | sort -V | tail -n1); ANDROID_JAR="${PLATFORM%/}/android.jar"

if [ -n "${JAVA_HOME:-}" ]; then JBIN="$JAVA_HOME/bin"
elif [ -d "/c/Program Files/Android/Android Studio/jbr/bin" ]; then JBIN="/c/Program Files/Android/Android Studio/jbr/bin"
else JBIN=$(dirname -- "$(command -v javac)"); fi

tool() { for ext in "" .exe .bat; do [ -f "$1$ext" ] && { echo "$1$ext"; return; }; done; echo "missing tool: $1" >&2; exit 2; }
AAPT2=$(tool "$BT/aapt2"); D8=$(tool "$BT/d8"); ZIPALIGN=$(tool "$BT/zipalign"); APKSIGNER=$(tool "$BT/apksigner")
JAVAC=$(tool "$JBIN/javac"); JAR=$(tool "$JBIN/jar"); KEYTOOL=$(tool "$JBIN/keytool")

KS="$HOME/.android/debug.keystore"
if [ ! -f "$KS" ]; then
  mkdir -p "$HOME/.android"
  "$KEYTOOL" -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null
fi

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen" "$OUT/assets"
cp "$HERE/config/clock.conf" "$OUT/assets/clock.conf"

"$AAPT2" compile --dir "$HERE/res" -o "$OUT/res.zip"
"$AAPT2" link -I "$ANDROID_JAR" --manifest "$HERE/AndroidManifest.xml" \
  --min-sdk-version 30 --target-sdk-version 30 \
  -A "$OUT/assets" --java "$OUT/gen" -o "$OUT/base.apk" "$OUT/res.zip"

# shellcheck disable=SC2046
"$JAVAC" -nowarn -Xlint:-options --release 11 -classpath "$ANDROID_JAR" -d "$OUT/classes" \
  $(find "$HERE/src" "$OUT/gen" -name '*.java')
# shellcheck disable=SC2046
"$D8" --release --min-api 30 --lib "$ANDROID_JAR" --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

cp "$OUT/base.apk" "$OUT/unaligned.apk"
"$JAR" -u -M -f "$OUT/unaligned.apk" -C "$OUT/dex" classes.dex
"$ZIPALIGN" -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out "$OUT/axsclock.apk" "$OUT/aligned.apk"
echo "$OUT/axsclock.apk ($(wc -c < "$OUT/axsclock.apk") bytes)"
