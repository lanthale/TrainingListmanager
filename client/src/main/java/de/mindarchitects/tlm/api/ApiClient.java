package de.mindarchitects.tlm.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import de.mindarchitects.tlm.api.Model.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Zugriff auf public/api.php per HTTPS mit Header X-Api-Key. */
public class ApiClient {

    private static final String OUTDATED_SERVER = "Auf dem Server läuft noch die alte API ohne QR-Codes pro Training. "
            + "Bitte public/api.php und src/invitation.php hochladen und "
            + "sql/migrations/004_training_code_consent.sql in phpMyAdmin ausführen.";

    private static final DateTimeFormatter SQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper json = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ApiClient(String baseUrl, String apiKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ApiException("Keine API-URL eingestellt (Einstellungen).");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException("Kein API-Schlüssel eingestellt (Einstellungen).");
        }
        this.baseUrl = baseUrl.trim();
        this.apiKey = apiKey.trim();
        requireHttps(this.baseUrl);
    }

    /** Der API-Schlüssel darf nie unverschlüsselt übertragen werden (Ausnahme: lokaler Test). */
    private static void requireHttps(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new ApiException("API-URL ist ungültig: " + url);
        }
        String host = uri.getHost() == null ? "" : uri.getHost();
        boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !local) {
            throw new ApiException("Die API-URL muss mit https:// beginnen, sonst würde der API-Schlüssel "
                    + "unverschlüsselt übertragen.");
        }
    }

    public void ping() {
        get("ping", Map.of());
    }

    public List<Training> trainings() {
        return convert(get("trainings", Map.of()), TrainingList.class).trainings();
    }

    public TrainingDetail training(long id) {
        JsonNode node = get("training", Map.of("id", String.valueOf(id)));
        if (!node.has("code")) {
            // Ältere api.php kennt noch keine QR-Codes pro Training
            throw new ApiException(OUTDATED_SERVER);
        }
        return convert(node, TrainingDetail.class);
    }

    /** Legt das Training an; der Server erzeugt dabei gleich den QR-Code. */
    public CreatedTraining createTraining(String title, String description, LocalDateTime startsAt, String location) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("starts_at", startsAt == null ? null : startsAt.format(SQL_DATETIME));
        body.put("location", location);
        return convert(post("create_training", body), CreatedTraining.class);
    }

    public void setActive(long id, boolean active) {
        post("set_active", Map.of("id", id, "active", active));
    }

    /** Neuer QR-Code für das Training; der bisherige wird ungültig. */
    public Code createCode(long trainingId) {
        try {
            return convert(post("create_code", Map.of("training_id", trainingId)), CodeResult.class).code();
        } catch (ApiException e) {
            throw e.getMessage() != null && e.getMessage().startsWith("Unbekannte Aktion") ? new ApiException(OUTDATED_SERVER) : e;
        }
    }

    /** Löscht das Training mit allen Registrierungen und QR-Codes endgültig. */
    public void deleteTraining(long id) {
        post("delete_training", Map.of("id", id));
    }

    // ---------------------------------------------------------------------

    private JsonNode get(String action, Map<String, String> params) {
        StringBuilder url = new StringBuilder(actionUrl(action));
        params.forEach((k, v) -> url.append('&').append(enc(k)).append('=').append(enc(v)));
        return send(request(url.toString()).GET().build());
    }

    private JsonNode post(String action, Object body) {
        try {
            String payload = json.writeValueAsString(body);
            return send(request(actionUrl(action))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build());
        } catch (IOException e) {
            throw new ApiException("JSON-Fehler: " + e.getMessage(), e);
        }
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("X-Api-Key", apiKey);
    }

    private JsonNode send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException("Server nicht erreichbar: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Abgebrochen", e);
        }
        JsonNode node;
        try {
            node = json.readTree(response.body());
        } catch (IOException e) {
            throw new ApiException(nonJsonHint(response.statusCode()), e);
        }
        if (response.statusCode() >= 400) {
            String msg = node != null && node.hasNonNull("error") ? node.get("error").asText() : "Fehler";
            throw new ApiException(msg + " (HTTP " + response.statusCode() + ")");
        }
        return node;
    }

    /** Erklärung, wenn der Server statt JSON z. B. eine HTML-Fehlerseite liefert. */
    private String nonJsonHint(int status) {
        String hint = switch (status) {
            case 403 -> "Zugriff auf api.php verweigert. Sind api.php und die aktuelle public/.htaccess hochgeladen?";
            case 404 -> "api.php nicht gefunden. API-URL in den Einstellungen und Upload prüfen.";
            case 500 -> "PHP-Fehler auf dem Server. PHP-Version (mind. 8.1) und Upload von src/ prüfen.";
            default -> "Der Server hat kein JSON geliefert. API-URL prüfen.";
        };
        return "Ungültige Antwort vom Server (HTTP " + status + "): " + hint + "\nURL: " + baseUrl;
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        try {
            return json.treeToValue(node, type);
        } catch (IOException e) {
            throw new ApiException("Unerwartete Antwort: " + e.getMessage(), e);
        }
    }

    private String actionUrl(String action) {
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "action=" + enc(action);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
