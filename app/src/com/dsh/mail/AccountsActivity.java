package com.dsh.mail;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/** Отдельный экран «Мои аккаунты»: список ящиков, добавление и редактирование. */
public class AccountsActivity extends Activity {

    private Db db;
    private AccountAdapter adapter;
    private ListView list;
    private TextView empty;

    @Override
    protected void onCreate(Bundle b) {
        setTheme(Themes.style(this));
        super.onCreate(b);
        setContentView(R.layout.activity_accounts);
        L.localize(findViewById(android.R.id.content));
        Ui.edgeToEdge(this, R.id.root);
        db = new Db(this);

        list = (ListView) findViewById(R.id.listAccounts);
        empty = (TextView) findViewById(R.id.empty);
        adapter = new AccountAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Account a = adapter.item(pos);
            Intent i = new Intent(this, AccountEditActivity.class);
            i.putExtra("account", a.id);
            startActivity(i);
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAdd).setOnClickListener(v ->
                startActivity(new Intent(this, AccountEditActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        List<Account> accs = db.accounts();
        adapter.setItems(accs);
        boolean nothing = accs.isEmpty();
        empty.setVisibility(nothing ? View.VISIBLE : View.GONE);
        list.setVisibility(nothing ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }
}
