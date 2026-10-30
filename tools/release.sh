#!/usr/bin/env bash
# Публикует собранный APK как GitHub Release (тег = versionName, файл DevMail.apk).
# Токен: $GITHUB_TOKEN, либо файл .github_token в корне репозитория.
# Использование: tools/release.sh [путь-к-apk]
#
# Правила, без которых Obtainium не увидит обновление:
#   * тег ровно номер версии (3.5), без букв и префиксов; versionName — 3.5, 3.6, 3.10;
#   * в релизе ровно один APK с постоянным именем DevMail.apk;
#   * релиз опубликован (не draft, не prerelease), иначе Obtainium его пропустит;
#   * versionCode и versionName всегда растут — повторно тот же тег не публикуем.
# Релиз создаётся черновиком, APK заливается, и только потом черновик публикуется,
# чтобы Obtainium никогда не увидел релиз без файла.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="DevilInTheCode/DevMail"
API="https://api.github.com"
UPLOAD="https://uploads.github.com"
APK="${1:-$ROOT/build/DevMail.apk}"
MANIFEST="$ROOT/app/AndroidManifest.xml"
CHANGELOG="$ROOT/CHANGELOG.md"
FORCE="${FORCE:-0}"

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
TAG="$VERSION_NAME"
ASSET="DevMail.apk"
SHA="$(sha256sum "$APK" | cut -d' ' -f1)"

echo "== release $TAG (versionCode $VERSION_CODE) =="
echo "apk: $APK ($(stat -c %s "$APK") байт)"
echo "sha256: $SHA"

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT
awk -v v="$VERSION_NAME" '
  /^## / { if (found) exit; if (index($0, "## " v) == 1) found = 1 }
  found { print }
' "$CHANGELOG" > "$BODY_FILE"
if [ ! -s "$BODY_FILE" ]; then
  printf 'DevMail %s (versionCode %s).\n' "$VERSION_NAME" "$VERSION_CODE" > "$BODY_FILE"
fi
printf '\n**APK:** `%s` · versionCode %s · SHA-256 `%s`\n' "$ASSET" "$VERSION_CODE" "$SHA" >> "$BODY_FILE"

api() {
  curl -sS -H "Authorization: Bearer $TOKEN" \
       -H "Accept: application/vnd.github+json" \
       -H "X-GitHub-Api-Version: 2022-11-28" "$@"
}

json_id() { python3 -c 'import sys,json
try: print(json.load(sys.stdin).get("id") or "")
except Exception: print("")'; }

REL_ID="$(api "$API/repos/$REPO/releases/tags/$TAG" 2>/dev/null | json_id)"

if [ -n "$REL_ID" ]; then
  if [ "$FORCE" != "1" ]; then
    echo "Релиз $TAG уже существует (id=$REL_ID)." >&2
    echo "Obtainium не увидит обновление с тем же тегом — подними versionName/versionCode." >&2
    echo "Перезалить файл в существующий релиз: FORCE=1 tools/release.sh $APK" >&2
    exit 1
  fi
  echo "релиз уже есть, id=$REL_ID — перезаливаю ассет (FORCE=1)"
else
  PAYLOAD="$(python3 -c 'import json,sys
print(json.dumps({"tag_name": sys.argv[1], "name": sys.argv[2],
                  "body": open(sys.argv[3], encoding="utf-8").read(),
                  "draft": True, "prerelease": False}))' "$TAG" "$VERSION_NAME" "$BODY_FILE")"
  RESP="$(api -X POST -d "$PAYLOAD" "$API/repos/$REPO/releases")"
  REL_ID="$(printf '%s' "$RESP" | json_id)"
  if [ -z "$REL_ID" ]; then
    echo "Не удалось создать черновик релиза:" >&2
    printf '%s\n' "$RESP" >&2
    exit 1
  fi
  echo "создан черновик релиза id=$REL_ID"
fi

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

# Публикуем черновик: до этого момента релиза для Obtainium не существует.
PUB="$(api -X PATCH -d '{"draft": false, "prerelease": false}' \
  "$API/repos/$REPO/releases/$REL_ID")"
if ! printf '%s' "$PUB" | python3 -c 'import sys,json; sys.exit(0 if json.load(sys.stdin).get("draft") is False else 1)'; then
  echo "Не удалось опубликовать релиз:" >&2
  printf '%s\n' "$PUB" >&2
  exit 1
fi
# После публикации ссылка ассета меняется с untagged-... на тег.
echo "готово: https://github.com/$REPO/releases/download/$TAG/$ASSET"
