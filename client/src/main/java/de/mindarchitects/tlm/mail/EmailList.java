package de.mindarchitects.tlm.mail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Zerlegt eingefügte Adresslisten (Zeilen, Komma, Semikolon, "Name &lt;mail&gt;"). */
public final class EmailList {
    private EmailList() {}

    private static final Pattern ANGLE = Pattern.compile("<([^<>]+)>");
    private static final Pattern VALID = Pattern.compile("^[^\\s@<>\",;]+@[^\\s@<>\",;]+\\.[^\\s@<>\",;]{2,}$");

    public record Result(List<String> valid, List<String> invalid) {}

    public static Result parse(String text) {
        Map<String, String> valid = new LinkedHashMap<>(); // lower-case → Original, entfernt Duplikate
        List<String> invalid = new ArrayList<>();
        if (text == null) {
            return new Result(List.of(), List.of());
        }
        for (String part : text.split("[\\r\\n,;\\t]+")) {
            String entry = part.strip();
            if (entry.isEmpty()) {
                continue;
            }
            Matcher m = ANGLE.matcher(entry);
            String email = (m.find() ? m.group(1) : entry).strip();
            if (email.regionMatches(true, 0, "mailto:", 0, 7)) {
                email = email.substring(7);
            }
            if (VALID.matcher(email).matches()) {
                valid.putIfAbsent(email.toLowerCase(), email);
            } else {
                invalid.add(entry);
            }
        }
        return new Result(new ArrayList<>(valid.values()), invalid);
    }
}
