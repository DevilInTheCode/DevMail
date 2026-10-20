package com.dsh.mail;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class Util {

    private Util() {
    }

    /** Копирует выбранный через SAF файл в кэш приложения. */
    public static File copyToCache(Context ctx, Uri uri, String subdir) {
        String name = "file";
        try {
            Cursor c = ctx.getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (c.moveToFirst() && idx >= 0) name = c.getString(idx);
                c.close();
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.trim().isEmpty()) name = "file";
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        try {
            File dir = new File(ctx.getCacheDir(), subdir);
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, name);
            if (out.exists()) out = new File(dir, System.currentTimeMillis() + "_" + name);
            InputStream in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            in.close();
            fos.close();
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
