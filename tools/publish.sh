#!/usr/bin/env bash
# Собирает APK, коммитит изменения, пушит main и публикует GitHub Release.
# Токен: $GITHUB_TOKEN или .github_token в корне.
# Тулчейн: PREFIX/ANDROID_JAR/KEYSTORE как в build.sh (по умолчанию .toolchain).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="DevilInTheCode/DevMail"
API="https://api.github.com"
cd "$ROOT"

TOKEN="${GITHUB_TOKEN:-}"
if [ -z "$TOKEN" ] && [ -f "$ROOT/.github_token" ]; then
  TOKEN="$(tr -d '\r\n' < "$ROOT/.github_token")"
fi
if [ -z "$TOKEN" ]; then
  echo "Нет токена: задай GITHUB_TOKEN или положи токен в .github_token" >&2
  exit 1
fi

VERSION_NAME="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$ROOT/app/AndroidManifest.xml" | head -1)"
echo "== сборка $VERSION_NAME =="

# Один тег = одна версия. Иначе Obtainium не увидит обновление.
LATEST_TAG="$(curl -sS -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "$API/repos/$REPO/releases/latest" | python3 -c 'import sys,json
try: print(json.load(sys.stdin).get("tag_name") or "")
except Exception: print("")')"
if [ "$LATEST_TAG" = "$VERSION_NAME" ]; then
  echo "Версия $VERSION_NAME уже опубликована ($LATEST_TAG)." >&2
  echo "Подними versionName и versionCode в app/AndroidManifest.xml." >&2
  exit 1
fi
bash "$ROOT/build.sh"

if [ -n "$(git status --porcelain)" ]; then
  git add -A
  git commit -q -m "Версия $VERSION_NAME"
  echo "коммит: $(git log --oneline -1)"
fi

HEADER="$(printf 'x-access-token:%s' "$TOKEN" | python3 -c 'import base64,sys; print("Authorization: Basic " + base64.b64encode(sys.stdin.buffer.read()).decode())')"
echo "== push =="
git -c http.extraHeader="$HEADER" push origin HEAD:main

echo "== release =="
GITHUB_TOKEN="$TOKEN" bash "$ROOT/tools/release.sh" "$ROOT/build/DevMail.apk"
