package com.dsh.mail;

import android.app.Application;
import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

/**
 * Перехват падений: стек пишется в файл, а при следующем запуске
 * копируется в буфер обмена и показывается тост — так отчёт можно
 * просто прислать, не подключая телефон к компьютеру.
 */
public class App extends Application {

    private static final String CRASH_FILE = "last_crash.txt";
    private static final String CRASH_SEEN = "last_crash_seen.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        L.init(this);
        installHandler();
    }

    private void installHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                String text = buildReport(thread, error);
                FileOutputStream fos = new FileOutputStream(new File(getFilesDir(), CRASH_FILE));
                try {
                    fos.write(text.getBytes("UTF-8"));
                } finally {
                    fos.close();
                }
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private static String buildReport(Thread thread, Throwable error) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("DevMail crash at " + new Date());
        pw.println("thread: " + thread.getName());
        pw.println("version: " + version());
        error.printStackTrace(pw);
        Throwable cause = error.getCause();
        int depth = 0;
        while (cause != null && depth < 3) {
            pw.println("--- cause ---");
            cause.printStackTrace(pw);
            cause = cause.getCause();
            depth++;
        }
        pw.flush();
        String text = sw.toString();
        return text.length() > 20000 ? text.substring(0, 20000) : text;
    }

    public static String version() {
        return "30/3.0";
    }

    /** Отчёт о прошлом падении, если он ещё не показан. */
    public static String pendingCrash(Context c) {
        try {
            File crash = new File(c.getFilesDir(), CRASH_FILE);
            if (!crash.exists()) return null;
            String text = read(crash);
            String seen = read(new File(c.getFilesDir(), CRASH_SEEN));
            if (text.trim().length() == 0 || text.equals(seen)) return null;
            return text;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Помечает отчёт показанным, чтобы диалог не всплывал каждый раз. */
    public static void markCrashSeen(Context c, String text) {
        try {
            FileOutputStream fos = new FileOutputStream(new File(c.getFilesDir(), CRASH_SEEN));
            try {
                fos.write(text.getBytes("UTF-8"));
            } finally {
                fos.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static String read(File f) {
        if (!f.exists()) return "";
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[(int) f.length()];
                int n = in.read(buf);
                return n > 0 ? new String(buf, 0, n, "UTF-8") : "";
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return "";
        }
    }
}
