package com.dsh.mail;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ComposeActivity extends Activity {

    private static final int REQ_ATTACH = 22;

    private Db db;
    private List<Account> accounts = new ArrayList<Account>();
    private Spinner spinner;
    private EditText to;
    private EditText subject;
    private EditText body;
    private TextView attachName;
    private final List<File> pendingAttach = new ArrayList<File>();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        setTheme(Themes.style(this));
        super.onCreate(b);
        setContentView(R.layout.activity_compose);
        L.localize(findViewById(android.R.id.content));
        Ui.edgeToEdge(this, R.id.root);
        db = new Db(this);
        accounts = db.accounts();
        if (accounts.isEmpty()) {
            Toast.makeText(this, L.t("Сначала добавьте аккаунт"), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        spinner = (Spinner) findViewById(R.id.spinnerAccount);
        to = (EditText) findViewById(R.id.to);
        subject = (EditText) findViewById(R.id.subject);
        body = (EditText) findViewById(R.id.body);
        attachName = (TextView) findViewById(R.id.attachName);

        List<String> labels = new ArrayList<String>();
        for (Account a : accounts) labels.add(a.email);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, R.layout.spinner_item, labels);
        ad.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinner.setAdapter(ad);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSend).setOnClickListener(v -> send());
        findViewById(R.id.btnAttach).setOnClickListener(v -> pick());

        ArrayList<String> presetPaths = getIntent().getStringArrayListExtra("attach_paths");
        if (presetPaths != null) {
            for (String p : presetPaths) {
                if (p == null || p.length() == 0) continue;
                File f = new File(p);
                if (f.exists() && f.length() > 0) pendingAttach.add(f);
            }
            showAttachNames();
        }
        String presetBody = getIntent().getStringExtra("body");
        if (presetBody != null && presetBody.length() > 0) body.setText(presetBody);
        String presetTo = getIntent().getStringExtra("to");
        if (presetTo != null && presetTo.length() > 0) to.setText(presetTo);
        String presetSubject = getIntent().getStringExtra("subject");
        if (presetSubject != null && presetSubject.length() > 0) subject.setText(presetSubject);

        String fwdBody = getIntent().getStringExtra("fwd_body");
        if (fwdBody != null) {
            String fwdSubject = getIntent().getStringExtra("fwd_subject");
            String fwdFrom = getIntent().getStringExtra("fwd_from");
            StringBuilder sb = new StringBuilder(L.t("\n\n---------- Пересланное сообщение ----------\n"));
            if (fwdFrom != null) sb.append(L.t("От: ")).append(fwdFrom).append('\n');
            if (fwdSubject != null) sb.append(L.t("Тема: ")).append(fwdSubject).append('\n');
            sb.append('\n').append(fwdBody);
            body.setText(sb.toString());
            body.setSelection(0);
            if (fwdSubject != null && fwdSubject.length() > 0) {
                subject.setText(fwdSubject.toLowerCase().startsWith("fwd:")
                        ? fwdSubject : "Fwd: " + fwdSubject);
            }
        }
    }

    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_ATTACH);
        } catch (Exception e) {
            Toast.makeText(this, L.t("Не удалось открыть выбор файла"), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_ATTACH || res != RESULT_OK || data == null || data.getData() == null) return;
        File copied = Util.copyToCache(this, data.getData(), "send");
        if (copied == null) {
            Toast.makeText(this, L.t("Не удалось прочитать файл"), Toast.LENGTH_SHORT).show();
            return;
        }
        pendingAttach.add(copied);
        showAttachNames();
    }

    private void showAttachNames() {
        if (attachName == null) return;
        if (pendingAttach.isEmpty()) {
            attachName.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder("📎 ");
        for (int i = 0; i < pendingAttach.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(pendingAttach.get(i).getName());
        }
        attachName.setText(sb.toString());
    }

    private void send() {
        String addr = to.getText().toString().trim();
        if (addr.length() == 0 || addr.indexOf('@') < 0) {
            Toast.makeText(this, L.t("Укажите адрес получателя"), Toast.LENGTH_SHORT).show();
            return;
        }
        int pos = spinner.getSelectedItemPosition();
        if (pos < 0 || pos >= accounts.size()) pos = 0;
        final Account acc = accounts.get(pos);
        final String fTo = addr;
        final String fSubject = subject.getText().toString().trim();
        final String fBody = body.getText().toString();
        final List<File> fAtt = new ArrayList<File>(pendingAttach);
        Toast.makeText(this, L.t("Отправка…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                MailService.send(ComposeActivity.this, db, acc, fTo, fSubject, fBody, fAtt);
                ui.post(() -> {
                    Toast.makeText(ComposeActivity.this, L.t("Отправлено"), Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (final Exception e) {
                ui.post(() -> Toast.makeText(ComposeActivity.this,
                        L.t("Ошибка: ") + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
