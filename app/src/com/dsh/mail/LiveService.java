package com.dsh.mail;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Фоновое соединение с почтой.
 *
 * Держит IMAP-сеанс живым после сворачивания приложения и проверяет новые
 * письма раз в 30 секунд. Работает как foreground service, поэтому система
 * его не убивает; в шторке висит тихое уведомление с кнопкой «Отключить».
 */
public class LiveService extends Service {

    private static final String CH = "live";
    private static final int ID = 2;
    static final String ACTION_STOP = "com.dsh.mail.STOP_LIVE";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            startForeground(ID, notification());
        } catch (Throwable ignored) {
        }
        try {
            LiveSync.startBackground(this);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            try {
                LiveSync.stopBackground();
            } catch (Throwable ignored) {
            }
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForeground(ID, notification());
        } catch (Throwable ignored) {
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            LiveSync.stopBackground();
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Запустить или остановить фон в соответствии с настройками аккаунтов. */
    public static void apply(Context ctx) {
        try {
            boolean need = false;
            for (Account a : new Db(ctx).accounts()) {
                // «мгновенно» и интервалы меньше 15 минут: JobScheduler так часто
                // не умеет, поэтому держим соединение сервисом.
                if (a.checkMin < 0 || (a.checkMin > 0 && a.checkMin < 15)) {
                    need = true;
                    break;
                }
            }
            Intent svc = new Intent(ctx, LiveService.class);
            if (need) {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc);
                else ctx.startService(svc);
            } else {
                ctx.stopService(svc);
            }
        } catch (Throwable ignored) {
        }
    }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, L.t("Соединение"), NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 10, open, flags);

        Intent stop = new Intent(this, LiveService.class).setAction(ACTION_STOP);
        PendingIntent ps = PendingIntent.getService(this, 11, stop, flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("DevMail")
                .setContentText(L.t("Почта проверяется в реальном времени"))
                .setContentIntent(pi)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, L.t("Отключить"), ps).build());
        return b.build();
    }
}
