package com.dsh.mail;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;

import java.util.Calendar;

public final class Ui {

    private static final int[] PALETTE = {
            0xFF00A884, 0xFF53BDEB, 0xFFE542A3, 0xFFF4A460,
            0xFF7F66FF, 0xFF3A8EDB, 0xFFD64545, 0xFF4CAF50
    };

    private Ui() {
    }

    /** Разрешает цвет из темы (например R.attr.c_accent). */
    public static int attr(Context c, int attr) {
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(attr, tv, true)) return tv.data;
        return 0xFFFFFFFF;
    }

    /** Content под прозрачными статус-баром и навигацией (Android 15 edge-to-edge). */
    public static void edgeToEdge(final Activity a, int rootId) {
        final View root = a.findViewById(rootId);
        if (root == null) return;
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    /** Копирует текст в буфер обмена и подтверждает тостом. */
    public static void copy(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("DevMail", text == null ? "" : text));
        android.widget.Toast.makeText(c, L.t("Скопировано"), android.widget.Toast.LENGTH_SHORT).show();
    }

    public static int dp(Context c, float v) {
        return (int) (c.getResources().getDisplayMetrics().density * v + 0.5f);
    }

    public static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    /** Цвет из палитры по индексу (для аккаунтов: у каждого свой). */
    public static int palette(int index) {
        int n = PALETTE.length;
        return PALETTE[((index % n) + n) % n];
    }

    /** Кружок с обводкой — для «все аккаунты», чтобы не сливался с фоном. */
    public static GradientDrawable ring(int fill, int stroke, int width) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        d.setStroke(Math.max(1, width), stroke);
        return d;
    }

    /** Квадратная картинка, обрезанная в круг. */
    public static Bitmap circleBitmap(Bitmap src, int size) {
        if (src == null || size <= 0) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        int side = Math.min(w, h);
        Bitmap square = Bitmap.createBitmap(src, (w - side) / 2, (h - side) / 2, side, side);
        if (side != size) {
            Bitmap scaled = Bitmap.createScaledBitmap(square, size, size, true);
            if (scaled != square) square.recycle();
            square = scaled;
        }
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(square, BitmapShader.TileMode.CLAMP, BitmapShader.TileMode.CLAMP));
        float r = size / 2f;
        c.drawCircle(r, r, r, p);
        return out;
    }

    /** Человеческий размер файла: 812 B, 12.4 KB, 3.1 MB. */
    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 100) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.0f MB", mb);
    }

    /** Круг с инициалами — заглушка, когда фото контакта нет. */
    public static Bitmap letterBitmap(String text, int color, int size) {
        Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size * 0.38f);
        p.setFakeBoldText(true);
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = size / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(text == null ? "?" : text, size / 2f, y, p);
        return bm;
    }

    public static int avatarColor(String key) {
        int h = 7;
        if (key != null) {
            for (int i = 0; i < key.length(); i++) h = h * 31 + key.charAt(i);
        }
        return PALETTE[Math.abs(h) % PALETTE.length];
    }

    public static String initials(String name, String email) {
        String s = name != null && name.trim().length() > 0 ? name.trim()
                : (email == null ? "" : email.trim());
        String[] parts = s.split("[\\s@._-]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.length() > 0) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (sb.length() == 2) break;
            }
        }
        if (sb.length() == 0) return "?";
        return sb.toString();
    }

    public static String timeShort(long ts) {
        return DateFormat.format("HH:mm", ts).toString();
    }

    public static boolean sameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    /** Сколько календарных дней назад (0 — сегодня, 1 — вчера). */
    public static int daysAgo(long ts) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(System.currentTimeMillis());
        a.set(Calendar.HOUR_OF_DAY, 0);
        a.set(Calendar.MINUTE, 0);
        a.set(Calendar.SECOND, 0);
        a.set(Calendar.MILLISECOND, 0);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(ts);
        b.set(Calendar.HOUR_OF_DAY, 0);
        b.set(Calendar.MINUTE, 0);
        b.set(Calendar.SECOND, 0);
        b.set(Calendar.MILLISECOND, 0);
        return (int) Math.round((a.getTimeInMillis() - b.getTimeInMillis()) / 86400000.0);
    }

    public static String chatTime(long ts) {
        long now = System.currentTimeMillis();
        if (sameDay(now, ts)) return DateFormat.format("HH:mm", ts).toString();
        int days = daysAgo(ts);
        if (days == 1) return L.t("вчера");
        if (days >= 2 && days <= 6) {
            String wd = DateFormat.format("EEE", ts).toString();
            return L.isEn() ? wd : wd.toLowerCase(new java.util.Locale("ru", "RU"));
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        if (c.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
            return DateFormat.format("d MMM", ts).toString();
        }
        return DateFormat.format("dd.MM.yy", ts).toString();
    }

    public static String dayLabel(long ts) {
        long now = System.currentTimeMillis();
        if (sameDay(now, ts)) return L.t("Сегодня");
        Calendar y = Calendar.getInstance();
        y.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(y.getTimeInMillis(), ts)) return L.t("Вчера");
        return DateFormat.format("d MMMM yyyy", ts).toString();
    }
}
