package com.dsh.mail;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Db extends SQLiteOpenHelper {

    private static final String NAME = "mail.db";
    private static final int VERSION = 11;

    private final Context appCtx;

    public Db(Context c) {
        super(c, NAME, null, VERSION);
        appCtx = c.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, label TEXT, email TEXT,"
                + " imap_host TEXT, imap_port INTEGER, imap_ssl INTEGER, smtp_host TEXT, smtp_port INTEGER,"
                + " smtp_ssl INTEGER, username TEXT, password TEXT, check_min INTEGER DEFAULT 15,"
                + " last_sync INTEGER DEFAULT 0, trust_all INTEGER DEFAULT 0)");
        d.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, account_id INTEGER,"
                + " folder TEXT, uid INTEGER, thread_key TEXT, peer_name TEXT, peer_email TEXT,"
                + " subject TEXT, date INTEGER, snippet TEXT, body TEXT, seen INTEGER DEFAULT 0,"
                + " outgoing INTEGER DEFAULT 0, raw_path TEXT, has_attach INTEGER DEFAULT 0, msgid TEXT,"
                + " quote_author TEXT, quote_text TEXT, state INTEGER DEFAULT 0, html TEXT)");
        d.execSQL("CREATE UNIQUE INDEX idx_msg ON messages(account_id, folder, uid)");
        d.execSQL("CREATE INDEX idx_thread ON messages(thread_key)");
        d.execSQL("CREATE UNIQUE INDEX idx_msgid ON messages(account_id, msgid)");
        d.execSQL("CREATE TABLE reads (thread_key TEXT PRIMARY KEY, ts INTEGER, last_id INTEGER DEFAULT 0)");
        d.execSQL("CREATE TABLE deleted_chats (thread_key TEXT PRIMARY KEY, ts INTEGER)");
        d.execSQL("CREATE TABLE chat_names (thread_key TEXT PRIMARY KEY, name TEXT, bg TEXT)");
        d.execSQL("CREATE TABLE chat_flags (thread_key TEXT PRIMARY KEY, pinned INTEGER DEFAULT 0, muted INTEGER DEFAULT 0)");
        d.execSQL("CREATE TABLE attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, message_id INTEGER,"
                + " part INTEGER, name TEXT, mime TEXT, size INTEGER, path TEXT,"
                + " state INTEGER DEFAULT 0, width INTEGER DEFAULT 0, height INTEGER DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int oldV, int newV) {
        if (oldV < 10) {
            // Групповые адреса вроде "hidden-recipients": ; попадали в peer_email
            // и превращались в отдельный чат с пустым именем. Такие письма
            // собираем в общую ветку «без собеседника».
            int bad = 0;
            try {
                Cursor c = d.rawQuery("SELECT COUNT(*) FROM messages WHERE thread_key<>'debug'"
                        + " AND (peer_email IS NULL OR peer_email NOT LIKE '%@%')", null);
                if (c.moveToFirst()) bad = c.getInt(0);
                c.close();
            } catch (Exception ignored) {
            }
            try {
                d.execSQL("UPDATE messages SET peer_email='', peer_name='',"
                        + " thread_key = account_id || (CASE WHEN outgoing=1 THEN '|?out' ELSE '|?in' END)"
                        + " WHERE thread_key<>'debug' AND (peer_email IS NULL OR peer_email NOT LIKE '%@%')");
            } catch (Exception ignored) {
            }
            try {
                // Письма, где текст был пуст, а вложение есть, показываем именем файла.
                d.execSQL("UPDATE messages SET"
                        + " body = COALESCE((SELECT '📄 ' || a.name FROM attachments a"
                        + "   WHERE a.message_id=messages.id AND a.name IS NOT NULL AND a.name<>''"
                        + "   ORDER BY a.id LIMIT 1), body),"
                        + " snippet = COALESCE((SELECT '📄 ' || a.name FROM attachments a"
                        + "   WHERE a.message_id=messages.id AND a.name IS NOT NULL AND a.name<>''"
                        + "   ORDER BY a.id LIMIT 1), snippet)"
                        + " WHERE has_attach=1 AND (body IS NULL OR body=''"
                        + "   OR body LIKE '%пустое сообщение%'"
                        + "   OR body LIKE '%(empty message)%')");
            } catch (Exception ignored) {
            }
            if (bad > 0) {
                try {
                    appCtx.getSharedPreferences(L.PREF, Context.MODE_PRIVATE)
                            .edit().putInt("bad_peers", bad).apply();
                } catch (Exception ignored) {
                }
            }
        }
        if (oldV < 11) {
            // Совсем пустые письма без отправителя (сбой загрузки: ни заголовков,
            // ни текста, ни вложений) — это мусор, из-за него появлялись чаты
            // с пустым именем. Реальные письма не удаляем.
            int junk = 0;
            try {
                Cursor c = d.rawQuery("SELECT COUNT(*) FROM messages WHERE thread_key<>'debug'"
                        + " AND (peer_email IS NULL OR peer_email='')"
                        + " AND (subject IS NULL OR subject='')"
                        + " AND (body IS NULL OR body='' OR body LIKE '%пустое сообщение%'"
                        + "      OR body LIKE '%(empty message)%')"
                        + " AND has_attach=0 AND outgoing=0", null);
                if (c.moveToFirst()) junk = c.getInt(0);
                c.close();
            } catch (Exception ignored) {
            }
            try {
                d.execSQL("DELETE FROM messages WHERE thread_key<>'debug'"
                        + " AND (peer_email IS NULL OR peer_email='')"
                        + " AND (subject IS NULL OR subject='')"
                        + " AND (body IS NULL OR body='' OR body LIKE '%пустое сообщение%'"
                        + "      OR body LIKE '%(empty message)%')"
                        + " AND has_attach=0 AND outgoing=0");
                d.execSQL("DELETE FROM attachments WHERE message_id NOT IN (SELECT id FROM messages)");
            } catch (Exception ignored) {
            }
            if (junk > 0) {
                try {
                    appCtx.getSharedPreferences(L.PREF, Context.MODE_PRIVATE)
                            .edit().putInt("empty_msgs", junk).apply();
                } catch (Exception ignored) {
                }
            }
        }
        if (oldV < 6) {
            try {
                d.execSQL("CREATE TABLE IF NOT EXISTS chat_names (thread_key TEXT PRIMARY KEY, name TEXT, bg TEXT)");
            } catch (Exception ignored) {
            }
        }
        if (oldV < 5) {
            try {
                d.execSQL("ALTER TABLE messages ADD COLUMN html TEXT");
            } catch (Exception ignored) {
            }
        }
        if (oldV < 4) {
            try {
                d.execSQL("CREATE TABLE IF NOT EXISTS reads (thread_key TEXT PRIMARY KEY, ts INTEGER)");
                d.execSQL("CREATE TABLE IF NOT EXISTS deleted_chats (thread_key TEXT PRIMARY KEY, ts INTEGER)");
                d.execSQL("ALTER TABLE messages ADD COLUMN state INTEGER DEFAULT 0");
            } catch (Exception ignored) {
            }
            try {
                d.execSQL("INSERT OR REPLACE INTO reads(thread_key, ts)"
                        + " SELECT thread_key, MAX(date) FROM messages GROUP BY thread_key");
            } catch (Exception ignored) {
            }
        }
        if (oldV < 3) {
            try {
                d.execSQL("ALTER TABLE accounts ADD COLUMN trust_all INTEGER DEFAULT 0");
            } catch (Exception ignored) {
            }
        }
        if (oldV < 2) {
            try {
                d.execSQL("ALTER TABLE messages ADD COLUMN quote_author TEXT");
                d.execSQL("ALTER TABLE messages ADD COLUMN quote_text TEXT");
            } catch (Exception ignored) {
            }
        }
        if (oldV < 8) {
            try {
                d.execSQL("CREATE TABLE IF NOT EXISTS chat_flags (thread_key TEXT PRIMARY KEY,"
                        + " pinned INTEGER DEFAULT 0, muted INTEGER DEFAULT 0)");
            } catch (Exception ignored) {
            }
        }
        if (oldV < 9) {
            try {
                d.execSQL("ALTER TABLE attachments ADD COLUMN path TEXT");
                d.execSQL("ALTER TABLE attachments ADD COLUMN state INTEGER DEFAULT 0");
                d.execSQL("ALTER TABLE attachments ADD COLUMN width INTEGER DEFAULT 0");
                d.execSQL("ALTER TABLE attachments ADD COLUMN height INTEGER DEFAULT 0");
            } catch (Exception ignored) {
            }
        }
        if (oldV < 7) {
            // Счётчик «новых» теперь по id последнего просмотренного письма:
            // так он не зависит от чужого \Seen и от расхождения часов сервера.
            try {
                d.execSQL("ALTER TABLE reads ADD COLUMN last_id INTEGER DEFAULT 0");
            } catch (Exception ignored) {
            }
            try {
                d.execSQL("UPDATE reads SET last_id=COALESCE((SELECT MAX(m.id) FROM messages m"
                        + " WHERE m.thread_key=reads.thread_key AND m.date<=reads.ts),0)");
            } catch (Exception ignored) {
            }
        }
    }

    // ---------- accounts ----------

    public List<Account> accounts() {
        List<Account> out = new ArrayList<Account>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,label,email,imap_host,imap_port,imap_ssl,smtp_host,smtp_port,smtp_ssl,"
                        + "username,password,check_min,last_sync,trust_all FROM accounts ORDER BY id", null);
        try {
            while (c.moveToNext()) {
                Account a = new Account();
                a.id = c.getLong(0);
                a.label = c.getString(1);
                a.email = c.getString(2);
                a.imapHost = c.getString(3);
                a.imapPort = c.getInt(4);
                a.imapSsl = c.getInt(5) == 1;
                a.smtpHost = c.getString(6);
                a.smtpPort = c.getInt(7);
                a.smtpSsl = c.getInt(8) == 1;
                a.username = c.getString(9);
                a.password = Crypto.decrypt(c.getString(10));
                a.checkMin = c.getInt(11);
                a.lastSync = c.getLong(12);
                a.trustAll = c.getInt(13) == 1;
                out.add(a);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public Account account(long id) {
        for (Account a : accounts()) if (a.id == id) return a;
        return null;
    }

    public long saveAccount(Account a) {
        ContentValues v = new ContentValues();
        v.put("label", a.label);
        v.put("email", a.email);
        v.put("imap_host", a.imapHost);
        v.put("imap_port", a.imapPort);
        v.put("imap_ssl", a.imapSsl ? 1 : 0);
        v.put("smtp_host", a.smtpHost);
        v.put("smtp_port", a.smtpPort);
        v.put("smtp_ssl", a.smtpSsl ? 1 : 0);
        v.put("username", a.username);
        v.put("password", Crypto.encrypt(a.password));
        v.put("check_min", a.checkMin);
        v.put("trust_all", a.trustAll ? 1 : 0);
        if (a.id > 0) {
            getWritableDatabase().update("accounts", v, "id=?", new String[]{String.valueOf(a.id)});
            return a.id;
        }
        return getWritableDatabase().insert("accounts", null, v);
    }

    public void deleteAccount(long id) {
        getWritableDatabase().delete("messages", "account_id=?", new String[]{String.valueOf(id)});
        getWritableDatabase().delete("accounts", "id=?", new String[]{String.valueOf(id)});
    }

    public void touchSync(long accountId, long ts) {
        ContentValues v = new ContentValues();
        v.put("last_sync", ts);
        getWritableDatabase().update("accounts", v, "id=?", new String[]{String.valueOf(accountId)});
    }

    // ---------- messages ----------

    /** Максимальный серверный UID в папке — точка отсчёта живого соединения. */
    public long maxUid(long accountId, String folder) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT MAX(uid) FROM messages WHERE account_id=? AND folder=? AND uid>0",
                new String[]{String.valueOf(accountId), folder});
        try {
            if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } finally {
            c.close();
        }
        return 0;
    }

    public boolean hasMessage(long accountId, String folder, long uid) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM messages WHERE account_id=? AND folder=? AND uid=? LIMIT 1",
                new String[]{String.valueOf(accountId), folder, String.valueOf(uid)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    /** id письма по (аккаунт, папка, uid) или 0. */
    public long messageId(long accountId, String folder, long uid) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id FROM messages WHERE account_id=? AND folder=? AND uid=? LIMIT 1",
                new String[]{String.valueOf(accountId), folder, String.valueOf(uid)});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    /** У письма не распознан адрес отправителя — его надо перечитать с сервера. */
    public boolean peerMissing(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM messages WHERE id=? AND (peer_email IS NULL OR peer_email='') LIMIT 1",
                new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    /** Письмо входящее и без адреса отправителя — его стоит перечитать с сервера. */
    public boolean needsPeerRepair(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM messages WHERE id=? AND outgoing=0"
                        + " AND (peer_email IS NULL OR peer_email='') LIMIT 1",
                new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    /** Перечитывает письмо поверх старой записи, сохраняя её id и отметку «прочитано». */
    public boolean updateMessage(long id, MailMessage m) {
        ContentValues v = new ContentValues();
        v.put("thread_key", m.threadKey);
        v.put("peer_name", m.peerName);
        v.put("peer_email", m.peerEmail);
        v.put("subject", m.subject);
        v.put("date", m.date);
        v.put("snippet", m.snippet);
        v.put("body", m.body);
        v.put("seen", m.seen ? 1 : 0);
        v.put("outgoing", m.outgoing ? 1 : 0);
        v.put("raw_path", m.rawPath);
        v.put("has_attach", m.hasAttach ? 1 : 0);
        v.put("quote_author", m.quoteAuthor);
        v.put("quote_text", m.quoteText);
        v.put("state", m.state);
        v.put("html", m.html);
        if (m.msgid != null && m.msgid.length() > 0) v.put("msgid", m.msgid);
        try {
            return getWritableDatabase().update("messages", v, "id=?",
                    new String[]{String.valueOf(id)}) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Старые вложения перечитываемого письма: перед повторным разбором убираем. */
    public void deleteAttachmentsFor(long messageId) {
        try {
            getWritableDatabase().delete("attachments", "message_id=?",
                    new String[]{String.valueOf(messageId)});
        } catch (Exception ignored) {
        }
    }

    public long insertMessage(MailMessage m) {
        ContentValues v = new ContentValues();
        v.put("account_id", m.accountId);
        v.put("folder", m.folder);
        v.put("uid", m.uid);
        v.put("thread_key", m.threadKey);
        v.put("peer_name", m.peerName);
        v.put("peer_email", m.peerEmail);
        v.put("subject", m.subject);
        v.put("date", m.date);
        v.put("snippet", m.snippet);
        v.put("body", m.body);
        v.put("seen", m.seen ? 1 : 0);
        v.put("outgoing", m.outgoing ? 1 : 0);
        v.put("raw_path", m.rawPath);
        v.put("has_attach", m.hasAttach ? 1 : 0);
        v.put("quote_author", m.quoteAuthor);
        v.put("quote_text", m.quoteText);
        v.put("state", m.state);
        v.put("html", m.html);
        if (m.msgid != null && m.msgid.length() > 0) v.put("msgid", m.msgid);
        return getWritableDatabase().insertWithOnConflict("messages", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<MailMessage> thread(String key) {
        List<MailMessage> out = new ArrayList<MailMessage>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,account_id,folder,uid,thread_key,peer_name,peer_email,subject,date,snippet,"
                        + "body,seen,outgoing,raw_path,has_attach,quote_author,quote_text,state,html FROM messages WHERE thread_key=? ORDER BY date ASC",
                new String[]{key});
        try {
            while (c.moveToNext()) out.add(readMessage(c));
        } finally {
            c.close();
        }
        return out;
    }

    public MailMessage message(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,account_id,folder,uid,thread_key,peer_name,peer_email,subject,date,snippet,"
                        + "body,seen,outgoing,raw_path,has_attach,quote_author,quote_text,state,html FROM messages WHERE id=?",
                new String[]{String.valueOf(id)});
        try {
            if (c.moveToFirst()) return readMessage(c);
        } finally {
            c.close();
        }
        return null;
    }

    private MailMessage readMessage(Cursor c) {
        MailMessage m = new MailMessage();
        m.id = c.getLong(0);
        m.accountId = c.getLong(1);
        m.folder = c.getString(2);
        m.uid = c.getLong(3);
        m.threadKey = c.getString(4);
        m.peerName = c.getString(5);
        m.peerEmail = c.getString(6);
        m.subject = c.getString(7);
        m.date = c.getLong(8);
        m.snippet = c.getString(9);
        m.body = c.getString(10);
        m.seen = c.getInt(11) == 1;
        m.outgoing = c.getInt(12) == 1;
        m.rawPath = c.getString(13);
        m.hasAttach = c.getInt(14) == 1;
        if (m.peerName == null) m.peerName = "";
        if (m.peerEmail == null) m.peerEmail = "";
        if (m.subject == null) m.subject = "";
        if (m.snippet == null) m.snippet = "";
        if (m.body == null) m.body = "";
        m.quoteAuthor = c.getString(15);
        m.quoteText = c.getString(16);
        if (m.quoteAuthor == null) m.quoteAuthor = "";
        if (m.quoteText == null) m.quoteText = "";
        m.state = c.getInt(17);
        m.html = c.isNull(18) ? "" : c.getString(18);
        return m;
    }

    public static final String DEBUG_THREAD = "debug";

    private static long logUid;

    /** Пишет строку в «Журнал отладки» (отдельный чат в списке). */
    public long log(String title, String text) {
        try {
            String body = text == null ? "" : text;
            String head = title == null ? "" : title.trim();
            if (head.length() > 0) body = body.length() == 0 ? head : head + ": " + body;
            MailMessage m = new MailMessage();
            m.accountId = 0;
            m.folder = "DEBUG";
            m.uid = System.currentTimeMillis() * 1000 + (logUid++ % 1000);
            m.threadKey = DEBUG_THREAD;
            m.peerName = L.t("Журнал отладки");
            m.peerEmail = "debug";
            m.subject = head;
            m.date = System.currentTimeMillis();
            m.body = body;
            String snip = body.replace('\n', ' ').replaceAll("\\s+", " ").trim();
            if (snip.length() > 120) snip = snip.substring(0, 120) + "…";
            m.snippet = snip;
            m.seen = true;
            m.outgoing = false;
            return insertMessage(m);
        } catch (Throwable t) {
            return -1;
        }
    }

    public void clearDebug() {
        getWritableDatabase().delete("messages", "thread_key=?", new String[]{DEBUG_THREAD});
    }

    private static final String CHAT_SELECT = "SELECT m.thread_key, m.peer_name, m.peer_email,"
            + " m.snippet, m.date, m.account_id, m.outgoing,"
            + " (SELECT COUNT(*) FROM messages x WHERE x.thread_key=m.thread_key AND x.outgoing=0"
            + " AND m.thread_key<>'debug'"
            + " AND x.id > COALESCE((SELECT r.last_id FROM reads r WHERE r.thread_key=m.thread_key),0)),"
            + " (SELECT email FROM accounts a WHERE a.id=m.account_id),"
            + " (SELECT x.snippet FROM messages x WHERE x.thread_key=m.thread_key AND x.outgoing=0"
            + " ORDER BY x.id DESC LIMIT 1)"
            + " FROM messages m"
            + " WHERE m.date = (SELECT MAX(y.date) FROM messages y WHERE y.thread_key=m.thread_key)"
            + " AND m.thread_key NOT IN (SELECT dc.thread_key FROM deleted_chats dc WHERE dc.ts >= m.date)";

    public List<ChatItem> chats() {
        return chatRows(null, null, 300);
    }

    /** Чаты только одного аккаунта (фильтр в SQL, чтобы лимит не съедал историю). */
    public List<ChatItem> chatsFor(long accountId) {
        return chatRows("m.account_id=?", new String[]{String.valueOf(accountId)}, 300);
    }

    private List<ChatItem> chatRows(String cond, String[] args, int limit) {
        List<ChatItem> out = new ArrayList<ChatItem>();
        String sql = CHAT_SELECT + (cond == null || cond.length() == 0 ? "" : " AND " + cond)
                + " GROUP BY m.thread_key ORDER BY (m.thread_key='debug') DESC, m.date DESC LIMIT " + limit;
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        try {
            while (c.moveToNext()) {
                ChatItem it = new ChatItem();
                it.threadKey = c.getString(0);
                it.name = c.getString(1);
                it.email = c.getString(2);
                it.snippet = c.getString(3);
                it.date = c.getLong(4);
                it.accountId = c.getLong(5);
                it.outgoing = c.getInt(6) == 1;
                it.unread = c.getInt(7);
                it.accountEmail = c.getString(8);
                it.inSnippet = c.getString(9);
                if (it.name == null || it.name.trim().length() == 0) it.name = it.email == null ? "" : it.email;
                out.add(it);
            }
        } finally {
            c.close();
        }
        Map<String, String> aliases = chatNames();
        Map<String, int[]> flags = chatFlags();
        for (ChatItem it : out) {
            String alias = aliases.get(it.threadKey);
            if (alias != null && alias.trim().length() > 0) it.name = alias;
            else if (DEBUG_THREAD.equals(it.threadKey)) it.name = L.t("Журнал отладки");
            int[] f = flags.get(it.threadKey);
            if (f != null) {
                it.pinned = f[0] == 1;
                it.muted = f[1] == 1;
            }
            if (it.name == null || it.name.trim().length() == 0) {
                it.name = fallbackName(it.threadKey);
            }
        }
        return out;
    }

    /** Имя ветки, у которой не осталось ни адреса, ни имени собеседника. */
    public static String fallbackName(String threadKey) {
        if (threadKey == null) return L.t("Без имени");
        if (threadKey.endsWith("|?in")) return L.t("Неизвестный отправитель");
        if (threadKey.endsWith("|?out")) return L.t("Скрытые получатели");
        return L.t("Без имени");
    }

    /** Поиск по имени, адресу, теме, сниппету и тексту письма (без учёта регистра). */
    public List<ChatItem> searchThreads(String q, int limit) {
        String s = q == null ? "" : q.trim();
        if (s.length() == 0) return new ArrayList<ChatItem>();
        final String needle = s.toLowerCase(Locale.ROOT);
        List<String> keys = new ArrayList<String>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT thread_key, peer_name, peer_email, subject, snippet, body FROM messages"
                        + " ORDER BY date DESC LIMIT 1500", null);
        try {
            while (c.moveToNext() && keys.size() < 40) {
                String k = c.getString(0);
                if (k == null || keys.contains(k)) continue;
                for (int i = 1; i <= 5; i++) {
                    String v = c.isNull(i) ? null : c.getString(i);
                    if (v != null && v.toLowerCase(Locale.ROOT).contains(needle)) {
                        keys.add(k);
                        break;
                    }
                }
            }
        } finally {
            c.close();
        }
        if (keys.isEmpty()) return new ArrayList<ChatItem>();
        StringBuilder cond = new StringBuilder("m.thread_key IN (");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) cond.append(',');
            cond.append('?');
        }
        cond.append(')');
        return chatRows(cond.toString(), keys.toArray(new String[0]), limit);
    }

    /** Закреплённые и заглушённые чаты: ключ -> {pinned, muted}. */
    public Map<String, int[]> chatFlags() {
        Map<String, int[]> out = new HashMap<String, int[]>();
        Cursor c = getReadableDatabase().rawQuery("SELECT thread_key, pinned, muted FROM chat_flags", null);
        try {
            while (c.moveToNext()) {
                out.put(c.getString(0), new int[]{c.getInt(1), c.getInt(2)});
            }
        } finally {
            c.close();
        }
        return out;
    }

    private boolean flag(String key, String col) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + col + " FROM chat_flags WHERE thread_key=?", new String[]{key});
        try {
            if (c.moveToFirst()) return c.getInt(0) == 1;
        } finally {
            c.close();
        }
        return false;
    }

    public boolean isPinned(String key) {
        return flag(key, "pinned");
    }

    public boolean isMuted(String key) {
        return flag(key, "muted");
    }

    public void setPinned(String key, boolean on) {
        getWritableDatabase().execSQL("INSERT OR IGNORE INTO chat_flags(thread_key, pinned, muted) VALUES(?,0,0)",
                new Object[]{key});
        getWritableDatabase().execSQL("UPDATE chat_flags SET pinned=? WHERE thread_key=?",
                new Object[]{on ? 1 : 0, key});
    }

    public void setMuted(String key, boolean on) {
        getWritableDatabase().execSQL("INSERT OR IGNORE INTO chat_flags(thread_key, pinned, muted) VALUES(?,0,0)",
                new Object[]{key});
        getWritableDatabase().execSQL("UPDATE chat_flags SET muted=? WHERE thread_key=?",
                new Object[]{on ? 1 : 0, key});
    }

    /** id последнего просмотренного письма в чате (для разделителя «Новые сообщения»). */
    public long lastSeenId(String key) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT last_id FROM reads WHERE thread_key=?", new String[]{key});
        try {
            if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } finally {
            c.close();
        }
        return 0;
    }

    public Map<String, String> chatNames() {
        Map<String, String> map = new HashMap<String, String>();
        try {
            Cursor c = getReadableDatabase().rawQuery("SELECT thread_key, name FROM chat_names"
                    + " WHERE name IS NOT NULL AND name<>''", null);
            try {
                while (c.moveToNext()) map.put(c.getString(0), c.getString(1));
            } finally {
                c.close();
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    public String chatName(String key) {
        return chatNames().get(key);
    }

    /** Новое название чата; null или пустая строка — вернуть исходное. */
    public void setChatName(String key, String name) {
        String bg = chatBg(key);
        String clean = name == null ? "" : name.trim();
        if (clean.length() == 0) {
            if (bg == null) {
                getWritableDatabase().delete("chat_names", "thread_key=?", new String[]{key});
            } else {
                ContentValues v = new ContentValues();
                v.put("thread_key", key);
                v.putNull("name");
                v.put("bg", bg);
                getWritableDatabase().insertWithOnConflict("chat_names", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            return;
        }
        ContentValues v = new ContentValues();
        v.put("thread_key", key);
        v.put("name", clean);
        if (bg == null) v.putNull("bg"); else v.put("bg", bg);
        getWritableDatabase().insertWithOnConflict("chat_names", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String chatBg(String key) {
        try {
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT bg FROM chat_names WHERE thread_key=?", new String[]{key});
            try {
                if (c.moveToFirst() && !c.isNull(0)) {
                    String v = c.getString(0);
                    return v == null || v.trim().length() == 0 ? null : v;
                }
            } finally {
                c.close();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public void setChatBg(String key, String path) {
        String name = chatName(key);
        ContentValues v = new ContentValues();
        v.put("thread_key", key);
        if (name == null) v.putNull("name"); else v.put("name", name);
        if (path == null || path.trim().length() == 0) v.putNull("bg"); else v.put("bg", path);
        getWritableDatabase().insertWithOnConflict("chat_names", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void deleteMessage(long id) {
        getWritableDatabase().delete("attachments", "message_id=?", new String[]{String.valueOf(id)});
        getWritableDatabase().delete("messages", "id=?", new String[]{String.valueOf(id)});
    }

    /** Локальное удаление переписки: с сервера ничего не удаляется, чат скрывается до нового письма. */
    public void deleteChat(String key) {
        SQLiteDatabase d = getWritableDatabase();
        try {
            Cursor c = d.rawQuery("SELECT raw_path FROM messages WHERE thread_key=?", new String[]{key});
            while (c.moveToNext()) {
                String rp = c.isNull(0) ? null : c.getString(0);
                if (rp != null) {
                    try {
                        new java.io.File(rp).delete();
                    } catch (Exception ignored) {
                    }
                }
            }
            c.close();
        } catch (Exception ignored) {
        }
        try {
            d.execSQL("DELETE FROM attachments WHERE message_id IN"
                    + " (SELECT id FROM messages WHERE thread_key=?)", new Object[]{key});
        } catch (Exception ignored) {
        }
        d.delete("messages", "thread_key=?", new String[]{key});
        d.delete("reads", "thread_key=?", new String[]{key});
        ContentValues v = new ContentValues();
        v.put("thread_key", key);
        v.put("ts", System.currentTimeMillis());
        d.insertWithOnConflict("deleted_chats", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Письмо пришло после локального удаления чата? Тогда его не сохраняем. */
    public boolean isThreadDeleted(String key, long date) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM deleted_chats WHERE thread_key=? AND ts>=? LIMIT 1",
                new String[]{key, String.valueOf(date)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public void updateMessageState(long id, int state) {
        ContentValues v = new ContentValues();
        v.put("state", state);
        getWritableDatabase().update("messages", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** Отправка завершилась: проставляем Message-ID и статус «отправлено». */
    public void finishSend(long id, String msgid, int state) {
        ContentValues v = new ContentValues();
        v.put("state", state);
        if (msgid != null && msgid.length() > 0) v.put("msgid", msgid);
        try {
            getWritableDatabase().update("messages", v, "id=?", new String[]{String.valueOf(id)});
        } catch (Exception ignored) {
        }
    }

    /** Письмо найдено на сервере в «Отправленных» — ставим двойную галочку. */
    public void markDelivered(String msgid) {
        if (msgid == null || msgid.length() == 0) return;
        ContentValues v = new ContentValues();
        v.put("state", MailMessage.ST_DELIVERED);
        getWritableDatabase().update("messages", v, "msgid=? AND outgoing=1 AND state>=?",
                new String[]{msgid, String.valueOf(MailMessage.ST_SENT)});
    }

    public void markUnread(long id) {
        ContentValues v = new ContentValues();
        v.put("seen", 0);
        getWritableDatabase().update("messages", v, "id=? AND outgoing=0", new String[]{String.valueOf(id)});
        MailMessage m = message(id);
        if (m != null) {
            ContentValues r = new ContentValues();
            r.put("thread_key", m.threadKey);
            r.put("ts", Math.max(0, m.date - 1));
            long lastBefore = 0;
            try {
                Cursor c = getReadableDatabase().rawQuery(
                        "SELECT MAX(id) FROM messages WHERE thread_key=? AND id<?",
                        new String[]{m.threadKey, String.valueOf(m.id)});
                if (c.moveToFirst() && !c.isNull(0)) lastBefore = c.getLong(0);
                c.close();
            } catch (Exception ignored) {
            }
            r.put("last_id", lastBefore);
            getWritableDatabase().insertWithOnConflict("reads", null, r, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    public void markThreadSeen(String key) {
        ContentValues v = new ContentValues();
        v.put("seen", 1);
        getWritableDatabase().update("messages", v, "thread_key=? AND outgoing=0", new String[]{key});
        long ts = System.currentTimeMillis();
        try {
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT MAX(date) FROM messages WHERE thread_key=?", new String[]{key});
            if (c.moveToFirst() && !c.isNull(0)) ts = c.getLong(0);
            c.close();
        } catch (Exception ignored) {
        }
        long last = 0;
        try {
            Cursor mc = getReadableDatabase().rawQuery(
                    "SELECT MAX(id) FROM messages WHERE thread_key=?", new String[]{key});
            if (mc.moveToFirst() && !mc.isNull(0)) last = mc.getLong(0);
            mc.close();
        } catch (Exception ignored) {
        }
        ContentValues r = new ContentValues();
        r.put("thread_key", key);
        r.put("ts", ts);
        r.put("last_id", last);
        getWritableDatabase().insertWithOnConflict("reads", null, r, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String lastSubject(String key) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT subject FROM messages WHERE thread_key=? AND subject IS NOT NULL AND subject<>''"
                        + " ORDER BY date DESC LIMIT 1", new String[]{key});
        try {
            if (c.moveToFirst()) return c.getString(0);
        } finally {
            c.close();
        }
        return "";
    }

    /**
     * Первая синхронизация аккаунта: вся уже загруженная история считается
     * просмотренной, иначе счётчик «новых» показал бы весь ящик.
     */
    public void baselineUnseen() {
        try {
            getWritableDatabase().execSQL("INSERT OR IGNORE INTO reads(thread_key, ts, last_id)"
                    + " SELECT m.thread_key, MAX(m.date), MAX(m.id) FROM messages m"
                    + " WHERE m.thread_key<>'debug'"
                    + " AND m.thread_key NOT IN (SELECT thread_key FROM reads)"
                    + " GROUP BY m.thread_key");
        } catch (Exception ignored) {
        }
    }

    public int unreadTotal() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM messages m WHERE m.outgoing=0 AND m.thread_key<>'debug'"
                        + " AND m.id > COALESCE((SELECT r.last_id FROM reads r"
                        + " WHERE r.thread_key=m.thread_key),0)", null);
        try {
            if (c.moveToFirst()) return c.getInt(0);
        } finally {
            c.close();
        }
        return 0;
    }

    // ---------- attachments ----------

    public void addAttachment(AttachmentInfo a) {
        ContentValues v = new ContentValues();
        v.put("message_id", a.messageId);
        v.put("part", a.part);
        v.put("name", a.name);
        v.put("mime", a.mime);
        v.put("size", a.size);
        v.put("path", a.path);
        v.put("state", a.state);
        v.put("width", a.width);
        v.put("height", a.height);
        a.id = getWritableDatabase().insert("attachments", null, v);
    }

    /** Запоминает путь к сохранённому письму (после догрузки с сервера). */
    public void setRawPath(long messageId, String path) {
        ContentValues v = new ContentValues();
        v.put("raw_path", path);
        getWritableDatabase().update("messages", v, "id=?", new String[]{String.valueOf(messageId)});
    }

    /** Сбрасывает пути вложений после очистки кеша: файлов больше нет. */
    public void clearAttachments() {
        ContentValues v = new ContentValues();
        v.putNull("path");
        v.put("state", 0);
        v.put("width", 0);
        v.put("height", 0);
        getWritableDatabase().update("attachments", v, null, null);
    }

    /** Пути всех скачанных вложений переписки. */
    public List<String> attachmentPaths(String threadKey) {
        List<String> out = new ArrayList<String>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT a.path FROM attachments a JOIN messages m ON a.message_id=m.id"
                        + " WHERE m.thread_key=? AND a.path IS NOT NULL", new String[]{threadKey});
        try {
            while (c.moveToNext()) {
                String p = c.getString(0);
                if (p != null && p.length() > 0) out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Обновляет путь/размер картинки после скачивания или сжатия. */
    public void updateAttachment(AttachmentInfo a) {
        ContentValues v = new ContentValues();
        v.put("path", a.path);
        v.put("state", a.state);
        v.put("size", a.size);
        v.put("mime", a.mime);
        v.put("width", a.width);
        v.put("height", a.height);
        getWritableDatabase().update("attachments", v, "id=?",
                new String[]{String.valueOf(a.id)});
    }

    public List<AttachmentInfo> attachments(long messageId) {
        List<AttachmentInfo> out = new ArrayList<AttachmentInfo>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,message_id,part,name,mime,size,path,state,width,height"
                        + " FROM attachments WHERE message_id=? ORDER BY id",
                new String[]{String.valueOf(messageId)});
        try {
            while (c.moveToNext()) {
                AttachmentInfo a = new AttachmentInfo();
                a.id = c.getLong(0);
                a.messageId = c.getLong(1);
                a.part = c.getInt(2);
                a.name = c.getString(3);
                a.mime = c.getString(4);
                a.size = c.getLong(5);
                a.path = c.getString(6);
                a.state = c.getInt(7);
                a.width = c.getInt(8);
                a.height = c.getInt(9);
                out.add(a);
            }
        } finally {
            c.close();
        }
        return out;
    }
}
