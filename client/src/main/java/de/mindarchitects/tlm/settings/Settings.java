package de.mindarchitects.tlm.settings;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Nicht-geheime Einstellungen (java.util.prefs). Der API-Schlüssel liegt in {@link Secrets}. */
public final class Settings {

    public static final String DEFAULT_API_URL = "https://trainings.mind-architects.de/api.php";

    /** Vorlage für die Mail mit der Anwesenheitsliste. Platzhalter: {titel} {datum} {ort} {anzahl}. */
    public static final String DEFAULT_REPORT_SUBJECT = "Anwesenheitsliste: {titel} ({datum})";

    public static final String DEFAULT_REPORT_BODY = """
            Guten Tag,

            anbei die Anwesenheitsliste zum Training „{titel}“ am {datum} in {ort} mit {anzahl} Teilnehmer(n).

            Viele Grüße
            """;

    private static final int SETTINGS_VERSION = 3;

    private final Preferences prefs = Preferences.userRoot().node("de/mindarchitects/traininglistmanager");
    private boolean removedSmtp;

    public Settings() {
        migrate();
    }

    /** Version 3: E-Mail-Versand per SMTP entfällt – alte SMTP- und Einladungs-Einstellungen entfernen. */
    private void migrate() {
        if (prefs.getInt("settingsVersion", 1) >= SETTINGS_VERSION) {
            return;
        }
        for (String key : new String[]{"smtpHost", "smtpPort", "smtpSecurity", "smtpUser", "fromAddress",
                "fromName", "mailSubject", "mailBody"}) {
            prefs.remove(key);
        }
        removedSmtp = true;
        prefs.putInt("settingsVersion", SETTINGS_VERSION);
        flush();
    }

    /** true direkt nach der Umstellung auf Version 3 (dann das alte SMTP-Passwort aus dem Schlüsselbund löschen). */
    public boolean removedSmtp() {
        return removedSmtp;
    }

    public String apiUrl() { return prefs.get("apiUrl", DEFAULT_API_URL); }
    public void apiUrl(String v) { prefs.put("apiUrl", v.trim()); }

    /** Beim Start automatisch auf GitHub nach einer neuen Version suchen. */
    public boolean checkUpdates() { return prefs.getBoolean("checkUpdates", true); }
    public void checkUpdates(boolean v) { prefs.putBoolean("checkUpdates", v); }

    /** Version, die der Nutzer mit „Überspringen“ ausgeblendet hat. */
    public String skippedVersion() { return prefs.get("skippedVersion", ""); }
    public void skippedVersion(String v) { prefs.put("skippedVersion", v); }

    public String exportDir() { return prefs.get("exportDir", System.getProperty("user.home")); }
    public void exportDir(String v) { prefs.put("exportDir", v); }

    /** Empfänger der Anwesenheitsliste (optional, wird im Mail-Entwurf vorbelegt). */
    public String reportRecipient() { return prefs.get("reportRecipient", ""); }
    public void reportRecipient(String v) { prefs.put("reportRecipient", v.trim()); }

    public String reportSubject() { return prefs.get("reportSubject", DEFAULT_REPORT_SUBJECT); }
    public void reportSubject(String v) { prefs.put("reportSubject", v); }

    /** Preferences-Werte sind auf 8 KB begrenzt; das reicht für einen Mailtext. */
    public String reportBody() { return prefs.get("reportBody", DEFAULT_REPORT_BODY); }
    public void reportBody(String v) { prefs.put("reportBody", v.length() > 8000 ? v.substring(0, 8000) : v); }

    public void flush() {
        try {
            prefs.flush();
        } catch (BackingStoreException ignored) {
            // Wird beim Beenden ohnehin erneut versucht
        }
    }
}
