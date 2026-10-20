package com.dsh.mail;

import android.content.Context;
import android.content.SharedPreferences;

/** Выбор темы: ночь / день / киберпанк. */
public final class Themes {

    public static final int NIGHT = 0;
    public static final int DAY = 1;
    public static final int CYBER = 2;

    private static final String PREFS = "devmail";
    private static final String KEY = "theme";

    private Themes() {
    }

    public static int current(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getInt(KEY, NIGHT);
    }

    public static void set(Context c, int theme) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, theme).apply();
    }

    public static int style(Context c) {
        switch (current(c)) {
            case DAY:
                return R.style.Theme_DevMail_Day;
            case CYBER:
                return R.style.Theme_DevMail_Cyber;
            default:
                return R.style.Theme_DevMail_Night;
        }
    }
}
