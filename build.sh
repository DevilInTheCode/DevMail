#!/usr/bin/env bash
# DevMail: сборка APK без Gradle — aapt2 -> javac -> d8 -> zip -> apksigner.
# Нужны: JDK 17+, android.jar (API 35), aapt2, d8, apksigner, python3.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PREFIX="${PREFIX:-$ROOT/.toolchain}"
ANDROID_JAR="${ANDROID_JAR:-$PREFIX/sdk/android-35/android.jar}"
export JAVA_HOME="${JAVA_HOME:-$PREFIX/lib/jvm/java-21-openjdk}"
export PATH="$PREFIX/bin:$JAVA_HOME/bin:$PATH"
export LD_LIBRARY_PATH="$PREFIX/lib:$JAVA_HOME/lib:${LD_LIBRARY_PATH:-}"
export TMPDIR="${TMPDIR:-$ROOT/build/tmp}"
export HOME="${HOME:-$ROOT/build/home}"
mkdir -p "$TMPDIR" "$HOME"

APP="$ROOT/app"
OUT="$ROOT/build"
LIBS="$ROOT/libs/android-mail-1.6.2.jar:$ROOT/libs/android-activation-1.6.2.jar"
VERSION_CODE="${VERSION_CODE:-32}"
VERSION_NAME="${VERSION_NAME:-3.2}"
KEYSTORE="${KEYSTORE:-$ROOT/debug.keystore}"

mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== 1/6 aapt2 compile =="
rm -f "$OUT/res.zip"
aapt2 compile --dir "$APP/res" -o "$OUT/res.zip"

echo "== 2/6 aapt2 link =="
rm -f "$OUT/app-unsigned.apk"
aapt2 link \
  -o "$OUT/app-unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -R "$OUT/res.zip" \
  --java "$OUT/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 35 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  --auto-add-overlay

echo "== 3/6 javac =="
rm -rf "$OUT/classes"; mkdir -p "$OUT/classes"
javac -encoding UTF-8 -source 17 -target 17 -nowarn \
  -classpath "$ANDROID_JAR:$LIBS" \
  -d "$OUT/classes" \
  $(find "$OUT/gen" "$APP/src" -name '*.java')

echo "== 4/6 d8 =="
rm -rf "$OUT/dex"; mkdir -p "$OUT/dex"
d8 --release --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class') \
  "$ROOT/libs/android-mail-1.6.2.jar" "$ROOT/libs/android-activation-1.6.2.jar"

echo "== 5/6 add classes.dex =="
python3 "$ROOT/tools/add_dex.py" "$OUT/app-unsigned.apk" "$OUT/dex/classes.dex" "$OUT/app-unzipped.apk"

echo "== 5b/6 mail resources =="
python3 "$ROOT/tools/add_res.py" "$OUT/app-unzipped.apk" \
  "$ROOT/libs/android-mail-1.6.2.jar" "$ROOT/libs/android-activation-1.6.2.jar"

echo "== 5c/6 align =="
python3 "$ROOT/tools/align.py" "$OUT/app-unzipped.apk" "$OUT/app-aligned.apk"

echo "== 6/6 sign =="
rm -f "$OUT/DevMail.apk"
apksigner sign \
  --ks "$KEYSTORE" \
  --ks-pass "${KEYSTORE_PASS:-pass:android}" \
  --key-pass "${KEY_PASS:-pass:android}" \
  --ks-key-alias "${KEY_ALIAS:-androiddebugkey}" \
  --min-sdk-version 26 \
  --out "$OUT/DevMail.apk" \
  "$OUT/app-aligned.apk"

if [ -n "${DEPLOY_DIR:-}" ]; then
  mkdir -p "$DEPLOY_DIR"
  cp "$OUT/DevMail.apk" "$DEPLOY_DIR/DevMail.apk"
  echo "deployed: $DEPLOY_DIR/DevMail.apk"
fi

apksigner verify --verbose "$OUT/DevMail.apk" | head -5
ls -l "$OUT/DevMail.apk"
