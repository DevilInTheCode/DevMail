# DevMail

Почтовый клиент для Android в стиле мессенджера: письма сгруппированы по собеседникам,
переписка выглядит как чат. Работает по IMAP/SMTP, без сторонних серверов и аккаунтов —
только ваши ящики.

Версия 3.1 (versionCode 31). Минимальная версия Android — 8.0 (API 26).

## Возможности

- Несколько почтовых аккаунтов, общий список чатов и фильтр по конкретному ящику.
- Чаты по переписке: непрочитанные, закрепление, «без звука», папки, поиск по письмам.
- Свайпы по строке чата — «прочитано» и «удалить», выбор нескольких чатов.
- Вложения: дозагрузка по нажатию, превью картинок в переписке, автозагрузка с лимитом
  размера и сжатием изображений.
- Карточка собеседника с ручной аватаркой из галереи (в списке чатов и в шапке переписки).
- «Поделиться» из любого приложения: выбор контакта и отправка файла письмом.
- Просмотр фото из вложения с масштабом (щипок, двойной тап).
- Очистка кеша вложений в настройках.
- Темы «Ночь», «День», «Киберпанк»; русский и английский языки.

## Сборка

Gradle не используется, сборка идёт напрямую инструментами Android SDK.
Нужны: JDK 17+, `android.jar` (API 35), `aapt2`, `d8`, `apksigner`, `python3`.

```bash
PREFIX=/path/to/toolchain \
ANDROID_JAR=/path/to/android-35/android.jar \
KEYSTORE=debug.keystore \
./build.sh
```

`PREFIX` — каталог, где лежат `bin/aapt2`, `bin/d8`, `bin/apksigner` и JDK
(по умолчанию `.toolchain` в корне проекта). Готовый файл — `build/DevMail.apk`.
Ключ подписи создаётся отдельно:

```bash
keytool -genkeypair -keystore debug.keystore -alias androiddebugkey \
  -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=DevMail Debug,O=DevMail,C=RU"
```

## Структура

```
app/AndroidManifest.xml   манифест приложения
app/res/                  ресурсы (layout, drawable, values)
app/src/com/dsh/mail/     исходники
libs/                     JavaMail для Android (android-mail, android-activation)
tools/                    add_dex.py, add_res.py, align.py — сборка APK на Python
build.sh                  сборка и подпись
CHANGELOG.md              история версий
```

Приложение не использует AndroidX и Kotlin: только платформа Android и JavaMail.

## Лицензия

MIT, см. [LICENSE](LICENSE).
