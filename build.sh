#!/bin/sh
# Builds build/tv-key.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# The toolchain (JDK 17 + Android build-tools/platform 34) comes from nixpkgs;
# building it accepts the Android SDK license (android_sdk.accept_license).
set -eu
cd "$(dirname "$0")"

TOOLCHAIN=$(nix build --impure --no-link --print-out-paths --expr '
  let p = import (builtins.getFlake "nixpkgs") {
        system = builtins.currentSystem;
        config = { allowUnfree = true; android_sdk.accept_license = true; };
      };
  in p.symlinkJoin {
    name = "tvkey-toolchain";
    paths = [ p.jdk17 (p.androidenv.composeAndroidPackages {
      platformVersions = [ "34" ]; buildToolsVersions = [ "34.0.0" ];
      includeEmulator = false; includeSystemImages = false; includeNDK = false;
    }).androidsdk ];
  }')
SDK=$TOOLCHAIN/libexec/android-sdk
BT=$SDK/build-tools/34.0.0
ANDROID_JAR=$SDK/platforms/android-34/android.jar
export JAVA_HOME=$TOOLCHAIN
PATH=$TOOLCHAIN/bin:$PATH

rm -rf build && mkdir -p build/res build/gen build/classes build/dex

"$BT/aapt2" compile --dir src/main/res -o build/res/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$ANDROID_JAR" \
  --manifest src/main/AndroidManifest.xml --java build/gen \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "${VERSION_CODE:-1}" --version-name "${VERSION_NAME:-0.1.0}" \
  build/res/res.zip

javac --release 11 -Xlint:all -Werror -Xlint:-options \
  -classpath "$ANDROID_JAR" -d build/classes \
  $(find src/main/java build/gen -name '*.java')

"$BT/d8" --release --min-api 26 --lib "$ANDROID_JAR" --output build/dex \
  $(find build/classes -name '*.class')

(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

# Without KEYSTORE: a local debug key, never committed. With KEYSTORE (the
# release key, kept outside the repo) apksigner prompts for the password, so
# it never lands in shell history or the environment.
if [ -n "${KEYSTORE:-}" ]; then
  "$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias tvkey \
    --out build/tv-key.apk build/aligned.apk
else
  KEYSTORE=debug.keystore
  if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
      -alias tvkey -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=tv-key debug" >/dev/null 2>&1
  fi
  "$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android \
    --out build/tv-key.apk build/aligned.apk
fi
"$BT/apksigner" verify build/tv-key.apk

ls -l build/tv-key.apk
