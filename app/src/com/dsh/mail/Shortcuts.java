package com.dsh.mail;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Быстрые действия по долгому нажатию на иконку: «Новое письмо» и последние
 * контакты с аватарками-буквами (как в мессенджерах).
 */
public final class Shortcuts {

    private static final int CONTACTS = 3;
    private static final int[] COLORS = {
            0xFF3D7EFF, 0xFF00BFA5, 0xFFFF7043, 0xFFAB47BC,
            0xFF26A69A, 0xFFEF5350, 0xFF5C6BC0, 0xFF8D6E63
    };
    private static String lastSignature = null;

    private Shortcuts() {
    }

    public static void update(Context ctx, List<ChatItem> chats) {
        if (Build.VERSION.SDK_INT < 25) return;
        try {
            Context app = ctx.getApplicationContext();
            StringBuilder sig = new StringBuilder();
            List<ShortcutInfo> out = new ArrayList<ShortcutInfo>();
            int rank = 1;
            for (ChatItem it : chats) {
                if (it == null || Db.DEBUG_THREAD.equals(it.threadKey)) continue;
                String name = it.name == null ? "" : it.name.trim();
                if (name.isEmpty()) continue;
                if (rank > CONTACTS) break;
                sig.append(it.threadKey).append('|').append(name).append('|')
                        .append(it.accountId).append(';');
                Intent i = new Intent(app, ChatActivity.class);
                i.setAction(Intent.ACTION_VIEW);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra("thread", it.threadKey);
                i.putExtra("name", name);
                i.putExtra("email", it.email);
                i.putExtra("account", it.accountId);
                String id = "chat" + Integer.toHexString(it.threadKey.hashCode());
                out.add(new ShortcutInfo.Builder(app, id)
                        .setShortLabel(shortName(name))
                        .setLongLabel(L.t("Написать") + ": " + name)
                        .setRank(rank)
                        .setIcon(letterIcon(app, name, colorFor(name), false))
                        .setIntent(i)
                        .build());
                rank++;
            }
            String s = sig.toString();
            if (s.equals(lastSignature)) return;
            lastSignature = s;

            Intent compose = new Intent(app, ComposeActivity.class);
            compose.setAction(Intent.ACTION_VIEW);
            compose.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            out.add(0, new ShortcutInfo.Builder(app, "compose")
                    .setShortLabel(L.t("Новое письмо"))
                    .setLongLabel(L.t("Новое письмо"))
                    .setRank(0)
                    .setIcon(letterIcon(app, "+", 0xFF25A56A, true))
                    .setIntent(compose)
                    .build());
            ((ShortcutManager) app.getSystemService(Context.SHORTCUT_SERVICE))
                    .setDynamicShortcuts(out);
        } catch (Throwable ignored) {
            // Лаунчер может не поддерживать динамические ярлыки — не роняем приложение.
        }
    }

    private static String shortName(String name) {
        String s = name.trim();
        int at = s.indexOf('@');
        if (at > 0) s = s.substring(0, at);
        if (s.length() > 14) s = s.substring(0, 13) + "…";
        return s;
    }

    private static int colorFor(String name) {
        int h = name == null ? 0 : name.hashCode();
        return COLORS[Math.abs(h) % COLORS.length];
    }

    private static android.graphics.drawable.Icon letterIcon(Context ctx, String text,
                                                            int color, boolean plus) {
        int size = Math.max(96, (int) (ctx.getResources().getDisplayMetrics().density * 48));
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawOval(new RectF(0, 0, size, size), p);
        p.setColor(Color.WHITE);
        if (plus) {
            p.setStrokeWidth(size * 0.09f);
            p.setStrokeCap(Paint.Cap.ROUND);
            float m = size * 0.5f, arm = size * 0.2f;
            c.drawLine(m - arm, m, m + arm, m, p);
            c.drawLine(m, m - arm, m, m + arm, p);
        } else {
            String letter = text == null || text.isEmpty() ? "?" : text.substring(0, 1).toUpperCase();
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setTextSize(size * 0.46f);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(letter, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p);
        }
        return android.graphics.drawable.Icon.createWithBitmap(bmp);
    }
}
