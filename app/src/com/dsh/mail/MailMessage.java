package com.dsh.mail;

public class MailMessage {

    public String html = "";

    public static final int ST_SENDING = 1;
    public static final int ST_FAILED = 2;
    public static final int ST_SENT = 3;
    public static final int ST_DELIVERED = 4;

    /** 0 — обычное письмо, 1..4 — статус исходящего. */
    public int state;
    public long id;
    public long accountId;
    public String folder = "";
    public long uid;
    public String threadKey = "";
    public String peerName = "";
    public String peerEmail = "";
    public String subject = "";
    public long date;
    public String snippet = "";
    public String body = "";
    public boolean seen;
    public boolean outgoing;
    public String rawPath;
    public boolean hasAttach;
    public String msgid = "";
    public String quoteAuthor = "";
    public String quoteText = "";
}
