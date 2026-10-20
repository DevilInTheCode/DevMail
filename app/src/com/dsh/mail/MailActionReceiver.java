package com.dsh.mail;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.io.File;

/** Действия из уведомления: быстрый ответ и «прочитано». */
public class MailActionReceiver extends BroadcastReceiver {

    public static final String ACTION_REPLY = "com.dsh.mail.action.REPLY";
    public static final String ACTION_MARK_READ = "com.dsh.mail.action.MARK_READ";
    public static final String KEY_TEXT = "reply_text";

    static Intent intent(Context ctx, String action, ChatItem it) {
        Intent i = new Intent(ctx, MailActionReceiver.class);
        i.setAction(action);
        i.putExtra("thread", it.threadKey);
        i.putExtra("name", it.name);
        i.putExtra("email", it.email);
        i.putExtra("account", it.accountId);
        return i;
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        final String action = intent.getAction();
        final String thread = intent.getStringExtra("thread");
        if (thread == null) return;
        if (ACTION_MARK_READ.equals(action)) {
            try {
                new Db(ctx).markThreadSeen(thread);
            } catch (Throwable ignored) {
            }
            Notifier.cancelThread(ctx, thread);
            return;
        }
        if (!ACTION_REPLY.equals(action)) return;

        String text = null;
        Bundle b = RemoteInputHelper.results(intent);
        if (b != null) {
            CharSequence cs = b.getCharSequence(KEY_TEXT);
            if (cs != null) text = cs.toString().trim();
        }
        if (text == null || text.length() == 0) return;

        final String body = text;
        final String email = intent.getStringExtra("email");
        final long account = intent.getLongExtra("account", 0);
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Db db = new Db(ctx);
                try {
                    Account a = db.account(account);
                    if (a == null) throw new Exception(L.t("аккаунт не найден"));
                    String subject = db.lastSubject(thread);
                    if (subject == null) subject = "";
                    if (!subject.toLowerCase(java.util.Locale.US).startsWith("re:")) subject = "Re: " + subject;
                    String to = email == null ? "" : email;
                    if (to.length() == 0) throw new Exception(L.t("не известен адрес собеседника"));
                    MailService.send(ctx, db, a, to, subject, body, (File) null, "", "", 0);
                    db.markThreadSeen(thread);
                    db.log(L.t("Ответ из шторки"), to + ": " + body);
                    Notifier.cancelThread(ctx, thread);
                } catch (Throwable e) {
                    try {
                        db.log(L.t("Ответ из шторки"), L.t("ошибка: ") + e);
                    } catch (Throwable ignored) {
                    }
                    try {
                        NotificationManager nm = (NotificationManager)
                                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                        if (nm != null) nm.cancelAll();
                    } catch (Throwable ignored) {
                    }
                } finally {
                    try {
                        pending.finish();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, "reply-from-notification").start();
    }

    /** RemoteInput.getResultsFromIntent без жёсткой зависимости от API-уровня. */
    static final class RemoteInputHelper {
        static Bundle results(Intent intent) {
            try {
                return android.app.RemoteInput.getResultsFromIntent(intent);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
