package de.mindarchitects.tlm;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Datumsformatierung zwischen API ("yyyy-MM-dd HH:mm:ss") und Anzeige. */
public final class Fmt {
    private Fmt() {}

    private static final DateTimeFormatter SQL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static LocalDateTime parse(String sql) {
        if (sql == null || sql.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(sql, SQL);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static String dateTime(LocalDateTime dt) {
        return dt == null ? "" : DISPLAY.format(dt);
    }

    public static String dateTime(String sql) {
        LocalDateTime dt = parse(sql);
        return dt == null ? (sql == null ? "" : sql) : dateTime(dt);
    }

    public static String nz(String s) {
        return s == null ? "" : s;
    }
}
