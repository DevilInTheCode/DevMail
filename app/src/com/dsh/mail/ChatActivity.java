package com.dsh.mail;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.content.res.ColorStateList;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatActivity extends Activity {

    private static final int REQ_FILE = 21;
    private static final int REQ_GALLERY = 22;
    private static final int REQ_CAMERA = 23;
    private static final int REQ_BG = 24;
    private static final int REQ_EXPORT = 25;
    private static final int REQ_AVATAR = 26;

    private Db db;
    private String threadKey;
    private String peerName;
    private String peerEmail;
    private long accountId;

    private MsgAdapter adapter;
    private ListView list;
    private EditText input;
    private ImageView btnAttach;
    private File pendingAttach;
    private File cameraFile;
    private boolean debugChat;
    private long newMark;
    /** С какого id считаются непрочитанные для счётчика внизу. */
    private long seenMark;
    private TextView avatarView;
    private ImageView avatarImg;
    private AlertDialog contactDialog;
    private String originalName;
    private TextView peerNameView;
    private MailMessage replyMsg;
    private View replyBar;
    private TextView replyAuthor;
    private TextView replyText;
    private View floatDate;
    private TextView floatDateText;
    private View btnDown;
    private TextView btnDownCount;
    private View chatSearchBar;
    private EditText chatSearchInput;
    private TextView chatSearchCount;
    private List<MailMessage> searchMatches;
    private int searchIdx = -1;
    private boolean searching;
    private boolean floatShown;
    private boolean initialScrollDone;
    private ListSwipe swipe;
    private boolean pendingBottom;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        setTheme(Themes.style(this));
        super.onCreate(b);
        setContentView(R.layout.activity_chat);
        L.localize(findViewById(android.R.id.content));
        Ui.edgeToEdge(this, R.id.root);
        db = new Db(this);
        threadKey = getIntent().getStringExtra("thread");
        peerName = getIntent().getStringExtra("name");
        peerEmail = getIntent().getStringExtra("email");
        accountId = getIntent().getLongExtra("account", 0);
        if (peerEmail == null) peerEmail = "";
        if (peerEmail.indexOf('@') <= 0) peerEmail = "";
        if (peerName == null || peerName.trim().isEmpty()) peerName = peerEmail;
        if (peerName == null || peerName.trim().isEmpty()) peerName = Db.fallbackName(threadKey);

        originalName = peerName;
        String alias = db.chatName(threadKey);
        if (alias != null && alias.trim().length() > 0) peerName = alias;

        avatarView = (TextView) findViewById(R.id.avatar);
        peerNameView = (TextView) findViewById(R.id.peerName);
        avatarView.setText(Ui.initials(peerName, peerEmail));
        avatarView.setBackground(Ui.circle(Ui.avatarColor(peerEmail)));
        avatarImg = (ImageView) findViewById(R.id.avatarImg);
        findViewById(R.id.avatarBox).setOnClickListener(v -> showContactCard());
        findViewById(R.id.peerInfo).setOnClickListener(v -> showContactCard());
        applyAvatar();
        peerNameView.setText(peerName);
        ((TextView) findViewById(R.id.peerEmail)).setText(peerEmail);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnChatMenu).setOnClickListener(v -> showChatMenu());

        debugChat = Db.DEBUG_THREAD.equals(threadKey);
        if (debugChat) {
            findViewById(R.id.composerRow).setVisibility(View.GONE);
            findViewById(R.id.replyBar).setVisibility(View.GONE);
            findViewById(R.id.btnChatMenu).setVisibility(View.GONE);
            findViewById(R.id.btnChatSearch).setVisibility(View.GONE);
            peerNameView.setText(L.t("Журнал отладки"));
            ((TextView) findViewById(R.id.peerEmail)).setText(L.t("ошибки синхронизации и подключения"));
            View copyAll = findViewById(R.id.btnCopyAll);
            copyAll.setVisibility(View.VISIBLE);
            copyAll.setOnClickListener(v -> copyAll());
            copyAll.setOnLongClickListener(v -> {
                confirmClearLog();
                return true;
            });
        }

        list = (ListView) findViewById(R.id.listMessages);
        list.setCacheColorHint(0);
        adapter = new MsgAdapter(this);
        adapter.setOnAttach((mm, aa) -> onAttachmentTap(mm, aa));
        list.setAdapter(adapter);
        swipe = new ListSwipe(list, true, row -> row, (pos, dir) -> {
            MailMessage m = adapter.messageAt(pos);
            if (m != null && !debugChat) startReply(m);
        });
        swipe.attach();
        list.setOnItemClickListener((p, v, pos, id) -> {
            if (adapter.isNewDivider(pos)) {
                jumpTo(pos, Ui.dp(this, 8));
                return;
            }
            onBubble(pos);
        });

        floatDate = findViewById(R.id.floatDate);
        floatDateText = (TextView) floatDate;
        btnDown = findViewById(R.id.btnDown);
        btnDownCount = (TextView) findViewById(R.id.btnDownCount);
        floatDate.setOnClickListener(v -> showDatePicker());
        btnDown.setOnClickListener(v -> scrollToBottom());
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int state) {
                if (state == SCROLL_STATE_IDLE) {
                    ui.removeCallbacks(autoHideFloat);
                    ui.postDelayed(autoHideFloat, 1200);
                } else {
                    ui.removeCallbacks(autoHideFloat);
                    animateFloat(true);
                }
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                updateScrollUi();
            }
        });

        chatSearchBar = findViewById(R.id.chatSearchBar);
        chatSearchInput = (EditText) findViewById(R.id.chatSearchInput);
        chatSearchCount = (TextView) findViewById(R.id.chatSearchCount);
        findViewById(R.id.btnChatSearch).setOnClickListener(v -> toggleChatSearch());
        findViewById(R.id.btnSearchClose).setOnClickListener(v -> closeSearch());
        findViewById(R.id.btnSearchPrev).setOnClickListener(v -> stepMatch(-1));
        findViewById(R.id.btnSearchNext).setOnClickListener(v -> stepMatch(1));
        chatSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (!searching) return;
                if (e.toString().trim().length() == 0) {
                    searchMatches = null;
                    searchIdx = -1;
                    adapter.setHighlight(0);
                    chatSearchCount.setText("");
                    load();
                } else {
                    renderSearch();
                }
            }
        });

        input = (EditText) findViewById(R.id.input);
        btnAttach = (ImageView) findViewById(R.id.btnAttach);
        btnAttach.setOnClickListener(v -> showAttachMenu());
        findViewById(R.id.btnSend).setOnClickListener(v -> send());

        replyBar = findViewById(R.id.replyBar);
        replyAuthor = (TextView) findViewById(R.id.replyAuthor);
        replyText = (TextView) findViewById(R.id.replyText);
        findViewById(R.id.btnCancelReply).setOnClickListener(v -> clearReply());

        list.setOnItemLongClickListener((p, v, pos, id) -> {
            if (swipe != null && swipe.isActive()) return true;
            MailMessage m = adapter.messageAt(pos);
            if (m == null) return false;
            showActions(m);
            return true;
        });

        String draft = drafts().getString(threadKey, "");
        if (draft.length() > 0) input.setText(draft);

        if (!debugChat) newMark = db.lastSeenId(threadKey);
        seenMark = newMark;
        load();
        applyChatBg();
    }

    private void showChatMenu() {
        final boolean pinned = db.isPinned(threadKey);
        final boolean muted = db.isMuted(threadKey);
        final String[] items = {
                pinned ? L.t("Открепить") : L.t("Закрепить"),
                muted ? L.t("Включить звук") : L.t("Без звука"),
                L.t("Переименовать чат"),
                L.t("Фон чата из галереи"),
                L.t("Убрать фон"),
                L.t("Экспорт переписки")};
        new AlertDialog.Builder(this)
                .setTitle(peerName)
                .setItems(items, (d, w) -> {
                    if (w == 0) {
                        db.setPinned(threadKey, !pinned);
                        Toast.makeText(this, L.t(!pinned ? "Чат закреплён" : "Чат откреплён"),
                                Toast.LENGTH_SHORT).show();
                    } else if (w == 1) {
                        db.setMuted(threadKey, !muted);
                        Toast.makeText(this, L.t(!muted ? "Уведомления выключены" : "Уведомления включены"),
                                Toast.LENGTH_SHORT).show();
                    } else if (w == 2) {
                        renameChat();
                    } else if (w == 3) {
                        pickBg();
                    } else if (w == 4) {
                        clearBg();
                    } else {
                        exportChat();
                    }
                })
                .setNegativeButton(L.t("Отмена"), null)
                .show();
    }

    /** Выгрузка переписки в .txt через системный выбор файла. */
    private void exportChat() {
        String safe = peerName.replaceAll("[^\\p{L}\\p{N}_-]+", "_");
        if (safe.length() == 0) safe = "chat";
        String name = safe + "-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                .format(new java.util.Date()) + ".txt";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            Toast.makeText(this, L.t("Нет приложения для сохранения файла"), Toast.LENGTH_SHORT).show();
        }
    }

    private String dumpThread() {
        StringBuilder sb = new StringBuilder();
        sb.append("DevMail — ").append(L.t("переписка с ")).append(peerName)
                .append(" <").append(peerEmail).append(">\n");
        sb.append(L.t("Выгружено: "))
                .append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        .format(new java.util.Date())).append("\n\n");
        java.util.List<MailMessage> msgs = db.thread(threadKey);
        for (MailMessage m : msgs) {
            sb.append("=== ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                    java.util.Locale.US).format(new java.util.Date(m.date))).append("\n");
            String from = m.outgoing ? L.t("Я") : (m.peerName == null || m.peerName.length() == 0
                    ? peerName : m.peerName);
            sb.append(from).append(" <").append(m.outgoing ? "me" : peerEmail).append(">\n");
            if (m.subject != null && m.subject.length() > 0) sb.append(L.t("Тема: ")).append(m.subject).append('\n');
            if (m.quoteAuthor != null && m.quoteAuthor.length() > 0) {
                sb.append('>').append(m.quoteAuthor).append(": ")
                        .append(m.quoteText == null ? "" : m.quoteText).append('\n');
            }
            sb.append('\n').append(m.body == null ? "" : m.body).append("\n");
            java.util.List<AttachmentInfo> atts = db.attachments(m.id);
            if (atts != null && !atts.isEmpty()) {
                sb.append(L.t("Вложения: "));
                for (int k = 0; k < atts.size(); k++) {
                    if (k > 0) sb.append(", ");
                    sb.append(atts.get(k).name);
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void renameChat() {
        final EditText e = new EditText(this);
        String alias = db.chatName(threadKey);
        e.setText(alias == null ? peerName : alias);
        e.setSelection(e.getText().length());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        e.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle(L.t("Название чата"))
                .setMessage(L.t("Меняется только на этом телефоне, письма и адрес не затрагиваются."))
                .setView(e)
                .setNeutralButton(L.t("Сбросить"), (d, w) -> {
                    db.setChatName(threadKey, null);
                    applyPeerName();
                })
                .setNegativeButton(L.t("Отмена"), null)
                .setPositiveButton(L.t("Сохранить"), (d, w) -> {
                    db.setChatName(threadKey, e.getText().toString());
                    applyPeerName();
                })
                .show();
    }

    private void applyPeerName() {
        String alias = db.chatName(threadKey);
        peerName = alias != null && alias.trim().length() > 0 ? alias
                : (originalName == null || originalName.trim().length() == 0 ? peerEmail : originalName);
        peerNameView.setText(peerName);
        avatarView.setText(Ui.initials(peerName, peerEmail));
    }

    private void pickBg() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i, L.t("Фон чата")), REQ_BG);
        } catch (Exception e) {
            Toast.makeText(this, L.t("Нет приложения галереи"), Toast.LENGTH_SHORT).show();
        }
    }

    private File bgFile() {
        File dir = new File(getFilesDir(), "bg");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "bg_" + Integer.toHexString(threadKey.hashCode()) + ".jpg");
    }

    private void saveBg(final Uri uri) {
        Toast.makeText(this, L.t("Готовим фон…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                InputStream probe = getContentResolver().openInputStream(uri);
                BitmapFactory.decodeStream(probe, null, o);
                probe.close();
                if (o.outWidth <= 0 || o.outHeight <= 0) throw new Exception(L.t("это не картинка"));
                int sample = 1;
                while (o.outWidth / (sample * 2) >= 2000 && o.outHeight / (sample * 2) >= 2000) sample *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = sample;
                InputStream in = getContentResolver().openInputStream(uri);
                Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
                in.close();
                if (bmp == null) throw new Exception(L.t("не удалось прочитать изображение"));
                File f = bgFile();
                FileOutputStream out = new FileOutputStream(f);
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
                out.close();
                bmp.recycle();
                db.setChatBg(threadKey, f.getAbsolutePath());
                ui.post(() -> {
                    applyChatBg();
                    Toast.makeText(this, L.t("Фон установлен"), Toast.LENGTH_SHORT).show();
                });
            } catch (final Exception e) {
                ui.post(() -> Toast.makeText(this, L.t("Не удалось поставить фон: ") + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void clearBg() {
        String path = db.chatBg(threadKey);
        if (path != null) {
            try {
                new File(path).delete();
            } catch (Exception ignored) {
            }
        }
        db.setChatBg(threadKey, null);
        applyChatBg();
        Toast.makeText(this, L.t("Фон убран"), Toast.LENGTH_SHORT).show();
    }

    private void applyChatBg() {
        if (list == null) return;
        String path = db.chatBg(threadKey);
        if (path == null || !new File(path).exists()) {
            list.setBackground(null);
            return;
        }
        try {
            Bitmap bmp = decodeCrop(path,
                    getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
            if (bmp == null) {
                list.setBackground(null);
                return;
            }
            Drawable photo = new BitmapDrawable(getResources(), bmp);
            GradientDrawable scrim = new GradientDrawable();
            scrim.setColor(0x66000000);
            list.setBackground(new LayerDrawable(new Drawable[]{photo, scrim}));
        } catch (Throwable ignored) {
            list.setBackground(null);
        }
    }

    private static Bitmap decodeCrop(String path, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        Bitmap src = BitmapFactory.decodeFile(path, o);
        if (src == null) return null;
        float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        int nw = Math.max(w, Math.round(src.getWidth() * scale));
        int nh = Math.max(h, Math.round(src.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(src, nw, nh, true);
        if (scaled != src) src.recycle();
        int left = Math.max(0, (nw - w) / 2);
        int top = Math.max(0, (nh - h) / 2);
        return Bitmap.createBitmap(scaled, left, top, Math.min(w, nw), Math.min(h, nh));
    }

    @Override
    protected void onStart() {
        super.onStart();
        LiveSync.acquire(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LiveSync.release();
    }

    private void onLiveMail() {
        if (!isFinishing()) load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LiveSync.setListener(this::onLiveMail);
        LiveSync.poke();
        load();
        applyAvatar();
    }

    /** Аватарка контакта (если задана вручную) поверх кружка с инициалами. */
    private void applyAvatar() {
        if (avatarImg == null) return;
        Bitmap bm = Avatars.load(this, peerEmail, Ui.dp(this, 40));
        if (bm != null) {
            avatarImg.setImageBitmap(bm);
            avatarImg.setVisibility(View.VISIBLE);
        } else {
            avatarImg.setImageDrawable(null);
            avatarImg.setVisibility(View.GONE);
        }
    }

    /** Карточка собеседника: фото, имя, адрес и быстрые действия. */
    private void showContactCard() {
        if (debugChat) return;
        if (peerEmail.length() == 0) {
            new AlertDialog.Builder(this)
                    .setTitle(peerName)
                    .setMessage(L.t("У этого письма нет адреса отправителя — ответить нельзя. Подробности в журнале отладки."))
                    .setPositiveButton(L.t("Закрыть"), null)
                    .show();
            return;
        }
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 22);
        col.setPadding(pad, Ui.dp(this, 6), pad, pad);

        final int photoSize = Ui.dp(this, 96);
        final ImageView photo = new ImageView(this);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(photoSize, photoSize);
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        photo.setLayoutParams(plp);
        Bitmap bm = Avatars.load(this, peerEmail, photoSize);
        if (bm != null) {
            photo.setImageBitmap(bm);
        } else {
            photo.setImageBitmap(Ui.letterBitmap(Ui.initials(peerName, peerEmail),
                    Ui.avatarColor(peerEmail), photoSize));
        }
        photo.setOnClickListener(v -> pickAvatar());
        col.addView(photo);

        TextView hint = new TextView(this);
        hint.setText(L.t("Нажмите на фото, чтобы сменить"));
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        hint.setTextColor(Ui.attr(this, R.attr.c_text2));
        hint.setPadding(0, Ui.dp(this, 8), 0, 0);
        col.addView(hint);

        TextView nameView = new TextView(this);
        nameView.setText(peerName);
        nameView.setTextSize(20);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        nameView.setGravity(Gravity.CENTER);
        nameView.setTextColor(Ui.attr(this, R.attr.c_text));
        nameView.setPadding(0, Ui.dp(this, 12), 0, 0);
        col.addView(nameView);

        TextView emailView = new TextView(this);
        emailView.setText(peerEmail);
        emailView.setTextSize(13);
        emailView.setGravity(Gravity.CENTER);
        emailView.setTextColor(Ui.attr(this, R.attr.c_text2));
        emailView.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        col.addView(emailView);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        TextView write = cardButton(L.t("Написать"), true);
        write.setOnClickListener(v -> {
            Intent i = new Intent(this, ComposeActivity.class);
            i.putExtra("to", peerEmail);
            startActivity(i);
        });
        TextView copy = cardButton(L.t("Копировать адрес"), false);
        copy.setOnClickListener(v -> {
            Ui.copy(this, peerEmail);
            Toast.makeText(this, L.t("Адрес скопирован"), Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(write, blp);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        blp2.leftMargin = Ui.dp(this, 8);
        row.addView(copy, blp2);
        col.addView(row);

        if (Avatars.has(this, peerEmail)) {
            TextView remove = cardButton(L.t("Удалить фото"), false);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Ui.dp(this, 8);
            remove.setLayoutParams(rlp);
            remove.setOnClickListener(v -> {
                Avatars.remove(this, peerEmail);
                applyAvatar();
                Toast.makeText(this, L.t("Фото удалено"), Toast.LENGTH_SHORT).show();
                reopenContactCard();
            });
            col.addView(remove);
        }

        contactDialog = new AlertDialog.Builder(this)
                .setView(col)
                .setPositiveButton(L.t("Закрыть"), null)
                .show();
    }

    private void reopenContactCard() {
        if (contactDialog != null) {
            contactDialog.dismiss();
            contactDialog = null;
        }
        showContactCard();
    }

    private TextView cardButton(String text, boolean accent) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12));
        b.setBackgroundResource(accent ? R.drawable.btn_accent : R.drawable.icon_btn_bg);
        b.setTextColor(accent ? Ui.attr(this, R.attr.c_on_accent)
                : Ui.attr(this, R.attr.c_text));
        return b;
    }

    private void pickAvatar() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_AVATAR);
        } catch (Exception e) {
            Toast.makeText(this, L.t("Не удалось открыть галерею"), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyAll() {
        String text = adapter.dumpText();
        if (text.length() == 0) {
            Toast.makeText(this, L.t("Журнал пуст"), Toast.LENGTH_SHORT).show();
            return;
        }
        Ui.copy(this, text);
    }

    private void confirmClearLog() {
        new AlertDialog.Builder(this)
                .setTitle(L.t("Очистить журнал?"))
                .setMessage(L.t("Удалить все записи отладки. Письма в почте не затрагиваются."))
                .setNegativeButton(L.t("Отмена"), null)
                .setPositiveButton(L.t("Очистить"), (d, w) -> {
                    db.clearDebug();
                    load();
                })
                .show();
    }

    private void load() {
        if (searching && chatSearchInput != null
                && chatSearchInput.getText().toString().trim().length() > 0) {
            renderSearch();
            return;
        }
        renderThread();
    }

    private void renderThread() {
        List<MailMessage> msgs = db.thread(threadKey);
        Map<Long, List<AttachmentInfo>> map = new HashMap<Long, List<AttachmentInfo>>();
        for (MailMessage m : msgs) {
            if (m.hasAttach) map.put(m.id, db.attachments(m.id));
        }
        final int anchorFirst = list.getFirstVisiblePosition();
        final int anchorTop = list.getChildAt(0) == null ? 0 : list.getChildAt(0).getTop();
        final boolean atBottom = isAtBottom();
        final boolean first = !initialScrollDone;
        adapter.setHighlight(0);
        adapter.setMessages(msgs, map, newMark);
        list.post(new Runnable() {
            @Override
            public void run() {
                if (first) {
                    initialScrollDone = true;
                    int div = adapter.newDividerPos();
                    if (div >= 0) {
                        jumpTo(div, Ui.dp(ChatActivity.this, 10));
                        updateScrollUi();
                        return;
                    }
                    long[] saved = readScroll();
                    if (saved != null) {
                        int pos = adapter.positionOfMessage(saved[0]);
                        if (pos >= 0) {
                            jumpTo(pos, (int) saved[1]);
                            updateScrollUi();
                            return;
                        }
                    }
                    scrollToBottom();
                } else if (atBottom || pendingBottom) {
                    pendingBottom = false;
                    scrollToBottom();
                } else if (anchorFirst >= 0 && anchorFirst < adapter.getCount()) {
                    list.setSelectionFromTop(anchorFirst, anchorTop);
                }
                updateScrollUi();
            }
        });
        db.markThreadSeen(threadKey);
        Notifier.cancelThread(this, threadKey);
    }

    /** Прыжок к строке списка; если позиционирование не сработало — хотя бы выделяем её. */
    private void jumpTo(final int pos, final int offset) {
        if (pos < 0) return;
        list.setSelectionFromTop(pos, offset);
        list.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (pos < list.getFirstVisiblePosition() || pos > list.getLastVisiblePosition()) {
                    list.setSelection(pos);
                }
            }
        }, 100);
    }

    /** Плавающая дата и кнопка «вниз» со счётчиком. */
    private void updateScrollUi() {
        if (adapter == null) return;
        if (adapter.getCount() == 0) {
            animateFloat(false);
            setDownVisible(false);
            return;
        }
        int firstPos = Math.max(0, list.getFirstVisiblePosition());
        long ts = adapter.dateAt(firstPos);
        if (ts <= 0) ts = adapter.dateAt(firstPos + 1);
        if (ts > 0) floatDateText.setText(Ui.dayLabel(ts));
        int last = list.getLastVisiblePosition();
        if (last >= adapter.getCount() - 1) seenMark = Math.max(seenMark, adapter.maxId());
        int unread = adapter.unreadAfter(last, seenMark);
        btnDownCount.setText(unread > 99 ? "99+" : String.valueOf(unread));
        btnDownCount.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        setDownVisible(adapter.hasMessagesAfter(last));
    }

    private void setDownVisible(boolean show) {
        if (show) {
            if (btnDown.getVisibility() != View.VISIBLE) {
                btnDown.setVisibility(View.VISIBLE);
                btnDown.animate().alpha(1f).translationY(0).setDuration(150).start();
            }
        } else if (btnDown.getVisibility() == View.VISIBLE) {
            btnDown.animate().alpha(0f).translationY(Ui.dp(this, 12)).setDuration(120)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            btnDown.setVisibility(View.INVISIBLE);
                        }
                    }).start();
        }
    }

    private final Runnable autoHideFloat = new Runnable() {
        @Override
        public void run() {
            animateFloat(false);
        }
    };

    private void animateFloat(boolean show) {
        if (floatShown == show) return;
        floatShown = show;
        floatDate.setEnabled(show);
        floatDate.animate().alpha(show ? 1f : 0f)
                .translationY(show ? 0 : -Ui.dp(this, 6)).setDuration(150).start();
    }

    private void scrollToBottom() {
        if (adapter.getCount() == 0) return;
        list.setSelection(adapter.getCount() - 1);
        setDownVisible(false);
    }

    private boolean isAtBottom() {
        if (adapter == null || adapter.getCount() == 0) return true;
        int last = list.getLastVisiblePosition();
        if (last < adapter.getCount() - 1) return false;
        View v = list.getChildAt(list.getChildCount() - 1);
        return v == null || v.getBottom() <= list.getHeight() + Ui.dp(this, 8);
    }

    /** Тап по плавающей дате — календарь и переход к нужному дню. */
    private void showDatePicker() {
        long ts = adapter.dateAt(list.getFirstVisiblePosition());
        if (ts <= 0) ts = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        DatePickerDialog d = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar t = Calendar.getInstance();
            t.set(year, month, day, 0, 0, 0);
            t.set(Calendar.MILLISECOND, 0);
            int pos = adapter.positionForDay(t.getTimeInMillis());
            if (pos >= 0) {
                jumpTo(pos, Ui.dp(this, 8));
            } else {
                Toast.makeText(this, L.t("Нет сообщений после этой даты"), Toast.LENGTH_SHORT).show();
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d.setTitle(L.t("Перейти к дате"));
        d.show();
    }

    private long[] readScroll() {
        try {
            String s = getSharedPreferences("ui", MODE_PRIVATE).getString("scroll_" + threadKey, null);
            if (s == null) return null;
            int c = s.indexOf(',');
            if (c <= 0) return null;
            return new long[]{Long.parseLong(s.substring(0, c)), Long.parseLong(s.substring(c + 1))};
        } catch (Throwable e) {
            return null;
        }
    }

    private void saveScroll() {
        try {
            SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
            if (searching || adapter.getCount() == 0 || isAtBottom()) {
                p.edit().remove("scroll_" + threadKey).apply();
                return;
            }
            MailMessage m = adapter.messageNear(list.getFirstVisiblePosition());
            if (m == null) return;
            int top = list.getChildAt(0) == null ? 0 : list.getChildAt(0).getTop();
            p.edit().putString("scroll_" + threadKey, m.id + "," + top).apply();
        } catch (Throwable ignored) {
        }
    }

    /** Поиск по переписке: список сужается до совпадений, ↑/↓ переключают их. */
    private void toggleChatSearch() {
        if (searching) {
            closeSearch();
            return;
        }
        searching = true;
        chatSearchBar.setVisibility(View.VISIBLE);
        chatSearchInput.setText("");
        chatSearchCount.setText("");
        chatSearchInput.requestFocus();
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.showSoftInput(chatSearchInput, 0);
    }

    private void closeSearch() {
        searching = false;
        chatSearchBar.setVisibility(View.GONE);
        chatSearchInput.setText("");
        chatSearchCount.setText("");
        searchMatches = null;
        searchIdx = -1;
        adapter.setHighlight(0);
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(chatSearchInput.getWindowToken(), 0);
        load();
    }

    private boolean hasText(String s, String needle) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(needle);
    }

    private void renderSearch() {
        String q = chatSearchInput.getText().toString().trim();
        if (q.length() == 0) {
            load();
            return;
        }
        String needle = q.toLowerCase(Locale.ROOT);
        List<MailMessage> all = db.thread(threadKey);
        List<MailMessage> hits = new ArrayList<MailMessage>();
        for (MailMessage m : all) {
            if (hits.size() >= 300) break;
            if (hasText(m.subject, needle) || hasText(m.body, needle)
                    || hasText(m.snippet, needle) || hasText(m.quoteText, needle)) {
                hits.add(m);
            }
        }
        int before = searchMatches == null ? -1 : searchMatches.size();
        searchMatches = hits;
        if (searchIdx >= hits.size() - 1 || before != hits.size()) {
            searchIdx = hits.isEmpty() ? -1 : hits.size() - 1;
        }
        Map<Long, List<AttachmentInfo>> map = new HashMap<Long, List<AttachmentInfo>>();
        for (MailMessage m : hits) {
            if (m.hasAttach) map.put(m.id, db.attachments(m.id));
        }
        adapter.setMessages(hits, map, 0);
        chatSearchCount.setText(hits.isEmpty() ? "0/0" : (searchIdx + 1) + "/" + hits.size());
        if (searchIdx >= 0) {
            MailMessage m = hits.get(searchIdx);
            adapter.setHighlight(m.id);
            int pos = adapter.positionOfMessage(m.id);
            if (pos >= 0) jumpTo(pos, Ui.dp(this, 70));
        }
    }

    private void stepMatch(int dir) {
        if (searchMatches == null || searchMatches.isEmpty()) return;
        searchIdx = (searchIdx + dir + searchMatches.size()) % searchMatches.size();
        MailMessage m = searchMatches.get(searchIdx);
        adapter.setHighlight(m.id);
        chatSearchCount.setText((searchIdx + 1) + "/" + searchMatches.size());
        int pos = adapter.positionOfMessage(m.id);
        if (pos >= 0) jumpTo(pos, Ui.dp(this, 70));
    }

    private void onBubble(int pos) {
        MailMessage m = adapter.messageAt(pos);
        if (m == null || !m.hasAttach) return;
        final List<AttachmentInfo> atts = db.attachments(m.id);
        if (atts.isEmpty()) return;
        final String[] names = new String[atts.size()];
        for (int i = 0; i < atts.size(); i++) {
            AttachmentInfo a = atts.get(i);
            names[i] = a.name + (a.prettySize().length() > 0 ? "  (" + a.prettySize() + ")" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle(L.t("Вложения"))
                .setItems(names, (d, which) -> onAttachmentTap(m, atts.get(which)))
                .setNegativeButton(L.t("Закрыть"), null)
                .show();
    }

    /** Тап по вложению: если ещё не скачано — достаём из письма, потом открываем. */
    private void onAttachmentTap(final MailMessage msg, final AttachmentInfo att) {
        if (att.downloaded()) {
            openDownloaded(att);
            return;
        }
        Toast.makeText(this, L.t("Загрузка вложения…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                AttachStore.fetch(ChatActivity.this, db, msg, att);
                ui.post(() -> {
                    load();
                    openDownloaded(att);
                });
            } catch (final Exception e) {
                ui.post(() -> Toast.makeText(ChatActivity.this,
                        L.t("Не удалось открыть: ") + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void openDownloaded(AttachmentInfo att) {
        if (att.path == null || !new File(att.path).exists()) return;
        if (att.isImage()) {
            Intent i = new Intent(this, PhotoActivity.class);
            i.putExtra(PhotoActivity.EXTRA_PATH, att.path);
            i.putExtra(PhotoActivity.EXTRA_NAME, att.name);
            try {
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, L.t("Не удалось открыть: ") + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        openFile(new File(att.path), att.mime);
    }

    private Uri uriFor(File f) {
        try {
            File attDir = new File(getFilesDir(), "att");
            File parent = f.getParentFile();
            if (parent != null && parent.getAbsolutePath().equals(attDir.getAbsolutePath())) {
                return Uri.parse("content://" + AttachProvider.AUTHORITY + "/att/"
                        + Uri.encode(f.getName()));
            }
        } catch (Exception ignored) {
        }
        return Uri.parse("content://" + AttachProvider.AUTHORITY + "/out/" + Uri.encode(f.getName()));
    }

    private void openFile(File f, String mime) {
        String type = mime == null ? null : mime.split(";")[0].trim();
        if (type == null || type.indexOf('/') < 0) {
            String ext = f.getName();
            int dot = ext.lastIndexOf('.');
            ext = dot > 0 ? ext.substring(dot + 1) : "";
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        }
        if (type == null) type = "*/*";
        Uri uri = uriFor(f);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, type);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, L.t("Открыть с помощью")));
        } catch (Exception e) {
            Toast.makeText(this, L.t("Нет приложения для этого файла"), Toast.LENGTH_SHORT).show();
        }
    }

    private void showAttachMenu() {
        final String[] items = {L.t("🖼  Изображение из галереи"), L.t("📷  Снимок с камеры"), L.t("📄  Файл")};
        new AlertDialog.Builder(this)
                .setTitle(L.t("Прикрепить"))
                .setItems(items, (d, w) -> {
                    if (w == 0) {
                        pickGallery();
                    } else if (w == 1) {
                        takePhoto();
                    } else {
                        pickFile();
                    }
                })
                .setNegativeButton(L.t("Отмена"), null)
                .show();
    }

    private void pickGallery() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i, L.t("Изображение")), REQ_GALLERY);
        } catch (Exception e) {
            Toast.makeText(this, L.t("Нет приложения галереи"), Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        try {
            File dir = new File(getCacheDir(), "send");
            if (!dir.exists()) dir.mkdirs();
            cameraFile = new File(dir, "cam_" + System.currentTimeMillis() + ".jpg");
            Uri out = Uri.parse("content://" + AttachProvider.AUTHORITY + "/send/"
                    + Uri.encode(cameraFile.getName()));
            Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, out);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            Toast.makeText(this, L.t("Нет приложения камеры"), Toast.LENGTH_SHORT).show();
        }
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_FILE);
        } catch (Exception e) {
            Toast.makeText(this, L.t("Не удалось открыть выбор файла"), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_AVATAR) {
            if (res == RESULT_OK && data != null && data.getData() != null) {
                boolean ok = Avatars.save(this, peerEmail, data.getData());
                applyAvatar();
                Toast.makeText(this, L.t(ok ? "Аватарка обновлена" : "Не удалось сохранить фото"),
                        Toast.LENGTH_SHORT).show();
                if (ok && contactDialog != null && contactDialog.isShowing()) reopenContactCard();
            }
            return;
        }
        if (req == REQ_CAMERA) {
            File shot = cameraFile;
            cameraFile = null;
            if (res == RESULT_OK && shot != null && shot.length() > 0) {
                pendingAttach = shot;
                updateAttachLabel();
            } else if (shot != null) {
                shot.delete();
            }
            return;
        }
        if (res != RESULT_OK) return;
        Uri uri = data == null ? null : data.getData();
        if (uri == null) return;
        if (req == REQ_EXPORT) {
            saveExport(uri);
            return;
        }
        if (req == REQ_FILE) {
            attachFromUri(uri, "file");
        } else if (req == REQ_GALLERY) {
            attachFromUri(uri, "photo.jpg");
        } else if (req == REQ_BG) {
            saveBg(uri);
        }
    }

    private void saveExport(Uri uri) {
        java.io.OutputStream os = null;
        try {
            os = getContentResolver().openOutputStream(uri);
            if (os == null) throw new java.io.IOException("no stream");
            os.write(dumpThread().getBytes("UTF-8"));
            os.flush();
            Toast.makeText(this, L.t("Переписка сохранена"), Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            db.log(L.t("Экспорт"), L.t("ошибка: ") + e);
            Toast.makeText(this, L.t("Не удалось сохранить: ") + e, Toast.LENGTH_LONG).show();
        } finally {
            if (os != null) try {
                os.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private void updateAttachLabel() {
        setAttachTint(pendingAttach != null);
        if (pendingAttach != null) {
            Toast.makeText(this, L.t("Вложение: ") + pendingAttach.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Подсвечивает скрепку, когда вложение уже прикреплено. */
    private void setAttachTint(boolean has) {
        if (btnAttach == null) return;
        int color = Ui.attr(this, has ? R.attr.c_accent : R.attr.c_text2);
        btnAttach.setImageTintList(ColorStateList.valueOf(color));
    }

    private void attachFromUri(final Uri uri, String fallback) {
        String name = fallback;
        try {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (c.moveToFirst() && idx >= 0) {
                    String n = c.getString(idx);
                    if (n != null && n.trim().length() > 0) name = n;
                }
                c.close();
            }
        } catch (Exception ignored) {
        }
        name = name == null || name.trim().isEmpty() ? fallback : name.replaceAll("[\\/:*?\"<>|]", "_");
        try {
            File dir = new File(getCacheDir(), "send");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, name);
            InputStream in = getContentResolver().openInputStream(uri);
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            in.close();
            fos.close();
            pendingAttach = out;
            updateAttachLabel();
        } catch (Exception e) {
            Toast.makeText(this, L.t("Не удалось прочитать файл"), Toast.LENGTH_SHORT).show();
        }
    }

    private SharedPreferences drafts() {
        return getSharedPreferences("drafts", Context.MODE_PRIVATE);
    }

    @Override
    protected void onPause() {
        LiveSync.setListener(null);
        super.onPause();
        saveScroll();
        if (debugChat) return;
        String t = input.getText().toString();
        if (t.trim().isEmpty()) {
            drafts().edit().remove(threadKey).apply();
        } else {
            drafts().edit().putString(threadKey, t).apply();
        }
    }

    private void clearReply() {
        replyMsg = null;
        replyBar.setVisibility(View.GONE);
    }

    private void startReply(MailMessage m) {
        replyMsg = m;
        String who = m.outgoing ? L.t("Вы") : (m.peerName != null && m.peerName.length() > 0
                ? m.peerName : m.peerEmail);
        replyAuthor.setText(who);
        String body = m.body == null ? "" : m.body.replace('\n', ' ').trim();
        if (body.length() > 140) body = body.substring(0, 140) + "…";
        replyText.setText(body);
        replyBar.setVisibility(View.VISIBLE);
        input.requestFocus();
    }

    private void showActions(final MailMessage m) {
        if (debugChat) {
            new AlertDialog.Builder(this)
                    .setTitle(L.t("Запись журнала"))
                    .setItems(new String[]{L.t("Копировать"), L.t("Удалить")}, (d, which) -> {
                        if (which == 0) {
                            Ui.copy(this, m.body == null ? "" : m.body);
                        } else {
                            db.deleteMessage(m.id);
                            load();
                        }
                    })
                    .setNegativeButton(L.t("Отмена"), null)
                    .show();
            return;
        }
        final java.util.ArrayList<String> labels = new java.util.ArrayList<String>();
        final java.util.ArrayList<Integer> ids = new java.util.ArrayList<Integer>();
        labels.add(L.t("Ответить")); ids.add(0);
        labels.add(L.t("Переслать")); ids.add(1);
        labels.add(L.t("Копировать текст")); ids.add(2);
        if (!m.outgoing) { labels.add(L.t("Отметить непрочитанным")); ids.add(3); }
        labels.add(L.t("Удалить (локально)")); ids.add(4);

        new AlertDialog.Builder(this)
                .setTitle(m.outgoing ? L.t("Ваше сообщение") : (m.peerName.length() > 0 ? m.peerName : m.peerEmail))
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    switch (ids.get(which)) {
                        case 0:
                            startReply(m);
                            break;
                        case 1:
                            forward(m);
                            break;
                        case 2:
                            copy(m.body);
                            break;
                        case 3:
                            db.markUnread(m.id);
                            load();
                            Toast.makeText(this, L.t("Помечено непрочитанным"), Toast.LENGTH_SHORT).show();
                            break;
                        case 4:
                            confirmDelete(m);
                            break;
                    }
                })
                .setNegativeButton(L.t("Отмена"), null)
                .show();
    }

    private void copy(String text) {
        Ui.copy(this, text);
    }

    private void forward(MailMessage m) {
        Intent i = new Intent(this, ComposeActivity.class);
        i.putExtra("fwd_subject", m.subject);
        i.putExtra("fwd_body", m.body);
        i.putExtra("fwd_from", m.outgoing ? L.t("Вы") : (m.peerName.length() > 0 ? m.peerName : m.peerEmail));
        startActivity(i);
    }

    private void confirmDelete(final MailMessage m) {
        new AlertDialog.Builder(this)
                .setTitle(L.t("Удалить сообщение?"))
                .setMessage(L.t("Удаляется только на этом телефоне, на сервере письмо остаётся."))
                .setNegativeButton(L.t("Отмена"), null)
                .setPositiveButton(L.t("Удалить"), (d, w) -> {
                    db.deleteMessage(m.id);
                    load();
                })
                .show();
    }

    private void send() {
        if (debugChat) return;
        if (peerEmail.length() == 0) {
            Toast.makeText(this, L.t("Нет адреса получателя"), Toast.LENGTH_SHORT).show();
            return;
        }
        final String text = input.getText().toString().trim();
        if (text.isEmpty() && pendingAttach == null) return;
        Account acc = db.account(accountId);
        if (acc == null) {
            List<Account> all = db.accounts();
            if (all.isEmpty()) {
                Toast.makeText(this, L.t("Нет аккаунта"), Toast.LENGTH_SHORT).show();
                return;
            }
            acc = all.get(0);
        }
        String subject = db.lastSubject(threadKey);
        if (subject == null || subject.trim().isEmpty()) {
            subject = L.t("Без темы");
        } else if (!subject.toLowerCase().startsWith("re:")) {
            subject = "Re: " + subject;
        }

        final Account fAcc = acc;
        final String fSubject = subject;
        final String fText = text;
        final File fAtt = pendingAttach;
        final String fTo = peerEmail;
        final String qAuthor;
        final String qText;
        if (replyMsg != null) {
            qAuthor = replyMsg.outgoing ? L.t("Вы")
                    : (replyMsg.peerName.length() > 0 ? replyMsg.peerName : replyMsg.peerEmail);
            qText = replyMsg.body == null ? "" : replyMsg.body;
        } else {
            qAuthor = "";
            qText = "";
        }

        input.setText("");
        pendingAttach = null;
        setAttachTint(false);
        clearReply();

        // Сразу показываем пузырь с часами, потом обновляем галочки.
        MailMessage loc = new MailMessage();
        loc.accountId = acc.id;
        loc.folder = "SENT";
        loc.uid = -(System.currentTimeMillis());
        loc.threadKey = threadKey;
        loc.peerName = peerName;
        loc.peerEmail = fTo;
        loc.subject = fSubject;
        loc.date = System.currentTimeMillis();
        String body = fText.length() == 0 ? "📎 " + (fAtt == null ? L.t("вложение") : fAtt.getName()) : fText;
        loc.body = body;
        String snip = body.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (snip.length() > 120) snip = snip.substring(0, 120) + "…";
        loc.snippet = snip;
        loc.seen = true;
        loc.outgoing = true;
        loc.hasAttach = fAtt != null;
        loc.quoteAuthor = qAuthor;
        loc.quoteText = qText;
        loc.state = MailMessage.ST_SENDING;
        final long localId = db.insertMessage(loc);
        load();

        new Thread(() -> {
            try {
                final String msgid = MailService.send(ChatActivity.this, db, fAcc, fTo, fSubject,
                        fText, fAtt, qAuthor, qText, localId);
                ui.post(() -> {
                    db.finishSend(localId, msgid, MailMessage.ST_SENT);
                    load();
                });
            } catch (final Throwable e) {
                ui.post(() -> {
                    db.updateMessageState(localId, MailMessage.ST_FAILED);
                    load();
                    String msg = String.valueOf(e.getMessage());
                    new AlertDialog.Builder(ChatActivity.this)
                            .setTitle(L.t("Письмо не отправлено"))
                            .setMessage(msg)
                            .setPositiveButton(L.t("Ок"), null)
                            .setNeutralButton(L.t("Скопировать"), (d, w) -> Ui.copy(ChatActivity.this, msg))
                            .show();
                });
            }
        }).start();
    }
}
