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

    /** Vorbelegung für den Mailversand (Postfach von mind-architects). Das Passwort trägt der Nutzer selbst ein. */
    public static final String DEFAULT_SMTP_HOST = "smtps.udag.de";
    public static final int DEFAULT_SMTP_PORT = 465;
    public static final SmtpSecurity DEFAULT_SMTP_SECURITY = SmtpSecurity.SSL;
    public static final String DEFAULT_MAILBOX = "office@mind-architects.de";

    private static final int SETTINGS_VERSION = 2;

    private final Preferences prefs = Preferences.userRoot().node("de/mindarchitects/traininglistmanager");

    public Settings() {
        migrate();
    }

    /** Ersetzt die alte IONOS-Vorbelegung einmalig durch die neue SMTP-Konfiguration. */
    private void migrate() {
        if (prefs.getInt("settingsVersion", 1) >= SETTINGS_VERSION) {
            return;
        }
        String host = prefs.get("smtpHost", "");
        if (host.isEmpty() || host.equalsIgnoreCase("smtp.ionos.de")) {
            prefs.remove("smtpHost");
            prefs.remove("smtpPort");
            prefs.remove("smtpSecurity");
        }
        if (prefs.get("smtpUser", "").isEmpty()) {
            prefs.remove("smtpUser");
        }
        if (prefs.get("fromAddress", "").isEmpty()) {
            prefs.remove("fromAddress");
        }
        prefs.putInt("settingsVersion", SETTINGS_VERSION);
        flush();
    }

    public String apiUrl() { return prefs.get("apiUrl", DEFAULT_API_URL); }
    public void apiUrl(String v) { prefs.put("apiUrl", v.trim()); }

    public String smtpHost() { return prefs.get("smtpHost", DEFAULT_SMTP_HOST); }
    public void smtpHost(String v) { prefs.put("smtpHost", v.trim()); }

    public int smtpPort() { return prefs.getInt("smtpPort", DEFAULT_SMTP_PORT); }
    public void smtpPort(int v) { prefs.putInt("smtpPort", v); }

    public SmtpSecurity smtpSecurity() {
        try {
            return SmtpSecurity.valueOf(prefs.get("smtpSecurity", DEFAULT_SMTP_SECURITY.name()));
        } catch (IllegalArgumentException e) {
            return DEFAULT_SMTP_SECURITY;
        }
    }
    public void smtpSecurity(SmtpSecurity v) { prefs.put("smtpSecurity", v.name()); }

    public String smtpUser() { return prefs.get("smtpUser", DEFAULT_MAILBOX); }
    public void smtpUser(String v) { prefs.put("smtpUser", v.trim()); }

    public String fromAddress() { return prefs.get("fromAddress", DEFAULT_MAILBOX); }
    public void fromAddress(String v) { prefs.put("fromAddress", v.trim()); }

    public String fromName() { return prefs.get("fromName", ""); }
    public void fromName(String v) { prefs.put("fromName", v.trim()); }

    public String exportDir() { return prefs.get("exportDir", System.getProperty("user.home")); }
    public void exportDir(String v) { prefs.put("exportDir", v); }

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
