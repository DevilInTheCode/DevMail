package com.dsh.mail;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class AccountAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<Account> items = new ArrayList<Account>();

    public AccountAdapter(Context c) {
        ctx = c;
    }

    public void setItems(List<Account> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public Account item(int pos) {
        return items.get(pos);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convert, ViewGroup parent) {
        View v = convert;
        if (v == null) v = LayoutInflater.from(ctx).inflate(R.layout.item_account, parent, false);
        Account a = items.get(position);
        TextView title = (TextView) v.findViewById(R.id.title);
        title.setText(a.title());
        TextView sub = (TextView) v.findViewById(R.id.sub);
        StringBuilder sb = new StringBuilder(a.email);
        sb.append(" · ").append(a.imapHost).append(":").append(a.imapPort);
        sb.append(" · ").append(a.checkMin < 0 ? L.t("мгновенно")
                : a.checkMin == 0 ? L.t("вручную") : L.t("каждые ") + a.checkMin + L.t(" мин"));
        sub.setText(sb.toString());
        return v;
    }
}
