package com.dsh.mail;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Локализация интерфейса: русский (по умолчанию) и английский.
 *
 * Тексты в коде оборачиваются в L.t(...), тексты из разметки переводятся
 * обходом дерева View сразу после setContentView (L.localize).
 */
public final class L {

    public static final String PREF = "ui";
    public static final String KEY_LANG = "lang";
    public static final String RU = "ru";
    public static final String EN = "en";

    private static volatile String lang = RU;
    private static final Map<String, String> DICT = new HashMap<String, String>();

    private L() {
    }

    static {
        DICT.put("Новые сообщения", "New messages");
        DICT.put("Поиск по чатам и письмам", "Search chats and messages");
        DICT.put("Закрепить", "Pin");
        DICT.put("Открепить", "Unpin");
        DICT.put("Без звука", "Mute");
        DICT.put("Включить звук", "Unmute");
        DICT.put("Чат закреплён", "Chat pinned");
        DICT.put("Чат откреплён", "Chat unpinned");
        DICT.put("Уведомления выключены", "Notifications muted");
        DICT.put("Уведомления включены", "Notifications unmuted");
        DICT.put("Экспорт переписки", "Export conversation");
        DICT.put("Ответить", "Reply");
        DICT.put("Прочитано", "Mark read");
        DICT.put("Ответ из шторки", "Reply from notification");
        DICT.put("переписка с ", "conversation with ");
        DICT.put("Выгружено: ", "Exported: ");
        DICT.put("Тема: ", "Subject: ");
        DICT.put("Вложения: ", "Attachments: ");
        DICT.put("Переписка сохранена", "Conversation saved");
        DICT.put("Черновик: ", "Draft: ");
        DICT.put("Выбрано: ", "Selected: ");
        DICT.put("Отмечено прочитанным: ", "Marked read: ");
        DICT.put("Удалить выбранные чаты?", "Delete selected chats?");
        DICT.put("Будет удалено только на этом телефоне: ", "Will be deleted only on this phone: ");
        DICT.put(". На сервере письма остаются.", ". Messages stay on the server.");
        DICT.put("Перейти к дате", "Jump to date");
        DICT.put("Нет сообщений после этой даты", "No messages after this date");
        DICT.put("Поиск в переписке", "Search in conversation");
        DICT.put("Не удалось сохранить: ", "Could not save: ");
        DICT.put("Нет приложения для сохранения файла", "No app to save the file");
        DICT.put("Нечего искать", "Nothing to search");
        DICT.put("Входящие", "Inbox");
        DICT.put("Ничего не найдено", "Nothing found");
        DICT.put("Попробуйте другой запрос.", "Try another query.");
        DICT.put("аккаунт не найден", "account not found");
        DICT.put("не известен адрес собеседника", "recipient address is unknown");
        DICT.put("опрос", "polling");
        DICT.put("Я", "Me");
        DICT.put("ошибка: ", "error: ");
        DICT.put(" · аккаунтов: ", " · accounts: ");
        DICT.put(" Б", " B");
        DICT.put(" КБ", " KB");
        DICT.put(" На сервере письма остаются; новые письма снова появятся в списке.", " Messages stay on the server; new mail will show up again.");
        DICT.put(" мин", " min");
        DICT.put(" писал(а):\n", " wrote:\n");
        DICT.put("%.1f МБ", "%.1f MB");
        DICT.put("(пустое сообщение)", "(empty message)");
        DICT.put("(сервер не отдал список папок)", "(server returned no folder list)");
        DICT.put(", не разобрано ", ", unparsed ");
        DICT.put(", новых ", ", new ");
        DICT.put(", отправленные ", ", sent ");
        DICT.put(", ошибок ", ", errors ");
        DICT.put(", папок: ", ", folders: ");
        DICT.put(", писем ", ", messages ");
        DICT.put(", писем: ", ", messages: ");
        DICT.put(", читаю ", ", reading ");
        DICT.put("--- причина ---", "--- reason ---");
        DICT.put(": новых ", ": new ");
        DICT.put(": писем ", ": messages ");
        DICT.put("IMAP — ВХОДЯЩИЕ", "IMAP — INCOMING");
        DICT.put("SMTP — ИСХОДЯЩИЕ", "SMTP — OUTGOING");
        DICT.put("\n  ошибка: ", "\n  error: ");
        DICT.put("\n\n---------- Пересланное сообщение ----------\n", "\n\n---------- Forwarded message ----------\n");
        DICT.put("» будет удалена только на этом телефоне.", "” will be deleted on this phone only.");
        DICT.put("АККАУНТЫ", "ACCOUNTS");
        DICT.put("Адрес почты", "Email address");
        DICT.put("Без темы", "No subject");
        DICT.put("ВХОД", "SIGN IN");
        DICT.put("Ваше сообщение", "Your message");
        DICT.put("Включите, если при проверке появляется ошибка вида «Trust anchor for certification path not found», «PKIX path building failed» или «certificate has expired». Соединение при этом остаётся зашифрованным, но приложение не проверяет, кому выдан сертификат.", "Turn on if the connection test fails with «Trust anchor for certification path not found», «PKIX path building failed» or «certificate has expired». The connection stays encrypted, but the app does not verify who the certificate was issued to.");
        DICT.put("Вложение: ", "Attachment: ");
        DICT.put("Вложения", "Attachments");
        DICT.put("Вручную — только по кнопке, но открытый чат всё равно обновляется сам. Мгновенно — постоянное соединение и тихое уведомление в фоне. Каждые N минут — автопроверка; меньше 15 минут Android разрешает только через постоянное соединение.", "Manual — only on tap, but an open chat still refreshes by itself. Instant — persistent connection and a quiet background notification. Every N minutes — scheduled check; Android allows less than 15 minutes only with a persistent connection.");
        DICT.put("Входящие: ", "Inbox: ");
        DICT.put("Входящие: не найдены\n", "Inbox: not found\n");
        DICT.put("Вчера", "Yesterday");
        DICT.put("Вы", "You");
        DICT.put("Вы: ", "You: ");
        DICT.put("Где: ", "Where: ");
        DICT.put("Готовим фон…", "Preparing background…");
        DICT.put("День", "Day");
        DICT.put("Живое соединение", "Live connection");
        DICT.put("Журнал отладки", "Debug log");
        DICT.put("Уже выбран", "Already selected");
        DICT.put("Журнал пуст", "Log is empty");
        DICT.put("Загрузка вложения…", "Downloading attachment…");
        DICT.put("Загрузить", "Download");
        DICT.put("Автозагрузка сообщений", "Auto-download messages");
        DICT.put("Подробный журнал писем", "Detailed mail log");
        DICT.put("вкл", "on");
        DICT.put("выкл", "off");
        DICT.put("Журнал писем: подробно", "Mail log: detailed");
        DICT.put("Журнал писем: кратко", "Mail log: brief");
        DICT.put("Обслуживание", "Maintenance");
        DICT.put("ЖУРНАЛ", "JOURNAL");
        DICT.put("ПАМЯТЬ", "MEMORY");
        DICT.put("ТЕМА", "THEME");
        DICT.put("ЯЗЫК", "LANGUAGE");
        DICT.put("ПОЧТА", "MAIL");
        DICT.put("Открыть журнал отладки", "Open debug log");
        DICT.put("Очистить журнал", "Clear log");
        DICT.put("Очистить журнал?", "Clear the log?");
        DICT.put("Все записи журнала будут удалены. Письма и аккаунты не затрагиваются.",
                "All log entries will be deleted. Messages and accounts are not affected.");
        DICT.put("Журнал очищен", "Log cleared");
        DICT.put("Не удалось очистить журнал", "Could not clear the log");
        DICT.put("Писем с нераспознанным адресом: ", "Messages with unrecognized address: ");
        DICT.put(". Они собраны в ветки «Неизвестный отправитель» и «Скрытые получатели». Подробности — в новом журнале писем.",
                ". They are grouped into “Unknown sender” and “Hidden recipients” threads. See the new mail log for details.");
        DICT.put("Все", "All");
        DICT.put("Все аккаунты", "All accounts");
        DICT.put("Аккаунт", "Account");
        DICT.put("Мои аккаунты", "My accounts");
        DICT.put("Поделиться в DevMail", "Share with DevMail");
        DICT.put("КОМУ ОТПРАВИТЬ", "SEND TO");
        DICT.put("Новый адрес…", "New address…");
        DICT.put("Поиск контакта", "Search contact");
        DICT.put("Контактов пока нет — используйте «Новый адрес…»",
                "No contacts yet — use “New address…”");
        DICT.put("Нечего отправить", "Nothing to send");
        DICT.put("Файл: ", "File: ");
        DICT.put("Файлов: ", "Files: ");
        DICT.put("Неизвестный отправитель", "Unknown sender");
        DICT.put("Скрытые получатели", "Hidden recipients");
        DICT.put("Без имени", "No name");
        DICT.put("Отправитель не распознан", "Sender not recognized");
        DICT.put("Письмо", "Message");
        DICT.put("Письмо перечитано", "Message re-read");
        DICT.put("Пропущено письмо", "Message skipped");
        DICT.put("Удалено пустых писем без заголовков: ", "Removed empty messages without headers: ");
        DICT.put("пустые заголовки и текст", "empty headers and body");
        DICT.put("не удалось прочитать", "could not read it");
        DICT.put("  (raw не сохранён)", "  (raw not saved)");
        DICT.put(", исправлено ", ", repaired ");
        DICT.put("Нет адреса получателя", "No recipient address");
        DICT.put("Вложение: ", "Attachment: ");
        DICT.put("У этого письма нет адреса отправителя — ответить нельзя. Подробности в журнале отладки.",
                "This message has no sender address — you can't reply. See the debug log for details.");
        DICT.put("Готовим файл…", "Preparing file…");
        DICT.put("Аккаунтов пока нет.\nДобавьте ящик, чтобы получать почту.",
                "No accounts yet.\nAdd a mailbox to receive mail.");
        DICT.put("Очистить кеш вложений", "Clear attachment cache");
        DICT.put("Удалить все скачанные вложения из писем?", "Delete all downloaded attachments?");
        DICT.put("Кеш уже пуст", "Cache is already empty");
        DICT.put("Письма и переписка останутся на месте — вложения можно будет скачать заново.",
                "Mail and conversations stay in place — attachments can be downloaded again.");
        DICT.put("Очистить", "Clear");
        DICT.put("Освобождено", "Freed");
        DICT.put("Двойной тап или щипок — масштаб", "Double tap or pinch to zoom");
        DICT.put("Нажмите на фото, чтобы сменить", "Tap the photo to change it");
        DICT.put("Написать", "Message");
        DICT.put("Копировать адрес", "Copy address");
        DICT.put("Удалить фото", "Remove photo");
        DICT.put("Фото удалено", "Photo removed");
        DICT.put("Аватарка обновлена", "Avatar updated");
        DICT.put("Не удалось сохранить фото", "Could not save the photo");
        DICT.put("Не удалось открыть галерею", "Could not open the gallery");
        DICT.put("Адрес скопирован", "Address copied");
        DICT.put("Аккаунт", "Account");
        DICT.put("В этом аккаунте пока нет писем", "No messages in this account yet");
        DICT.put("До 160 КБ, изображения плохого качества", "Up to 160 KB, low quality images");
        DICT.put("До 640 КБ, изображения среднего качества", "Up to 640 KB, medium quality images");
        DICT.put("До 5 МБ", "Up to 5 MB");
        DICT.put("До 25 МБ", "Up to 25 MB");
        DICT.put("Вложение сохранено", "Attachment saved");
        DICT.put("Закрыть", "Close");
        DICT.put("Запись журнала", "Log entry");
        DICT.put("Запуск", "Launch");
        DICT.put("Изображение", "Image");
        DICT.put("Итог", "Result");
        DICT.put("Каждые ", "Every ");
        DICT.put("Каждые 15 минут", "Every 15 minutes");
        DICT.put("Каждые 30 минут", "Every 30 minutes");
        DICT.put("Каждые 5 минут", "Every 5 minutes");
        DICT.put("Каждые 60 минут", "Every 60 minutes");
        DICT.put("Киберпанк", "Cyberpunk");
        DICT.put("Класс: ", "Class: ");
        DICT.put("Кому: адрес", "To: address");
        DICT.put("Копировать", "Copy");
        DICT.put("Копировать текст", "Copy text");
        DICT.put("Логин (обычно адрес)", "Login (usually the address)");
        DICT.put("Мгновенно (постоянное соединение)", "Instant (persistent connection)");
        DICT.put("Меняется только на этом телефоне, письма и адрес не затрагиваются.", "Changes on this phone only; messages and the address are untouched.");
        DICT.put("Нажмите, чтобы выбрать", "Tap to choose");
        DICT.put("Название (например, Работа)", "Name (e.g. Work)");
        DICT.put("Название чата", "Chat name");
        DICT.put("Не удалось открыть выбор файла", "Could not open the file picker");
        DICT.put("Не удалось открыть: ", "Could not open: ");
        DICT.put("Не удалось подключиться", "Connection failed");
        DICT.put("Не удалось поставить фон: ", "Could not set the background: ");
        DICT.put("Не удалось прочитать файл", "Could not read the file");
        DICT.put("Не удалось сохранить аккаунт", "Could not save the account");
        DICT.put("Нет аккаунта", "No account");
        DICT.put("Нет приложения галереи", "No gallery app found");
        DICT.put("Нет приложения для этого файла", "No app can open this file");
        DICT.put("Нет приложения камеры", "No camera app found");
        DICT.put("Новая почта", "New mail");
        DICT.put("Новое письмо", "New message");
        DICT.put("Новых писем: ", "New messages: ");
        DICT.put("Письма по чатам: имя собеседника и текст сообщения", "Chat notifications: contact name and message text");
        DICT.put("Журнал отладки включён", "Debug log enabled");
        DICT.put("Журнал отладки выключен", "Debug log disabled");
        DICT.put("Ночь", "Night");
        DICT.put("Ок", "OK");
        DICT.put("От: ", "From: ");
        DICT.put("Отключить", "Turn off");
        DICT.put("Открыть с помощью", "Open with");
        DICT.put("Отмена", "Cancel");
        DICT.put("Отметить непрочитанным", "Mark as unread");
        DICT.put("Отметить прочитанным", "Mark as read");
        DICT.put("Отправка…", "Sending…");
        DICT.put("Отправленные: ", "Sent: ");
        DICT.put("Отправлено", "Sent");
        DICT.put("Отчёт о падении", "Crash report");
        DICT.put("Очистить", "Clear");
        DICT.put("Очистить журнал", "Clear log");
        DICT.put("Очистить журнал?", "Clear the log?");
        DICT.put("Ошибка", "Error");
        DICT.put("Ошибка письма", "Message error");
        DICT.put("Ошибка подключения", "Connection error");
        DICT.put("Ошибка синхронизации", "Sync error");
        DICT.put("Ошибка: ", "Error: ");
        DICT.put("ПРОВЕРКА ПОЧТЫ", "CHECKING MAIL");
        DICT.put("Падение", "Crash");
        DICT.put("Папка", "Folder");
        DICT.put("Папки: ", "Folders: ");
        DICT.put("Пароль", "Password");
        DICT.put("Переименовать", "Rename");
        DICT.put("Переименовать чат", "Rename chat");
        DICT.put("Переписка с «", "Chat with “");
        DICT.put("Переслать", "Forward");
        DICT.put("Письма этого аккаунта тоже будут удалены.", "Messages of this account will be deleted too.");
        DICT.put("Письмо не отправлено", "Message not sent");
        DICT.put("Подключение работает ✓", "Connection works ✓");
        DICT.put("Помечено непрочитанным", "Marked as unread");
        DICT.put("Почта проверяется в реальном времени", "Mail is checked in real time");
        DICT.put("Прикрепить", "Attach");
        DICT.put("Принимать все сертификаты", "Accept all certificates");
        DICT.put("Принимать сертификаты и повторить", "Accept certificates and retry");
        DICT.put("Проверить соединение", "Test connection");
        DICT.put("Проверка ок", "Test OK");
        DICT.put("Проверка почты", "Mail check");
        DICT.put("Проверяем соединение…", "Testing connection…");
        DICT.put("СЕРТИФИКАТ СЕРВЕРА", "SERVER CERTIFICATE");
        DICT.put("Сбой синхронизации", "Sync failure");
        DICT.put("Сбросить", "Reset");
        DICT.put("Сегодня", "Today");
        DICT.put("Синхронизация…", "Syncing…");
        DICT.put("Скопировано", "Copied");
        DICT.put("Скопировать", "Copy");
        DICT.put("Сначала добавьте аккаунт", "Add an account first");
        DICT.put("Соединение", "Connection");
        DICT.put("Сообщение: ", "Message: ");
        DICT.put("Сохранено", "Saved");
        DICT.put("Сохранить", "Save");
        DICT.put("Старт", "Start");
        DICT.put("ТЕМА", "THEME");
        DICT.put("Текст письма", "Message text");
        DICT.put("Тема", "Subject");
        DICT.put("Только вручную", "Manual only");
        DICT.put("Убрать фон", "Remove background");
        DICT.put("Удалить", "Delete");
        DICT.put("Удалить (локально)", "Delete (locally)");
        DICT.put("Удалить аккаунт", "Delete account");
        DICT.put("Удалить аккаунт?", "Delete account?");
        DICT.put("Удалить все записи отладки. Письма в почте не затрагиваются.", "Delete all debug entries. Mail is not affected.");
        DICT.put("Удалить сообщение?", "Delete message?");
        DICT.put("Удалить чат локально", "Delete chat locally");
        DICT.put("Удалить чат?", "Delete chat?");
        DICT.put("Удаляется только на этом телефоне, на сервере письмо остаётся.", "Deleted on this phone only; the message stays on the server.");
        DICT.put("Укажите IMAP-сервер", "Enter the IMAP server");
        DICT.put("Укажите SMTP-сервер", "Enter the SMTP server");
        DICT.put("Укажите адрес получателя", "Enter the recipient address");
        DICT.put("Укажите адрес почты", "Enter the email address");
        DICT.put("Укажите логин", "Enter the login");
        DICT.put("Укажите пароль", "Enter the password");
        DICT.put("Фон убран", "Background removed");
        DICT.put("Фон установлен", "Background set");
        DICT.put("Фон чата", "Chat background");
        DICT.put("Фон чата из галереи", "Chat background from gallery");
        DICT.put("Цитата", "Quote");
        DICT.put("Чат удалён", "Chat deleted");
        DICT.put("ЯЩИК", "MAILBOX");
        DICT.put("вложение", "attachment");
        DICT.put("вложение не найдено в письме", "attachment not found in the message");
        DICT.put("вручную", "manual");
        DICT.put("входящие", "inbox");
        DICT.put("входящие (", "inbox (");
        DICT.put("вчера", "yesterday");
        DICT.put("исходящ", "outgoing");
        DICT.put("исходящие", "sent");
        DICT.put("каждые ", "every ");
        DICT.put("мгновенно", "instant");
        DICT.put("не задан IMAP-сервер", "IMAP server not set");
        DICT.put("не задан SMTP-сервер", "SMTP server not set");
        DICT.put("не нашёл папку входящих. Папки на сервере: ", "inbox folder not found. Folders on the server: ");
        DICT.put("не удалось получить папки: ", "could not get folders: ");
        DICT.put("не удалось прочитать изображение", "could not read the image");
        DICT.put("неверный логин или пароль", "invalid login or password");
        DICT.put("отправлен", "sent");
        DICT.put("отправленные", "sent");
        DICT.put("отправлено", "sent");
        DICT.put("ошибки синхронизации и подключения", "sync and connection errors");
        DICT.put("папка не найдена", "folder not found");
        DICT.put("письмо не сохранено целиком", "message not saved completely");
        DICT.put("синхронизация", "sync");
        DICT.put("черновик", "draft");
        DICT.put("это не картинка", "this is not an image");
        DICT.put("📄  Файл", "📄  File");
        DICT.put("📎 Прикрепить", "📎 Attach");
        DICT.put("📷  Снимок с камеры", "📷  Camera photo");
        DICT.put("🖼  Изображение из галереи", "🖼  Image from gallery");
        DICT.put("Чаты", "Chats");
        DICT.put("Настройки", "Settings");
        DICT.put("Пока пусто", "Nothing here yet");
        DICT.put("Добавьте аккаунт в настройках и обновите.", "Add an account in Settings and refresh.");
        DICT.put("+ Добавить аккаунт", "+ Add account");
        DICT.put("Сообщение", "Message");
        DICT.put("Аккаунт", "Account");
        DICT.put("ЯЗЫК", "LANGUAGE");
    }

    public static void init(Context c) {
        try {
            lang = c.getSharedPreferences(PREF, 0).getString(KEY_LANG, RU);
        } catch (Throwable t) {
            lang = RU;
        }
        applyLocale();
    }

    public static String lang() {
        return lang;
    }

    public static boolean isEn() {
        return EN.equals(lang);
    }

    public static void setLang(Context c, String code) {
        lang = EN.equals(code) ? EN : RU;
        try {
            SharedPreferences p = c.getSharedPreferences(PREF, 0);
            p.edit().putString(KEY_LANG, lang).apply();
        } catch (Throwable ignored) {
        }
        applyLocale();
    }

    /** Системные даты и форматы — на языке интерфейса. */
    public static void applyLocale() {
        try {
            Locale.setDefault(isEn() ? Locale.US : new Locale("ru", "RU"));
        } catch (Throwable ignored) {
        }
    }

    /** Перевод строки на выбранный язык. */
    public static String t(String ru) {
        if (ru == null) return null;
        if (!isEn()) return ru;
        String v = DICT.get(ru);
        return v == null ? ru : v;
    }

    /** Перевод подписей, заданных в разметке (text/hint). */
    public static void localize(View root) {
        if (root == null) return;
        try {
            if (root instanceof EditText) {
                CharSequence h = ((EditText) root).getHint();
                if (h != null) {
                    String tr = t(h.toString());
                    if (!tr.equals(h.toString())) ((EditText) root).setHint(tr);
                }
            }
            if (root instanceof TextView) {
                CharSequence tx = ((TextView) root).getText();
                if (tx != null) {
                    String tr = t(tx.toString());
                    if (!tr.equals(tx.toString())) ((TextView) root).setText(tr);
                }
            }
            if (root instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) root;
                for (int i = 0; i < g.getChildCount(); i++) localize(g.getChildAt(i));
            }
        } catch (Throwable ignored) {
        }
    }
}
