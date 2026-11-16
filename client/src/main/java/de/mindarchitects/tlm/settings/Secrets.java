package de.mindarchitects.tlm.settings;

import com.github.javakeyring.Keyring;

import java.util.HashMap;
import java.util.Map;

/**
 * API-Schlüssel und SMTP-Passwort im Schlüsselbund des Betriebssystems
 * (macOS-Keychain, Windows Credential Manager, Linux Secret Service).
 * Ist keiner verfügbar, werden die Werte nur bis zum Beenden im Speicher gehalten.
 */
public final class Secrets {

    public static final String API_KEY = "api-key";
    public static final String SMTP_PASSWORD = "smtp-password";

    private static final String SERVICE = "TrainingListmanager";

    private final Keyring keyring;
    private final Map<String, String> memory = new HashMap<>();

    public Secrets() {
        Keyring k;
        try {
            k = Keyring.create();
        } catch (Exception | LinkageError e) {
            k = null;
        }
        this.keyring = k;
    }

    public boolean isPersistent() {
        return keyring != null;
    }

    public String get(String name) {
        if (memory.containsKey(name)) {
            return memory.get(name);
        }
        if (keyring != null) {
            try {
                String value = keyring.getPassword(SERVICE, name);
                memory.put(name, value);
                return value;
            } catch (Exception e) {
                return "";
            }
        }
        return "";
    }

    public void set(String name, String value) {
        value = value == null ? "" : value;
        memory.put(name, value);
        if (keyring == null) {
            return;
        }
        try {
            if (value.isEmpty()) {
                keyring.deletePassword(SERVICE, name);
            } else {
                keyring.setPassword(SERVICE, name, value);
            }
        } catch (Exception ignored) {
            // z. B. Löschen eines nicht vorhandenen Eintrags
        }
    }
}
