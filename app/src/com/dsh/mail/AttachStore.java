package com.dsh.mail;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * Вложения: автозагрузка по лимиту из настроек, сжатие картинок и превью в чате.
 *
 * Файл письма целиком лежит в files/raw/*.eml, поэтому «скачивание» — это распаковка
 * нужной MIME-части из него (без обращения к серверу) с сохранением в files/att/.
 */
public class AttachStore {

    /** Уровни настройки «Автозагрузка сообщений». */
    public static final int ALL = 0;
    public static final int K160 = 1;
    public static final int K640 = 2;
    public static final int M5 = 3;
    public static final int M25 = 4;

    private static final long KB = 1024L;
    private static final long MB = 1024L * 1024L;
    private static LruCache<String, Bitmap> cache;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE);
    }

    public static int level(Context c) {
        int v = prefs(c).getInt("auto_dl", K640);
        return v < 0 || v > M25 ? K640 : v;
    }

    public static void setLevel(Context c, int lv) {
        prefs(c).edit().putInt("auto_dl", lv).apply();
        clearCache();
    }

    public static String[] labels() {
        return new String[]{
                L.t("Все"),
                L.t("До 160 КБ, изображения плохого качества"),
                L.t("До 640 КБ, изображения среднего качества"),
                L.t("До 5 МБ"),
                L.t("До 25 МБ")};
    }

    public static long limitBytes(int lv) {
        switch (lv) {
            case K160: return 160 * KB;
            case K640: return 640 * KB;
            case M5: return 5 * MB;
            case M25: return 25 * MB;
            default: return Long.MAX_VALUE;
        }
    }

    /** Сколько максимум держать на диске сырое письмо ради вложений. */
    public static long maxRaw(int lv) {
        if (lv == ALL) return 48 * MB;
        long need = limitBytes(lv) * 4 / 3 + 3 * MB;
        return Math.min(48 * MB, Math.max(12 * MB, need));
    }

    private static boolean lowQuality(int lv) {
        return lv == K160;
    }

    private static int targetBytes(int lv) {
        if (lv == K160) return (int) (160 * KB);
        if (lv == K640) return (int) (640 * KB);
        return 0;
    }

    private static int maxDim(int lv) {
        if (lv == K160) return 720;
        if (lv == K640) return 1280;
        if (lv == M5 || lv == M25) return 2560;
        return 0;
    }

    private static int firstQuality(int lv) {
        if (lv == K160) return 55;
        if (lv == K640) return 72;
        return 85;
    }

    /** Нужно ли тянуть вложение сразу после синхронизации. */
    public static boolean shouldAuto(Context c, AttachmentInfo a) {
        int lv = level(c);
        // Картинки при «160/640 КБ» качаем всегда, но ужимаем до лимита.
        if (a.isImage() && (lv == K160 || lv == K640)) return true;
        long lim = limitBytes(lv);
        if (lim == Long.MAX_VALUE) return true;
        return a.size > 0 && a.size <= lim;
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "att");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String safeName(String name) {
        String n = name == null || name.trim().length() == 0 ? "file" : name.trim();
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    // ---------------- скачивание ----------------

    /**
     * Достаёт вложение из сохранённого письма, при необходимости сжимает картинку
     * и запоминает путь в базе. Вызывать не из UI-потока.
     */
    public static AttachmentInfo fetch(Context c, Db db, MailMessage msg, AttachmentInfo a) throws Exception {
        if (a.downloaded()) return a;
        if (a.part < 0) throw new Exception(L.t("вложение не найдено в письме"));
        if (msg.rawPath == null || !new File(msg.rawPath).exists()) {
            MailService.refetchRaw(c, db, msg);
        }
        File src = MailService.extractPart(c, msg, a);
        File out;
        int lv = level(c);
        if (a.isImage() && (lv == K160 || lv == K640)) {
            out = compress(c, src, a, lv);
            if (out == null) out = copy(c, src, a);
        } else {
            out = copy(c, src, a);
        }
        try {
            src.delete();
        } catch (Exception ignored) {
        }
        a.path = out.getAbsolutePath();
        a.state = 1;
        a.size = out.length();
        if (a.width <= 0 || a.height <= 0) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try {
                BitmapFactory.decodeFile(a.path, o);
                a.width = o.outWidth;
                a.height = o.outHeight;
            } catch (Throwable ignored) {
            }
        }
        db.updateAttachment(a);
        return a;
    }

    /** Сохраняет файл, который уже есть на телефоне (например, только что отправленный). */
    public static AttachmentInfo storeLocal(Context c, Db db, AttachmentInfo a, File src) throws Exception {
        File out = copy(c, src, a);
        a.path = out.getAbsolutePath();
        a.state = 1;
        a.size = out.length();
        if (a.mime == null || a.mime.length() == 0) {
            String n = a.name == null ? "" : a.name.toLowerCase(Locale.ROOT);
            int dot = n.lastIndexOf('.');
            String ext = dot > 0 ? n.substring(dot + 1) : "";
            String t = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (t != null) a.mime = t;
        }
        if (a.isImage()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try {
                BitmapFactory.decodeFile(a.path, o);
                a.width = o.outWidth;
                a.height = o.outHeight;
            } catch (Throwable ignored) {
            }
        }
        db.updateAttachment(a);
        return a;
    }

    private static File copy(Context c, File src, AttachmentInfo a) throws Exception {
        File out = new File(dir(c), "att" + a.id + "_" + safeName(a.name));
        InputStream in = new java.io.FileInputStream(src);
        OutputStream os = new FileOutputStream(out);
        try {
            byte[] buf = new byte[16384];
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

    /** Сжимает картинку под лимит уровня. null — если не получилось. */
    private static File compress(Context c, File src, AttachmentInfo a, int lv) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(src.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int dim = maxDim(lv);
            int sample = 1;
            if (dim > 0) {
                while (bounds.outWidth / (sample * 2) >= dim
                        && bounds.outHeight / (sample * 2) >= dim) {
                    sample *= 2;
                }
            }
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = sample;
            Bitmap bm = BitmapFactory.decodeFile(src.getAbsolutePath(), opt);
            if (bm == null) return null;

            if (dim > 0) {
                int w = bm.getWidth();
                int h = bm.getHeight();
                int big = Math.max(w, h);
                if (big > dim) {
                    float k = (float) dim / big;
                    Bitmap scaled = Bitmap.createScaledBitmap(bm, Math.max(1, Math.round(w * k)),
                            Math.max(1, Math.round(h * k)), true);
                    if (scaled != bm) bm.recycle();
                    bm = scaled;
                }
            }

            boolean alpha = bm.hasAlpha();
            boolean webp = alpha || (a.mime != null && a.mime.toLowerCase(Locale.ROOT).contains("png"));
            Bitmap.CompressFormat fmt = webp ? Bitmap.CompressFormat.WEBP : Bitmap.CompressFormat.JPEG;
            String ext = webp ? ".webp" : ".jpg";

            int target = targetBytes(lv);
            int q = firstQuality(lv);
            byte[] data = null;
            while (true) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bm.compress(fmt, Math.max(30, q), bos);
                data = bos.toByteArray();
                if (target <= 0 || data.length <= target || q <= 32) break;
                q -= 8;
            }
            int w = bm.getWidth();
            int h = bm.getHeight();
            bm.recycle();

            // Если «сжатие» вышло больше исходника — оставляем исходник как есть.
            if (data == null || (target <= 0 && data.length >= src.length())) return null;

            File out = new File(dir(c), "att" + a.id + ext);
            FileOutputStream fos = new FileOutputStream(out);
            try {
                fos.write(data);
            } finally {
                fos.close();
            }
            a.width = w;
            a.height = h;
            a.mime = webp ? "image/webp" : "image/jpeg";
            return out;
        } catch (Throwable e) {
            Log.w("AttachStore", "compress: " + e);
            return null;
        }
    }

    /** Автозагрузка всех подходящих вложений письма (вызывать из фонового потока). */
    public static void autoFetch(Context c, Db db, MailMessage msg, long msgId) {
        if (msg == null || msg.rawPath == null) return;
        try {
            List<AttachmentInfo> list = db.attachments(msgId);
            for (AttachmentInfo a : list) {
                if (a.downloaded() || !shouldAuto(c, a)) continue;
                try {
                    fetch(c, db, msg, a);
                } catch (Exception e) {
                    Log.w("AttachStore", "auto " + a.name + ": " + e);
                }
            }
        } catch (Throwable e) {
            Log.w("AttachStore", "autoFetch: " + e);
        }
    }

    // ---------------- превью ----------------

    private static LruCache<String, Bitmap> cache() {
        if (cache == null) {
            cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap b) {
                    return b == null ? 0 : b.getByteCount();
                }
            };
        }
        return cache;
    }

    public static void clearCache() {
        if (cache != null) {
            cache.evictAll();
        }
    }

    /** Миниатюра для пузыря; null — файла нет или это не картинка. */
    public static Bitmap thumb(AttachmentInfo a, int maxPx) {
        if (a == null || !a.downloaded()) return null;
        String key = a.path + "@" + maxPx;
        Bitmap b = cache().get(key);
        if (b != null) return b;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(a.path, bounds);
            if (bounds.outWidth <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= maxPx
                    && bounds.outHeight / (sample * 2) >= maxPx) {
                sample *= 2;
            }
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = sample;
            Bitmap bm = BitmapFactory.decodeFile(a.path, opt);
            if (bm != null) cache().put(key, bm);
            return bm;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Удаляет скачанные файлы переписки (при удалении чата). */
    /** Сколько занимают скачанные вложения и временные файлы. */
    public static long cacheSize(Context c) {
        long total = sizeOf(dir(c));
        total += sizeOf(new File(c.getFilesDir(), "out"));
        total += sizeOf(c.getCacheDir());
        return total;
    }

    private static long sizeOf(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        File[] kids = f.listFiles();
        if (kids == null) return 0;
        long total = 0;
        for (File k : kids) total += sizeOf(k);
        return total;
    }

    private static void wipe(File f) {
        if (f == null || !f.exists()) return;
        if (f.isFile()) {
            f.delete();
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) wipe(k);
        }
    }

    /**
     * Чистит кеш приложения: все скачанные вложения, копии для «поделиться» и
     * временные файлы. Сами письма (files/raw) и аватарки остаются, поэтому
     * вложение можно скачать заново из сохранённого письма.
     */
    public static long clearAll(Context c, Db db) {
        long freed = cacheSize(c);
        wipe(dir(c));
        wipe(new File(c.getFilesDir(), "out"));
        wipe(c.getCacheDir());
        clearCache();
        if (db != null) db.clearAttachments();
        return freed;
    }

    public static void deleteThread(Db db, String threadKey) {
        try {
            List<String> paths = db.attachmentPaths(threadKey);
            for (String p : paths) {
                if (p == null || p.length() == 0) continue;
                try {
                    new File(p).delete();
                } catch (Exception ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        clearCache();
    }

    /** Размер файла на диске, если он есть. */
    public static long localSize(AttachmentInfo a) {
        try {
            return a.downloaded() ? new File(a.path).length() : 0;
        } catch (Throwable e) {
            return 0;
        }
    }
}
