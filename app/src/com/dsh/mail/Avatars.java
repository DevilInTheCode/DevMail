package com.dsh.mail;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Фотографии контактов. Почта не отдаёт аватарки, поэтому фото задаётся вручную:
 * из галереи оно копируется в files/avatars/<md5 e-mail> и дальше показывается
 * в списке чатов, в шапке переписки и в карточке собеседника.
 */
public final class Avatars {

    private static final int MAX_SIDE = 512;
    private static final Map<String, Bitmap> MEM = new HashMap<String, Bitmap>();

    private Avatars() {
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "avatars");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] raw = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                int v = b & 0xFF;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String key(String email) {
        return md5(email == null ? "" : email.trim().toLowerCase());
    }

    public static File file(Context c, String email) {
        return new File(dir(c), key(email) + ".img");
    }

    public static boolean has(Context c, String email) {
        File f = file(c, email);
        return f.exists() && f.length() > 0;
    }

    /** Круглая аватарка из файла или null. */
    public static Bitmap load(Context c, String email, int size) {
        if (email == null || email.length() == 0) return null;
        String k = key(email) + ":" + size;
        Bitmap cached = MEM.get(k);
        if (cached != null && !cached.isRecycled()) return cached;
        File f = file(c, email);
        if (!f.exists() || f.length() == 0) return null;
        Bitmap bm = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            int sample = 1;
            int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxSide / (sample * 2) >= size * 2) sample *= 2;
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = sample;
            bm = BitmapFactory.decodeFile(f.getAbsolutePath(), opt);
        } catch (Throwable ignored) {
        }
        if (bm == null) return null;
        Bitmap round = Ui.circleBitmap(bm, size);
        if (round != bm) bm.recycle();
        MEM.put(k, round);
        return round;
    }

    /** Сохраняет выбранную картинку как аватар контакта. */
    public static boolean save(Context c, String email, Uri uri) {
        if (email == null || uri == null) return false;
        byte[] data;
        try {
            ContentResolver cr = c.getContentResolver();
            InputStream in = cr.openInputStream(uri);
            if (in == null) return false;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[16 * 1024];
            int n;
            while ((n = in.read(tmp)) > 0) buf.write(tmp, 0, n);
            in.close();
            data = buf.toByteArray();
        } catch (Throwable e) {
            return false;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int sample = 1;
        int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
        while (maxSide / (sample * 2) >= MAX_SIDE) sample *= 2;
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inSampleSize = sample;
        Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length, opt);
        if (bm == null) return false;
        int rotate = 0;
        try {
            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(data));
            int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) rotate = 90;
            else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotate = 180;
            else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotate = 270;
        } catch (Throwable ignored) {
        }
        if (rotate != 0) {
            Matrix m = new Matrix();
            m.postRotate(rotate);
            Bitmap rot = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
            if (rot != bm) bm.recycle();
            bm = rot;
        }
        int w = bm.getWidth();
        int h = bm.getHeight();
        int side = Math.min(w, h);
        if (side > MAX_SIDE) {
            Bitmap scaled = Bitmap.createScaledBitmap(bm, MAX_SIDE, MAX_SIDE, true);
            bm.recycle();
            bm = scaled;
            side = MAX_SIDE;
        }
        boolean alpha = bm.hasAlpha();
        File out = file(c, email);
        try {
            FileOutputStream fos = new FileOutputStream(out);
            bm.compress(alpha ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
        } catch (Throwable e) {
            return false;
        } finally {
            bm.recycle();
        }
        clearMemory();
        return out.length() > 0;
    }

    public static void remove(Context c, String email) {
        File f = file(c, email);
        if (f.exists()) f.delete();
        clearMemory();
    }

    public static void clearMemory() {
        MEM.clear();
    }
}
