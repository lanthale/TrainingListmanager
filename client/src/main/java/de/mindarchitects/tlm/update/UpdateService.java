package de.mindarchitects.tlm.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Software-Update über GitHub Releases: neueste Version abfragen, passenden Installer
 * herunterladen (mit SHA-256-Prüfung) und starten.
 */
public final class UpdateService {

    public static final String REPO = "lanthale/TrainingListmanager";
    private static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String USER_AGENT = "TrainingListmanager-Updater";

    public enum Os { MAC, WINDOWS, LINUX, OTHER }

    /**
     * Öffentlicher Ed25519-Schlüssel für die Update-Signaturen (X.509, Base64).
     * Der private Schlüssel liegt nur als GitHub-Secret UPDATE_SIGNING_KEY vor
     * (siehe client/packaging/SignRelease.java).
     */
    static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAer0T0PlKCAO7DQUyNX9IcQ4VBuoh8qlfs4qo1ifnGZI=";

    /** signatureUrl ist null, wenn das Release keine Signatur (.sig) für den Installer enthält. */
    public record Asset(String name, long size, String url, String sha256, String signatureUrl) {
        public boolean signed() {
            return signatureUrl != null;
        }
    }

    /** asset ist null, wenn es für dieses System keinen passenden Installer gibt. */
    public record Release(String version, String tag, String name, String notes, String pageUrl, Asset asset) {}

    @FunctionalInterface
    public interface DownloadProgress {
        void update(long done, long total);
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper json = new ObjectMapper();

    public static Os os() {
        String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (n.contains("mac")) return Os.MAC;
        if (n.contains("win")) return Os.WINDOWS;
        if (n.contains("linux")) return Os.LINUX;
        return Os.OTHER;
    }

    /** Neuestes veröffentlichtes Release (ohne Entwürfe und Vorabversionen). */
    public Release latest() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(LATEST))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", USER_AGENT)
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) {
            throw new IOException("Noch kein Release auf GitHub veröffentlicht.");
        }
        if (res.statusCode() == 403 || res.statusCode() == 429) {
            throw new IOException("GitHub-Abfragelimit erreicht. Bitte später erneut versuchen.");
        }
        if (res.statusCode() != 200) {
            throw new IOException("GitHub antwortet mit HTTP " + res.statusCode());
        }
        JsonNode r = json.readTree(res.body());
        String tag = r.path("tag_name").asText();
        return new Release(
                AppVersion.normalize(tag),
                tag,
                r.path("name").asText(tag),
                r.path("body").asText(""),
                r.path("html_url").asText("https://github.com/" + REPO + "/releases"),
                pickAsset(r.path("assets")).orElse(null));
    }

    /** Liefert ein neueres Release als die installierte Version, sonst leer. */
    public Optional<Release> checkForUpdate() throws IOException, InterruptedException {
        Release r = latest();
        return AppVersion.isNewer(r.version(), AppVersion.current()) ? Optional.of(r) : Optional.empty();
    }

    private static Optional<Asset> pickAsset(JsonNode assets) {
        String ext = switch (os()) {
            case MAC -> ".dmg";
            case WINDOWS -> ".msi";
            case LINUX -> ".deb";
            case OTHER -> null;
        };
        if (ext == null) {
            return Optional.empty();
        }
        for (JsonNode a : assets) {
            String name = a.path("name").asText();
            if (name.toLowerCase(Locale.ROOT).endsWith(ext)) {
                String digest = a.path("digest").asText("");
                String sha = digest.startsWith("sha256:") ? digest.substring(7) : null;
                String sigUrl = null;
                for (JsonNode s : assets) {
                    if (s.path("name").asText().equals(name + ".sig")) {
                        sigUrl = s.path("browser_download_url").asText();
                    }
                }
                return Optional.of(new Asset(name, a.path("size").asLong(),
                        a.path("browser_download_url").asText(), sha, sigUrl));
            }
        }
        return Optional.empty();
    }

    /**
     * Lädt den Installer in ein temporäres Verzeichnis, prüft die SHA-256-Prüfsumme und die
     * Ed25519-Signatur. Ohne gültige Signatur wird die Datei gelöscht und nichts installiert.
     */
    public Path download(Asset asset, DownloadProgress progress, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        if (!asset.signed()) {
            throw new IOException("Dieses Update ist nicht signiert und wird aus Sicherheitsgründen nicht installiert.");
        }
        byte[] signature = fetchSignature(asset.signatureUrl());
        HttpRequest req = HttpRequest.newBuilder(URI.create(asset.url()))
                .timeout(Duration.ofMinutes(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/octet-stream")
                .GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            throw new IOException("Download fehlgeschlagen (HTTP " + res.statusCode() + ")");
        }
        long total = res.headers().firstValueAsLong("Content-Length").orElse(asset.size());

        Path dir = Files.createTempDirectory("tlm-update");
        Path target = dir.resolve(asset.name().replaceAll("[\\\\/:]", "_"));
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException(e);
        }
        try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                if (cancelled.getAsBoolean()) {
                    throw new IOException("Download abgebrochen.");
                }
                out.write(buf, 0, n);
                sha.update(buf, 0, n);
                done += n;
                progress.update(done, total);
            }
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        if (asset.sha256() != null) {
            String actual = HexFormat.of().formatHex(sha.digest());
            if (!actual.equalsIgnoreCase(asset.sha256())) {
                Files.deleteIfExists(target);
                throw new IOException("Prüfsumme stimmt nicht – Download verworfen.");
            }
        }
        if (!verifySignature(target, signature)) {
            Files.deleteIfExists(target);
            throw new IOException("Signatur ungültig – Download verworfen. Das Update stammt nicht vom Herausgeber.");
        }
        return target;
    }

    private byte[] fetchSignature(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/octet-stream")
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Signatur konnte nicht geladen werden (HTTP " + res.statusCode() + ")");
        }
        try {
            return Base64.getDecoder().decode(res.body().strip());
        } catch (IllegalArgumentException e) {
            throw new IOException("Signaturdatei ist beschädigt.");
        }
    }

    /** Prüft die Ed25519-Signatur einer Datei gegen den eingebauten öffentlichen Schlüssel. */
    static boolean verifySignature(Path file, byte[] signature) throws IOException {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    s.update(buf, 0, n);
                }
            }
            return s.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /**
     * Startet die Installation. Windows: MSI-Upgrade über msiexec.
     * macOS: Disk-Image öffnen (App in „Programme“ ziehen). Linux: Paket mit der Softwareverwaltung öffnen.
     */
    public void launchInstaller(Path file) throws IOException {
        ProcessBuilder pb = switch (os()) {
            case WINDOWS -> new ProcessBuilder("msiexec", "/i", file.toString());
            case MAC -> new ProcessBuilder("open", file.toString());
            case LINUX -> new ProcessBuilder("xdg-open", file.toString());
            case OTHER -> throw new IOException("Automatische Installation wird auf diesem System nicht unterstützt.");
        };
        pb.inheritIO().start();
    }
}
