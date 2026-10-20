package com.dsh.mail;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Уведомления по чатам: на каждую переписку с новыми письмами — своя карточка
 * с именем собеседника и текстом последнего письма. Нажатие открывает сразу
 * этот чат (список чатов остаётся под ним в стеке — «назад» вернёт к списку).
 */
public final class Notifier {

    private static final String CHANNEL = "mail2";
    private static final String OLD_CHANNEL = "mail";
    private static final String GROUP = "com.dsh.mail.MAIL";
    private static final int SUMMARY_ID = 1;
    private static final String DEBUG_THREAD = "debug";
    private static final int MAX_CARDS = 6;

    private Notifier() {
    }

    /** Построить уведомления по актуальному состоянию базы. */
    public static void newMail(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            createChannel(nm);

            if (isForeground(ctx)) {
                // Приложение на экране — счётчики и так видны, не дублируем в шторку.
                cancelAll(ctx);
                return;
            }
            List<ChatItem> all = new Db(ctx).chats();
            List<ChatItem> fresh = new ArrayList<ChatItem>();
            int total = 0;
            for (ChatItem it : all) {
                if (it.unread <= 0) continue;
                if (DEBUG_THREAD.equals(it.threadKey)) continue;
                if (it.muted) continue;
                total += it.unread;
                if (fresh.size() < MAX_CARDS) fresh.add(it);
            }
            if (fresh.isEmpty()) {
                cancelAll(ctx);
                return;
            }
            if (total <= 0) {
                cancelAll(ctx);
                return;
            }
            for (ChatItem it : fresh) {
                nm.notify(idOf(it.threadKey), card(ctx, it, fresh.size() > 1));
            }
            if (fresh.size() > 1) nm.notify(SUMMARY_ID, summary(ctx, fresh, total));
            cleanOld(nm, fresh);
        } catch (Exception ignored) {
        }
    }

    /** Убрать карточку чата (открыли переписку — читать больше нечего). */
    public static void cancelThread(final Context ctx, String threadKey) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.cancel(idOf(threadKey));
            nm.cancel(SUMMARY_ID);
        } catch (Exception ignored) {
        }
        // Пересобрать сводку, если письма остались в других чатах.
        new Thread(new Runnable() {
            @Override
            public void run() {
                newMail(ctx);
            }
        }, "notif-refresh").start();
    }

    public static void cancelAll(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancelAll();
        } catch (Exception ignored) {
        }
    }

    private static void cleanOld(NotificationManager nm, List<ChatItem> fresh) {
        // Уведомления по чатам, которые уже прочитаны, гасим (id не сбрасываются сами).
        try {
            android.service.notification.StatusBarNotification[] live = nm.getActiveNotifications();
            if (live == null) return;
            List<String> keys = new ArrayList<String>();
            for (ChatItem it : fresh) keys.add(it.threadKey);
            for (android.service.notification.StatusBarNotification s : live) {
                int id = s.getId();
                if (id == SUMMARY_ID) continue;
                boolean known = false;
                for (ChatItem it : fresh) {
                    if (idOf(it.threadKey) == id) {
                        known = true;
                        break;
                    }
                }
                if (!known) nm.cancel(id);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isForeground(Context ctx) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            java.util.List<android.app.ActivityManager.RunningAppProcessInfo> ps = am.getRunningAppProcesses();
            if (ps == null) return false;
            String me = ctx.getPackageName();
            for (android.app.ActivityManager.RunningAppProcessInfo p : ps) {
                if (!me.equals(p.processName)) continue;
                return p.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Notification card(Context ctx, ChatItem it, boolean grouped) {
        Intent main = new Intent(ctx, MainActivity.class);
        main.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Intent chat = new Intent(ctx, ChatActivity.class);
        chat.putExtra("thread", it.threadKey);
        chat.putExtra("name", it.name);
        chat.putExtra("email", it.email);
        chat.putExtra("account", it.accountId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivities(ctx, idOf(it.threadKey),
                new Intent[]{main, chat}, flags);

        Notification.Builder b = builder(ctx);
        String text = TextUtils.isEmpty(it.inSnippet) ? it.snippet : it.inSnippet;
        if (text == null) text = "";
        b.setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(it.name)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setWhen(it.date > 0 ? it.date : System.currentTimeMillis())
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setNumber(it.unread)
                .setOnlyAlertOnce(false);
        if (!TextUtils.isEmpty(it.accountEmail)) b.setSubText(it.accountEmail);
        Notification.BigTextStyle big = new Notification.BigTextStyle().bigText(
                text.trim().isEmpty() ? L.t("Новых писем: ") + it.unread : text);
        if (it.unread > 1) big.setSummaryText(L.t("Новых писем: ") + it.unread);
        b.setStyle(big);
        b.addAction(replyAction(ctx, it));
        b.addAction(readAction(ctx, it));
        if (grouped) b.setGroup(GROUP);
        return b.build();
    }

    private static Notification.Action replyAction(Context ctx, ChatItem it) {
        Intent i = MailActionReceiver.intent(ctx, MailActionReceiver.ACTION_REPLY, it);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        // Прямой ответ: система дописывает текст в Intent, immutable это блокирует.
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        else if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, idOf(it.threadKey) + 3, i, flags);
        RemoteInput ri = new RemoteInput.Builder(MailActionReceiver.KEY_TEXT)
                .setLabel(L.t("Ответить"))
                .build();
        return new Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(ctx, android.R.drawable.ic_menu_send),
                L.t("Ответить"), pi)
                .addRemoteInput(ri)
                .setAllowGeneratedReplies(true)
                .build();
    }

    private static Notification.Action readAction(Context ctx, ChatItem it) {
        Intent i = MailActionReceiver.intent(ctx, MailActionReceiver.ACTION_MARK_READ, it);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, idOf(it.threadKey) + 5, i, flags);
        return new Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(ctx, android.R.drawable.ic_menu_view),
                L.t("Прочитано"), pi)
                .build();
    }

    /** Обновить карточку после действий из шторки (или убрать её). */
    public static void refresh(Context ctx) {
        newMail(ctx);
    }

    private static Notification summary(Context ctx, List<ChatItem> fresh, int total) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(ctx, SUMMARY_ID, i, flags);

        Notification.Builder b = builder(ctx);
        b.setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(L.t("Новая почта"))
                .setContentText(L.t("Новых писем: ") + total)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setNumber(total);
        Notification.InboxStyle st = new Notification.InboxStyle();
        for (ChatItem it : fresh) {
            String line = TextUtils.isEmpty(it.inSnippet) ? it.snippet : it.inSnippet;
            st.addLine(it.name + ": " + (line == null ? "" : line));
        }
        st.setSummaryText(L.t("Новых писем: ") + total);
        b.setStyle(st);
        return b.build();
    }

    private static Notification.Builder builder(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(ctx, CHANNEL);
        return new Notification.Builder(ctx);
    }

    private static void createChannel(NotificationManager nm) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationChannel ch = new NotificationChannel(CHANNEL, L.t("Новая почта"),
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription(L.t("Письма по чатам: имя собеседника и текст сообщения"));
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
            try {
                nm.deleteNotificationChannel(OLD_CHANNEL);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    /** Стабильный id уведомления для чата. */
    private static int idOf(String threadKey) {
        if (threadKey == null || threadKey.isEmpty()) return 2;
        int h = threadKey.hashCode() & 0x7fffffff;
        return 100 + (h % 100000);
    }
}
