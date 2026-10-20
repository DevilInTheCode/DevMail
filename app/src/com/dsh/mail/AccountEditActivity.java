package com.dsh.mail;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class AccountEditActivity extends Activity {

    private Db db;
    private long accountId;
    private Account existing;

    private EditText label, email, imapHost, imapPort, smtpHost, smtpPort, username, password, interval;
    private int checkMin = 15;
    private CheckBox imapSsl, smtpSsl, trustAll;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        setTheme(Themes.style(this));
        super.onCreate(b);
        setContentView(R.layout.activity_account);
        L.localize(findViewById(android.R.id.content));
        Ui.edgeToEdge(this, R.id.root);
        db = new Db(this);
        accountId = getIntent().getLongExtra("account", 0);
        existing = accountId > 0 ? db.account(accountId) : null;

        label = (EditText) findViewById(R.id.label);
        email = (EditText) findViewById(R.id.email);
        imapHost = (EditText) findViewById(R.id.imapHost);
        imapPort = (EditText) findViewById(R.id.imapPort);
        smtpHost = (EditText) findViewById(R.id.smtpHost);
        smtpPort = (EditText) findViewById(R.id.smtpPort);
        username = (EditText) findViewById(R.id.username);
        password = (EditText) findViewById(R.id.password);
        imapSsl = (CheckBox) findViewById(R.id.imapSsl);
        smtpSsl = (CheckBox) findViewById(R.id.smtpSsl);
        trustAll = (CheckBox) findViewById(R.id.trustAll);
        interval = (EditText) findViewById(R.id.interval);

        Account a = existing != null ? existing : new Account();
        label.setText(a.label == null ? "" : a.label);
        email.setText(a.email == null ? "" : a.email);
        imapHost.setText(a.imapHost == null ? "" : a.imapHost);
        imapPort.setText(String.valueOf(a.imapPort <= 0 ? 993 : a.imapPort));
        smtpHost.setText(a.smtpHost == null ? "" : a.smtpHost);
        smtpPort.setText(String.valueOf(a.smtpPort <= 0 ? 465 : a.smtpPort));
        username.setText(a.username == null ? "" : a.username);
        password.setText(a.password == null ? "" : a.password);
        imapSsl.setChecked(a.imapSsl);
        smtpSsl.setChecked(a.smtpSsl);
        trustAll.setChecked(a.trustAll);
        checkMin = existing != null ? a.checkMin : 15;
        interval.setFocusable(false);
        interval.setClickable(true);
        interval.setOnClickListener(v -> pickInterval());
        showInterval();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnTest).setOnClickListener(v -> test());
        findViewById(R.id.btnDelete).setVisibility(existing != null ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
    }

    private Account collect() {
        Account a = new Account();
        a.id = accountId;
        a.label = label.getText().toString().trim();
        a.email = email.getText().toString().trim();
        a.imapHost = imapHost.getText().toString().trim();
        a.smtpHost = smtpHost.getText().toString().trim();
        a.username = username.getText().toString().trim();
        a.password = password.getText().toString();
        a.imapPort = parseInt(imapPort.getText().toString(), 993);
        a.smtpPort = parseInt(smtpPort.getText().toString(), 465);
        a.imapSsl = imapSsl.isChecked();
        a.smtpSsl = smtpSsl.isChecked();
        a.trustAll = trustAll.isChecked();
        a.checkMin = checkMin;
        if (a.username.length() == 0) a.username = a.email;
        return a;
    }

    static String labelFor(int m) {
        if (m < 0) return L.t("Мгновенно (постоянное соединение)");
        if (m == 0) return L.t("Только вручную");
        return L.t("Каждые ") + m + L.t(" мин");
    }

    private void showInterval() {
        interval.setText(labelFor(checkMin));
    }

    private void pickInterval() {
        final String[] labels = {
                L.t("Только вручную"),
                L.t("Мгновенно (постоянное соединение)"),
                L.t("Каждые 5 минут"),
                L.t("Каждые 15 минут"),
                L.t("Каждые 30 минут"),
                L.t("Каждые 60 минут")
        };
        final int[] values = {0, -1, 5, 15, 30, 60};
        int sel = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == checkMin) sel = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(L.t("Проверка почты"))
                .setSingleChoiceItems(labels, sel, (d, w) -> {
                    checkMin = values[w];
                    showInterval();
                    d.dismiss();
                })
                .setNegativeButton(L.t("Отмена"), null)
                .show();
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private String validate(Account a) {
        if (a.email.length() == 0 || a.email.indexOf('@') < 0) return L.t("Укажите адрес почты");
        if (a.imapHost.length() == 0) return L.t("Укажите IMAP-сервер");
        if (a.smtpHost.length() == 0) return L.t("Укажите SMTP-сервер");
        if (a.username.length() == 0) return L.t("Укажите логин");
        if (a.password.length() == 0) return L.t("Укажите пароль");
        return null;
    }

    private void test() {
        Account a = collect();
        String err = validate(a);
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, L.t("Проверяем соединение…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String res = MailService.test(a);
            final String info = res == null ? MailService.folderInfo(a) : null;
            if (res == null) {
                db.log(L.t("Проверка ок"), info == null ? "" : info);
            } else {
                db.log(L.t("Ошибка подключения"), res);
            }
            ui.post(() -> {
                if (isFinishing()) return;
                if (res == null) {
                    new AlertDialog.Builder(AccountEditActivity.this)
                            .setTitle(L.t("Подключение работает ✓"))
                            .setMessage(info == null ? "" : info)
                            .setPositiveButton(L.t("Ок"), null)
                            .show();
                } else {
                    AlertDialog.Builder dlg = new AlertDialog.Builder(AccountEditActivity.this)
                            .setTitle(L.t("Не удалось подключиться"))
                            .setMessage(res)
                            .setPositiveButton(L.t("Ок"), null)
                            .setNeutralButton(L.t("Скопировать"), (dd, ww) -> Ui.copy(AccountEditActivity.this, res));
                    if (isCertError(res)) {
                        dlg.setNegativeButton(L.t("Принимать сертификаты и повторить"), (d, w) -> {
                            trustAll.setChecked(true);
                            test();
                        });
                    }
                    dlg.show();
                }
            });
        }).start();
    }

    private static boolean isCertError(String res) {
        if (res == null) return false;
        String r = res.toLowerCase();
        return r.contains("certpathvalidator")
                || r.contains("certification path")
                || r.contains("pkix")
                || r.contains("trust anchor")
                || r.contains("certificate")
                || r.contains("sslhandshake");
    }

    private void save() {
        Account a = collect();
        String err = validate(a);
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
            return;
        }
        long id = db.saveAccount(a);
        if (id <= 0) {
            Toast.makeText(this, L.t("Не удалось сохранить аккаунт"), Toast.LENGTH_SHORT).show();
            return;
        }
        Schedule.reschedule(this);
        LiveService.apply(this);
        Toast.makeText(this, L.t("Сохранено"), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(L.t("Удалить аккаунт?"))
                .setMessage(L.t("Письма этого аккаунта тоже будут удалены."))
                .setNegativeButton(L.t("Отмена"), null)
                .setPositiveButton(L.t("Удалить"), (d, w) -> {
                    db.deleteAccount(accountId);
                    Schedule.reschedule(AccountEditActivity.this);
                    finish();
                })
                .show();
    }
}
