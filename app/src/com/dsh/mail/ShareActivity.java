package com.dsh.mail;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Приём файлов из других приложений («Поделиться»): копирует присланное во
 * временную папку и просит выбрать получателя, после чего открывает письмо.
 */
public class ShareActivity extends Activity {

    private Db db;
    private final List<ChatItem> contacts = new ArrayList<ChatItem>();
    private final List<ChatItem> visible = new ArrayList<ChatItem>();
    private final ArrayList<String> paths = new ArrayList<String>();
    private String text;
    private String subject;
    private String query = "";
    private ContactsAdapter adapter;
    private ListView list;
    private TextView empty;

    @Override
    protected void onCreate(Bundle b) {
        setTheme(Themes.style(this));
        super.onCreate(b);
        setContentView(R.layout.activity_share);
        L.localize(findViewById(android.R.id.content));
        Ui.edgeToEdge(this, R.id.root);
        db = new Db(this);

        Intent in = getIntent();
        text = in.getStringExtra(Intent.EXTRA_TEXT);
        subject = in.getStringExtra(Intent.EXTRA_SUBJECT);
        collectStreams(in);
        if (paths.isEmpty() && (text == null || text.trim().length() == 0)) {
            Toast.makeText(this, L.t("Нечего отправить"), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView summary = (TextView) findViewById(R.id.shareSummary);
        summary.setText(summaryText());

        list = (ListView) findViewById(R.id.shareList);
        empty = (TextView) findViewById(R.id.shareEmpty);
        adapter = new ContactsAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> openCompose(visible.get(pos).email));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.shareNew).setOnClickListener(v -> openCompose(""));

        EditText search = (EditText) findViewById(R.id.shareSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b1, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b1, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                query = e.toString().trim().toLowerCase();
                filter();
            }
        });

        for (ChatItem it : db.chats()) {
            if (it == null || it.email == null || it.email.trim().length() == 0) continue;
            if (Db.DEBUG_THREAD.equals(it.threadKey)) continue;
            contacts.add(it);
        }
        filter();
    }

    private void collectStreams(Intent in) {
        ArrayList<Uri> uris = new ArrayList<Uri>();
        Object stream = in.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream instanceof Uri) uris.add((Uri) stream);
        Object many = in.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (many instanceof ArrayList) {
            for (Object o : (ArrayList<?>) many) {
                if (o instanceof Uri) uris.add((Uri) o);
            }
        }
        for (Uri u : uris) {
            File f = Util.copyToCache(this, u, "send");
            if (f != null && f.length() > 0) paths.add(f.getAbsolutePath());
        }
    }

    private String summaryText() {
        if (!paths.isEmpty()) {
            String first = new File(paths.get(0)).getName();
            String label = paths.size() == 1
                    ? L.t("Файл: ") + first
                    : L.t("Файлов: ") + paths.size() + " · " + first;
            if (text != null && text.trim().length() > 0) {
                label += "  ·  " + preview(text);
            }
            return label;
        }
        return preview(text);
    }

    private String preview(String s) {
        String flat = s.replace('\n', ' ').replace('\r', ' ').trim();
        return flat.length() > 90 ? flat.substring(0, 90) + "…" : flat;
    }

    private void filter() {
        visible.clear();
        for (ChatItem it : contacts) {
            String name = it.name == null ? "" : it.name;
            String email = it.email == null ? "" : it.email;
            if (query.length() == 0
                    || name.toLowerCase().contains(query)
                    || email.toLowerCase().contains(query)) {
                visible.add(it);
            }
        }
        adapter.notifyDataSetChanged();
        boolean nothing = visible.isEmpty();
        empty.setVisibility(nothing ? View.VISIBLE : View.GONE);
        list.setVisibility(nothing ? View.GONE : View.VISIBLE);
    }

    private void openCompose(String email) {
        Intent i = new Intent(this, ComposeActivity.class);
        i.putExtra("to", email);
        if (subject != null && subject.length() > 0) i.putExtra("subject", subject);
        if (text != null && text.length() > 0) i.putExtra("body", text);
        i.putStringArrayListExtra("attach_paths", paths);
        startActivity(i);
        finish();
    }

    private class ContactsAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public Object getItem(int i) {
            return visible.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            View v = convert;
            if (v == null) {
                v = LayoutInflater.from(ShareActivity.this)
                        .inflate(R.layout.item_contact, parent, false);
            }
            ChatItem it = visible.get(pos);
            ((TextView) v.findViewById(R.id.name)).setText(it.name);
            ((TextView) v.findViewById(R.id.email)).setText(it.email);

            TextView avatar = (TextView) v.findViewById(R.id.avatar);
            avatar.setText(Ui.initials(it.name, it.email));
            avatar.setBackground(Ui.circle(Ui.avatarColor(it.email)));

            ImageView photo = (ImageView) v.findViewById(R.id.avatarImg);
            Bitmap bm = Avatars.load(ShareActivity.this, it.email, Ui.dp(ShareActivity.this, 40));
            if (bm != null) {
                photo.setImageBitmap(bm);
                photo.setVisibility(View.VISIBLE);
            } else {
                photo.setImageDrawable(null);
                photo.setVisibility(View.GONE);
            }
            return v;
        }
    }
}
