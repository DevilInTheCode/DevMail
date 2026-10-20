package com.dsh.mail;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import com.sun.mail.imap.IMAPSSLStore;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.smtp.SMTPSSLTransport;
import com.sun.mail.smtp.SMTPTransport;

import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.UIDFolder;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

/**
 * Вся сетевая работа: IMAP-синхронизация, отправка через SMTP, разбор MIME.
 */
public final class MailService {

    private static final String TAG = "Mail";
    private static final int MAX_FETCH = 25;

    private MailService() {
    }

    // ---------------- подключение ----------------

    private static Properties props(Account a) {
        Properties p = new Properties();
        p.put("mail.mime.decodetext.strict", "false");
        p.put("mail.mime.charset", "UTF-8");
        p.put("mail.mime.address.strict", "false");

        p.put("mail.imap.connectiontimeout", "20000");
        p.put("mail.imap.timeout", "40000");
        p.put("mail.imaps.connectiontimeout", "20000");
        p.put("mail.imaps.timeout", "40000");
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imap.starttls.enable", "true");
        p.put("mail.imap.starttls.required", "false");

        p.put("mail.smtp.connectiontimeout", "20000");
        p.put("mail.smtp.timeout", "40000");
        p.put("mail.smtp.writetimeout", "40000");
        p.put("mail.smtp.auth", "true");
        if (a.trustAll) {
            // Принимать любые сертификаты: самоподписанные, корпоративные, просроченные.
            p.put("mail.imaps.ssl.trust", "*");
            p.put("mail.imap.ssl.trust", "*");
            p.put("mail.smtp.ssl.trust", "*");
            p.put("mail.smtps.ssl.trust", "*");
            p.put("mail.imaps.ssl.checkserveridentity", "false");
            p.put("mail.imap.ssl.checkserveridentity", "false");
            p.put("mail.smtp.ssl.checkserveridentity", "false");
            p.put("mail.smtps.ssl.checkserveridentity", "false");
            p.put("mail.imaps.socketFactory.class", TrustAllSocketFactory.class.getName());
            p.put("mail.imaps.socketFactory.port", String.valueOf(a.imapPort));
            p.put("mail.imaps.socketFactory.fallback", "false");
            p.put("mail.imaps.socketFactory.fallback", "false");
            if (a.smtpSsl) {
                p.put("mail.smtp.socketFactory.class", TrustAllSocketFactory.class.getName());
                p.put("mail.smtp.socketFactory.port", String.valueOf(a.smtpPort));
                p.put("mail.smtp.socketFactory.fallback", "false");
                p.put("mail.smtps.socketFactory.class", TrustAllSocketFactory.class.getName());
                p.put("mail.smtps.socketFactory.port", String.valueOf(a.smtpPort));
                p.put("mail.smtps.socketFactory.fallback", "false");
            }
        }
        if (a.smtpSsl) {
            p.put("mail.smtp.ssl.enable", "true");
        } else {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "false");
        }
        return p;
    }

    static Store openStore(Account a) throws Exception {
        if (a.imapHost == null || a.imapHost.trim().isEmpty()) {
            throw new Exception(L.t("не задан IMAP-сервер"));
        }
        Session s = Session.getInstance(props(a));
        Store store;
        try {
            store = s.getStore(a.imapSsl ? "imaps" : "imap");
        } catch (javax.mail.NoSuchProviderException e) {
            store = a.imapSsl ? new IMAPSSLStore(s, null) : new IMAPStore(s, null);
        }
        store.connect(a.imapHost.trim(), a.imapPort, a.username, a.password);
        return store;
    }

    /** Проверка настроек: возвращает null при успехе, иначе текст ошибки. */
    public static String test(Account a) {
        try {
            Store store = openStore(a);
            store.close();
        } catch (Exception e) {
            return "IMAP: " + describe(e);
        }
        try {
            Session s = Session.getInstance(props(a));
            Transport t = transport(s, a);
            t.connect(a.smtpHost == null ? "" : a.smtpHost.trim(), a.smtpPort, a.username, a.password);
            t.close();
        } catch (Exception e) {
            return "SMTP: " + describe(e);
        }
        return null;
    }

    private static Transport transport(Session s, Account a) throws Exception {
        try {
            return s.getTransport("smtp");
        } catch (javax.mail.NoSuchProviderException e) {
            return a.smtpSsl ? new SMTPSSLTransport(s, null) : new SMTPTransport(s, null);
        }
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) m = e.getClass().getSimpleName();
        if (e instanceof javax.mail.AuthenticationFailedException) return L.t("неверный логин или пароль");
        return m;
    }

    // ---------------- синхронизация ----------------

    /** Результат синхронизации одного ящика — для диагностики в интерфейсе. */
    public static class SyncResult {
        public int added;
        public String inbox = "";
        public int inboxTotal;
        public int failed;
        public String sent = "";
        public String error;
        public String sampleError = "";
        public String folders = "";
    }

    public static SyncResult syncAccount(Context ctx, Db db, Account a) throws Exception {
        return syncAccount(ctx, db, a, false);
    }

    /** quiet=true — не писать в журнал отладки (живое соединение проверяет часто). */
    public static SyncResult syncAccount(Context ctx, Db db, Account a, boolean quiet) throws Exception {
        SyncResult r = new SyncResult();
        Store store = openStore(a);
        try {
            Folders found = Folders.discover(store);
            r.folders = found.describe();
            r.inbox = found.inbox;

            log(db, quiet, L.t("Старт"), a.email + " → " + found.inbox + L.t(", папок: ") + found.all.size());
            Folder opened = openAny(store, found.inboxCandidates());
            if (opened == null) {
                r.error = L.t("не нашёл папку входящих. Папки на сервере: ") + found.describe();
                log(db, quiet, L.t("Ошибка"), r.error);
            } else {
                r.inbox = opened.getFullName();
                try {
                    syncFolder(ctx, db, a, opened, r, quiet);
                } catch (Exception e) {
                    r.error = L.t("входящие (") + r.inbox + "): " + describe(e);
                } finally {
                    close(opened);
                }
            }

            log(db, quiet, L.t("Итог"), r.inbox + L.t(": писем ") + r.inboxTotal + L.t(", новых ") + r.added
                    + L.t(", ошибок ") + r.failed + (r.sampleError.length() > 0 ? " (" + r.sampleError + ")" : ""));
            if (r.error != null) log(db, quiet, L.t("Ошибка"), r.error);

            if (found.sent != null) {
                Folder sf = openAny(store, new String[]{found.sent});
                if (sf != null) {
                    r.sent = sf.getFullName();
                    try {
                        if (!sf.getFullName().equalsIgnoreCase(r.inbox)) syncFolder(ctx, db, a, sf, r, quiet);
                    } catch (Exception e) {
                        Log.w(TAG, "sent folder: " + e);
                    } finally {
                        close(sf);
                    }
                }
            }
        } finally {
            try {
                store.close();
            } catch (Exception ignored) {
            }
        }
        if (a.lastSync == 0) db.baselineUnseen();
        db.touchSync(a.id, System.currentTimeMillis());
        return r;
    }

    private static void log(Db db, boolean quiet, String tag, String text) {
        if (!quiet) db.log(tag, text);
    }

    private static void close(Folder f) {
        try {
            f.close(false);
        } catch (Exception ignored) {
        }
    }

    static Folder openAny(Store store, String[] candidates) {
        for (String name : candidates) {
            if (name == null) continue;
            try {
                Folder f = store.getFolder(name);
                if (f == null) continue;
                f.open(Folder.READ_ONLY);
                return f;
            } catch (Exception e) {
                Log.w(TAG, "open " + name + ": " + e);
            }
        }
        return null;
    }

    private static void syncFolder(Context ctx, Db db, Account a, Folder f, SyncResult r, boolean quiet) throws Exception {
        String folderName = f.getFullName();
        int total = f.getMessageCount();
        if (folderName.equalsIgnoreCase(r.inbox)) r.inboxTotal = total;
        if (total <= 0) return;

        int start = Math.max(1, total - MAX_FETCH + 1);
        Message[] msgs = f.getMessages(start, total);
        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.ENVELOPE);
        fp.add(FetchProfile.Item.FLAGS);
        f.fetch(msgs, fp);
        UIDFolder uf = (UIDFolder) f;
        log(db, quiet, L.t("Папка"), folderName + L.t(": писем ") + total + L.t(", читаю ") + (total - start + 1));
        int logged = 0;
        for (Message m : msgs) {
            long uid = 0;
            try {
                uid = uf.getUID(m);
            } catch (Exception ignored) {
            }
            if (uid <= 0) continue;
            if (db.hasMessage(a.id, folderName, uid)) continue;
            try {
                if (storeMessage(ctx, db, a, m, folderName, uid)) r.added++;
            } catch (Exception e) {
                r.failed++;
                if (r.sampleError.length() == 0) {
                    r.sampleError = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                Log.w(TAG, "store message: " + e);
                if (logged < 10) {
                    logged++;
                    log(db, quiet, L.t("Ошибка письма"), "uid " + uid + ": " + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
                }
            }
        }
        log(db, quiet, L.t("Прочитано"), folderName + L.t(": новых ") + r.added + L.t(", ошибок ") + r.failed);
    }

    /** Диагностика для кнопки «Проверить соединение»: какие папки видит сервер. */
    public static String folderInfo(Account a) {
        Store store = null;
        try {
            store = openStore(a);
            Folders f = Folders.discover(store);
            StringBuilder sb = new StringBuilder();
            Folder inbox = openAny(store, f.inboxCandidates());
            if (inbox == null) {
                sb.append(L.t("Входящие: не найдены\n"));
            } else {
                sb.append(L.t("Входящие: ")).append(inbox.getFullName())
                        .append(L.t(", писем: ")).append(inbox.getMessageCount()).append('\n');
                close(inbox);
            }
            if (f.sent != null) sb.append(L.t("Отправленные: ")).append(f.sent).append('\n');
            sb.append(L.t("Папки: ")).append(f.describe());
            return sb.toString();
        } catch (Exception e) {
            return L.t("не удалось получить папки: ") + describe(e);
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean storeMessage(Context ctx, Db db, Account a, Message m, String folder, long uid) throws Exception {
        String msgid = null;
        try {
            String[] h = m.getHeader("Message-ID");
            if (h != null && h.length > 0) msgid = h[0];
        } catch (Exception ignored) {
        }

        String rawPath = null;
        File raw = null;
        try {
            File dir = new File(ctx.getFilesDir(), "raw");
            if (!dir.exists()) dir.mkdirs();
            raw = new File(dir, a.id + "_" + folder.replaceAll("[^A-Za-z0-9]", "_") + "_" + uid + ".eml");
            FileOutputStream fos = new FileOutputStream(raw);
            try {
                m.writeTo(fos);
            } finally {
                fos.close();
            }
            if (raw.length() > AttachStore.maxRaw(AttachStore.level(ctx))) {
                raw.delete();
                raw = null;
            } else {
                rawPath = raw.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.w(TAG, "raw save: " + e);
        }

        // Разбираем сохранённый raw: так размеры вложений и кодировки точные
        // (у IMAP-сообщения getSize() часто -1, пока не догрузишь часть).
        Message src = m;
        if (rawPath != null) {
            MimeMessage rm = null;
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(rawPath);
                rm = new MimeMessage(Session.getInstance(new Properties()), fis);
            } catch (Exception e) {
                Log.w(TAG, "raw parse: " + e);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (Exception ignored) {
                    }
                }
            }
            if (rm != null) src = rm;
        }

        String subject = "";
        try {
            subject = src.getSubject() == null ? "" : src.getSubject();
        } catch (Exception ignored) {
        }

        String peerName = "";
        String peerEmail = "";
        Address[] from = null;
        try {
            from = src.getFrom();
        } catch (Exception ignored) {
        }
        if (from != null && from.length > 0) {
            InternetAddress ia = toAddress(from[0]);
            peerEmail = ia.getAddress() == null ? "" : ia.getAddress();
            peerName = ia.getPersonal() == null ? "" : ia.getPersonal();
        }

        boolean outgoing = peerEmail.equalsIgnoreCase(a.email);
        if (outgoing) {
            Address[] to = null;
            try {
                to = src.getRecipients(Message.RecipientType.TO);
            } catch (Exception ignored) {
            }
            if (to != null && to.length > 0) {
                InternetAddress ia = toAddress(to[0]);
                peerEmail = ia.getAddress() == null ? peerEmail : ia.getAddress();
                peerName = ia.getPersonal() == null ? "" : ia.getPersonal();
            }
        }
        if (peerEmail == null) peerEmail = "";

        StringBuilder plain = new StringBuilder();
        StringBuilder html = new StringBuilder();
        List<AttachmentInfo> atts = new ArrayList<AttachmentInfo>();
        try {
            walk(src, new int[]{0}, plain, html, atts);
        } catch (Exception e) {
            Log.w(TAG, "walk: " + e);
        }

        String body = plain.toString().trim();
        if (body.length() == 0) body = htmlToText(html.toString());
        if (body.length() == 0) body = L.t("(пустое сообщение)");

        String snippet = body.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ");
        if (snippet.length() > 120) snippet = snippet.substring(0, 120) + "…";

        long date = 0;
        try {
            Date d = src.getSentDate();
            if (d != null) date = d.getTime();
        } catch (Exception ignored) {
        }
        if (date == 0) date = System.currentTimeMillis();

        boolean seen = false;
        try {
            seen = m.isSet(Flags.Flag.SEEN);
        } catch (Exception ignored) {
        }

        MailMessage mm = new MailMessage();
        mm.accountId = a.id;
        mm.folder = folder;
        mm.uid = uid;
        mm.msgid = msgid == null ? "" : msgid;
        mm.threadKey = a.id + "|" + peerEmail.toLowerCase();
        mm.peerName = peerName;
        mm.peerEmail = peerEmail;
        mm.subject = subject;
        mm.date = date;
        mm.snippet = snippet;
        mm.body = body;
        mm.seen = seen;
        mm.outgoing = outgoing;
        mm.rawPath = rawPath;
        mm.hasAttach = !atts.isEmpty();
        mm.state = outgoing ? MailMessage.ST_DELIVERED : 0;
        mm.html = html.toString();

        if (db.isThreadDeleted(mm.threadKey, mm.date)) {
            if (raw != null) raw.delete();
            return false;
        }

        long id = db.insertMessage(mm);
        if (outgoing) db.markDelivered(mm.msgid);
        if (id <= 0) {
            if (raw != null) raw.delete();
            return false;
        }
        for (AttachmentInfo ai : atts) {
            ai.messageId = id;
            db.addAttachment(ai);
        }
        mm.id = id;
        AttachStore.autoFetch(ctx, db, mm, id);
        return true;
    }

    // ---------------- разбор MIME ----------------

    private static InternetAddress toAddress(Address a) {
        try {
            if (a instanceof InternetAddress) return (InternetAddress) a;
            return new InternetAddress(a.toString());
        } catch (Exception e) {
            return new InternetAddress();
        }
    }

    private static String decode(String s) {
        if (s == null) return "";
        try {
            return MimeUtility.decodeText(s);
        } catch (Exception e) {
            return s;
        }
    }

    private static String text(Part p) throws Exception {
        Object c = p.getContent();
        if (c instanceof String) return (String) c;
        if (c instanceof InputStream) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            InputStream in = (InputStream) c;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            String cs = "UTF-8";
            try {
                ContentType ct = new ContentType(p.getContentType());
                String pcs = ct.getParameter("charset");
                if (pcs != null && pcs.trim().length() > 0) cs = pcs.trim();
            } catch (Exception ignored) {
            }
            try {
                return new String(bos.toByteArray(), cs);
            } catch (Exception e) {
                return new String(bos.toByteArray(), "UTF-8");
            }
        }
        return c == null ? "" : c.toString();
    }

    private static void walk(Part p, int[] counter, StringBuilder plain, StringBuilder html,
                             List<AttachmentInfo> atts) throws Exception {
        int idx = counter[0]++;
        String fname = null;
        try {
            fname = p.getFileName();
        } catch (Exception ignored) {
        }
        String disp = null;
        try {
            disp = p.getDisposition();
        } catch (Exception ignored) {
        }
        boolean attach = fname != null && fname.length() > 0
                || Part.ATTACHMENT.equalsIgnoreCase(disp);

        if (attach) {
            AttachmentInfo ai = new AttachmentInfo();
            ai.part = idx;
            ai.name = decode(fname);
            if (ai.name == null || ai.name.trim().length() == 0) ai.name = L.t("вложение");
            try {
                ai.mime = p.getContentType();
            } catch (Exception ignored) {
            }
            try {
                ai.size = p.getSize();
            } catch (Exception ignored) {
            }
            atts.add(ai);
            return;
        }
        if (p.isMimeType("text/plain")) {
            plain.append(text(p)).append('\n');
        } else if (p.isMimeType("text/html")) {
            html.append(text(p));
        }
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                walk(mp.getBodyPart(i), counter, plain, html, atts);
            }
        }
    }

    private static Part findPart(Part p, int[] counter, int target) throws Exception {
        int idx = counter[0]++;
        if (idx == target) return p;
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                Part found = findPart(mp.getBodyPart(i), counter, target);
                if (found != null) return found;
            }
        }
        return null;
    }

    static String htmlToText(String html) {
        if (html == null || html.length() == 0) return "";
        String s = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</p>", "\n\n");
        s = s.replaceAll("(?i)</div>", "\n");
        s = s.replaceAll("(?i)</tr>", "\n");
        s = s.replaceAll("(?i)</li>", "\n");
        s = s.replaceAll("<[^>]+>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&laquo;", "«").replace("&raquo;", "»").replace("&mdash;", "—");
        s = s.replaceAll("[ \\t]+", " ");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    // ---------------- отправка ----------------

    public static void send(Context ctx, Db db, Account a, String to, String subject, String body,
                            File attach) throws Exception {
        send(ctx, db, a, to, subject, body, singleFile(attach), "", "", 0);
    }

    /** @return Message-ID отправленного письма (нужен для галочек в чате). */
    public static String send(Context ctx, Db db, Account a, String to, String subject, String body,
                              File attach, String quoteAuthor, String quoteText, long localId) throws Exception {
        return send(ctx, db, a, to, subject, body, singleFile(attach), quoteAuthor, quoteText, localId);
    }

    /** Несколько вложений без цитаты. */
    public static void send(Context ctx, Db db, Account a, String to, String subject, String body,
                            List<File> attach) throws Exception {
        send(ctx, db, a, to, subject, body, attach, "", "", 0);
    }

    /** То же, но с несколькими вложениями (например, «Поделиться» из галереи). */
    public static String send(Context ctx, Db db, Account a, String to, String subject, String body,
                              List<File> attach, String quoteAuthor, String quoteText, long localId) throws Exception {
        List<File> files = attach == null ? new ArrayList<File>() : attach;
        if (a.smtpHost == null || a.smtpHost.trim().isEmpty()) throw new Exception(L.t("не задан SMTP-сервер"));
        String text = body == null ? "" : body;
        String full = text;
        if (quoteText != null && quoteText.trim().length() > 0) {
            StringBuilder q = new StringBuilder("\n\n");
            if (quoteAuthor != null && quoteAuthor.trim().length() > 0) {
                q.append(quoteAuthor.trim()).append(L.t(" писал(а):\n"));
            }
            for (String line : quoteText.split("\n")) {
                q.append("> ").append(line).append('\n');
            }
            full = text + q;
        }

        Session s = Session.getInstance(props(a));
        MimeMessage m = new MimeMessage(s);
        m.setFrom(new InternetAddress(a.email));
        m.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        m.setSubject(subject == null ? "" : subject, "UTF-8");
        m.setSentDate(new Date());
        String msgid = "<" + System.currentTimeMillis() + "." + Math.abs(a.id)
                + "@" + domain(a.email) + ">";
        m.setHeader("Message-ID", msgid);

        if (!files.isEmpty()) {
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(full, "UTF-8");
            Multipart mp = new MimeMultipart("mixed");
            mp.addBodyPart(textPart);
            for (File f : files) {
                if (f == null || !f.exists()) continue;
                MimeBodyPart filePart = new MimeBodyPart();
                filePart.attachFile(f);
                filePart.setFileName(f.getName());
                mp.addBodyPart(filePart);
            }
            m.setContent(mp);
        } else {
            m.setText(full, "UTF-8");
        }
        m.saveChanges();

        Transport t = transport(s, a);
        try {
            t.connect(a.smtpHost.trim(), a.smtpPort, a.username, a.password);
            t.sendMessage(m, m.getAllRecipients());
        } finally {
            try {
                t.close();
            } catch (Exception ignored) {
            }
        }

        if (localId <= 0) {
            try {
                insertSent(ctx, db, a, to, subject, text, quoteAuthor, quoteText, files, msgid);
            } catch (Exception e) {
                Log.w(TAG, "local copy: " + e);
            }
        }
        return msgid;
    }

    /** Локальная копия отправленного письма (для чата и для склейки с папкой «Отправленные»). */
    private static void insertSent(Context ctx, Db db, Account a, String to, String subject, String body,
                                   String quoteAuthor, String quoteText, List<File> attach, String msgid) {
        String peer = to == null ? "" : to.trim();
        int lt = peer.lastIndexOf('<');
        int gt = peer.lastIndexOf('>');
        if (lt >= 0 && gt > lt) peer = peer.substring(lt + 1, gt).trim();
        if (peer.length() == 0) peer = to == null ? "" : to.trim();

        MailMessage m = new MailMessage();
        m.accountId = a.id;
        m.folder = "SENT";
        m.uid = -(System.currentTimeMillis() / 1000);
        m.msgid = msgid;
        m.threadKey = a.id + "|" + peer.toLowerCase();
        m.peerEmail = peer;
        m.subject = subject == null ? "" : subject;
        m.date = System.currentTimeMillis();
        String snip = body.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (snip.length() > 120) snip = snip.substring(0, 120) + "…";
        m.snippet = snip;
        m.body = body;
        m.seen = true;
        m.outgoing = true;
        m.hasAttach = attach != null && !attach.isEmpty();
        m.quoteAuthor = quoteAuthor == null ? "" : quoteAuthor;
        m.quoteText = quoteText == null ? "" : quoteText;
        m.state = MailMessage.ST_SENT;
        long id = db.insertMessage(m);
        if (id > 0 && attach != null) {
            for (File attachFile : attach) {
                if (attachFile == null) continue;
                AttachmentInfo ai = new AttachmentInfo();
                ai.messageId = id;
                ai.part = -1;
                ai.name = attachFile.getName();
                ai.size = attachFile.length();
                db.addAttachment(ai);
                try {
                    AttachStore.storeLocal(ctx, db, ai, attachFile);
                } catch (Exception e) {
                    Log.w(TAG, "local attach: " + e);
                }
            }
        }
    }

    private static List<File> singleFile(File f) {
        List<File> out = new ArrayList<File>();
        if (f != null) out.add(f);
        return out;
    }

    private static String domain(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        return at > 0 ? email.substring(at + 1) : "local";
    }

    // ---------------- вложения ----------------

    /**
     * Достаёт письмо с сервера по UID, если raw не сохранился (письмо больше лимита).
     * Нужно, чтобы вложение можно было скачать по нажатию в любой момент.
     */
    public static File refetchRaw(Context ctx, Db db, MailMessage msg) throws Exception {
        if (msg == null) throw new Exception(L.t("письмо не сохранено целиком"));
        if (msg.folder == null || msg.uid <= 0) {
            throw new Exception(L.t("письмо не сохранено целиком"));
        }
        Account a = db.account(msg.accountId);
        if (a == null) throw new Exception(L.t("аккаунт не найден"));
        Store store = null;
        Folder folder = null;
        try {
            store = openStore(a);
            folder = store.getFolder(msg.folder);
            if (folder == null) throw new Exception(L.t("папка не найдена"));
            folder.open(Folder.READ_ONLY);
            Message m = ((UIDFolder) folder).getMessageByUID(msg.uid);
            if (m == null) throw new Exception(L.t("письмо не найдено на сервере"));
            File dir = new File(ctx.getFilesDir(), "raw");
            if (!dir.exists()) dir.mkdirs();
            String fname = msg.accountId + "_" + msg.folder.replaceAll("[^A-Za-z0-9]", "_")
                    + "_" + msg.uid + ".eml";
            File raw = new File(dir, fname);
            FileOutputStream fos = new FileOutputStream(raw);
            try {
                m.writeTo(fos);
            } finally {
                fos.close();
            }
            msg.rawPath = raw.getAbsolutePath();
            db.setRawPath(msg.id, msg.rawPath);
            return raw;
        } finally {
            if (folder != null) close(folder);
            if (store != null) {
                try {
                    store.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Распаковывает MIME-часть вложения из сохранённого письма во временный файл. */
    public static File extractPart(Context ctx, MailMessage msg, AttachmentInfo att) throws Exception {
        if (msg.rawPath == null || !new File(msg.rawPath).exists()) {
            throw new Exception(L.t("письмо не сохранено целиком"));
        }
        Session s = Session.getInstance(new Properties());
        FileInputStream fis = new FileInputStream(msg.rawPath);
        MimeMessage m;
        try {
            m = new MimeMessage(s, fis);
        } finally {
            fis.close();
        }
        Part part = findPart(m, new int[]{0}, att.part);
        if (part == null) throw new Exception(L.t("вложение не найдено в письме"));

        File dir = new File(ctx.getCacheDir(), "out");
        if (!dir.exists()) dir.mkdirs();
        String name = att.name == null || att.name.trim().length() == 0 ? "file" : att.name;
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        File out = new File(dir, name);
        InputStream in = part.getInputStream();
        OutputStream os = new FileOutputStream(out);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            os.close();
        }
        return out;
    }
}
