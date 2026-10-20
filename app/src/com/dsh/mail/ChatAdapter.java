package com.dsh.mail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChatAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<ChatItem> items = new ArrayList<ChatItem>();
    private Map<Long, Integer> accColors = new HashMap<Long, Integer>();

    /** Цвета аккаунтов: один и тот же в шапке и в метках чатов. */
    public void setAccountColors(Map<Long, Integer> colors) {
        accColors = colors == null ? new HashMap<Long, Integer>() : colors;
    }

    private Map<String, String> drafts = new HashMap<String, String>();
    private final Set<String> selected = new HashSet<String>();
    private boolean showAccount;

    public ChatAdapter(Context c) {
        ctx = c;
    }

    public void setItems(List<ChatItem> list, boolean multiAccount, Map<String, String> draftMap) {
        items.clear();
        items.addAll(list);
        showAccount = multiAccount;
        drafts = draftMap == null ? new HashMap<String, String>() : draftMap;
        notifyDataSetChanged();
    }

    public void setSelected(Set<String> keys) {
        selected.clear();
        if (keys != null) selected.addAll(keys);
        notifyDataSetChanged();
    }

    public ChatItem item(int pos) {
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
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.item_chat, parent, false);
        }
        final ChatItem it = items.get(position);
        ((TextView) v.findViewById(R.id.swipeReadLabel)).setText(L.t("Прочитано"));
        ((TextView) v.findViewById(R.id.swipeDeleteLabel)).setText(L.t("Удалить"));

        View content = v.findViewById(R.id.rowContent);
        content.setTranslationX(0);
        content.setBackgroundResource(selected.contains(it.threadKey)
                ? R.drawable.row_chat_sel : R.drawable.row_chat);

        TextView avatar = (TextView) v.findViewById(R.id.avatar);
        avatar.setText(Ui.initials(it.name, it.email));
        avatar.setBackground(Ui.circle(Ui.avatarColor(it.email)));
        ImageView avatarImg = (ImageView) v.findViewById(R.id.avatarImg);
        Bitmap photo = Avatars.load(ctx, it.email, Ui.dp(ctx, 46));
        if (photo != null) {
            avatarImg.setImageBitmap(photo);
            avatarImg.setVisibility(View.VISIBLE);
        } else {
            avatarImg.setImageDrawable(null);
            avatarImg.setVisibility(View.GONE);
        }

        TextView name = (TextView) v.findViewById(R.id.name);
        name.setText((it.pinned ? "📌 " : "") + it.name + (it.muted ? "  🔕" : ""));

        TextView time = (TextView) v.findViewById(R.id.time);
        time.setText(Ui.chatTime(it.date));

        TextView snippet = (TextView) v.findViewById(R.id.snippet);
        String draft = drafts.get(it.threadKey);
        if (draft != null && draft.trim().length() > 0) {
            String flat = draft.replace('\n', ' ').replace('\r', ' ').trim();
            snippet.setText(L.t("Черновик: ") + flat);
            snippet.setTextColor(Ui.attr(ctx, R.attr.c_accent));
            snippet.setTypeface(null, Typeface.ITALIC);
        } else {
            snippet.setText(it.outgoing ? L.t("Вы: ") + it.snippet : it.snippet);
            snippet.setTextColor(it.unread > 0 ? Ui.attr(ctx, R.attr.c_text)
                    : Ui.attr(ctx, R.attr.c_text2));
            snippet.setTypeface(null, it.unread > 0 ? Typeface.BOLD : Typeface.NORMAL);
        }

        TextView badge = (TextView) v.findViewById(R.id.badge);
        if (it.unread > 0) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(it.unread > 99 ? "99+" : String.valueOf(it.unread));
            badge.setBackgroundResource(it.muted ? R.drawable.chip_bg : R.drawable.badge_bg);
            badge.setTextColor(it.muted ? Ui.attr(ctx, R.attr.c_text2)
                    : Ui.attr(ctx, R.attr.c_on_accent));
        } else {
            badge.setVisibility(View.GONE);
        }

        TextView tag = (TextView) v.findViewById(R.id.accountTag);
        if (showAccount && it.accountEmail != null) {
            tag.setVisibility(View.VISIBLE);
            tag.setText(it.accountEmail);
            Integer col = accColors.get(it.accountId);
            tag.setTextColor(col != null ? col : Ui.attr(ctx, R.attr.c_accent));
        } else {
            tag.setVisibility(View.GONE);
        }
        return v;
    }
}
