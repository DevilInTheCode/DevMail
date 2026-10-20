package com.dsh.mail;

public class ChatItem {
    public String threadKey = "";
    public String name = "";
    public String email = "";
    public String snippet = "";
    /** Текст последнего входящего — для уведомления (в списке может быть и наш ответ). */
    public String inSnippet = "";
    public long date;
    public int unread;
    public long accountId;
    public String accountEmail = "";
    public boolean outgoing;
    public boolean pinned;
    public boolean muted;
}
