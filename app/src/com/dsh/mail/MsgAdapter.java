package com.dsh.mail;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MsgAdapter extends BaseAdapter {

    private static final int T_DATE = 0;
    private static final int T_IN = 1;
    private static final int T_OUT = 2;
    private static final int T_NEW = 3;

    private static class Row {
        int type;
        MailMessage msg;
        String label;
        long date;
    }

    /** Нажатие на вложение в пузыре: скачать / открыть. */
    public interface OnAttach {
        void onAttach(MailMessage m, AttachmentInfo a);
    }

    private final Context ctx;
    private final List<Row> rows = new ArrayList<Row>();
    private OnAttach onAttach;
    private Map<Long, List<AttachmentInfo>> atts = new HashMap<Long, List<AttachmentInfo>>();
    private long highlightId;
    /** id, до которого всё прочитано: более новые входящие подсвечиваются. */
    private long readMark;

    public MsgAdapter(Context c) {
        ctx = c;
    }

    public void setOnAttach(OnAttach l) {
        onAttach = l;
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Кнопки/превью вложений под текстом пузыря. */
    private void bindAttachments(final View v, final MailMessage m) {
        LinearLayout box = (LinearLayout) v.findViewById(R.id.attBox);
        if (box == null) return;
        box.removeAllViews();
        List<AttachmentInfo> list = atts.get(m.id);
        if (list == null || list.isEmpty()) {
            box.setVisibility(View.GONE);
            return;
        }
        box.setVisibility(View.VISIBLE);
        for (final AttachmentInfo a : list) {
            if (a.isImage() && a.downloaded()) {
                ImageView iv = new ImageView(ctx);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        dp(200), ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(4);
                iv.setLayoutParams(lp);
                iv.setAdjustViewBounds(true);
                iv.setMaxHeight(dp(260));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setBackgroundResource(R.drawable.att_img_bg);
                Bitmap bm = AttachStore.thumb(a, dp(240));
                if (bm != null) iv.setImageBitmap(bm);
                else iv.setImageResource(android.R.drawable.ic_menu_gallery);
                iv.setOnClickListener(x -> {
                    if (onAttach != null) onAttach.onAttach(m, a);
                });
                box.addView(iv);
            } else {
                TextView row = new TextView(ctx);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(4);
                row.setLayoutParams(lp);
                row.setTextSize(13);
                row.setTextColor(Ui.attr(ctx, R.attr.c_text));
                row.setBackgroundResource(R.drawable.chip_bg);
                row.setPadding(dp(10), dp(7), dp(10), dp(7));
                StringBuilder sb = new StringBuilder();
                sb.append(a.isImage() ? "🖼 " : "📎 ").append(a.name);
                String sz = a.prettySize();
                if (sz.length() > 0) sb.append(" · ").append(sz);
                if (!a.downloaded()) sb.append(" · ⬇ ").append(L.t("Загрузить"));
                row.setText(sb.toString());
                row.setOnClickListener(x -> {
                    if (onAttach != null) onAttach.onAttach(m, a);
                });
                box.addView(row);
            }
        }
    }

    public void setHighlight(long messageId) {
        highlightId = messageId;
        notifyDataSetChanged();
    }

    public void setReadMark(long id) {
        readMark = id;
    }

    /** Убирает разделитель «Новые сообщения»: пользователь дочитал до конца. */
    public void clearNewDivider() {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i).type == T_NEW) rows.remove(i);
        }
    }

    public void setMessages(List<MailMessage> list, Map<Long, List<AttachmentInfo>> attachments) {
        setMessages(list, attachments, 0);
    }

    /**
     * @param newMark id последнего просмотренного письма: перед первым более новым
     *                входящим вставляется разделитель «Новые сообщения».
     */
    public void setMessages(List<MailMessage> list, Map<Long, List<AttachmentInfo>> attachments, long newMark) {
        rows.clear();
        atts = attachments;
        long lastDay = -1;
        boolean marked = false;
        for (MailMessage m : list) {
            if (lastDay < 0 || !Ui.sameDay(lastDay, m.date)) {
                Row r = new Row();
                r.type = T_DATE;
                r.label = Ui.dayLabel(m.date);
                r.date = m.date;
                rows.add(r);
                lastDay = m.date;
            }
            if (!marked && newMark > 0 && !m.outgoing && m.id > newMark) {
                Row nr = new Row();
                nr.type = T_NEW;
                nr.label = L.t("Новые сообщения");
                rows.add(nr);
                marked = true;
            }
            Row r = new Row();
            r.type = m.outgoing ? T_OUT : T_IN;
            r.msg = m;
            r.date = m.date;
            rows.add(r);
        }
        notifyDataSetChanged();
    }

    /** Весь текст переписки — для кнопки «Скопировать журнал». */
    public String dumpText() {
        StringBuilder sb = new StringBuilder();
        for (Row r : rows) {
            if (r.msg == null) continue;
            sb.append(Ui.timeShort(r.msg.date)).append(' ')
                    .append(Ui.dayLabel(r.msg.date)).append('\n')
                    .append(r.msg.body == null ? "" : r.msg.body).append("\n\n");
        }
        return sb.toString().trim();
    }

    public MailMessage messageAt(int pos) {
        if (pos < 0 || pos >= rows.size()) return null;
        return rows.get(pos).msg;
    }

    public boolean isNewDivider(int pos) {
        return pos >= 0 && pos < rows.size() && rows.get(pos).type == T_NEW;
    }

    /** Дата, к которой относится строка: для разделителя — его день, для письма — дата письма. */
    public long dateAt(int pos) {
        if (pos < 0 || pos >= rows.size()) return -1;
        Row r = rows.get(pos);
        if (r.type == T_NEW) return -1;
        return r.date;
    }

    /** Позиция разделителя «Новые сообщения», иначе -1. */
    public int newDividerPos() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).type == T_NEW) return i;
        }
        return -1;
    }

    public int positionOfMessage(long id) {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.msg != null && r.msg.id == id) return i;
        }
        return -1;
    }

    /** Первое письмо на позиции pos или ниже (пропускаем разделители дней). */
    public MailMessage messageNear(int pos) {
        for (int i = Math.max(0, pos); i < rows.size() && i < pos + 4; i++) {
            if (rows.get(i).msg != null) return rows.get(i).msg;
        }
        return null;
    }

    /** Сколько писем (не разделителей) находится ниже позиции pos. */
    /** Есть ли строки ниже позиции (для кнопки «вниз»). */
    public boolean hasMessagesAfter(int pos) {
        for (int i = Math.max(0, pos) + 1; i < rows.size(); i++) {
            if (rows.get(i).msg != null) return true;
        }
        return false;
    }

    /** Непрочитанные входящие ниже позиции: всё, что новее отметки просмотра. */
    public int unreadAfter(int pos, long seenMark) {
        int n = 0;
        for (int i = Math.max(0, pos) + 1; i < rows.size(); i++) {
            MailMessage m = rows.get(i).msg;
            if (m != null && !m.outgoing && m.id > seenMark) n++;
        }
        return n;
    }

    /** id последнего письма в списке. */
    public long maxId() {
        long id = 0;
        for (Row r : rows) {
            if (r.msg != null && r.msg.id > id) id = r.msg.id;
        }
        return id;
    }

    /** Первая строка, дата которой не раньше midnight; -1 — таких нет. */
    public int positionForDay(long midnight) {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.type == T_NEW) continue;
            if (r.date >= midnight) return i;
        }
        return -1;
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public boolean isEnabled(int position) {
        return rows.get(position).type != T_DATE;
    }

    @Override
    public View getView(int position, View convert, ViewGroup parent) {
        Row r = rows.get(position);
        if (r.type == T_NEW) {
            View v = convert;
            if (v == null) {
                v = LayoutInflater.from(ctx).inflate(R.layout.item_new, parent, false);
            }
            ((TextView) v.findViewById(R.id.newChip)).setText(r.label);
            return v;
        }
        if (r.type == T_DATE) {
            View v = convert;
            if (v == null) {
                v = LayoutInflater.from(ctx).inflate(R.layout.item_date, parent, false);
            }
            ((TextView) v.findViewById(R.id.chip)).setText(r.label);
            return v;
        }
        boolean out = r.type == T_OUT;
        View v = convert;
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(
                    out ? R.layout.item_msg_out : R.layout.item_msg_in, parent, false);
        }
        final MailMessage m = r.msg;
        v.setTranslationX(0);
        if (!out) {
            boolean unread = readMark > 0 && m.id > readMark;
            v.findViewById(R.id.bubble).setBackgroundResource(
                    unread ? R.drawable.bubble_in_unread : R.drawable.bubble_in);
        }
        View quoteBox = v.findViewById(R.id.quoteBox);
        if (m.quoteText != null && m.quoteText.trim().length() > 0) {
            quoteBox.setVisibility(View.VISIBLE);
            ((TextView) v.findViewById(R.id.quoteAuthor)).setText(
                    m.quoteAuthor != null && m.quoteAuthor.length() > 0 ? m.quoteAuthor : L.t("Цитата"));
            ((TextView) v.findViewById(R.id.quoteText)).setText(m.quoteText);
        } else {
            quoteBox.setVisibility(View.GONE);
        }
        TextView body = (TextView) v.findViewById(R.id.body);
        CharSequence text = m.body == null ? "" : m.body;
        if (!out && m.html != null && m.html.trim().length() > 0) {
            try {
                CharSequence rich = Html.fromHtml(m.html, Html.FROM_HTML_MODE_LEGACY);
                if (rich != null && rich.length() > 0) text = rich;
            } catch (Throwable ignored) {
            }
        }
        body.setText(text);
        boolean hasAtts = atts.get(m.id) != null && !atts.get(m.id).isEmpty();
        String empty = L.t("(пустое сообщение)");
        body.setVisibility(hasAtts && (text.length() == 0 || empty.equals(text.toString()))
                ? View.GONE : View.VISIBLE);
        body.setBackgroundResource(highlightId != 0 && m.id == highlightId ? R.drawable.hl_bg : 0);
        body.setMovementMethod(text instanceof Spanned ? LinkMovementMethod.getInstance() : null);
        bindAttachments(v, m);
        TextView meta = (TextView) v.findViewById(R.id.meta);
        meta.setText(Ui.timeShort(m.date));

        TextView state = (TextView) v.findViewById(R.id.state);
        if (state != null) {
            String mark = "";
            if (m.outgoing) {
                if (m.state == MailMessage.ST_SENDING) mark = "🕓";
                else if (m.state == MailMessage.ST_FAILED) mark = "⚠";
                else if (m.state == MailMessage.ST_SENT) mark = "✓";
                else if (m.state == MailMessage.ST_DELIVERED) mark = "✓✓";
            }
            state.setText(mark);
            state.setVisibility(mark.length() == 0 ? View.GONE : View.VISIBLE);
            if (m.state == MailMessage.ST_FAILED) {
                state.setTextColor(0xFFFF5252);
            } else {
                state.setTextColor(Ui.attr(ctx, R.attr.c_text2));
            }
        }
        return v;
    }
}
