package com.dsh.mail;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private int verTaps;
    private long lastVerTap;

    private static final String STATE_TAB = "tab";

    private Db db;
    private ChatAdapter chatAdapter;
    private ListView listChats;
    private View settingsPanel;
    private View empty;
    private TextView title;
    private TextView tabChats;
    private TextView tabSettings;
    private int tab;
    private boolean syncing;
    private View searchBar;
    private EditText searchInput;
    private String query = "";
    private boolean selMode;
    private ListSwipe swipe;
    private final Set<String> selected = new LinkedHashSet<String>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    /** Выбранный аккаунт для списка чатов: -1 — все аккаунты. */
    private long selAccount = -1;
    private TextView btnAccount;

    @Override
    protected void onCreate(Bundle b) {
        setTheme(Themes.style(this));
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        L.localize(findViewById(android.R.id.content));
        Ui.edgeToEdge(this, R.id.root);
        db = new Db(this);
        try {
            String vn = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            db.log(L.t("Запуск"), "DevMail " + vn + " · Android " + Build.VERSION.RELEASE
                    + L.t(" · аккаунтов: ") + db.accounts().size());
        } catch (Throwable ignored) {
        }

        title = (TextView) findViewById(R.id.title);
        btnAccount = (TextView) findViewById(R.id.btnAccount);
        selAccount = getSharedPreferences("ui", MODE_PRIVATE).getLong("acct", -1);
        if (btnAccount != null) btnAccount.setOnClickListener(v -> showAccountPicker());
        empty = findViewById(R.id.empty);
        settingsPanel = findViewById(R.id.settingsPanel);
        searchBar = findViewById(R.id.searchBar);
        searchInput = (EditText) findViewById(R.id.searchInput);
        findViewById(R.id.btnSearch).setOnClickListener(v -> toggleSearch());
        findViewById(R.id.btnSearchClear).setOnClickListener(v -> {
            searchInput.setText("");
            toggleSearch();
        });
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                query = e.toString().trim();
                reload();
            }
        });

        listChats = (ListView) findViewById(R.id.listChats);
        chatAdapter = new ChatAdapter(this);
        listChats.setAdapter(chatAdapter);
        swipe = new ListSwipe(listChats, false,
                row -> row instanceof android.view.ViewGroup
                        && ((android.view.ViewGroup) row).getChildCount() > 1
                        ? ((android.view.ViewGroup) row).getChildAt(1) : row,
                (pos, dir) -> {
                    if (selMode || pos < 0 || pos >= chatAdapter.getCount()) return;
                    ChatItem it = chatAdapter.item(pos);
                    if (dir > 0) {
                        db.markThreadSeen(it.threadKey);
                        Notifier.cancelThread(MainActivity.this, it.threadKey);
                        reload();
                        Toast.makeText(MainActivity.this, L.t("Прочитано") + ": " + it.name,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        confirmDeleteChat(it);
                    }
                });
        swipe.attach();
        listChats.setOnItemClickListener((p, v, pos, id) -> {
            if (selMode) {
                toggleSelection(chatAdapter.item(pos));
                return;
            }
            ChatItem it = chatAdapter.item(pos);
            Intent i = new Intent(MainActivity.this, ChatActivity.class);
            i.putExtra("thread", it.threadKey);
            i.putExtra("name", it.name);
            i.putExtra("email", it.email);
            i.putExtra("account", it.accountId);
            startActivity(i);
        });

        listChats.setOnItemLongClickListener((p, v, pos, id) -> {
            if (swipe != null && swipe.isActive()) return true;
            if (!selMode && query.length() > 0) {
                showChatMenu(chatAdapter.item(pos));
                return true;
            }
            toggleSelection(chatAdapter.item(pos));
            return true;
        });

        findViewById(R.id.btnSelRead).setOnClickListener(v -> markSelectedRead());
        findViewById(R.id.btnSelDelete).setOnClickListener(v -> deleteSelected());
        findViewById(R.id.btnSelCancel).setOnClickListener(v -> exitSelection());
        findViewById(R.id.btnSelMenu).setOnClickListener(v -> openSelectedMenu());

        findViewById(R.id.btnAccounts).setOnClickListener(v ->
                startActivity(new Intent(this, AccountsActivity.class)));
        findViewById(R.id.btnClearCache).setOnClickListener(v -> confirmClearCache());

        findViewById(R.id.btnNew).setOnClickListener(v -> {
            if (db.accounts().isEmpty()) {
                startActivity(new Intent(this, AccountEditActivity.class));
            } else {
                startActivity(new Intent(this, ComposeActivity.class));
            }
        });
        findViewById(R.id.btnRefresh).setOnClickListener(v -> syncNow());
        findViewById(R.id.btnAddAccount).setOnClickListener(v ->
                startActivity(new Intent(this, AccountEditActivity.class)));

        tabChats = (TextView) findViewById(R.id.tabChats);
        tabSettings = (TextView) findViewById(R.id.tabSettings);
        tabChats.setOnClickListener(v -> showTab(0));
        tabSettings.setOnClickListener(v -> showTab(1));

        findViewById(R.id.btnLangRu).setOnClickListener(v -> pickLang(L.RU));
        findViewById(R.id.btnLangEn).setOnClickListener(v -> pickLang(L.EN));

        findViewById(R.id.themeNight).setOnClickListener(v -> pickTheme(Themes.NIGHT));
        findViewById(R.id.themeDay).setOnClickListener(v -> pickTheme(Themes.DAY));
        findViewById(R.id.themeCyber).setOnClickListener(v -> pickTheme(Themes.CYBER));

        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
                }
            } catch (Exception ignored) {
            }
        }

        tab = b == null ? 0 : b.getInt(STATE_TAB, 0);
        showTab(tab);
        reload();
        applyThemeChips();
        applyLangChips();
        applyAutoDl();
        applyMailLog();
        reportMigration();
        setupVersion();
        Schedule.reschedule(this);
        if (!db.accounts().isEmpty() && b == null) syncNow();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, tab);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LiveSync.acquire(this);
        startLiveService();
    }

    private void startLiveService() {
        LiveService.apply(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LiveSync.release();
    }

    @Override
    protected void onPause() {
        LiveSync.setListener(null);
        super.onPause();
    }

    private void onLiveMail() {
        if (!isFinishing()) reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LiveSync.setListener(this::onLiveMail);
        Notifier.cancelAll(this);
        LiveSync.poke();
        reload();
        maybeSync();
        showCrashReport();
    }

    /** Показываем отчёт о прошлом падении с кнопкой «Скопировать». */
    private void showCrashReport() {
        final String text = App.pendingCrash(this);
        if (text == null) return;
        App.markCrashSeen(this, text);
        db.log(L.t("Падение"), text);
        if (isFinishing()) return;
        String shown = text.length() > 6000 ? text.substring(0, 6000) + "\n…" : text;
        new AlertDialog.Builder(this)
                .setTitle(L.t("Отчёт о падении"))
                .setMessage(shown)
                .setPositiveButton(L.t("Скопировать"), (d, w) -> Ui.copy(this, text))
                .setNegativeButton(L.t("Закрыть"), null)
                .show();
    }

    /** Проверяем почту при возврате в приложение, если прошлая синхронизация была давно. */
    /** Полный текст ошибки: класс, сообщение, стек и цепочка причин. */
    static String crashReport(String where, Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        pw.println(L.t("Где: ") + where);
        pw.println(L.t("Класс: ") + t.getClass().getName());
        pw.println(L.t("Сообщение: ") + t.getMessage());
        t.printStackTrace(pw);
        Throwable c = t.getCause();
        int depth = 0;
        while (c != null && depth < 5) {
            pw.println(L.t("--- причина ---"));
            c.printStackTrace(pw);
            c = c.getCause();
            depth++;
        }
        pw.flush();
        String text = sw.toString();
        return text.length() > 20000 ? text.substring(0, 20000) : text;
    }

    private void maybeSync() {
        if (syncing) return;
        List<Account> accs = db.accounts();
        if (accs.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Account a : accs) {
            if (a.lastSync == 0 || now - a.lastSync > 60000) {
                syncNow();
                return;
            }
        }
    }

    /** Низ настроек: версия; три тапа по ней — журнал отладки туда-обратно. */
    private void applyAutoDl() {
        TextView t = (TextView) findViewById(R.id.btnAutoDl);
        if (t == null) return;
        t.setText(L.t("Автозагрузка сообщений") + ": "
                + AttachStore.labels()[AttachStore.level(this)]);
        t.setOnClickListener(v -> showAutoDlDialog());
    }

    /** Строка «Подробный журнал писем»: включает запись заголовков каждого письма. */
    private void applyMailLog() {
        TextView t = (TextView) findViewById(R.id.btnMailLog);
        if (t == null) return;
        boolean on = getSharedPreferences(L.PREF, MODE_PRIVATE).getBoolean("mail_log", false);
        t.setText(L.t("Подробный журнал писем") + ": " + L.t(on ? "вкл" : "выкл"));
        t.setOnClickListener(v -> {
            boolean now = !getSharedPreferences(L.PREF, MODE_PRIVATE).getBoolean("mail_log", false);
            getSharedPreferences(L.PREF, MODE_PRIVATE).edit().putBoolean("mail_log", now).apply();
            applyMailLog();
            Toast.makeText(this, L.t(now ? "Журнал писем: подробно" : "Журнал писем: кратко"),
                    Toast.LENGTH_SHORT).show();
        });
    }

    /** Отчёт миграции: сколько старых писем потеряли адрес и куда они переехали. */
    private void reportMigration() {
        android.content.SharedPreferences sp = getSharedPreferences(L.PREF, MODE_PRIVATE);
        int n = sp.getInt("bad_peers", 0);
        if (n <= 0) return;
        sp.edit().remove("bad_peers").apply();
        db.log(L.t("Обслуживание"), L.t("Писем с нераспознанным адресом: ") + n
                + L.t(". Они собраны в ветки «Неизвестный отправитель» и «Скрытые получатели»."
                + " Подробности — в новом журнале писем."));
    }

    private void showAutoDlDialog() {
        new AlertDialog.Builder(this)
                .setTitle(L.t("Автозагрузка сообщений"))
                .setSingleChoiceItems(AttachStore.labels(), AttachStore.level(this),
                        (d, which) -> {
                            AttachStore.setLevel(this, which);
                            applyAutoDl();
                            d.dismiss();
                        })
                .setNegativeButton(L.t("Отмена"), null)
                .show();
    }

    private void setupVersion() {
        TextView v = (TextView) findViewById(R.id.appVersion);
        String raw = App.version();
        String nice = raw.contains("/") ? raw.substring(raw.indexOf('/') + 1) + " · " + raw.substring(0, raw.indexOf('/')) : raw;
        v.setText("DevMail " + nice);
        v.setOnClickListener(x -> {
            long now = System.currentTimeMillis();
            if (now - lastVerTap > 1200) verTaps = 0;
            lastVerTap = now;
            verTaps++;
            if (verTaps >= 3) {
                verTaps = 0;
                toggleDebugChat();
            }
        });
    }

    private boolean debugChatVisible() {
        // По умолчанию отладка выключена: включается только тройным тапом по версии.
        return getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("debug_chat", false);
    }

    private void toggleDebugChat() {
        boolean on = !debugChatVisible();
        getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("debug_chat", on).apply();
        if (on) {
            // чтобы чат появился сразу, даже если журнал ещё пуст
            db.log(L.t("Отладка"), L.t("Журнал отладки включён"));
        } else {
            Notifier.cancelThread(this, "debug");
        }
        Toast.makeText(this, L.t(on ? "Журнал отладки включён" : "Журнал отладки выключен"), Toast.LENGTH_SHORT).show();
        reload();
    }

    /** Строки настроек: сколько аккаунтов и сколько занимает кеш вложений. */
    private void updateAccountRows(List<Account> accs) {
        TextView acc = (TextView) findViewById(R.id.btnAccounts);
        if (acc != null) {
            acc.setText(L.t("Мои аккаунты") + " · " + accs.size());
        }
        refreshCacheLabel();
    }

    /** Размер кеша считаем в фоне, чтобы не тормозить список чатов. */
    private void refreshCacheLabel() {
        final TextView clr = (TextView) findViewById(R.id.btnClearCache);
        if (clr == null) return;
        clr.setText(L.t("Очистить кеш вложений"));
        new Thread(() -> {
            final long sz = AttachStore.cacheSize(this);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                TextView t = (TextView) findViewById(R.id.btnClearCache);
                if (t != null && sz > 0) {
                    t.setText(L.t("Очистить кеш вложений") + " · " + Ui.size(sz));
                }
            });
        }).start();
    }

    private void confirmClearCache() {
        final long sz = AttachStore.cacheSize(this);
        new AlertDialog.Builder(this)
                .setTitle(L.t("Очистить кеш вложений"))
                .setMessage(L.t("Удалить все скачанные вложения из писем?") + "\n\n"
                        + (sz > 0 ? Ui.size(sz) : L.t("Кеш уже пуст"))
                        + "\n\n" + L.t("Письма и переписка останутся на месте — вложения можно будет скачать заново."))
                .setPositiveButton(L.t("Очистить"), (d, w) -> new Thread(() -> {
                    final long freed = AttachStore.clearAll(MainActivity.this, db);
                    runOnUiThread(() -> {
                        Toast.makeText(this, L.t("Освобождено") + " " + Ui.size(freed),
                                Toast.LENGTH_SHORT).show();
                        reload();
                    });
                }).start())
                .setNegativeButton(L.t("Отмена"), null)
                .show();
    }

    private void applyLangChips() {
        int accent = Ui.attr(this, R.attr.c_accent);
        int text2 = Ui.attr(this, R.attr.c_text2);
        boolean en = L.isEn();
        int[] ids = {R.id.btnLangRu, R.id.btnLangEn};
        for (int i = 0; i < ids.length; i++) {
            TextView t = (TextView) findViewById(ids[i]);
            boolean on = (i == 1) == en;
            t.setBackgroundResource(on ? R.drawable.btn_accent : R.drawable.chip_bg);
            t.setTextColor(on ? Ui.attr(this, R.attr.c_on_accent) : text2);
            t.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void pickTheme(int theme) {
        if (Themes.current(this) == theme) return;
        Themes.set(this, theme);
        recreate();
    }

    private void applyThemeChips() {
        int active = Themes.current(this);
        int accent = Ui.attr(this, R.attr.c_accent);
        int text2 = Ui.attr(this, R.attr.c_text2);
        int[] ids = {R.id.themeNight, R.id.themeDay, R.id.themeCyber};
        for (int i = 0; i < ids.length; i++) {
            TextView t = (TextView) findViewById(ids[i]);
            boolean on = i == active;
            t.setBackgroundResource(on ? R.drawable.btn_accent : R.drawable.chip_bg);
            t.setTextColor(on ? Ui.attr(this, R.attr.c_on_accent) : text2);
            t.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void showChatMenu(final ChatItem it) {
        final boolean debug = Db.DEBUG_THREAD.equals(it.threadKey);
        final boolean pinned = db.isPinned(it.threadKey);
        final boolean muted = db.isMuted(it.threadKey);
        if (debug) {
            new AlertDialog.Builder(this)
                    .setTitle(it.name)
                    .setItems(new String[]{L.t("Очистить журнал"), L.t("Удалить чат локально")}, (d, w) -> {
                        if (w == 0) db.clearDebug();
                        else confirmDeleteChat(it);
                        reload();
                    })
                    .setNegativeButton(L.t("Отмена"), null)
                    .show();
            return;
        }
        final String[] items = {
                pinned ? L.t("Открепить") : L.t("Закрепить"),
                muted ? L.t("Включить звук") : L.t("Без звука"),
                L.t("Переименовать"),
                L.t("Отметить прочитанным"),
                L.t("Удалить чат локально")};
        new AlertDialog.Builder(this)
                .setTitle(it.name)
                .setItems(items, (d, w) -> {
                    if (w == 0) {
                        db.setPinned(it.threadKey, !pinned);
                    } else if (w == 1) {
                        db.setMuted(it.threadKey, !muted);
                    } else if (w == 2) {
                        renameChat(it);
                        return;
                    } else if (w == 3) {
                        db.markThreadSeen(it.threadKey);
                    } else {
                        confirmDeleteChat(it);
                        return;
                    }
                    reload();
                })
                .setNegativeButton(L.t("Отмена"), null)
                .show();
    }

    private void renameChat(final ChatItem it) {
        final EditText e = new EditText(this);
        String alias = db.chatName(it.threadKey);
        e.setText(alias == null ? it.name : alias);
        e.setSelection(e.getText().length());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        e.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle(L.t("Название чата"))
                .setMessage(L.t("Меняется только на этом телефоне, письма и адрес не затрагиваются."))
                .setView(e)
                .setNeutralButton(L.t("Сбросить"), (d, w) -> {
                    db.setChatName(it.threadKey, null);
                    reload();
                })
                .setNegativeButton(L.t("Отмена"), null)
                .setPositiveButton(L.t("Сохранить"), (d, w) -> {
                    db.setChatName(it.threadKey, e.getText().toString());
                    reload();
                })
                .show();
    }

    private void confirmDeleteChat(final ChatItem it) {
        new AlertDialog.Builder(this)
                .setTitle(L.t("Удалить чат?"))
                .setMessage(L.t("Переписка с «") + it.name + L.t("» будет удалена только на этом телефоне.")
                        + L.t(" На сервере письма остаются; новые письма снова появятся в списке."))
                .setNegativeButton(L.t("Отмена"), null)
                .setPositiveButton(L.t("Удалить"), (d, w) -> {
                    AttachStore.deleteThread(db, it.threadKey);
                    db.deleteChat(it.threadKey);
                    Notifier.cancelThread(this, it.threadKey);
                    reload();
                    Toast.makeText(this, L.t("Чат удалён"), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /** Долгое нажатие — режим выбора нескольких чатов. */
    private void toggleSelection(ChatItem it) {
        if (!selMode) {
            selMode = true;
            selected.clear();
        }
        if (!selected.add(it.threadKey)) selected.remove(it.threadKey);
        if (selected.isEmpty()) selMode = false;
        chatAdapter.setSelected(selected);
        applySelectionUi();
    }

    private void exitSelection() {
        selMode = false;
        selected.clear();
        chatAdapter.setSelected(selected);
        applySelectionUi();
    }

    private void applySelectionUi() {
        View search = findViewById(R.id.btnSearch);
        View refresh = findViewById(R.id.btnRefresh);
        View create = findViewById(R.id.btnNew);
        View selRead = findViewById(R.id.btnSelRead);
        View selDelete = findViewById(R.id.btnSelDelete);
        View selCancel = findViewById(R.id.btnSelCancel);
        View selMenu = findViewById(R.id.btnSelMenu);
        if (selMode) {
            title.setText(L.t("Выбрано: ") + selected.size());
            search.setVisibility(View.GONE);
            refresh.setVisibility(View.GONE);
            create.setVisibility(View.GONE);
            searchBar.setVisibility(View.GONE);
            selRead.setVisibility(View.VISIBLE);
            selDelete.setVisibility(View.VISIBLE);
            selCancel.setVisibility(View.VISIBLE);
            selMenu.setVisibility(selected.size() == 1 ? View.VISIBLE : View.GONE);
            if (btnAccount != null) btnAccount.setVisibility(View.GONE);
        } else {
            boolean chats = tab == 0;
            title.setText(L.t(getString(chats ? R.string.title_chats : R.string.title_settings)));
            search.setVisibility(chats ? View.VISIBLE : View.GONE);
            refresh.setVisibility(chats ? View.VISIBLE : View.GONE);
            create.setVisibility(chats ? View.VISIBLE : View.GONE);
            selRead.setVisibility(View.GONE);
            selDelete.setVisibility(View.GONE);
            selCancel.setVisibility(View.GONE);
            selMenu.setVisibility(View.GONE);
            if (!chats) searchBar.setVisibility(View.GONE);
            if (btnAccount != null) btnAccount.setVisibility(chats ? View.VISIBLE : View.GONE);
            else if (query.length() > 0) searchBar.setVisibility(View.VISIBLE);
        }
    }

    private ChatItem findByKey(String key) {
        for (int i = 0; i < chatAdapter.getCount(); i++) {
            ChatItem it = chatAdapter.item(i);
            if (it.threadKey.equals(key)) return it;
        }
        return null;
    }

    private void markSelectedRead() {
        final int n = selected.size();
        for (String k : new ArrayList<String>(selected)) {
            db.markThreadSeen(k);
            Notifier.cancelThread(this, k);
        }
        exitSelection();
        reload();
        Toast.makeText(this, L.t("Отмечено прочитанным: ") + n, Toast.LENGTH_SHORT).show();
    }

    private void deleteSelected() {
        final int n = selected.size();
        if (n == 0) return;
        new AlertDialog.Builder(this)
                .setTitle(L.t("Удалить выбранные чаты?"))
                .setMessage(L.t("Будет удалено только на этом телефоне: ") + n
                        + L.t(". На сервере письма остаются."))
                .setNegativeButton(L.t("Отмена"), null)
                .setPositiveButton(L.t("Удалить"), (d, w) -> {
                    for (String k : new ArrayList<String>(selected)) {
                        AttachStore.deleteThread(db, k);
                        db.deleteChat(k);
                        Notifier.cancelThread(this, k);
                    }
                    selMode = false;
                    selected.clear();
                    chatAdapter.setSelected(selected);
                    reload();
                    applySelectionUi();
                    Toast.makeText(this, L.t("Чат удалён"), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void openSelectedMenu() {
        for (String k : selected) {
            ChatItem it = findByKey(k);
            if (it != null) {
                showChatMenu(it);
                return;
            }
        }
    }

    private void pickLang(String code) {
        if (code.equals(L.lang())) {
            Toast.makeText(this, L.t("Уже выбран"), Toast.LENGTH_SHORT).show();
            return;
        }
        L.setLang(this, code);
        recreate();
    }

    private void toggleSearch() {
        boolean show = searchBar.getVisibility() != View.VISIBLE;
        if (show && tab != 0) showTab(0);
        searchBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            searchInput.requestFocus();
            android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(searchInput, 0);
        } else {
            searchInput.setText("");
            android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            query = "";
            reload();
        }
    }

    private void showTab(int which) {
        tab = which;
        boolean chats = which == 0;
        if (!chats && selMode) {
            selMode = false;
            selected.clear();
            chatAdapter.setSelected(selected);
        }
        settingsPanel.setVisibility(chats ? View.GONE : View.VISIBLE);
        listChats.setVisibility(chats ? View.VISIBLE : View.GONE);
        if (!chats) empty.setVisibility(View.GONE);
        applySelectionUi();
        int text2 = Ui.attr(this, R.attr.c_text2);
        int onAccent = Ui.attr(this, R.attr.c_on_accent);
        tabChats.setBackgroundResource(chats ? R.drawable.tab_on : 0);
        tabSettings.setBackgroundResource(chats ? 0 : R.drawable.tab_on);
        tabChats.setTextColor(chats ? onAccent : text2);
        tabSettings.setTextColor(chats ? text2 : onAccent);
        tabChats.setTypeface(null, Typeface.BOLD);
        tabSettings.setTypeface(null, Typeface.BOLD);
    }

    /** Кружок слева в шапке: буква аккаунта и его цвет либо «все аккаунты». */
    private void applyAccountUi(List<Account> accs) {
        if (btnAccount == null) return;
        int idx = -1;
        for (int i = 0; i < accs.size(); i++) {
            if (accs.get(i).id == selAccount) idx = i;
        }
        if (selAccount == -1 || idx < 0) {
            btnAccount.setText("@");
            btnAccount.setTextSize(16);
            int accent = Ui.attr(this, R.attr.c_accent);
            btnAccount.setBackground(Ui.ring(0x00000000, accent, Ui.dp(this, 1.6f)));
            btnAccount.setTextColor(accent);
        } else {
            String email = accs.get(idx).email == null ? "" : accs.get(idx).email;
            btnAccount.setText(email.length() == 0 ? "?" : email.substring(0, 1).toUpperCase());
            btnAccount.setTextSize(15);
            btnAccount.setBackground(Ui.circle(Ui.palette(idx)));
            btnAccount.setTextColor(0xFFFFFFFF);
        }
    }

    private void showAccountPicker() {
        final List<Account> accs = db.accounts();
        if (accs.isEmpty()) {
            startActivity(new Intent(this, AccountEditActivity.class));
            return;
        }
        final String[] names = new String[accs.size() + 1];
        final int[] colors = new int[names.length];
        names[0] = L.t("Все аккаунты");
        colors[0] = Ui.attr(this, R.attr.c_chip);
        int checked = 0;
        for (int i = 0; i < accs.size(); i++) {
            names[i + 1] = accs.get(i).email;
            colors[i + 1] = Ui.palette(i);
            if (accs.get(i).id == selAccount) checked = i + 1;
        }
        final int chosen = checked;
        android.widget.BaseAdapter rows = new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return names.length;
            }

            @Override
            public Object getItem(int i) {
                return names[i];
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View cv, android.view.ViewGroup parent) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(MainActivity.this, 52));
                int padH = Ui.dp(MainActivity.this, 20);
                int padV = Ui.dp(MainActivity.this, 8);
                row.setPadding(padH, padV, padH, padV);

                TextView dot = new TextView(MainActivity.this);
                int sz = Ui.dp(MainActivity.this, 32);
                dot.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
                dot.setGravity(android.view.Gravity.CENTER);
                dot.setTextSize(14);
                dot.setTypeface(null, Typeface.BOLD);
                dot.setTextColor(0xFFFFFFFF);
                if (i == 0) {
                    int accent = Ui.attr(MainActivity.this, R.attr.c_accent);
                    dot.setBackground(Ui.ring(0x00000000, accent, Ui.dp(MainActivity.this, 1.4f)));
                    dot.setTextColor(accent);
                    dot.setText("@");
                } else {
                    dot.setBackground(Ui.circle(colors[i]));
                    dot.setTextColor(0xFFFFFFFF);
                    dot.setText(names[i].length() == 0 ? "?"
                            : names[i].substring(0, 1).toUpperCase());
                }
                row.addView(dot);

                TextView tv = new TextView(MainActivity.this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.leftMargin = Ui.dp(MainActivity.this, 14);
                tv.setLayoutParams(lp);
                tv.setText(names[i]);
                tv.setTextSize(15);
                tv.setSingleLine(true);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                tv.setTextColor(Ui.attr(MainActivity.this, R.attr.c_text));
                row.addView(tv);

                TextView mark = new TextView(MainActivity.this);
                mark.setText("✓");
                mark.setTextSize(16);
                mark.setTextColor(Ui.attr(MainActivity.this, R.attr.c_accent));
                mark.setVisibility(chosen == i ? View.VISIBLE : View.INVISIBLE);
                row.addView(mark);
                return row;
            }
        };
        new AlertDialog.Builder(this)
                .setTitle(L.t("Аккаунт"))
                .setAdapter(rows, (d, which) -> {
                    d.dismiss();
                    selAccount = which == 0 ? -1 : accs.get(which - 1).id;
                    getSharedPreferences("ui", MODE_PRIVATE).edit()
                            .putLong("acct", selAccount).apply();
                    removeSearch();
                    reload();
                })
                .setNegativeButton(L.t("Закрыть"), null)
                .show();
    }

    /** Сброс поиска, чтобы список не оставался пустым после смены аккаунта. */
    private void removeSearch() {
        if (searchBar != null && searchBar.getVisibility() == View.VISIBLE) toggleSearch();
    }

    private void reload() {
        List<Account> accs = db.accounts();
        boolean known = false;
        for (Account a : accs) {
            if (a.id == selAccount) known = true;
        }
        if (selAccount != -1 && !known) {
            selAccount = -1;
            getSharedPreferences("ui", MODE_PRIVATE).edit().putLong("acct", selAccount).apply();
        }
        applyAccountUi(accs);
        List<ChatItem> chats = query.length() == 0
                ? (selAccount == -1 ? db.chats() : db.chatsFor(selAccount))
                : db.searchThreads(query, 100);
        if (selAccount != -1) {
            for (int i = chats.size() - 1; i >= 0; i--) {
                if (chats.get(i).accountId != selAccount) chats.remove(i);
            }
        }
        if (!debugChatVisible()) {
            for (int i = chats.size() - 1; i >= 0; i--) {
                if ("debug".equals(chats.get(i).threadKey)) chats.remove(i);
            }
        }
        chats.sort((a, b) -> {
            boolean ad = Db.DEBUG_THREAD.equals(a.threadKey);
            boolean bd = Db.DEBUG_THREAD.equals(b.threadKey);
            if (ad != bd) return ad ? -1 : 1;
            if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
            return Long.compare(b.date, a.date);
        });
        Map<Long, Integer> accColors = new HashMap<Long, Integer>();
        for (int i = 0; i < accs.size(); i++) accColors.put(accs.get(i).id, Ui.palette(i));
        chatAdapter.setAccountColors(accColors);
        chatAdapter.setItems(chats, accs.size() > 1 && selAccount == -1, drafts());
        if (selMode) {
            Set<String> present = new HashSet<String>();
            for (ChatItem c : chats) present.add(c.threadKey);
            selected.retainAll(present);
            if (selected.isEmpty()) selMode = false;
        }
        chatAdapter.setSelected(selected);
        applySelectionUi();
        updateAccountRows(accs);
        int unread = 0;
        for (ChatItem c : chats) unread += c.unread;
        String chatsLabel = L.t(getString(R.string.title_chats));
        tabChats.setText(unread > 0 ? chatsLabel + " · " + unread : chatsLabel);
        if (tab == 0) {
            boolean nothing = chats.isEmpty();
            empty.setVisibility(nothing ? View.VISIBLE : View.GONE);
            listChats.setVisibility(nothing ? View.GONE : View.VISIBLE);
            TextView et = (TextView) findViewById(R.id.emptyTitle);
            TextView es = (TextView) findViewById(R.id.emptySub);
            if (query.length() > 0) {
                if (et != null) et.setText(L.t("Ничего не найдено"));
                if (es != null) es.setText(L.t("Попробуйте другой запрос."));
            } else {
                if (et != null) et.setText(L.t(getString(R.string.empty_title)));
                if (es != null) {
                    es.setText(L.t(selAccount == -1 ? getString(R.string.empty_chats)
                            : "В этом аккаунте пока нет писем"));
                }
            }
        }
    }

    private Map<String, String> drafts() {
        Map<String, String> out = new HashMap<String, String>();
        try {
            for (Map.Entry<String, ?> e : getSharedPreferences("drafts", Context.MODE_PRIVATE)
                    .getAll().entrySet()) {
                Object v = e.getValue();
                if (v instanceof String && ((String) v).trim().length() > 0) {
                    out.put(e.getKey(), (String) v);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    @Override
    public void onBackPressed() {
        if (selMode) {
            exitSelection();
            return;
        }
        super.onBackPressed();
    }

    private void syncNow() {
        if (syncing) return;
        final List<Account> accs = db.accounts();
        if (accs.isEmpty()) {
            Toast.makeText(this, L.t("Сначала добавьте аккаунт"), Toast.LENGTH_SHORT).show();
            return;
        }
        syncing = true;
        Toast.makeText(this, L.t("Синхронизация…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
            final StringBuilder errors = new StringBuilder();
            final StringBuilder details = new StringBuilder();
            int added = 0;
            for (Account a : accs) {
                try {
                    MailService.SyncResult r = MailService.syncAccount(MainActivity.this, db, a);
                    added += r.added;
                    details.append(a.email).append(": ")
                            .append(r.inbox.length() == 0 ? L.t("папка не найдена") : r.inbox)
                            .append(L.t(", писем ")).append(r.inboxTotal)
                            .append(L.t(", новых ")).append(r.added);
                    if (r.sent.length() > 0) details.append(L.t(", отправленные ")).append(r.sent);
                    if (r.failed > 0) {
                        details.append(L.t(", не разобрано ")).append(r.failed)
                                .append(" (").append(r.sampleError).append(')');
                    }
                    if (r.error != null) details.append(L.t("\n  ошибка: ")).append(r.error);
                    details.append('\n');
                } catch (Throwable e) {
                    final String report = crashReport(a.email, e);
                    db.log(L.t("Сбой синхронизации"), report);
                    errors.append(report).append('\n');
                }
            }
            final int fAdded = added;
            ui.post(() -> {
                syncing = false;
                if (isFinishing()) return;
                reload();
                if (errors.length() > 0) {
                    final String report = (errors.toString() + "\n" + details.toString()).trim();
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(L.t("Ошибка синхронизации"))
                            .setMessage(report)
                            .setPositiveButton(L.t("Ок"), null)
                            .setNeutralButton(L.t("Скопировать"), (d, w) -> Ui.copy(MainActivity.this, report))
                            .show();
                } else if (fAdded > 0) {
                    Toast.makeText(MainActivity.this, L.t("Новых писем: ") + fAdded, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Throwable t) {
            final String report = crashReport(L.t("синхронизация"), t);
            db.log(L.t("Сбой синхронизации"), report);
            ui.post(() -> {
                syncing = false;
                if (isFinishing()) return;
                Ui.copy(MainActivity.this, report);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(L.t("Сбой синхронизации"))
                        .setMessage(report)
                        .setPositiveButton(L.t("Ок"), null)
                        .setNeutralButton(L.t("Скопировать"), (d, w) -> Ui.copy(MainActivity.this, report))
                        .show();
            });
        }
        }).start();
    }
}
