package com.dsh.mail;

/** Чистка HTML перед показом: стили и скрипты не должны попадать в текст письма. */
public final class HtmlText {

    private HtmlText() {
    }

    /**
     * Убирает то, что Android-парсер HTML иначе выдаёт как обычный текст:
     * &lt;style&gt; (CSS), &lt;script&gt;, &lt;head&gt; и HTML-комментарии, включая
     * условные комментарии Outlook.
     */
    public static String clean(String html) {
        if (html == null || html.length() == 0) return "";
        String s = html;
        s = s.replaceAll("(?is)<!--.*?-->", " ");
        s = s.replaceAll("(?is)<head\\b[^>]*>.*?</head\\s*>", " ");
        s = s.replaceAll("(?is)<(style|script|title)\\b[^>]*>.*?</\\1\\s*>", " ");
        // Незакрытый style/script: режем до конца — лучше без хвоста CSS, чем с ним.
        s = s.replaceAll("(?is)<(style|script)\\b[^>]*>.*$", " ");
        s = s.replaceAll("(?is)</?(style|script|head)\\b[^>]*>", " ");
        return layout(s);
    }

    /**
     * Возвращает блочную разметку в текст с переносами: Android-парсер HTML не делает
     * переводов строк для таблиц и div, поэтому чек/квитанция превращались в одну строку.
     */
    public static String layout(String html) {
        if (html == null || html.length() == 0) return "";
        String s = html;
        s = s.replaceAll("(?is)<br\\s*/?>", "<br>");
        s = s.replaceAll("(?is)</t[dh]\\s*>", " ");
        s = s.replaceAll("(?is)</tr\\s*>", "<br>");
        s = s.replaceAll("(?is)</(div|p|li|ul|ol|h[1-6]|blockquote|header|footer|section|article|table)\\s*>", "<br>");
        s = s.replaceAll("(?is)(<br>\\s*){3,}", "<br><br>");
        return s;
    }
}
