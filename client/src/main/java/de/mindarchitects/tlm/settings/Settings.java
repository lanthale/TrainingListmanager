package de.mindarchitects.tlm.settings;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Nicht-geheime Einstellungen (java.util.prefs). Passwörter liegen in {@link Secrets}. */
public final class Settings {

    public enum SmtpSecurity { STARTTLS, SSL, NONE }

    public static final String DEFAULT_API_URL = "https://trainings.mind-architects.de/api.php";

    public static final String DEFAULT_SUBJECT = "Einladung: {titel}";

    public static final String DEFAULT_BODY = """
            Guten Tag,

            Sie sind herzlich zum Training „{titel}“ am {datum} in {ort} eingeladen.

            Bitte registrieren Sie sich über den folgenden QR-Code oder Ihren persönlichen Link:
            {link}

            {qr}

            Der Link ist nur für Sie bestimmt und kann einmal verwendet werden.

            Viele Grüße
            """;

    private final Preferences prefs = Preferences.userRoot().node("de/mindarchitects/traininglistmanager");

    public String apiUrl() { return prefs.get("apiUrl", DEFAULT_API_URL); }
    public void apiUrl(String v) { prefs.put("apiUrl", v.trim()); }

    public String smtpHost() { return prefs.get("smtpHost", "smtp.ionos.de"); }
    public void smtpHost(String v) { prefs.put("smtpHost", v.trim()); }

    public int smtpPort() { return prefs.getInt("smtpPort", 587); }
    public void smtpPort(int v) { prefs.putInt("smtpPort", v); }

    public SmtpSecurity smtpSecurity() {
        try {
            return SmtpSecurity.valueOf(prefs.get("smtpSecurity", SmtpSecurity.STARTTLS.name()));
        } catch (IllegalArgumentException e) {
            return SmtpSecurity.STARTTLS;
        }
    }
    public void smtpSecurity(SmtpSecurity v) { prefs.put("smtpSecurity", v.name()); }

    public String smtpUser() { return prefs.get("smtpUser", ""); }
    public void smtpUser(String v) { prefs.put("smtpUser", v.trim()); }

    public String fromAddress() { return prefs.get("fromAddress", ""); }
    public void fromAddress(String v) { prefs.put("fromAddress", v.trim()); }

    public String fromName() { return prefs.get("fromName", ""); }
    public void fromName(String v) { prefs.put("fromName", v.trim()); }

    public String mailSubject() { return prefs.get("mailSubject", DEFAULT_SUBJECT); }
    public void mailSubject(String v) { prefs.put("mailSubject", v); }

    /** Preferences-Werte sind auf 8 KB begrenzt; das reicht für einen Mailtext. */
    public String mailBody() { return prefs.get("mailBody", DEFAULT_BODY); }
    public void mailBody(String v) { prefs.put("mailBody", v.length() > 8000 ? v.substring(0, 8000) : v); }

    public void flush() {
        try {
            prefs.flush();
        } catch (BackingStoreException ignored) {
            // Wird beim Beenden ohnehin erneut versucht
        }
    }
}
