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
        return s;
    }
}
