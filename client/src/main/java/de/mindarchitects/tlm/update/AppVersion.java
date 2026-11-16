package de.mindarchitects.tlm.update;

/** Installierte Programmversion und Versionsvergleich. */
public final class AppVersion {
    private AppVersion() {}

    public static final String DEV = "0.0.0-dev";

    /**
     * Version des laufenden Programms: zuerst -Dapp.version (setzt der Installer),
     * dann Implementation-Version aus dem Jar-Manifest, sonst {@link #DEV}.
     */
    public static String current() {
        String v = System.getProperty("app.version");
        if (v == null || v.isBlank()) {
            v = AppVersion.class.getPackage().getImplementationVersion();
        }
        return v == null || v.isBlank() ? DEV : normalize(v);
    }

    public static boolean isDev() {
        return DEV.equals(current());
    }

    /** "v1.2.3" → "1.2.3". */
    public static String normalize(String v) {
        String s = v.strip();
        return s.startsWith("v") || s.startsWith("V") ? s.substring(1) : s;
    }

    /** Vergleicht "1.2.3" mit "1.10.0" numerisch; Suffixe wie "-SNAPSHOT" zählen als älter. */
    public static int compare(String a, String b) {
        String[] pa = normalize(a).split("-", 2);
        String[] pb = normalize(b).split("-", 2);
        String[] na = pa[0].split("\\.");
        String[] nb = pb[0].split("\\.");
        for (int i = 0; i < Math.max(na.length, nb.length); i++) {
            int x = i < na.length ? parse(na[i]) : 0;
            int y = i < nb.length ? parse(nb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        boolean sa = pa.length > 1, sb = pb.length > 1;
        return sa == sb ? 0 : sa ? -1 : 1;
    }

    public static boolean isNewer(String candidate, String installed) {
        return compare(candidate, installed) > 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D.*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
