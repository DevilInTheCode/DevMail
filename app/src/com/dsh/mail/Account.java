package com.dsh.mail;

public class Account {
    public long id;
    public String label = "";
    public String email = "";
    public String imapHost = "";
    public int imapPort = 993;
    public boolean imapSsl = true;
    public String smtpHost = "";
    public int smtpPort = 465;
    public boolean smtpSsl = true;
    public String username = "";
    public String password = "";
    public int checkMin = 15;
    public boolean trustAll;
    public long lastSync;

    public String title() {
        if (label != null && label.trim().length() > 0) return label;
        return email;
    }
}
