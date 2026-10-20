package com.dsh.mail;

import com.sun.mail.imap.IMAPFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.mail.Folder;
import javax.mail.Store;

/**
 * Поиск папок на IMAP-сервере.
 *
 * Папки ищутся не по «угадыванию имён», а по флагам
 * RFC 6154 SPECIAL-USE (\Inbox, \Sent), которые сервер отдаёт в ответе LIST.
 * Если флагов нет (старые серверы) — подстраховка по списку имён,
 * включая локализованные («Отправленные», «Исходящие» и т.п.).
 */
public class Folders {

    public String inbox = "INBOX";
    public String sent;
    public final List<String> all = new ArrayList<String>();

    private static final String[] SENT_ALIASES = {
            "sent", "sent items", "sent messages", "sent mail", "inbox.sent",
            "[gmail]/sent mail", L.t("отправленные"), L.t("отправлено"), L.t("исходящие"),
            "gesendet", "éléments envoyés", "enviados", "posta inviata"
    };

    public static Folders discover(Store store) {
        Folders f = new Folders();
        String flaggedInbox = null;
        String flaggedSent = null;
        try {
            Folder root = store.getDefaultFolder();
            Folder[] list = root.list("*");
            if (list != null) {
                for (Folder folder : list) {
                    if (folder == null) continue;
                    try {
                        if ((folder.getType() & Folder.HOLDS_MESSAGES) == 0) continue;
                    } catch (Exception ignored) {
                        continue;
                    }
                    String name = folder.getFullName();
                    if (name == null || name.length() == 0) continue;
                    f.all.add(name);
                    String[] attrs = null;
                    if (folder instanceof IMAPFolder) {
                        try {
                            attrs = ((IMAPFolder) folder).getAttributes();
                        } catch (Exception ignored) {
                        }
                    }
                    if (attrs == null) continue;
                    for (String at : attrs) {
                        if (at == null) continue;
                        String a = at.toLowerCase(Locale.ROOT);
                        if (a.equals("\\inbox")) flaggedInbox = name;
                        else if (a.equals("\\sent")) flaggedSent = name;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (flaggedInbox != null) f.inbox = flaggedInbox;
        else {
            String inboxName = guessInbox(f.all);
            if (inboxName != null) f.inbox = inboxName;
        }
        f.sent = flaggedSent != null ? flaggedSent : guessSent(f.all);
        return f;
    }

    /** Имена-кандидаты входящих: сначала найденное, потом стандартные варианты. */
    public String[] inboxCandidates() {
        List<String> c = new ArrayList<String>();
        c.add(inbox);
        for (String n : new String[]{"INBOX", "Inbox", "inbox"}) {
            boolean dup = false;
            for (String x : c) if (x.equalsIgnoreCase(n)) dup = true;
            if (!dup) c.add(n);
        }
        return c.toArray(new String[0]);
    }

    public String describe() {
        return all.isEmpty() ? L.t("(сервер не отдал список папок)") : join(all);
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    private static String guessInbox(List<String> names) {
        for (String n : names) if (n.equalsIgnoreCase("INBOX")) return n;
        for (String n : names) {
            String l = n.toLowerCase(Locale.ROOT);
            if (l.equals("входящие") || l.equals("inbox")) return n;
        }
        return null;
    }

    private static String guessSent(List<String> names) {
        String best = null;
        for (String n : names) {
            String l = n.toLowerCase(Locale.ROOT);
            for (String alias : SENT_ALIASES) {
                if (l.equals(alias)) return n;
            }
        }
        for (String n : names) {
            String l = n.toLowerCase(Locale.ROOT);
            if (l.contains("draft") || l.contains("черновик")) continue;
            if (l.contains("sent") || l.contains("отправлен") || l.contains("исходящ")) {
                if (best == null || l.length() < best.length()) best = l;
                if (best.equals(l)) return n;
            }
        }
        return null;
    }
}
