package de.mindarchitects.tlm.mail;

import java.util.Map;

/**
 * Mail-Vorlage mit Platzhaltern {titel}, {datum}, {ort}, {link}, {qr}.
 * Aus dem Klartext wird zusätzlich eine HTML-Fassung mit eingebettetem QR-Bild erzeugt.
 */
public final class MailTemplate {
    private MailTemplate() {}

    public static final String QR_CID = "qrcode";

    public record Rendered(String subject, String text, String html) {}

    public static Rendered render(String subject, String body, String title, String date, String location, String link) {
        Map<String, String> values = Map.of(
                "{titel}", nz(title),
                "{datum}", nz(date),
                "{ort}", nz(location));

        String subj = replace(subject, values).replace("{link}", "").replace("{qr}", "").strip();
        String filledBody = replace(body, values);

        String text = filledBody.replace("{link}", link).replace("{qr}", "").strip();

        String bodyForHtml = filledBody.contains("{qr}") ? filledBody : filledBody + "\n\n{qr}";
        String html = escape(bodyForHtml)
                .replace("{link}", "<a href=\"" + escape(link) + "\">" + escape(link) + "</a>")
                .replace("{qr}", "<img src=\"cid:" + QR_CID + "\" alt=\"QR-Code\" width=\"220\" height=\"220\">")
                .strip()
                .replace("\r\n", "\n")
                .replace("\n", "<br>\n");
        html = "<!doctype html><html><body style=\"font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.5\">\n"
                + html + "\n</body></html>";
        return new Rendered(subj, text, html);
    }

    private static String replace(String s, Map<String, String> values) {
        String result = nz(s);
        for (var e : values.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
