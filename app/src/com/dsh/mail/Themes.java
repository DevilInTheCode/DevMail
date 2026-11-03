package com.dsh.mail;

import android.content.Context;
import android.content.SharedPreferences;

/** Выбор темы: ночь / день / neon / amoled / retro. */
public final class Themes {

    public static final int NIGHT = 0;
    public static final int DAY = 1;
    public static final int NEON = 2;
    public static final int AMOLED = 3;
    public static final int RETRO = 4;

    private static final String PREFS = "devmail";
    private static final String KEY = "theme";

    private Themes() {
    }

    public static int current(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int v = p.getInt(KEY, NIGHT);
        return v >= NIGHT && v <= RETRO ? v : NIGHT;
    }

    public static void set(Context c, int theme) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, theme).apply();
    }

    public static String name(Context c, int theme) {
        switch (theme) {
            case DAY:
                return L.t("День");
            case NEON:
                return "Neon";
            case AMOLED:
                return "AMOLED";
            case RETRO:
                return "Retro";
            default:
                return L.t("Ночь");
        }
    }

    public static String[] names(Context c) {
        return new String[]{name(c, NIGHT), name(c, DAY), name(c, NEON),
                name(c, AMOLED), name(c, RETRO)};
    }

    public static int style(Context c) {
        switch (current(c)) {
            case DAY:
                return R.style.Theme_DevMail_Day;
            case NEON:
                return R.style.Theme_DevMail_Neon;
            case AMOLED:
                return R.style.Theme_DevMail_Amoled;
            case RETRO:
                return R.style.Theme_DevMail_Retro;
            default:
                return R.style.Theme_DevMail_Night;
        }
    }
}
