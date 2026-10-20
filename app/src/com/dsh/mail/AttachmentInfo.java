package com.dsh.mail;

import java.util.Locale;

public class AttachmentInfo {
    public long id;
    public long messageId;
    public int part;
    public String name = "";
    public String mime = "";
    public long size;
    /** Куда сохранён файл (null — ещё не скачивали). */
    public String path;
    /** 1 — файл уже лежит на телефоне. */
    public int state;
    public int width;
    public int height;

    public boolean downloaded() {
        return state == 1 && path != null && path.length() > 0
                && new java.io.File(path).exists();
    }

    public boolean isImage() {
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("image/")) return true;
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp")
                || n.endsWith(".heic") || n.endsWith(".heif");
    }

    public String prettySize() {
        if (size <= 0) return "";
        if (size < 1024) return size + L.t(" Б");
        if (size < 1024 * 1024) return (size / 1024) + L.t(" КБ");
        return String.format(L.t("%.1f МБ"), size / 1048576.0);
    }
}
