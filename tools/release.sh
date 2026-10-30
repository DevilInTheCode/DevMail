#!/usr/bin/env bash
# Публикует собранный APK как GitHub Release (тег v<versionName>).
# Токен: $GITHUB_TOKEN, либо файл .github_token в корне репозитория.
# Использование: tools/release.sh [путь-к-apk]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="DevilInTheCode/DevMail"
API="https://api.github.com"
UPLOAD="https://uploads.github.com"
APK="${1:-$ROOT/build/DevMail.apk}"
MANIFEST="$ROOT/app/AndroidManifest.xml"
CHANGELOG="$ROOT/CHANGELOG.md"

TOKEN="${GITHUB_TOKEN:-}"
if [ -z "$TOKEN" ] && [ -f "$ROOT/.github_token" ]; then
  TOKEN="$(tr -d '\r\n' < "$ROOT/.github_token")"
fi
if [ -z "$TOKEN" ]; then
  echo "Нет токена: задай GITHUB_TOKEN или положи токен в .github_token" >&2
  exit 1
fi
[ -f "$APK" ] || { echo "Нет APK: $APK" >&2; exit 1; }

VERSION_NAME="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$MANIFEST" | head -1)"
VERSION_CODE="$(sed -n 's/.*android:versionCode="\([^"]*\)".*/\1/p' "$MANIFEST" | head -1)"
TAG="v$VERSION_NAME"
ASSET="DevMail-$VERSION_NAME.apk"

echo "== release $TAG (versionCode $VERSION_CODE) =="
echo "apk: $APK ($(stat -c %s "$APK") байт)"

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT
awk -v v="$VERSION_NAME" '
  /^## / { if (found) exit; if (index($0, "## " v) == 1) found = 1 }
  found { print }
' "$CHANGELOG" > "$BODY_FILE"
if [ ! -s "$BODY_FILE" ]; then
  printf 'DevMail %s (versionCode %s).\n' "$VERSION_NAME" "$VERSION_CODE" > "$BODY_FILE"
fi

api() {
  curl -sS -H "Authorization: Bearer $TOKEN" \
       -H "Accept: application/vnd.github+json" \
       -H "X-GitHub-Api-Version: 2022-11-28" "$@"
}

release_id() {
  api "$API/repos/$REPO/releases/tags/$TAG" 2>/dev/null \
    | python3 -c 'import sys,json
try: print(json.load(sys.stdin).get("id") or "")
except Exception: print("")'
}

REL_ID="$(release_id)"
if [ -z "$REL_ID" ]; then
  PAYLOAD="$(python3 -c 'import json,sys
print(json.dumps({"tag_name": sys.argv[1], "name": "DevMail " + sys.argv[2],
                  "body": open(sys.argv[3], encoding="utf-8").read(),
                  "draft": False, "prerelease": False}))' "$TAG" "$VERSION_NAME" "$BODY_FILE")"
  RESP="$(api -X POST -d "$PAYLOAD" "$API/repos/$REPO/releases")"
  REL_ID="$(printf '%s' "$RESP" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("id") or "")')"
  if [ -z "$REL_ID" ]; then
    echo "Не удалось создать релиз:" >&2
    printf '%s\n' "$RESP" >&2
    exit 1
  fi
  echo "создан релиз id=$REL_ID"
else
  echo "релиз уже есть, id=$REL_ID — обновляю ассет"
fi

# Удаляем одноимённый ассет, если он уже загружен (перезаливка).
OLD_ID="$(api "$API/repos/$REPO/releases/$REL_ID/assets" | python3 -c '
import sys, json
want = sys.argv[1]
for a in json.load(sys.stdin):
    if a.get("name") == want:
        print(a["id"]); break
' "$ASSET")"
if [ -n "$OLD_ID" ]; then
  api -X DELETE "$API/repos/$REPO/releases/assets/$OLD_ID" > /dev/null
  echo "старый ассет удалён"
fi

RESP="$(curl -sS -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/vnd.android.package-archive" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  --data-binary @"$APK" \
  "$UPLOAD/repos/$REPO/releases/$REL_ID/assets?name=$ASSET")"

URL="$(printf '%s' "$RESP" | python3 -c 'import sys,json
try: print(json.load(sys.stdin).get("browser_download_url") or "")
except Exception: print("")')"
if [ -z "$URL" ]; then
  echo "Загрузка ассета не удалась:" >&2
  printf '%s\n' "$RESP" >&2
  exit 1
fi
echo "готово: $URL"
