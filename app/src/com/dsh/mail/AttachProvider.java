package com.dsh.mail;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

/** Отдаёт вложения из кэша другим приложениям по content:// URI. */
public class AttachProvider extends ContentProvider {

    public static final String AUTHORITY = "com.dsh.mail.files";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException("no path");
        String[] parts = path.split("/");
        if (parts.length < 3) throw new FileNotFoundException(uri.toString());
        String dirName = parts[1];
        String name = Uri.decode(parts[2]);
        File dir = "att".equals(dirName)
                ? new File(getContext().getFilesDir(), "att")
                : new File(getContext().getCacheDir(), dirName);
        File f = new File(dir, name);
        if ("w".equals(mode) || "rw".equals(mode) || "wt".equals(mode)) {
            if (!dir.exists()) dir.mkdirs();
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        if (!f.exists()) throw new FileNotFoundException(f.getAbsolutePath());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.getPath();
        if (path == null) return "*/*";
        int dot = path.lastIndexOf('.');
        if (dot < 0) return "*/*";
        String ext = path.substring(dot + 1).toLowerCase();
        String t = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return t == null ? "*/*" : t;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
