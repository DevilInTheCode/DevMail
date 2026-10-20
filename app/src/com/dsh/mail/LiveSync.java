package com.dsh.mail;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Store;
import javax.mail.UIDFolder;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

/**
 * «Живое» соединение с почтой.
 *
 * Пока приложение открыто — держим один IMAP-сеанс на аккаунт и спрашиваем
 * сервер только про новые UID (опрос раз в 3 секунды). В фоне темп берётся из
 * настройки аккаунта: «мгновенно» — раз в 30 секунд, «каждые N минут» — раз в
 * N минут, «только вручную» — в фоне соединение не держим.
 *
 * Если сервер умеет IMAP IDLE — вместо опроса ждём команду сервера: письмо
 * приходит мгновенно (и в фоне тоже). Серверам без IDLE оставляем опрос.
 *
 * Дополнительно раз в 2 минуты (в фоне — раз в 10) делаем обычную синхронизацию:
 * это страховка, если сервер не отдаёт новые UID так, как мы ждём.
 */
public final class LiveSync {

    public interface Listener {
        void onNewMail();
    }

    private static final String TAG = "live";
    private static final List<LiveSync> POOL = new ArrayList<LiveSync>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Listener listener;
    private static volatile int fgRefs;
    private static boolean background;

    private final Context ctx;
    private final Account acc;
    private volatile boolean running = true;
    private volatile Thread worker;
    /** Папка, в которой сейчас висит IDLE (для прерывания снаружи). */
    private volatile Folder idleFolder;
    private volatile boolean poked;

    private LiveSync(Context c, Account a) {
        ctx = c.getApplicationContext();
        acc = a;
    }

    /** Экран открылся: соединение работает в «быстром» режиме (опрос раз в 3 с). */
    public static synchronized void acquire(Context c) {
        fgRefs++;
        startPool(c);
    }

    public static synchronized void release() {
        if (fgRefs > 0) fgRefs--;
        if (fgRefs == 0 && !background) stopPool();
    }

    /** Фоновый сервис: держим соединение и после сворачивания приложения. */
    public static synchronized void startBackground(Context c) {
        background = true;
        startPool(c);
    }

    public static synchronized void stopBackground() {
        background = false;
        if (fgRefs == 0) stopPool();
    }

    /** Разбудить потоки и проверить почту прямо сейчас (открыли чат, нажали «Проверить»). */
    public static synchronized void poke() {
        for (LiveSync l : POOL) l.wake();
    }

    /** Разбудить поток: прервать паузу или снять IDLE, чтобы сразу проверить почту. */
    private void wake() {
        poked = true;
        Thread t = worker;
        if (t != null) t.interrupt();
        abortIdle(idleFolder);
    }

    private static void startPool(Context c) {
        if (!POOL.isEmpty()) return;
        Context app = c.getApplicationContext();
        for (Account a : new Db(app).accounts()) {
            LiveSync l = new LiveSync(app, a);
            POOL.add(l);
            l.start();
        }
        Log.i(TAG, "live started, accounts=" + POOL.size());
    }

    private static void stopPool() {
        for (LiveSync l : POOL) l.running = false;
        for (LiveSync l : POOL) {
            Thread t = l.worker;
            if (t != null) t.interrupt();
            abortIdle(l.idleFolder);
        }
        POOL.clear();
        Log.i(TAG, "live stopped");
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    private void start() {
        Thread t = new Thread(this::loop, "live-" + acc.id);
        t.setDaemon(true);
        worker = t;
        t.start();
    }

    private void loop() {
        Db db = new Db(ctx);
        while (running) {
            // «Только вручную»: в фоне этому аккаунту соединение не нужно.
            if (fgRefs == 0 && acc.checkMin == 0) {
                sleep(15000);
                continue;
            }

            Store store = null;
            Folder folder = null;
            try {
                store = MailService.openStore(acc);
                Folders found = Folders.discover(store);
                folder = MailService.openAny(store, found.inboxCandidates());
                if (folder == null) {
                    sleep(60000);
                    continue;
                }
                // openAny() уже открыл папку — повторный open() запрещён JavaMail.
                UIDFolder uf = (UIDFolder) folder;
                String folderName = folder.getFullName();
                long last = db.maxUid(acc.id, folderName);
                long lastFull = 0;
                boolean useIdle = canIdle(folder);
                db.log(L.t("Живое соединение"), acc.email + ": "
                        + (useIdle ? "IMAP IDLE" : L.t("опрос")) + ", UID " + last);

                while (running) {
                    if (fgRefs == 0 && acc.checkMin == 0) break;

                    long now = System.currentTimeMillis();
                    if (lastFull == 0 || now - lastFull > fullSyncEvery()) {
                        lastFull = now;
                        try {
                            MailService.SyncResult r = MailService.syncAccount(ctx, db, acc, true);
                            if (r.added > 0) announce(r.added);
                            long got = db.maxUid(acc.id, folderName);
                            if (got > last) last = got;
                        } catch (Throwable e) {
                            Log.w(TAG, "full sync: " + e);
                        }
                    }

                    Message[] fresh = null;
                    try {
                        fresh = uf.getMessagesByUID(last + 1, UIDFolder.LASTUID);
                    } catch (Exception e) {
                        break; // соединение отвалилось — переподключимся
                    }
                    if (fresh != null && fresh.length > 0) {
                        long max = last;
                        for (Message m : fresh) {
                            try {
                                max = Math.max(max, uf.getUID(m));
                            } catch (Exception ignored) {
                            }
                        }
                        last = max > last ? max : last + 1;
                        try {
                            MailService.SyncResult r = MailService.syncAccount(ctx, db, acc, true);
                            if (r.added > 0) announce(r.added);
                            long got = db.maxUid(acc.id, folderName);
                            if (got > last) last = got;
                        } catch (Throwable e) {
                            Log.w(TAG, "live sync: " + e);
                        }
                    }
                    if (useIdle) {
                        try {
                            if (running) idleWait((IMAPFolder) folder);
                            if (running) sleep(200);
                        } catch (Throwable e) {
                            Log.w(TAG, "idle: " + e);
                            useIdle = false;
                        }
                    } else {
                        sleep(interval());
                    }
                }
            } catch (Throwable e) {
                Log.w(TAG, "live: " + e);
                try {
                    db.log(L.t("Живое соединение"), L.t("ошибка: ") + e);
                } catch (Throwable ignored) {
                }
            } finally {
                try {
                    if (folder != null && folder.isOpen()) folder.close(false);
                } catch (Exception ignored) {
                }
                try {
                    if (store != null) store.close();
                } catch (Exception ignored) {
                }
            }
            sleep(60000);
        }
    }

    private boolean canIdle(Folder folder) {
        if (!(folder instanceof IMAPFolder)) return false;
        try {
            Store st = folder.getStore();
            return st instanceof IMAPStore && ((IMAPStore) st).hasCapability("IDLE");
        } catch (Throwable e) {
            return false;
        }
    }

    /** Ждём событие сервера. Возврат — есть новые письма, отключение или poke(). */
    private void idleWait(IMAPFolder f) throws Exception {
        poked = false;
        idleFolder = f;
        try {
            if (poked) return;
            f.idle();
        } finally {
            idleFolder = null;
        }
    }

    /** Снять IDLE из другого потока (JavaMail: idleAbort шлёт DONE в сокет). */
    private static void abortIdle(Folder f) {
        if (f == null) return;
        try {
            java.lang.reflect.Method m = Class.forName("com.sun.mail.imap.IMAPFolder")
                    .getDeclaredMethod("idleAbort");
            m.setAccessible(true);
            m.invoke(f);
        } catch (Throwable ignored) {
        }
    }

    /** Пауза между проверками: открытое приложение — 3 с, фон — из настроек аккаунта. */
    private long interval() {
        if (fgRefs > 0) return 3000;
        if (acc.checkMin < 0) return 30000;
        if (acc.checkMin > 0) return Math.max(30000, acc.checkMin * 60000L);
        return 300000;
    }

    private long fullSyncEvery() {
        return fgRefs > 0 ? 120000 : 600000;
    }

    private void announce(int added) {
        try {
            new Db(ctx).log(L.t("Новое письмо"), acc.email + ": " + added);
        } catch (Throwable ignored) {
        }
        if (fgRefs == 0) {
            try {
                Notifier.newMail(ctx);
            } catch (Throwable ignored) {
            }
        } else {
            notifyUi();
        }
    }

    private static void notifyUi() {
        MAIN.post(() -> {
            Listener l = listener;
            if (l == null) return;
            try {
                l.onNewMail();
            } catch (Throwable ignored) {
            }
        });
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // проснулись по poke() — сразу проверяем
        }
    }
}
